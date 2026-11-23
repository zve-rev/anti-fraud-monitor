package com.zverev.riskassessment.unit.util;

import com.zverev.riskassessment.util.AlertFilter;
import com.zverev.shared.entity.Alert;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.MerchantCountry;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.riskassessment.dto.GuardViolation;
import com.zverev.riskassessment.guard.Guard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertFilterTest {

    private Transaction transaction;
    private Guard firstGuard;
    private Guard secondGuard;
    private AlertFilter alertFilter;

    @BeforeEach
    void setUp() {
        transaction = Transaction.builder()
                .externalId(UUID.randomUUID())
                .cardToken("test-card-token")
                .amount(new BigDecimal("1000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Test Merchant")
                .merchantCountry(MerchantCountry.values()[0])
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        firstGuard = mock(Guard.class);
        secondGuard = mock(Guard.class);
    }

    @Test
    void shouldNotAddAlertsWhenNoGuardDetectsViolation() {
        when(firstGuard.evaluate(transaction)).thenReturn(Optional.empty());
        when(secondGuard.evaluate(transaction)).thenReturn(Optional.empty());

        alertFilter = new AlertFilter(List.of(firstGuard, secondGuard));

        alertFilter.filter(transaction);

        assertThat(transaction.getAlerts()).isEmpty();
    }

    @Test
    void shouldAddAlertWhenGuardDetectsViolation() {
        GuardViolation violation = new GuardViolation(
                AlertSeverity.HIGH,
                "High-risk merchant category: Casino"
        );

        when(firstGuard.evaluate(transaction)).thenReturn(Optional.of(violation));
        when(firstGuard.getRule()).thenReturn(Rule.HIGH_RISK_MERCHANT);

        alertFilter = new AlertFilter(List.of(firstGuard));

        alertFilter.filter(transaction);

        assertThat(transaction.getAlerts()).hasSize(1);

        Alert alert = transaction.getAlerts().get(0);

        assertThat(alert.getRule()).isEqualTo(Rule.HIGH_RISK_MERCHANT);
        assertThat(alert.getSeverity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(alert.getMessage())
                .isEqualTo("High-risk merchant category: Casino");
        assertThat(alert.getCreatedAt()).isNotNull();
        assertThat(alert.getTransaction()).isSameAs(transaction);
    }

    @Test
    void shouldAddAlertsFromMultipleGuards() {
        GuardViolation firstViolation = new GuardViolation(
                AlertSeverity.MEDIUM,
                "Amount exceeds threshold"
        );

        GuardViolation secondViolation = new GuardViolation(
                AlertSeverity.HIGH,
                "High-risk merchant"
        );

        when(firstGuard.evaluate(transaction)).thenReturn(Optional.of(firstViolation));
        when(firstGuard.getRule()).thenReturn(Rule.AMOUNT_THRESHOLD);

        when(secondGuard.evaluate(transaction)).thenReturn(Optional.of(secondViolation));
        when(secondGuard.getRule()).thenReturn(Rule.HIGH_RISK_MERCHANT);

        alertFilter = new AlertFilter(List.of(firstGuard, secondGuard));

        alertFilter.filter(transaction);

        assertThat(transaction.getAlerts()).hasSize(2);

        assertThat(transaction.getAlerts())
                .extracting(Alert::getRule)
                .containsExactly(
                        Rule.AMOUNT_THRESHOLD,
                        Rule.HIGH_RISK_MERCHANT
                );

        assertThat(transaction.getAlerts())
                .extracting(Alert::getSeverity)
                .containsExactly(
                        AlertSeverity.MEDIUM,
                        AlertSeverity.HIGH
                );

        assertThat(transaction.getAlerts())
                .allSatisfy(alert ->
                        assertThat(alert.getTransaction()).isSameAs(transaction)
                );
    }

    @Test
    void shouldAddOnlyAlertsFromGuardsWithViolations() {
        GuardViolation violation = new GuardViolation(
                AlertSeverity.CRITICAL,
                "Velocity exceeded"
        );

        when(firstGuard.evaluate(transaction)).thenReturn(Optional.of(violation));
        when(firstGuard.getRule()).thenReturn(Rule.VELOCITY);

        when(secondGuard.evaluate(transaction)).thenReturn(Optional.empty());

        alertFilter = new AlertFilter(List.of(firstGuard, secondGuard));

        alertFilter.filter(transaction);

        assertThat(transaction.getAlerts()).hasSize(1);
        assertThat(transaction.getAlerts().get(0).getRule())
                .isEqualTo(Rule.VELOCITY);
    }
}
