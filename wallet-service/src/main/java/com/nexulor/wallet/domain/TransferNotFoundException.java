package com.nexulor.wallet.domain;

public class TransferNotFoundException extends DomainException {

    public TransferNotFoundException(String message) {
        super(message);
    }
}
