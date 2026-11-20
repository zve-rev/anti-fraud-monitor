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

class HighRiskMerchantGuardTest {

    private final HighRiskMerchantGuard guard = new HighRiskMerchantGuard();

    @Test
    @DisplayName("regular merchant - no violation")
    void regular_empty() {
        assertThat(guard.evaluate(tx("Coffee Shop"))).isEmpty();
    }

    @Test
    @DisplayName("casino - HIGH violation")
    void casino_violation() {
        var result = guard.evaluate(tx("Casino"));

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(result.get().message()).contains("Casino");
    }

    @Test
    @DisplayName("lowercase name - no violation, match is case sensitive")
    void lowercase_empty() {
        assertThat(guard.evaluate(tx("casino"))).isEmpty();
    }

    private Transaction tx(String merchant) {
        return Transaction.builder()
                .externalId(UUID.randomUUID())
                .cardToken("card-1")
                .amount(new BigDecimal("100"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName(merchant)
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
