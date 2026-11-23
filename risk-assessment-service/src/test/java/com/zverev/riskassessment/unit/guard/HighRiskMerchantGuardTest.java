package com.zverev.riskassessment.unit.guard;

import com.zverev.riskassessment.guard.HighRiskMerchantGuard;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HighRiskMerchantGuardTest {

    private HighRiskMerchantGuard guard;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        guard = new HighRiskMerchantGuard();
        transaction = mock(Transaction.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Casino",
            "Online Poker",
            "Crypto Exchange",
            "Gambling",
            "Wire Transfer"
    })
    void shouldReturnViolationForHighRiskMerchant(String merchantName) {
        when(transaction.getMerchantName()).thenReturn(merchantName);

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(result.get().message())
                .isEqualTo("High-risk merchant category: " + merchantName);
    }

    @Test
    void shouldReturnEmptyForRegularMerchant() {
        when(transaction.getMerchantName()).thenReturn("Supermarket");

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForMerchantWithDifferentLetterCase() {
        when(transaction.getMerchantName()).thenReturn("casino");

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnHighRiskMerchantRule() {
        assertThat(guard.getRule()).isEqualTo(Rule.HIGH_RISK_MERCHANT);
    }
}
