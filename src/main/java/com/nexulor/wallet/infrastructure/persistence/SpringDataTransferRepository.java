package com.nexulor.wallet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataTransferRepository extends JpaRepository<TransferJpaEntity, UUID> {

    @Query("""
            select t from TransferJpaEntity t
            where t.sourceWalletId = :walletId or t.destinationWalletId = :walletId
            order by t.createdAt desc
            """)
    List<TransferJpaEntity> findByWalletId(@Param("walletId") UUID walletId);
}
