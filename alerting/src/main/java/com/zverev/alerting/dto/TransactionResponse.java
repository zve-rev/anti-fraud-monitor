package com.zverev.alerting.dto;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        Long id,
        UUID externalId,
        String cardTokenMasked,
        BigDecimal amount,
        CurrencyCode currency,
        String merchantName,
        MerchantCountry merchantCountry,
        TransactionStatus status,
        Instant occurredAt,
        Instant createdAt,
        List<AlertResponse> alerts
) {
    public static TransactionResponse toTransactionResponse(Transaction transaction, String cardTokenMasked) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getExternalId(),
                cardTokenMasked,
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getMerchantName(),
                transaction.getMerchantCountry(),
                transaction.getStatus(),
                transaction.getOccurredAt(),
                transaction.getCreatedAt(),
                transaction.getAlerts().stream().map(AlertResponse::toAlertResponse).toList()
        );
    }
}
