package com.zverev.dto;

import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionEvent(
        UUID externalId,
        String cardToken,
        BigDecimal amount,
        CurrencyCode currency,
        String merchantName,
        MerchantCountry merchantCountry,
        Instant occurredAt
) {}
