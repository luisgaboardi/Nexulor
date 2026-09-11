package com.nexulor.wallet.application.port;

import com.nexulor.wallet.domain.Transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID transferId);

    List<Transfer> findByWalletId(UUID walletId);
}
