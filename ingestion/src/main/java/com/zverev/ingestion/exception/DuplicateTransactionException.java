package com.zverev.ingestion.exception;

import lombok.Getter;

import java.util.UUID;

public class DuplicateTransactionException extends RuntimeException {

    @Getter
    private final UUID externalId;

    public DuplicateTransactionException(UUID externalId) {
        super(String.format("Transaction with externalId %s already exists", externalId));
        this.externalId = externalId;
    }
}
