package com.nexulor.gateway.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload shapes returned by the downstream REST services. Kept as records so
 * the gateway depends only on the fields it actually projects into GraphQL.
 */
public final class DownstreamDtos {

    private DownstreamDtos() {
    }

    public record WalletResponse(
            UUID id,
            UUID ownerId,
            BigDecimal balance,
            String currency,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TransferResponse(
            UUID id,
            UUID sourceWalletId,
            UUID destinationWalletId,
            BigDecimal amount,
            String currency,
            String status,
            Instant createdAt,
            Instant completedAt) {
    }

    public record StatementResponse(
            String id,
            UUID walletId,
            UUID transferId,
            UUID counterpartyWalletId,
            String direction,
            BigDecimal amount,
            String currency,
            Instant completedAt) {
    }
}
