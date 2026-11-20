package com.zverev.scoring.guard;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmountThresholdGuardTest {

    private final AmountThresholdGuard guard = new AmountThresholdGuard();

    @Test
    @DisplayName("below threshold - no violation")
    void belowThreshold_empty() {
        assertThat(guard.evaluate(tx("4999.99", CurrencyCode.EUR))).isEmpty();
    }

    @Test
    @DisplayName("at threshold - no violation")
    void atThreshold_empty() {
        assertThat(guard.evaluate(tx("5000", CurrencyCode.EUR))).isEmpty();
    }

    @Test
    @DisplayName("above threshold - MEDIUM violation")
    void aboveThreshold_violation() {
        var result = guard.evaluate(tx("5000.01", CurrencyCode.EUR));

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(result.get().message()).contains("5000.01 EUR").contains("5000 EUR");
    }

    @Test
    @DisplayName("threshold depends on currency - 100000 JPY is fine")
    void jpy_empty() {
        assertThat(guard.evaluate(tx("100000", CurrencyCode.JPY))).isEmpty();
    }

    private Transaction tx(String amount, CurrencyCode currency) {
        return Transaction.builder()
                .externalId(UUID.randomUUID())
                .cardToken("card-1")
                .amount(new BigDecimal(amount))
                .currencyCode(currency)
                .merchantName("Shop")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
