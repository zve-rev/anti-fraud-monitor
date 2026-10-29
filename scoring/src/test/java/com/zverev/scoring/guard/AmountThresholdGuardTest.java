package com.zverev.scoring.guard;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountThresholdGuardTest {

    private final AmountThresholdGuard guard = new AmountThresholdGuard();

    @Test
    @DisplayName("below threshold - no violation")
    void belowThreshold_returnsEmpty() {
        assertThat(guard.evaluate(tx(new BigDecimal("4999.99"), CurrencyCode.EUR))).isEmpty();
    }

    @Test
    @DisplayName("at threshold - no violation (strict >)")
    void atThreshold_returnsEmpty() {
        assertThat(guard.evaluate(tx(new BigDecimal("5000"), CurrencyCode.EUR))).isEmpty();
    }

    @Test
    @DisplayName("above threshold - violation MEDIUM")
    void aboveThreshold_returnsViolation() {
        var result = guard.evaluate(tx(new BigDecimal("5000.01"), CurrencyCode.EUR));

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(
                com.zverev.entity.enums.AlertSeverity.MEDIUM);
        assertThat(result.get().message()).contains("5000.01 EUR").contains("5000 EUR");
    }

    @Test
    @DisplayName("currency-specific threshold - JPY 100k is below 800k")
    void jpyThresholdIsDifferent() {
        assertThat(guard.evaluate(tx(new BigDecimal("100000"), CurrencyCode.JPY))).isEmpty();
    }

    private Transaction tx(BigDecimal amount, CurrencyCode currency) {
        return Transaction.builder()
                .amount(amount)
                .currencyCode(currency)
                .cardToken("card-1")
                .merchantName("Shop")
                .merchantCountry(com.zverev.entity.enums.MerchantCountry.GERMANY)
                .occurredAt(java.time.Instant.now())
                .createdAt(java.time.Instant.now())
                .externalId(java.util.UUID.randomUUID())
                .build();
    }
}
