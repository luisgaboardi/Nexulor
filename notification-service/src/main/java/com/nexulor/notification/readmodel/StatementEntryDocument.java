package com.nexulor.notification.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CQRS read model (ADR-004): one entry per wallet touched by a completed
 * transfer, projected from the {@code transaction-completed} topic. Purely a
 * projection — can be rebuilt from the topic at any time.
 */
@Document("statement_entries")
@CompoundIndex(name = "wallet_time_idx", def = "{'walletId': 1, 'completedAt': -1}")
public class StatementEntryDocument {

    @Id
    private String id;

    private UUID walletId;
    private UUID transferId;
    private UUID counterpartyWalletId;
    private String direction; // DEBIT | CREDIT
    private BigDecimal amount;
    private String currency;
    private Instant completedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public UUID getCounterpartyWalletId() {
        return counterpartyWalletId;
    }

    public void setCounterpartyWalletId(UUID counterpartyWalletId) {
        this.counterpartyWalletId = counterpartyWalletId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
