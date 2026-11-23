package com.zverev.transaction.exception;

public class TransactionAlreadyExistsException extends RuntimeException {

    public TransactionAlreadyExistsException(String message) {
        super(message);
    }
}
