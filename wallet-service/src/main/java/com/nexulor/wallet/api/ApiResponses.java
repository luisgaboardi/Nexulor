package com.nexulor.wallet.api;

import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;
import com.nexulor.wallet.domain.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ApiResponses {

    private ApiResponses() {
    }

    public record WalletResponse(
            UUID id,
            UUID ownerId,
            BigDecimal balance,
            String currency,
            Instant createdAt,
            Instant updatedAt) {

        public static WalletResponse from(Wallet wallet) {
            return new WalletResponse(
                    wallet.id(),
                    wallet.ownerId(),
                    wallet.balance().amount(),
                    wallet.balance().currencyCode(),
                    wallet.createdAt(),
                    wallet.updatedAt());
        }
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

        public static TransferResponse from(Transfer transfer) {
            Money amount = transfer.amount();
            return new TransferResponse(
                    transfer.id(),
                    transfer.sourceWalletId(),
                    transfer.destinationWalletId(),
                    amount.amount(),
                    amount.currencyCode(),
                    transfer.status().name(),
                    transfer.createdAt(),
                    transfer.completedAt());
        }
    }

    public record ErrorResponse(String code, String message) {
    }
}
