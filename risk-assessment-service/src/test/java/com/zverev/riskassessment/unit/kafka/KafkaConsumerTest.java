package com.zverev.riskassessment.unit.kafka;

import com.zverev.riskassessment.kafka.KafkaConsumer;
import com.zverev.shared.dto.TransactionEvent;
import com.zverev.shared.entity.Alert;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.CurrencyCode;
import com.zverev.shared.entity.enums.MerchantCountry;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.shared.entity.enums.TransactionStatus;
import com.zverev.shared.repository.TransactionRepository;
import com.zverev.shared.exception.TransactionNotFoundException;
import com.zverev.riskassessment.util.AlertFilter;
import com.zverev.riskassessment.util.TransactionStatusResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KafkaConsumerTest {

    private TransactionRepository transactionRepository;
    private AlertFilter alertFilter;
    private TransactionStatusResolver transactionStatusResolver;
    private MeterRegistry meterRegistry;

    private KafkaConsumer kafkaConsumer;

    private Transaction transaction;
    private TransactionEvent transactionEvent;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        alertFilter = mock(AlertFilter.class);
        transactionStatusResolver = mock(TransactionStatusResolver.class);
        meterRegistry = mock(MeterRegistry.class);

        kafkaConsumer = new KafkaConsumer(
                transactionRepository,
                alertFilter,
                transactionStatusResolver,
                meterRegistry
        );

        UUID externalId = UUID.randomUUID();

        transaction = Transaction.builder()
                .externalId(externalId)
                .cardToken("1234567890123456")
                .amount(new BigDecimal("1000"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Test Merchant")
                .merchantCountry(MerchantCountry.values()[0])
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        transactionEvent = new TransactionEvent(
                externalId,
                "1234567890123456",
                new BigDecimal("1000"),
                CurrencyCode.EUR,
                "Test Merchant",
                MerchantCountry.values()[0],
                Instant.now()
        );
    }

    @Test
    void shouldThrowExceptionWhenTransactionDoesNotExist() {
        when(transactionRepository.findByExternalId(transactionEvent.externalId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> kafkaConsumer.consume(transactionEvent))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(transactionEvent.externalId().toString());

        verify(alertFilter, never()).filter(any());
        verify(transactionStatusResolver, never()).resolve(anyList());
    }

    @Test
    void shouldSkipTransactionWhenStatusIsNotNew() {
        transaction.setStatus(TransactionStatus.APPROVED);

        when(transactionRepository.findByExternalId(transactionEvent.externalId()))
                .thenReturn(Optional.of(transaction));

        kafkaConsumer.consume(transactionEvent);

        verify(alertFilter, never()).filter(any());
        verify(transactionStatusResolver, never()).resolve(anyList());
        verifyNoInteractions(meterRegistry);
    }

    @Test
    void shouldApproveTransactionWhenNoAlertsAreCreated() {
        Counter processedCounter = mock(Counter.class);

        when(transactionRepository.findByExternalId(transactionEvent.externalId()))
                .thenReturn(Optional.of(transaction));

        when(transactionStatusResolver.resolve(transaction.getAlerts()))
                .thenReturn(TransactionStatus.APPROVED);

        when(meterRegistry.counter(
                "antifraud.transactions.processed",
                "status",
                "APPROVED"
        )).thenReturn(processedCounter);

        kafkaConsumer.consume(transactionEvent);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.APPROVED);

        verify(alertFilter).filter(transaction);
        verify(transactionStatusResolver).resolve(transaction.getAlerts());
        verify(processedCounter).increment();
    }

    @Test
    void shouldDeclineTransactionWhenCriticalAlertExists() {
        Counter processedCounter = mock(Counter.class);
        Counter alertCounter = mock(Counter.class);

        transaction.addAlert(
                new Alert(
                        Rule.VELOCITY,
                        AlertSeverity.CRITICAL,
                        "Velocity exceeded",
                        Instant.now()
                )
        );

        when(transactionRepository.findByExternalId(transactionEvent.externalId()))
                .thenReturn(Optional.of(transaction));

        when(transactionStatusResolver.resolve(transaction.getAlerts()))
                .thenReturn(TransactionStatus.DECLINED);

        when(meterRegistry.counter(
                "antifraud.transactions.processed",
                "status",
                "DECLINED"
        )).thenReturn(processedCounter);

        when(meterRegistry.counter(
                "antifraud.alerts.created",
                "rule",
                Rule.VELOCITY.name()
        )).thenReturn(alertCounter);

        kafkaConsumer.consume(transactionEvent);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.DECLINED);

        verify(alertFilter).filter(transaction);
        verify(transactionStatusResolver).resolve(transaction.getAlerts());
        verify(processedCounter).increment();
        verify(alertCounter).increment();
    }

    @Test
    void shouldIncrementAlertMetricForEveryAlert() {
        Counter processedCounter = mock(Counter.class);
        Counter velocityCounter = mock(Counter.class);
        Counter amountThresholdCounter = mock(Counter.class);

        transaction.addAlert(
                new Alert(
                        Rule.VELOCITY,
                        AlertSeverity.CRITICAL,
                        "Velocity exceeded",
                        Instant.now()
                )
        );

        transaction.addAlert(
                new Alert(
                        Rule.AMOUNT_THRESHOLD,
                        AlertSeverity.MEDIUM,
                        "Amount exceeded",
                        Instant.now()
                )
        );

        when(transactionRepository.findByExternalId(transactionEvent.externalId()))
                .thenReturn(Optional.of(transaction));

        when(transactionStatusResolver.resolve(transaction.getAlerts()))
                .thenReturn(TransactionStatus.DECLINED);

        when(meterRegistry.counter(
                "antifraud.transactions.processed",
                "status",
                "DECLINED"
        )).thenReturn(processedCounter);

        when(meterRegistry.counter(
                "antifraud.alerts.created",
                "rule",
                Rule.VELOCITY.name()
        )).thenReturn(velocityCounter);

        when(meterRegistry.counter(
                "antifraud.alerts.created",
                "rule",
                Rule.AMOUNT_THRESHOLD.name()
        )).thenReturn(amountThresholdCounter);

        kafkaConsumer.consume(transactionEvent);

        verify(processedCounter).increment();
        verify(velocityCounter).increment();
        verify(amountThresholdCounter).increment();
    }
}
