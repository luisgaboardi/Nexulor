package com.nexulor.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransferTest {

    @Test
    void createsCompletedTransfer() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();

        Transfer transfer = Transfer.completed(source, destination, Money.of("25.00", "BRL"));

        assertEquals(TransferStatus.COMPLETED, transfer.status());
        assertEquals(source, transfer.sourceWalletId());
        assertEquals(destination, transfer.destinationWalletId());
    }

    @Test
    void rejectsSameWalletTransfer() {
        UUID walletId = UUID.randomUUID();

        assertThrows(
                InvalidTransferException.class,
                () -> Transfer.completed(walletId, walletId, Money.of("1.00", "BRL")));
    }
}
