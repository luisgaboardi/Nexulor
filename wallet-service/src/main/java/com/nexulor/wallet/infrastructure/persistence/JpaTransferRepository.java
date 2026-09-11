package com.nexulor.wallet.infrastructure.persistence;

import com.nexulor.wallet.application.port.TransferRepository;
import com.nexulor.wallet.domain.Transfer;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTransferRepository implements TransferRepository {

    private final SpringDataTransferRepository springDataTransferRepository;

    public JpaTransferRepository(SpringDataTransferRepository springDataTransferRepository) {
        this.springDataTransferRepository = springDataTransferRepository;
    }

    @Override
    public Transfer save(Transfer transfer) {
        TransferJpaEntity saved = springDataTransferRepository.save(TransferMapper.toEntity(transfer));
        return TransferMapper.toDomain(saved);
    }

    @Override
    public Optional<Transfer> findById(UUID transferId) {
        return springDataTransferRepository.findById(transferId).map(TransferMapper::toDomain);
    }

    @Override
    public List<Transfer> findByWalletId(UUID walletId) {
        return springDataTransferRepository.findByWalletId(walletId).stream()
                .map(TransferMapper::toDomain)
                .toList();
    }
}
