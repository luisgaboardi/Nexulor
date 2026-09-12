package com.nexulor.fraud.domain;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Transfer context to evaluate. Amount is expressed in minor units to stay
 * consistent with the wire contract and avoid floating point artifacts.
 */
public record TransactionContext(
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigInteger amountMinor,
        String currencyCode) {

    public TransactionContext {
        if (transferId == null || sourceWalletId == null || destinationWalletId == null) {
            throw new IllegalArgumentException("wallet and transfer ids are required");
        }
        if (amountMinor == null || amountMinor.signum() <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode is required");
        }
        // NOTE: self-transfers are NOT rejected here — detecting them is the
        // SelfTransferRule's job (R3); the wallet domain enforces it too.
    }
}
