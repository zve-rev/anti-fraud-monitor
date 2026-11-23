package com.zverev.transaction.unit.service;

import com.zverev.shared.entity.Alert;
import com.zverev.shared.entity.enums.AlertSeverity;
import com.zverev.shared.entity.enums.Rule;
import com.zverev.shared.exception.TransactionNotFoundException;
import com.zverev.shared.repository.AlertRepository;
import com.zverev.shared.repository.TransactionRepository;
import com.zverev.transaction.dto.AlertResponse;
import com.zverev.transaction.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AlertService alertService;

    private Long transactionId;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        transactionId = 1L;
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void shouldFindTransactionAlertsSuccessfully() {
        Alert alert = createAlert();

        Page<Alert> alertPage = new PageImpl<>(
                List.of(alert),
                pageable,
                1
        );

        when(transactionRepository.existsById(transactionId))
                .thenReturn(true);

        when(alertRepository.findByTransactionId(transactionId, pageable))
                .thenReturn(alertPage);

        Page<AlertResponse> result = alertService.findTransactionAlerts(
                transactionId,
                pageable
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        AlertResponse response = result.getContent().get(0);

        assertEquals(Rule.AMOUNT_THRESHOLD, response.rule());
        assertEquals(AlertSeverity.HIGH, response.severity());
        assertEquals("High transaction amount", response.message());
        assertEquals(
                Instant.parse("2026-11-20T10:20:00Z"),
                response.createdAt()
        );

        verify(transactionRepository).existsById(transactionId);
        verify(alertRepository).findByTransactionId(transactionId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenTransactionDoesNotExist() {
        when(transactionRepository.existsById(transactionId))
                .thenReturn(false);

        assertThrows(
                TransactionNotFoundException.class,
                () -> alertService.findTransactionAlerts(transactionId, pageable)
        );

        verify(transactionRepository).existsById(transactionId);
        verifyNoInteractions(alertRepository);
    }

    @Test
    void shouldReturnEmptyPageWhenTransactionHasNoAlerts() {
        Page<Alert> emptyPage = Page.empty(pageable);

        when(transactionRepository.existsById(transactionId))
                .thenReturn(true);

        when(alertRepository.findByTransactionId(transactionId, pageable))
                .thenReturn(emptyPage);

        Page<AlertResponse> result = alertService.findTransactionAlerts(
                transactionId,
                pageable
        );

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());

        verify(transactionRepository).existsById(transactionId);
        verify(alertRepository).findByTransactionId(transactionId, pageable);
    }

    @Test
    void shouldPreservePaginationInformation() {
        List<Alert> alerts = List.of(
                createAlert(),
                createAlert()
        );

        Page<Alert> alertPage = new PageImpl<>(
                alerts,
                PageRequest.of(1, 2),
                5
        );

        Pageable secondPage = PageRequest.of(1, 2);

        when(transactionRepository.existsById(transactionId))
                .thenReturn(true);

        when(alertRepository.findByTransactionId(transactionId, secondPage))
                .thenReturn(alertPage);

        Page<AlertResponse> result = alertService.findTransactionAlerts(
                transactionId,
                secondPage
        );

        assertEquals(2, result.getNumberOfElements());
        assertEquals(1, result.getNumber());
        assertEquals(2, result.getSize());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());

        verify(transactionRepository).existsById(transactionId);
        verify(alertRepository).findByTransactionId(transactionId, secondPage);
    }

    private Alert createAlert() {
        return new Alert(
                Rule.AMOUNT_THRESHOLD,
                AlertSeverity.HIGH,
                "High transaction amount",
                Instant.parse("2026-11-20T10:20:00Z")
        );
    }
}
