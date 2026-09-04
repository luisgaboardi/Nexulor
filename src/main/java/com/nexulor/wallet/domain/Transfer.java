package com.nexulor.wallet.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Completed P2P transfer record. In Phase 1 transfers are applied synchronously
 * inside a single ACID transaction; status is therefore always COMPLETED on persist.
 */
public class Transfer {

    private final UUID id;
    private final UUID sourceWalletId;
    private final UUID destinationWalletId;
    private final Money amount;
    private final TransferStatus status;
    private final Instant createdAt;
    private final Instant completedAt;

    private Transfer(
            UUID id,
            UUID sourceWalletId,
            UUID destinationWalletId,
            Money amount,
            TransferStatus status,
            Instant createdAt,
            Instant completedAt) {
        this.id = Objects.requireNonNull(id);
        this.sourceWalletId = Objects.requireNonNull(sourceWalletId);
        this.destinationWalletId = Objects.requireNonNull(destinationWalletId);
        this.amount = Objects.requireNonNull(amount);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.completedAt = Objects.requireNonNull(completedAt);

        if (sourceWalletId.equals(destinationWalletId)) {
            throw new InvalidTransferException("source and destination wallets must differ");
        }
        if (amount.isZero()) {
            throw new InvalidTransferException("transfer amount must be greater than zero");
        }
    }

    public static Transfer completed(UUID sourceWalletId, UUID destinationWalletId, Money amount) {
        Instant now = Instant.now();
        return new Transfer(
                UUID.randomUUID(),
                sourceWalletId,
                destinationWalletId,
                amount,
                TransferStatus.COMPLETED,
                now,
                now);
    }

    public static Transfer restore(
            UUID id,
            UUID sourceWalletId,
            UUID destinationWalletId,
            Money amount,
            TransferStatus status,
            Instant createdAt,
            Instant completedAt) {
        return new Transfer(id, sourceWalletId, destinationWalletId, amount, status, createdAt, completedAt);
    }

    public UUID id() {
        return id;
    }

    public UUID sourceWalletId() {
        return sourceWalletId;
    }

    public UUID destinationWalletId() {
        return destinationWalletId;
    }

    public Money amount() {
        return amount;
    }

    public TransferStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
