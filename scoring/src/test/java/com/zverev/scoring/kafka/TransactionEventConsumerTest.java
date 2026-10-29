package com.zverev.scoring.kafka;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.*;
import com.zverev.exception.TransactionNotFoundException;
import com.zverev.repository.TransactionRepository;
import com.zverev.scoring.service.GuardFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private GuardFilterService guardFilterService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private TransactionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(transactionRepository, guardFilterService, redisTemplate);
    }

    @Test
    @DisplayName("first event, no violations - saved as APPROVED")
    void firstEventNoViolations_approved() {
        UUID externalId = UUID.randomUUID();
        TransactionEvent event = event(externalId);
        Transaction tx = transaction(externalId);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)))).thenReturn(true);
        when(transactionRepository.findByExternalId(externalId)).thenReturn(Optional.of(tx));

        consumer.consume(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.APPROVED);
        verify(guardFilterService).filter(tx);
    }

    @Test
    @DisplayName("critical alert - saved as DECLINED")
    void criticalAlert_declined() {
        UUID externalId = UUID.randomUUID();
        TransactionEvent event = event(externalId);
        Transaction tx = transaction(externalId);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)))).thenReturn(true);
        when(transactionRepository.findByExternalId(externalId)).thenReturn(Optional.of(tx));
        doAnswer(inv -> {
            tx.addAlert(new Alert(Rule.VELOCITY, AlertSeverity.CRITICAL, "velocity", Instant.now()));
            return null;
        }).when(guardFilterService).filter(tx);

        consumer.consume(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.DECLINED);
    }

    @Test
    @DisplayName("high alert (non-critical) - saved as REVIEW")
    void highAlert_review() {
        UUID externalId = UUID.randomUUID();
        TransactionEvent event = event(externalId);
        Transaction tx = transaction(externalId);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)))).thenReturn(true);
        when(transactionRepository.findByExternalId(externalId)).thenReturn(Optional.of(tx));
        doAnswer(inv -> {
            tx.addAlert(new Alert(Rule.HIGH_RISK_MERCHANT, AlertSeverity.HIGH, "merchant", Instant.now()));
            return null;
        }).when(guardFilterService).filter(tx);

        consumer.consume(event);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.REVIEW);
    }

    @Test
    @DisplayName("duplicate event - skipped, no save, no filter")
    void duplicateEvent_skipped() {
        UUID externalId = UUID.randomUUID();
        TransactionEvent event = event(externalId);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)))).thenReturn(false);

        consumer.consume(event);

        verify(transactionRepository, never()).findByExternalId(any());
        verify(transactionRepository, never()).save(any());
        verify(guardFilterService, never()).filter(any());
    }

    @Test
    @DisplayName("transaction not found - TransactionNotFoundException")
    void notFound_throwsException() {
        UUID externalId = UUID.randomUUID();
        TransactionEvent event = event(externalId);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), eq(Duration.ofHours(24)))).thenReturn(true);
        when(transactionRepository.findByExternalId(externalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining(externalId.toString());

        verify(transactionRepository, never()).save(any());
        verify(guardFilterService, never()).filter(any());
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
