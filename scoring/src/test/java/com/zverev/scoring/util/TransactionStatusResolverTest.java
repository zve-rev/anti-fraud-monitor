package com.zverev.scoring.util;

import com.zverev.entity.Alert;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.Rule;
import com.zverev.entity.enums.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusResolverTest {

    private final TransactionStatusResolver resolver = new TransactionStatusResolver();

    @Test
    @DisplayName("no alerts - APPROVED")
    void noAlerts_approved() {
        assertThat(resolver.resolve(List.of())).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("non-critical alerts - REVIEW")
    void nonCritical_review() {
        List<Alert> alerts = List.of(
                alert(Rule.AMOUNT_THRESHOLD, AlertSeverity.MEDIUM),
                alert(Rule.HIGH_RISK_MERCHANT, AlertSeverity.HIGH)
        );

        assertThat(resolver.resolve(alerts)).isEqualTo(TransactionStatus.REVIEW);
    }

    @Test
    @DisplayName("at least one critical alert - DECLINED")
    void hasCritical_declined() {
        List<Alert> alerts = List.of(
                alert(Rule.AMOUNT_THRESHOLD, AlertSeverity.MEDIUM),
                alert(Rule.VELOCITY, AlertSeverity.CRITICAL)
        );

        assertThat(resolver.resolve(alerts)).isEqualTo(TransactionStatus.DECLINED);
    }

    private Alert alert(Rule rule, AlertSeverity severity) {
        return new Alert(rule, severity, "test", Instant.now());
    }
}
