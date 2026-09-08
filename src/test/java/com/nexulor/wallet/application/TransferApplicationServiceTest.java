package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.TransferRepository;
import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.InsufficientFundsException;
import com.nexulor.wallet.domain.InvalidTransferException;
import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;
import com.nexulor.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferApplicationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransferRepository transferRepository;

    private TransferApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TransferApplicationService(walletRepository, transferRepository);
    }

    @Test
    void transfersFundsBetweenWallets() {
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID destinationId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Wallet source = fundedWallet(sourceId, "100.00");
        Wallet destination = fundedWallet(destinationId, "10.00");

        when(walletRepository.lockById(sourceId)).thenReturn(source);
        when(walletRepository.lockById(destinationId)).thenReturn(destination);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transfer result = service.transfer(new TransferCommand(
                sourceId,
                destinationId,
                new BigDecimal("40.00"),
                "BRL"));

        assertEquals(Money.of("60.00", "BRL"), source.balance());
        assertEquals(Money.of("50.00", "BRL"), destination.balance());
        assertEquals(sourceId, result.sourceWalletId());
        assertEquals(destinationId, result.destinationWalletId());

        verify(walletRepository, times(2)).save(any(Wallet.class));
        verify(transferRepository).save(any(Transfer.class));
    }

    @Test
    void locksWalletsInDeterministicOrder() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Wallet source = fundedWallet(higher, "100.00");
        Wallet destination = fundedWallet(lower, "0.00");

        when(walletRepository.lockById(lower)).thenReturn(destination);
        when(walletRepository.lockById(higher)).thenReturn(source);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.transfer(new TransferCommand(higher, lower, new BigDecimal("10.00"), "BRL"));

        ArgumentCaptor<UUID> lockOrder = ArgumentCaptor.forClass(UUID.class);
        verify(walletRepository, times(2)).lockById(lockOrder.capture());
        assertEquals(lower, lockOrder.getAllValues().get(0));
        assertEquals(higher, lockOrder.getAllValues().get(1));
    }

    @Test
    void rejectsInsufficientFundsWithoutPersistingTransfer() {
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID destinationId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        when(walletRepository.lockById(sourceId)).thenReturn(fundedWallet(sourceId, "5.00"));
        when(walletRepository.lockById(destinationId)).thenReturn(fundedWallet(destinationId, "0.00"));

        assertThrows(
                InsufficientFundsException.class,
                () -> service.transfer(new TransferCommand(
                        sourceId,
                        destinationId,
                        new BigDecimal("5.01"),
                        "BRL")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    void rejectsSelfTransfer() {
        UUID walletId = UUID.randomUUID();

        assertThrows(
                InvalidTransferException.class,
                () -> service.transfer(new TransferCommand(
                        walletId,
                        walletId,
                        new BigDecimal("1.00"),
                        "BRL")));

        verify(walletRepository, never()).lockById(any());
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
