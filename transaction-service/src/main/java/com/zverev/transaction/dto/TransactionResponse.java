package com.zverev.transaction.dto;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
        name = "TransactionResponse",
        description = "Response containing the transaction details, processing status, and associated alerts."
)
public record TransactionResponse(

        @Schema(
                description = "Unique internal identifier assigned to the transaction.",
                example = "1"
        )
        Long id,

        @Schema(
                description = "Unique external identifier provided by the originating system.",
                example = "550e8400-e29b-41d4-a716-446655440000"
        )
        UUID externalId,

        @Schema(
                description = "Masked card token associated with the transaction. Sensitive card information is not exposed.",
                example = "****-1234"
        )
        String cardTokenMasked,

        @Schema(
                description = "Monetary amount of the transaction.",
                example = "1250.5000"
        )
        BigDecimal amount,

        @Schema(
                description = "Currency in which the transaction was processed."
        )
        CurrencyCode currency,

        @Schema(
                description = "Name of the merchant associated with the transaction.",
                example = "Amazon"
        )
        String merchantName,

        @Schema(
                description = "Country associated with the merchant."
        )
        MerchantCountry merchantCountry,

        @Schema(
                description = "Current processing status of the transaction."
        )
        TransactionStatus status,

        @Schema(
                description = "Timestamp when the transaction originally occurred.",
                example = "2026-11-20T10:15:30Z",
                type = "string",
                format = "date-time"
        )
        Instant occurredAt,

        @Schema(
                description = "Timestamp when the transaction was created in the system.",
                example = "2026-11-20T10:15:31Z",
                type = "string",
                format = "date-time"
        )
        Instant createdAt,

        @Schema(
                description = "List of alerts generated during transaction processing."
        )
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
