package com.zverev.ingestion.dto;

import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.CurrencyCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionRequest(
        @NotNull UUID externalId,
        @NotBlank @Size(max = 255) String cardToken,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotNull CurrencyCode currency,
        @NotBlank @Size(max = 255) String merchantName,
        @NotNull MerchantCountry merchantCountry,
        @NotNull @PastOrPresent Instant occurredAt
) {}
