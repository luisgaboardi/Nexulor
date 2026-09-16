package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.FraudEvaluationPort;
import com.nexulor.wallet.application.port.TransferIdempotencyPort;
import com.nexulor.wallet.application.port.TransferRepository;
import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;
import com.nexulor.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferIdempotencyTest {

    private static final String KEY = "client-order-42";

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private FraudEvaluationPort fraudEvaluationPort;

    @Mock
    private TransferIdempotencyPort idempotencyPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransferApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TransferApplicationService(
                walletRepository, transferRepository, fraudEvaluationPort, idempotencyPort, eventPublisher);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @Test
    void replaysStoredTransferWhenKeyWasCompleted() {
        UUID transferId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(idempotencyPort.tryBegin(eq(KEY), anyString()))
                .thenReturn(TransferIdempotencyPort.Outcome.REPLAY_COMPLETED);
        when(idempotencyPort.findCompleted(KEY)).thenReturn(Optional.of(
                new TransferIdempotencyPort.StoredResponse(transferId, "fp")));
        Transfer stored = Transfer.restore(
                transferId,
                sourceId,
                destinationId,
                Money.of("10.00", "BRL"),
                com.nexulor.wallet.domain.TransferStatus.COMPLETED,
                java.time.Instant.now(),
                java.time.Instant.now());
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(stored));

        Transfer result = service.transfer(
                new TransferCommand(sourceId, destinationId, new BigDecimal("10.00"), "BRL"), KEY);

        assertEquals(transferId, result.id());
        verify(walletRepository, never()).lockById(any());
        verify(fraudEvaluationPort, never()).evaluate(any(), any(), any(), any(), anyString());
        Mockito.verifyNoInteractions(eventPublisher);
    }

    @Test
    void refusesConcurrentRequestWithSameKey() {
        when(idempotencyPort.tryBegin(eq(KEY), anyString()))
                .thenReturn(TransferIdempotencyPort.Outcome.IN_FLIGHT);

        assertThrows(
                TransferApplicationService.IdempotencyKeyInFlightException.class,
                () -> service.transfer(command(), KEY));

        verify(walletRepository, never()).lockById(any());
    }

    @Test
    void refusesSameKeyWithDifferentPayload() {
        when(idempotencyPort.tryBegin(eq(KEY), anyString()))
                .thenReturn(TransferIdempotencyPort.Outcome.CONFLICT);

        assertThrows(
                TransferApplicationService.IdempotencyKeyConflictException.class,
                () -> service.transfer(command(), KEY));

        verify(walletRepository, never()).lockById(any());
    }

    @Test
    void completesKeyAfterSuccessfulTransfer() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(idempotencyPort.tryBegin(eq(KEY), anyString()))
                .thenReturn(TransferIdempotencyPort.Outcome.ACQUIRED);
        when(fraudEvaluationPort.evaluate(any(), any(), any(), any(), anyString()))
                .thenReturn(FraudEvaluationPort.Decision.APPROVE);
        when(walletRepository.lockById(any(UUID.class))).thenAnswer(invocation ->
                fundedWallet(invocation.getArgument(0), "50.00"));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.transfer(command(), KEY);

        verify(idempotencyPort).complete(eq(KEY), anyString(), any(UUID.class));
    }

    @Test
    void doesNotCompleteKeyWhenTransferFails() {
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(idempotencyPort.tryBegin(eq(KEY), anyString()))
                .thenReturn(TransferIdempotencyPort.Outcome.ACQUIRED);
        when(fraudEvaluationPort.evaluate(any(), any(), any(), any(), anyString()))
                .thenReturn(FraudEvaluationPort.Decision.REJECT);

        assertThrows(
                TransferApplicationService.FraudRejectedException.class,
                () -> service.transfer(command(), KEY));

        verify(idempotencyPort, never()).complete(anyString(), anyString(), any());
    }

    private TransferCommand command() {
        return new TransferCommand(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"), "BRL");
    }

    private static Wallet fundedWallet(UUID id, String amount) {
        Wallet wallet = Wallet.open(UUID.randomUUID(), "BRL");
        wallet.credit(Money.of(amount, "BRL"));
        return Wallet.restore(
                id,
                wallet.ownerId(),
                wallet.balance(),
                wallet.version(),
                wallet.createdAt(),
                wallet.updatedAt());
    }
}
