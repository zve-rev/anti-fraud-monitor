package com.zverev.shared.dto;

import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.MerchantCountry;

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
