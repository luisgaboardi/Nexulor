package com.nexulor.wallet.domain;

public class WalletAlreadyExistsException extends DomainException {

    public WalletAlreadyExistsException(String message) {
        super(message);
    }
}
