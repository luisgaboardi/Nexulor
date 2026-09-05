package com.nexulor.wallet.application;

import com.nexulor.wallet.application.port.TransferRepository;
import com.nexulor.wallet.application.port.WalletRepository;
import com.nexulor.wallet.domain.InvalidTransferException;
import com.nexulor.wallet.domain.Money;
import com.nexulor.wallet.domain.Transfer;
import com.nexulor.wallet.domain.TransferNotFoundException;
import com.nexulor.wallet.domain.Wallet;
import com.nexulor.wallet.domain.WalletNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TransferApplicationService {

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;

    public TransferApplicationService(
            WalletRepository walletRepository,
            TransferRepository transferRepository) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
    }

    /**
     * Executes a P2P transfer under a single ACID transaction.
     * Wallets are locked in UUID order to prevent deadlocks under concurrent transfers.
     */
    @Transactional
    public Transfer transfer(TransferCommand command) {
        if (command.sourceWalletId().equals(command.destinationWalletId())) {
            throw new InvalidTransferException("source and destination wallets must differ");
        }

        Money amount = Money.of(command.amount(), command.currency());
        if (amount.isZero()) {
            throw new InvalidTransferException("transfer amount must be greater than zero");
        }

        List<UUID> lockOrder = List.of(command.sourceWalletId(), command.destinationWalletId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        Wallet first = lockWallet(lockOrder.get(0));
        Wallet second = lockWallet(lockOrder.get(1));

        Wallet source = first.id().equals(command.sourceWalletId()) ? first : second;
        Wallet destination = first.id().equals(command.destinationWalletId()) ? first : second;

        source.debit(amount);
        destination.credit(amount);

        walletRepository.save(source);
        walletRepository.save(destination);

        Transfer transfer = Transfer.completed(source.id(), destination.id(), amount);
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("transfer not found: " + transferId));
    }

    @Transactional(readOnly = true)
    public List<Transfer> listByWallet(UUID walletId) {
        if (walletRepository.findById(walletId).isEmpty()) {
            throw new WalletNotFoundException("wallet not found: " + walletId);
        }
        return transferRepository.findByWalletId(walletId);
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.lockById(walletId);
    }
}
