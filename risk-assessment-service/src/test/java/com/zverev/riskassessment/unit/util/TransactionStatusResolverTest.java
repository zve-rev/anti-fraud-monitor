package com.zverev.riskassessment.unit.util;

import com.zverev.riskassessment.util.TransactionStatusResolver;
import com.zverev.shared.entity.Alert;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.shared.entity.enums.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusResolverTest {

    private TransactionStatusResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TransactionStatusResolver();
    }

    @Test
    void shouldApproveTransactionWhenThereAreNoAlerts() {
        TransactionStatus result = resolver.resolve(List.of());

        assertThat(result).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    void shouldReturnReviewWhenThereAreOnlyMediumAlerts() {
        List<Alert> alerts = List.of(
                createAlert(AlertSeverity.MEDIUM),
                createAlert(AlertSeverity.MEDIUM)
        );

        TransactionStatus result = resolver.resolve(alerts);

        assertThat(result).isEqualTo(TransactionStatus.REVIEW);
    }

    @Test
    void shouldReturnReviewWhenThereAreOnlyHighAlerts() {
        List<Alert> alerts = List.of(
                createAlert(AlertSeverity.HIGH)
        );

        TransactionStatus result = resolver.resolve(alerts);

        assertThat(result).isEqualTo(TransactionStatus.REVIEW);
    }

    @Test
    void shouldDeclineTransactionWhenThereIsCriticalAlert() {
        List<Alert> alerts = List.of(
                createAlert(AlertSeverity.CRITICAL)
        );

        TransactionStatus result = resolver.resolve(alerts);

        assertThat(result).isEqualTo(TransactionStatus.DECLINED);
    }

    @Test
    void shouldDeclineTransactionWhenCriticalAlertIsAmongOtherAlerts() {
        List<Alert> alerts = List.of(
                createAlert(AlertSeverity.MEDIUM),
                createAlert(AlertSeverity.HIGH),
                createAlert(AlertSeverity.CRITICAL)
        );

        TransactionStatus result = resolver.resolve(alerts);

        assertThat(result).isEqualTo(TransactionStatus.DECLINED);
    }

    private Alert createAlert(AlertSeverity severity) {
        return new Alert(
                Rule.AMOUNT_THRESHOLD,
                severity,
                "Test alert",
                Instant.now()
        );
    }
}
