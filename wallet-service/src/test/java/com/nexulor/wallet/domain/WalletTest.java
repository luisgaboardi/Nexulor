package com.nexulor.wallet.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletTest {

    @Test
    void opensWithZeroBalance() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), "BRL");

        assertEquals(Money.zero("BRL"), wallet.balance());
        assertEquals(0L, wallet.version());
    }

    @Test
    void creditsAndDebitsKeepInvariant() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), "BRL");

        wallet.credit(Money.of("100.00", "BRL"));
        wallet.debit(Money.of("40.00", "BRL"));

        assertEquals(Money.of("60.00", "BRL"), wallet.balance());
    }

    @Test
    void debitFailsWhenBalanceIsInsufficient() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), "USD");
        wallet.credit(Money.of("10.00", "USD"));

        assertThrows(InsufficientFundsException.class, () -> wallet.debit(Money.of("10.01", "USD")));
        assertEquals(Money.of("10.00", "USD"), wallet.balance());
    }
}
