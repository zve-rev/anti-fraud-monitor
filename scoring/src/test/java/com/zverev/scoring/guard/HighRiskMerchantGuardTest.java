package com.zverev.scoring.guard;

import com.zverev.entity.Transaction;
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
    void regularMerchant_returnsEmpty() {
        assertThat(guard.evaluate(tx("Coffee Shop"))).isEmpty();
    }

    @Test
    @DisplayName("casino - violation HIGH")
    void casino_returnsViolation() {
        var result = guard.evaluate(tx("Casino"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("Casino");
    }

    @Test
    @DisplayName("case-sensitive: 'casino' lowercase - no violation")
    void lowercaseCasino_returnsEmpty() {
        assertThat(guard.evaluate(tx("casino"))).isEmpty();
    }

    private Transaction tx(String merchant) {
        return Transaction.builder()
                .amount(new BigDecimal("100"))
                .currencyCode(CurrencyCode.EUR)
                .cardToken("card-1")
                .merchantName(merchant)
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .externalId(UUID.randomUUID())
                .build();
    }
}
