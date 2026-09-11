package com.nexulor.wallet.infrastructure.persistence;

import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.Wallet;
import com.nexulor.wallet.domain.WalletNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaWalletRepository implements WalletRepository {

    private final SpringDataWalletRepository springDataWalletRepository;

    public JpaWalletRepository(SpringDataWalletRepository springDataWalletRepository) {
        this.springDataWalletRepository = springDataWalletRepository;
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletJpaEntity entity = springDataWalletRepository.findById(wallet.id())
                .orElseGet(WalletJpaEntity::new);

        if (entity.getId() == null) {
            entity = WalletMapper.toEntity(wallet);
        } else {
            WalletMapper.copyToEntity(wallet, entity);
        }

        WalletJpaEntity saved = springDataWalletRepository.save(entity);
        return WalletMapper.toDomain(saved);
    }

    @Override
    public Optional<Wallet> findById(UUID walletId) {
        return springDataWalletRepository.findById(walletId).map(WalletMapper::toDomain);
    }

    @Override
    public Optional<Wallet> findByOwnerId(UUID ownerId) {
        return springDataWalletRepository.findByOwnerId(ownerId).map(WalletMapper::toDomain);
    }

    @Override
    public Wallet lockById(UUID walletId) {
        return springDataWalletRepository.findByIdForUpdate(walletId)
                .map(WalletMapper::toDomain)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found: " + walletId));
    }

    @Override
    public boolean existsByOwnerId(UUID ownerId) {
        return springDataWalletRepository.existsByOwnerId(ownerId);
    }
}
