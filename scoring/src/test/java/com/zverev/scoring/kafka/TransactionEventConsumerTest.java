package com.zverev.scoring.kafka;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.Rule;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.exception.TransactionNotFoundException;
import com.zverev.repository.TransactionRepository;
import com.zverev.scoring.util.AlertFilter;
import com.zverev.scoring.util.TransactionStatusResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AlertFilter alertFilter;

    private TransactionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(
                transactionRepository, alertFilter, new TransactionStatusResolver(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("new event, no alerts - status APPROVED")
    void noAlerts_approved() {
        UUID id = UUID.randomUUID();
        Transaction tx = transaction(id);
        when(transactionRepository.findByExternalId(id)).thenReturn(Optional.of(tx));

        consumer.consume(event(id));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        verify(alertFilter).filter(tx);
    }

    @Test
    @DisplayName("critical alert - status DECLINED")
    void criticalAlert_declined() {
        UUID id = UUID.randomUUID();
        Transaction tx = transaction(id);
        when(transactionRepository.findByExternalId(id)).thenReturn(Optional.of(tx));
        doAnswer(inv -> {
            tx.addAlert(new Alert(Rule.VELOCITY, AlertSeverity.CRITICAL, "velocity", Instant.now()));
            return null;
        }).when(alertFilter).filter(tx);

        consumer.consume(event(id));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.DECLINED);
    }

    @Test
    @DisplayName("already processed event - skipped, status unchanged")
    void alreadyProcessed_skipped() {
        UUID id = UUID.randomUUID();
        Transaction tx = transaction(id);
        tx.setStatus(TransactionStatus.APPROVED);
        when(transactionRepository.findByExternalId(id)).thenReturn(Optional.of(tx));

        consumer.consume(event(id));

        verify(alertFilter, never()).filter(any());
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("transaction not found - exception, so Kafka retries later")
    void notFound_throws() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findByExternalId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.consume(event(id)))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(alertFilter, never()).filter(any());
    }

    @Test
    @DisplayName("first attempt fails, retry succeeds - event is not lost")
    void failureThenRetry_processed() {
        UUID id = UUID.randomUUID();
        Transaction tx = transaction(id);
        when(transactionRepository.findByExternalId(id)).thenReturn(Optional.of(tx));
        doThrow(new RuntimeException("redis down")).doNothing().when(alertFilter).filter(tx);

        assertThatThrownBy(() -> consumer.consume(event(id)))
                .isInstanceOf(RuntimeException.class);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.NEW);

        consumer.consume(event(id));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.APPROVED);
    }

    private TransactionEvent event(UUID externalId) {
        return new TransactionEvent(externalId, "card-1", new BigDecimal("100"),
                CurrencyCode.EUR, "Shop", MerchantCountry.GERMANY, Instant.now());
    }

    private Transaction transaction(UUID externalId) {
        return Transaction.builder()
                .externalId(externalId)
                .cardToken("card-1")
                .amount(new BigDecimal("100"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Shop")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
