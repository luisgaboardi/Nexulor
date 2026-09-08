package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.Wallet;
import com.nexulor.wallet.domain.WalletAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletApplicationServiceTest {

    @Mock
    private WalletRepository walletRepository;

    private WalletApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WalletApplicationService(walletRepository);
    }

    @Test
    void createsWalletForNewOwner() {
        UUID ownerId = UUID.randomUUID();
        when(walletRepository.existsByOwnerId(ownerId)).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Wallet wallet = service.createWallet(new CreateWalletCommand(ownerId, "BRL"));

        assertEquals(ownerId, wallet.ownerId());
        assertEquals("BRL", wallet.balance().currencyCode());
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void rejectsDuplicateOwnerWallet() {
        UUID ownerId = UUID.randomUUID();
        when(walletRepository.existsByOwnerId(ownerId)).thenReturn(true);

        assertThrows(
                WalletAlreadyExistsException.class,
                () -> service.createWallet(new CreateWalletCommand(ownerId, "BRL")));

        verify(walletRepository, never()).save(any());
    }
}
