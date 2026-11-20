package com.zverev.ingestion.service;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.ingestion.dto.TransactionRequest;
import com.zverev.ingestion.exception.TransactionAlreadyExistsException;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                outboxEventRepository,
                JsonMapper.builder().build()
        );
    }

    @Test
    @DisplayName("new transaction - saved as NEW and outbox event persisted")
    void processNew_savesAndPersistsOutbox() {
        UUID externalId = UUID.randomUUID();
        TransactionRequest request = request(externalId);

        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = transactionService.process(request);

        assertThat(response.status()).isEqualTo(TransactionStatus.NEW);
        verify(transactionRepository).save(any(Transaction.class));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("transactions.raw");
        assertThat(saved.getMessageKey()).isEqualTo("card-1");
        assertThat(saved.getEventType()).isEqualTo("TransactionCreated");
        assertThat(saved.getAggregateId()).isEqualTo(externalId.toString());
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getPayload()).contains(externalId.toString()).contains("card-1");
    }

    @Test
    @DisplayName("unique constraint violation on save - TransactionAlreadyExistsException, no outbox")
    void processRace_throwsException() {
        UUID externalId = UUID.randomUUID();
        TransactionRequest request = request(externalId);

        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);
        when(transactionRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> transactionService.process(request))
                .isInstanceOf(TransactionAlreadyExistsException.class)
                .hasMessageContaining(externalId.toString());

        verify(outboxEventRepository, never()).save(any());
    }

    private TransactionRequest request(UUID externalId) {
        return new TransactionRequest(
                externalId,
                "card-1",
                new BigDecimal("100"),
                CurrencyCode.EUR,
                "Shop",
                MerchantCountry.GERMANY,
                Instant.now()
        );
    }
}