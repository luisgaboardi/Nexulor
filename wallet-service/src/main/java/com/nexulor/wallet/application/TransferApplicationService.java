package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.FraudEvaluationPort;
import com.nexulor.wallet.application.port.TransferIdempotencyPort;
import com.nexulor.wallet.application.port.TransferRepository;
import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.InvalidTransferException;
import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;
import com.nexulor.wallet.domain.TransferNotFoundException;
import com.nexulor.wallet.domain.Wallet;
import com.nexulor.wallet.domain.WalletNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TransferApplicationService {

    static final int MONEY_SCALE = 2;

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final FraudEvaluationPort fraudEvaluation;
    private final TransferIdempotencyPort idempotency;
    private final ApplicationEventPublisher eventPublisher;

    public TransferApplicationService(
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            FraudEvaluationPort fraudEvaluation,
            TransferIdempotencyPort idempotency,
            ApplicationEventPublisher eventPublisher) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.fraudEvaluation = fraudEvaluation;
        this.idempotency = idempotency;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes a P2P transfer under a single ACID transaction.
     * Wallets are locked in UUID order to prevent deadlocks under concurrent transfers.
     * Fraud is evaluated BEFORE any lock or balance mutation (fail-closed, ADR-005):
     * rejected or unreachable fraud never moves money.
     *
     * <p>Phase 3: the {@code idempotencyKey} guards against duplicate submissions
     * across instances via Redis (ADR-002); a matching replay returns the original
     * transfer, a changed payload under the same key is a 409 conflict. The
     * completion event is published to Kafka only after commit (ADR-004).</p>
     */
    @Transactional
    public Transfer transfer(TransferCommand command, String idempotencyKey) {
        validate(command);

        String fingerprint = fingerprintOf(command);

        TransferIdempotencyPort.Outcome outcome = idempotency.tryBegin(idempotencyKey, fingerprint);
        switch (outcome) {
            case REPLAY_COMPLETED -> {
                TransferIdempotencyPort.StoredResponse stored =
                        idempotency.findCompleted(idempotencyKey).orElseThrow();
                return transferRepository.findById(stored.transferId())
                        .orElseThrow(() -> new IllegalStateException(
                                "idempotency record references unknown transfer " + stored.transferId()));
            }
            case IN_FLIGHT -> throw new IdempotencyKeyInFlightException(
                    "a request with this Idempotency-Key is already being processed");
            case CONFLICT -> throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used with a different request payload");
            case ACQUIRED -> { /* proceed, complete() below releases the lock */ }
        }

        Transfer transfer = executeTransfer(command);
        idempotency.complete(idempotencyKey, fingerprint, transfer.id());
        return transfer;
    }

    /**
     * Cheap request validation before the idempotency protocol so invalid
     * requests never consume an idempotency slot.
     */
    private void validate(TransferCommand command) {
        if (command.sourceWalletId().equals(command.destinationWalletId())) {
            throw new InvalidTransferException("source and destination wallets must differ");
        }
        Money amount = Money.of(command.amount(), command.currency());
        if (amount.isZero()) {
            throw new InvalidTransferException("transfer amount must be greater than zero");
        }
    }

    private Transfer executeTransfer(TransferCommand command) {
        Money amount = Money.of(command.amount(), command.currency());
        UUID transferId = UUID.randomUUID();
        evaluateFraud(transferId, command, amount);

        List<UUID> lockOrder = List.of(command.sourceWalletId(), command.destinationWalletId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        Wallet first = lockWallet(lockOrder.get(0));
        Wallet second = lockWallet(lockOrder.get(1));

        Wallet source = first.id().equals(command.sourceWalletId()) ? first : second;
        Wallet destination = first.id().equals(command.destinationWalletId()) ? first : second;

        source.debit(amount);
        destination.credit(amount);

        walletRepository.save(source);
        walletRepository.save(destination);

        Transfer transfer = Transfer.completed(source.id(), destination.id(), amount);
        Transfer saved = transferRepository.save(transfer);

        publishCompletedEvent(saved);
        return saved;
    }

    /**
     * Registers the AFTER_COMMIT publication of the transfer completion event.
     * Must be called inside the active transaction; the event reaches Kafka
     * only if the transfer commits.
     */
    private void publishCompletedEvent(Transfer transfer) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "transfer must run inside a transaction so events are published after commit");
        }
        eventPublisher.publishEvent(TransactionCompletedEvent.from(
                transfer.id(),
                transfer.sourceWalletId(),
                transfer.destinationWalletId(),
                transfer.amount().amount(),
                transfer.amount().currencyCode(),
                transfer.completedAt()));
    }

    private String fingerprintOf(TransferCommand command) {
        String canonical = "%s|%s|%s|%s".formatted(
                command.sourceWalletId(),
                command.destinationWalletId(),
                command.amount().stripTrailingZeros().toPlainString(),
                command.currency());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void evaluateFraud(UUID transferId, TransferCommand command, Money amount) {
        BigInteger amountMinor = amount.amount()
                .movePointRight(MONEY_SCALE)
                .toBigIntegerExact();
        FraudEvaluationPort.Decision decision = fraudEvaluation.evaluate(
                transferId,
                command.sourceWalletId(),
                command.destinationWalletId(),
                amountMinor,
                command.currency());
        switch (decision) {
            case APPROVE, REVIEW -> { /* REVIEW handled as approve in Phase 2 (ADR-005) */ }
            case REJECT -> throw new FraudRejectedException(
                    "transfer rejected by fraud rule");
        }
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("transfer not found: " + transferId));
    }

    @Transactional(readOnly = true)
    public List<Transfer> listByWallet(UUID walletId) {
        if (walletRepository.findById(walletId).isEmpty()) {
            throw new WalletNotFoundException("wallet not found: " + walletId);
        }
        return transferRepository.findByWalletId(walletId);
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.lockById(walletId);
    }

    /**
     * Marker exception so the API layer can map fraud rejections to a
     * domain-level 422 response without leaking the fraud service contract.
     */
    public static class FraudRejectedException extends RuntimeException {
        public FraudRejectedException(String message) {
            super(message);
        }
    }

    /** Same key is being processed by another request (409 retry later). */
    public static class IdempotencyKeyInFlightException extends RuntimeException {
        public IdempotencyKeyInFlightException(String message) {
            super(message);
        }
    }

    /** Same key, different payload (409, client bug — never replay). */
    public static class IdempotencyKeyConflictException extends RuntimeException {
        public IdempotencyKeyConflictException(String message) {
            super(message);
        }
    }
}
