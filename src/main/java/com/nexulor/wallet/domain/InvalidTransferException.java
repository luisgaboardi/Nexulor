package com.nexulor.wallet.domain;

public class InvalidTransferException extends DomainException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
