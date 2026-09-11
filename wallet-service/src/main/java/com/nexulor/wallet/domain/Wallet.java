package com.nexulor.wallet.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a user's digital wallet.
 * Balance mutations are only allowed through domain methods to keep invariants intact.
 */
public class Wallet {

    private final UUID id;
    private final UUID ownerId;
    private Money balance;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Wallet(UUID id, UUID ownerId, Money balance, long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Wallet open(UUID ownerId, String currencyCode) {
        Instant now = Instant.now();
        return new Wallet(
                UUID.randomUUID(),
                ownerId,
                Money.zero(currencyCode),
                0L,
                now,
                now);
    }

    public static Wallet restore(
            UUID id,
            UUID ownerId,
            Money balance,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new Wallet(id, ownerId, balance, version, createdAt, updatedAt);
    }

    public void credit(Money amount) {
        this.balance = this.balance.add(amount);
        touch();
    }

    public void debit(Money amount) {
        this.balance = this.balance.subtract(amount);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Money balance() {
        return balance;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
