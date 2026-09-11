package com.nexulor.wallet.infrastructure.persistence;

import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;

final class TransferMapper {

    private TransferMapper() {
    }

    static Transfer toDomain(TransferJpaEntity entity) {
        return Transfer.restore(
                entity.getId(),
                entity.getSourceWalletId(),
                entity.getDestinationWalletId(),
                Money.of(entity.getAmount(), entity.getCurrency()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getCompletedAt());
    }

    static TransferJpaEntity toEntity(Transfer transfer) {
        TransferJpaEntity entity = new TransferJpaEntity();
        entity.setId(transfer.id());
        entity.setSourceWalletId(transfer.sourceWalletId());
        entity.setDestinationWalletId(transfer.destinationWalletId());
        entity.setAmount(transfer.amount().amount());
        entity.setCurrency(transfer.amount().currencyCode());
        entity.setStatus(transfer.status());
        entity.setCreatedAt(transfer.createdAt());
        entity.setCompletedAt(transfer.completedAt());
        return entity;
    }
}
