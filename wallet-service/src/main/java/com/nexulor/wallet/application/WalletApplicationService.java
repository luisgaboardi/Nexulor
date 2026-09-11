package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Wallet;
import com.nexulor.wallet.domain.WalletAlreadyExistsException;
import com.nexulor.wallet.domain.WalletNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WalletApplicationService {

    private final WalletRepository walletRepository;

    public WalletApplicationService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public Wallet createWallet(CreateWalletCommand command) {
        if (walletRepository.existsByOwnerId(command.ownerId())) {
            throw new WalletAlreadyExistsException(
                    "wallet already exists for ownerId=" + command.ownerId());
        }
        Wallet wallet = Wallet.open(command.ownerId(), command.currency());
        return walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found: " + walletId));
    }

    @Transactional(readOnly = true)
    public Wallet getWalletByOwner(UUID ownerId) {
        return walletRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for ownerId=" + ownerId));
    }

    /**
     * Phase-1 helper to fund a wallet for demos and tests.
     * Production funding flows will be introduced with payment rails later.
     */
    @Transactional
    public Wallet credit(CreditWalletCommand command) {
        Wallet wallet = walletRepository.lockById(command.walletId());
        wallet.credit(Money.of(command.amount(), command.currency()));
        return walletRepository.save(wallet);
    }
}
