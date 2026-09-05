package com.nexulor.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency) {
}
