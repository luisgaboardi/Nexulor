package com.nexulor.wallet.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload of the {@code transaction-completed} topic (ADR-004), published by
 * the application layer after the transfer commits. Consumers build
 * notifications and CQRS statement read models from it. Versioned envelope so
 * the schema can evolve without breaking consumers.
 */
public record TransactionCompletedEvent(
        String eventId,
        String eventType,
        String eventVersion,
        UUID transferId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        BigDecimal amount,
        String currency,
        Instant completedAt) {

    public static final String TYPE = "transaction.completed";
    public static final String VERSION = "1";

    public static TransactionCompletedEvent from(
            UUID transferId,
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            Instant completedAt) {
        return new TransactionCompletedEvent(
                UUID.randomUUID().toString(),
                TYPE,
                VERSION,
                transferId,
                sourceWalletId,
                destinationWalletId,
                amount,
                currency,
                completedAt);
    }
}
