package com.nexulor.wallet.infrastructure.persistence;

import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Wallet;

final class WalletMapper {

    private WalletMapper() {
    }

    static Wallet toDomain(WalletJpaEntity entity) {
        return Wallet.restore(
                entity.getId(),
                entity.getOwnerId(),
                Money.of(entity.getBalanceAmount(), entity.getCurrency()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static WalletJpaEntity toEntity(Wallet wallet) {
        WalletJpaEntity entity = new WalletJpaEntity();
        entity.setId(wallet.id());
        entity.setOwnerId(wallet.ownerId());
        entity.setBalanceAmount(wallet.balance().amount());
        entity.setCurrency(wallet.balance().currencyCode());
        entity.setVersion(wallet.version());
        entity.setCreatedAt(wallet.createdAt());
        entity.setUpdatedAt(wallet.updatedAt());
        return entity;
    }

    static void copyToEntity(Wallet wallet, WalletJpaEntity entity) {
        entity.setBalanceAmount(wallet.balance().amount());
        entity.setCurrency(wallet.balance().currencyCode());
        entity.setUpdatedAt(wallet.updatedAt());
    }
}
