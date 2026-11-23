package com.zverev.transaction.unit.service;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.OutboxEvent;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.entity.enums.Rule;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.repository.TransactionRepository;
import com.zverev.transaction.dto.AlertResponse;
import com.zverev.transaction.dto.TransactionCreatedResponse;
import com.zverev.transaction.dto.TransactionRequest;
import com.zverev.transaction.dto.TransactionResponse;
import com.zverev.transaction.exception.TransactionAlreadyExistsException;
import com.zverev.transaction.exception.TransactionNotFoundException;
import com.zverev.transaction.service.AlertService;
import com.zverev.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private TransactionService transactionService;

    private UUID externalId;
    private TransactionRequest request;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transactionService, "kafkaTopic", "transactions.raw");

        externalId = UUID.randomUUID();

        request = new TransactionRequest(
                externalId,
                "card-token-123",
                new BigDecimal("150.2500"),
                CurrencyCode.EUR,
                "Amazon",
                MerchantCountry.GERMANY,
                Instant.parse("2026-11-20T10:15:30Z")
        );
    }

    @Test
    void shouldProcessTransactionSuccessfully() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 1L);
            return transaction;
        });

        when(jsonMapper.writeValueAsString(any(TransactionEvent.class)))
                .thenReturn("{\"externalId\":\"" + externalId + "\"}");

        TransactionCreatedResponse response = transactionService.process(request);

        assertNotNull(response);
        assertEquals(1L, response.transactionId());
        assertEquals(TransactionStatus.NEW, response.status());

        verify(transactionRepository).existsByExternalId(externalId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(jsonMapper).writeValueAsString(any(TransactionEvent.class));

        verifyNoMoreInteractions(transactionRepository, outboxEventRepository, jsonMapper);
    }

    @Test
    void shouldThrowExceptionWhenTransactionAlreadyExists() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(true);

        assertThrows(
                TransactionAlreadyExistsException.class,
                () -> transactionService.process(request)
        );

        verify(transactionRepository).existsByExternalId(externalId);
        verify(transactionRepository, never()).save(any(Transaction.class));
        verifyNoInteractions(outboxEventRepository, jsonMapper);
    }

    @Test
    void shouldNotSaveOutboxEventWhenTransactionSavingFails() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);

        RuntimeException exception = new RuntimeException("Database error");

        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> transactionService.process(request)
        );

        assertSame(exception, thrown);

        verify(transactionRepository).existsByExternalId(externalId);
        verify(transactionRepository).save(any(Transaction.class));
        verifyNoInteractions(outboxEventRepository, jsonMapper);
    }

    @Test
    void shouldNotSaveOutboxEventWhenSerializationFails() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 1L);
            return transaction;
        });

        RuntimeException exception = new RuntimeException("Serialization error");

        when(jsonMapper.writeValueAsString(any(TransactionEvent.class)))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> transactionService.process(request)
        );

        assertSame(exception, thrown);

        verify(transactionRepository).existsByExternalId(externalId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(jsonMapper).writeValueAsString(any(TransactionEvent.class));
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldPropagateExceptionWhenOutboxSavingFails() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 1L);
            return transaction;
        });

        when(jsonMapper.writeValueAsString(any(TransactionEvent.class)))
                .thenReturn("{\"externalId\":\"" + externalId + "\"}");

        RuntimeException exception = new RuntimeException("Outbox database error");

        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenThrow(exception);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> transactionService.process(request)
        );

        assertSame(exception, thrown);

        verify(transactionRepository).existsByExternalId(externalId);
        verify(transactionRepository).save(any(Transaction.class));
        verify(jsonMapper).writeValueAsString(any(TransactionEvent.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldCreateOutboxEventWithCorrectData() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 1L);
            return transaction;
        });

        when(jsonMapper.writeValueAsString(any(TransactionEvent.class)))
                .thenReturn("{\"externalId\":\"" + externalId + "\"}");

        transactionService.process(request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outboxEvent = captor.getValue();

        assertEquals(externalId.toString(), outboxEvent.getAggregateId());
        assertEquals("transactions.raw", outboxEvent.getTopic());
        assertEquals("card-token-123", outboxEvent.getMessageKey());
        assertEquals("{\"externalId\":\"" + externalId + "\"}", outboxEvent.getPayload());
        assertEquals(OutboxEventStatus.PENDING, outboxEvent.getStatus());
        assertEquals(0, outboxEvent.getAttempts());
        assertNotNull(outboxEvent.getCreatedAt());
    }

    @Test
    void shouldSaveTransactionWithCorrectData() {
        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 1L);
            return transaction;
        });

        when(jsonMapper.writeValueAsString(any(TransactionEvent.class)))
                .thenReturn("{}");

        transactionService.process(request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository).save(captor.capture());

        Transaction transaction = captor.getValue();

        assertEquals(externalId, transaction.getExternalId());
        assertEquals("card-token-123", transaction.getCardToken());
        assertEquals(new BigDecimal("150.2500"), transaction.getAmount());
        assertEquals(CurrencyCode.EUR, transaction.getCurrencyCode());
        assertEquals("Amazon", transaction.getMerchantName());
        assertEquals(MerchantCountry.GERMANY, transaction.getMerchantCountry());
        assertEquals(Instant.parse("2026-11-20T10:15:30Z"), transaction.getOccurredAt());
        assertNotNull(transaction.getCreatedAt());
        assertEquals(TransactionStatus.NEW, transaction.getStatus());
    }

    @Test
    void shouldFindAllTransactionsWhenStatusIsNull() {
        Transaction transaction = createTransaction(1L);

        Page<Transaction> transactionPage = new PageImpl<>(
                List.of(transaction),
                PageRequest.of(0, 10),
                1
        );

        when(transactionRepository.findAll(any(Pageable.class)))
                .thenReturn(transactionPage);

        Page<TransactionResponse> result = transactionService.findAllTransactions(
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());

        TransactionResponse response = result.getContent().get(0);

        assertEquals(1L, response.id());
        assertEquals(externalId, response.externalId());
        assertEquals("**** -123", response.cardTokenMasked());
        assertEquals(new BigDecimal("150.2500"), response.amount());
        assertEquals(CurrencyCode.EUR, response.currency());
        assertEquals("Amazon", response.merchantName());
        assertEquals(MerchantCountry.GERMANY, response.merchantCountry());
        assertEquals(TransactionStatus.NEW, response.status());

        verify(transactionRepository).findAll(any(Pageable.class));
        verify(transactionRepository, never()).findByStatus(any(), any(Pageable.class));
    }

    @Test
    void shouldFindTransactionsByStatus() {
        Transaction transaction = createTransaction(1L);

        Page<Transaction> transactionPage = new PageImpl<>(
                List.of(transaction),
                PageRequest.of(0, 10),
                1
        );

        when(transactionRepository.findByStatus(
                eq(TransactionStatus.NEW),
                any(Pageable.class)
        )).thenReturn(transactionPage);

        Page<TransactionResponse> result = transactionService.findAllTransactions(
                TransactionStatus.NEW,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(TransactionStatus.NEW, result.getContent().get(0).status());

        verify(transactionRepository).findByStatus(
                eq(TransactionStatus.NEW),
                any(Pageable.class)
        );
        verify(transactionRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldReturnEmptyPageWhenNoTransactionsExist() {
        Page<Transaction> emptyPage = Page.empty(PageRequest.of(0, 10));

        when(transactionRepository.findAll(any(Pageable.class)))
                .thenReturn(emptyPage);

        Page<TransactionResponse> result = transactionService.findAllTransactions(
                null,
                PageRequest.of(0, 10)
        );

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());

        verify(transactionRepository).findAll(any(Pageable.class));
    }

    @Test
    void shouldFindTransactionById() {
        Transaction transaction = createTransaction(1L);

        when(transactionRepository.findByIdWithAlerts(1L))
                .thenReturn(Optional.of(transaction));

        TransactionResponse result = transactionService.findTransactionById(1L);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals(externalId, result.externalId());
        assertEquals("**** -123", result.cardTokenMasked());
        assertEquals(new BigDecimal("150.2500"), result.amount());
        assertEquals(CurrencyCode.EUR, result.currency());
        assertEquals("Amazon", result.merchantName());
        assertEquals(MerchantCountry.GERMANY, result.merchantCountry());
        assertEquals(TransactionStatus.NEW, result.status());
        assertTrue(result.alerts().isEmpty());

        verify(transactionRepository).findByIdWithAlerts(1L);
    }

    @Test
    void shouldThrowExceptionWhenTransactionDoesNotExist() {
        when(transactionRepository.findByIdWithAlerts(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                TransactionNotFoundException.class,
                () -> transactionService.findTransactionById(999L)
        );

        verify(transactionRepository).findByIdWithAlerts(999L);
    }

    @Test
    void shouldReturnMaskedTokenWhenTokenIsShorterThanFourCharacters() {
        Transaction transaction = createTransaction(1L, "abc");

        when(transactionRepository.findByIdWithAlerts(1L))
                .thenReturn(Optional.of(transaction));

        TransactionResponse result = transactionService.findTransactionById(1L);

        assertEquals("****", result.cardTokenMasked());

        verify(transactionRepository).findByIdWithAlerts(1L);
    }

    @Test
    void shouldDelegateFindingAlertsToAlertService() {
        Long transactionId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        Page<AlertResponse> expectedPage = Page.empty(pageable);

        when(alertService.findTransactionAlerts(transactionId, pageable))
                .thenReturn(expectedPage);

        Page<AlertResponse> result = transactionService.findTransactionAlerts(
                transactionId,
                pageable
        );

        assertSame(expectedPage, result);

        verify(alertService).findTransactionAlerts(transactionId, pageable);
        verifyNoInteractions(transactionRepository, outboxEventRepository, jsonMapper);
    }

    @Test
    void shouldReturnAlertsPageWithContentFromAlertService() {
        Long transactionId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        AlertResponse alert = new AlertResponse(
                10L,
                Rule.AMOUNT_THRESHOLD,
                AlertSeverity.HIGH,
                "High transaction amount",
                Instant.parse("2026-11-20T10:20:00Z")
        );

        Page<AlertResponse> expectedPage = new PageImpl<>(
                List.of(alert),
                pageable,
                1
        );

        when(alertService.findTransactionAlerts(transactionId, pageable))
                .thenReturn(expectedPage);

        Page<AlertResponse> result = transactionService.findTransactionAlerts(
                transactionId,
                pageable
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(alert, result.getContent().get(0));

        verify(alertService).findTransactionAlerts(transactionId, pageable);
    }

    private Transaction createTransaction(Long id) {
        return createTransaction(id, "card-token-123");
    }

    private Transaction createTransaction(Long id, String cardToken) {
        Transaction transaction = Transaction.builder()
                .externalId(externalId)
                .cardToken(cardToken)
                .amount(new BigDecimal("150.2500"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Amazon")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.parse("2026-11-20T10:15:30Z"))
                .createdAt(Instant.parse("2026-11-20T10:16:00Z"))
                .build();

        ReflectionTestUtils.setField(transaction, "id", id);

        return transaction;
    }
}
