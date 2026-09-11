package com.nexulor.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditWalletCommand(UUID walletId, BigDecimal amount, String currency) {
}
