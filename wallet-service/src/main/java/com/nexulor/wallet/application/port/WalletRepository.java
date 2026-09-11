package com.nexulor.wallet.application.port;

import com.nexulor.wallet.domain.Wallet;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findById(UUID walletId);

    Optional<Wallet> findByOwnerId(UUID ownerId);

    /**
     * Loads wallets for update in a deterministic lock order to avoid deadlocks
     * during concurrent P2P transfers involving overlapping accounts.
     */
    Wallet lockById(UUID walletId);

    boolean existsByOwnerId(UUID ownerId);
}
