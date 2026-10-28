package com.zverev.ingestion.dto;

import com.zverev.entity.enums.TransactionStatus;

public record TransactionCreatedResponse(
        Long transactionId,
        TransactionStatus status
) {}
