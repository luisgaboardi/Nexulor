package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.FraudEvaluationPort;
import com.nexulor.wallet.application.port.TransferRepository;
import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.InvalidTransferException;
import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;
import com.nexulor.wallet.domain.TransferNotFoundException;
import com.nexulor.wallet.domain.Wallet;
import com.nexulor.wallet.domain.WalletNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TransferApplicationService {

    static final int MONEY_SCALE = 2;

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final FraudEvaluationPort fraudEvaluation;

    public TransferApplicationService(
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            FraudEvaluationPort fraudEvaluation) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.fraudEvaluation = fraudEvaluation;
    }

    /**
     * Executes a P2P transfer under a single ACID transaction.
     * Wallets are locked in UUID order to prevent deadlocks under concurrent transfers.
     * Fraud is evaluated BEFORE any lock or balance mutation (fail-closed, ADR-005):
     * rejected or unreachable fraud never moves money.
     */
    @Transactional
    public Transfer transfer(TransferCommand command) {
        if (command.sourceWalletId().equals(command.destinationWalletId())) {
            throw new InvalidTransferException("source and destination wallets must differ");
        }

        Money amount = Money.of(command.amount(), command.currency());
        if (amount.isZero()) {
            throw new InvalidTransferException("transfer amount must be greater than zero");
        }

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
        return transferRepository.save(transfer);
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
}
