package com.nexulor.wallet.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateWalletCommand(UUID ownerId, String currency) {
}
