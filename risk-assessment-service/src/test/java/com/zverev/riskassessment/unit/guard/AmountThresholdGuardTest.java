package com.zverev.riskassessment.unit.guard;

import com.zverev.riskassessment.guard.AmountThresholdGuard;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmountThresholdGuardTest {

    private AmountThresholdGuard guard;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        guard = new AmountThresholdGuard();
        transaction = mock(Transaction.class);
    }

    @Test
    void shouldReturnEmptyWhenAmountIsBelowThreshold() {
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.EUR);
        when(transaction.getAmount()).thenReturn(new BigDecimal("4999.99"));

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenAmountEqualsThreshold() {
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.EUR);
        when(transaction.getAmount()).thenReturn(new BigDecimal("5000"));

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnViolationWhenAmountExceedsThreshold() {
        when(transaction.getCurrencyCode()).thenReturn(CurrencyCode.EUR);
        when(transaction.getAmount()).thenReturn(new BigDecimal("5000.01"));

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.MEDIUM);
        assertThat(result.get().message())
                .isEqualTo("Amount 5000.01 EUR exceeds threshold 5000 EUR");
    }

    @Test
    void shouldReturnAmountThresholdRule() {
        assertThat(guard.getRule()).isEqualTo(Rule.AMOUNT_THRESHOLD);
    }

    @Test
    void shouldUseCorrectThresholdForEachSupportedCurrency() {
        assertThreshold(CurrencyCode.EUR, "5000");
        assertThreshold(CurrencyCode.CHF, "5000");
        assertThreshold(CurrencyCode.CAD, "7000");
        assertThreshold(CurrencyCode.AUD, "8000");
        assertThreshold(CurrencyCode.BRL, "30000");
        assertThreshold(CurrencyCode.JPY, "800000");
    }

    private void assertThreshold(CurrencyCode currency, String threshold) {
        when(transaction.getCurrencyCode()).thenReturn(currency);
        when(transaction.getAmount()).thenReturn(new BigDecimal(threshold));

        Optional<GuardViolation> result = guard.evaluate(transaction);

        assertThat(result).isEmpty();

        when(transaction.getAmount()).thenReturn(
                new BigDecimal(threshold).add(new BigDecimal("0.01"))
        );

        result = guard.evaluate(transaction);

        assertThat(result).isPresent();
        assertThat(result.get().severity()).isEqualTo(AlertSeverity.MEDIUM);
    }
}
