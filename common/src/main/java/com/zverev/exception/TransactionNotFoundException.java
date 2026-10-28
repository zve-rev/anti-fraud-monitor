package com.zverev.exception;

import lombok.Getter;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    @Getter
    private final UUID externalId;

    public TransactionNotFoundException(UUID externalId) {
        super(String.format("Transaction with externalId %s not found", externalId));
        this.externalId = externalId;
    }
}
