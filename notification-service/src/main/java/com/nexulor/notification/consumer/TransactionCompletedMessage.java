package com.nexulor.notification.consumer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deserialized form of the {@code transaction-completed} payload published by
 * the wallet service. Kept as an independent record so the consumer owns its
 * contract copy (tolerant reader pattern).
 */
public record TransactionCompletedMessage(
        String eventId,
        String eventType,
        String eventVersion,
        String transferId,
        String sourceWalletId,
        String destinationWalletId,
        BigDecimal amount,
        String currency,
        Instant completedAt) {
}
