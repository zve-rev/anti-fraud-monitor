package com.zverev.transaction.dto;

import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(
        name = "TransactionRequest",
        description = "Request payload containing the transaction details submitted for processing."
)
public record TransactionRequest(

        @Schema(
                description = "Unique external identifier assigned to the transaction by the originating system.",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull UUID externalId,

        @Schema(
                description = "Tokenized card identifier. The original card number must never be provided.",
                example = "card-token-123",
                maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 255)
        String cardToken,

        @Schema(
                description = "Transaction amount. Must be greater than zero and contain no more than 15 integer digits and 4 fractional digits.",
                example = "1250.5000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @Positive
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @Schema(
                description = "ISO currency code used for the transaction.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull CurrencyCode currency,

        @Schema(
                description = "Name of the merchant where the transaction was initiated.",
                example = "Amazon",
                maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 255)
        String merchantName,

        @Schema(
                description = "Country in which the merchant is registered or operates.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull MerchantCountry merchantCountry,

        @Schema(
                description = "Timestamp indicating when the transaction occurred. Must not be in the future. Uses ISO 8601 date-time format.",
                example = "2026-11-20T10:15:30Z",
                type = "string",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @PastOrPresent
        Instant occurredAt
) {}
