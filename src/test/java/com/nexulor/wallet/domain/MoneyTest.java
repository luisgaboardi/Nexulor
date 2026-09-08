package com.nexulor.wallet.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void addsSameCurrencyAmounts() {
        Money left = Money.of("10.50", "BRL");
        Money right = Money.of("1.25", "BRL");

        Money result = left.add(right);

        assertEquals(new BigDecimal("11.7500"), result.amount());
        assertEquals("BRL", result.currencyCode());
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> Money.of("-1.00", "USD"));
    }

    @Test
    void subtractThrowsWhenInsufficientFunds() {
        Money balance = Money.of("5.00", "USD");
        Money debit = Money.of("5.0001", "USD");

        InsufficientFundsException ex = assertThrows(
                InsufficientFundsException.class,
                () -> balance.subtract(debit));

        assertTrue(ex.getMessage().contains("insufficient funds"));
    }

    @Test
    void rejectsCurrencyMismatch() {
        Money brl = Money.of("1.00", "BRL");
        Money usd = Money.of("1.00", "USD");

        assertThrows(CurrencyMismatchException.class, () -> brl.add(usd));
    }
}
