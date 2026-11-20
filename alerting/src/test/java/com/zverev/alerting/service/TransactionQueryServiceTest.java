package com.zverev.alerting.service;

import com.zverev.alerting.dto.TransactionResponse;
import com.zverev.alerting.exception.ResourceNotFoundException;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionQueryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    private TransactionQueryService service;

    @BeforeEach
    void setUp() {
        service = new TransactionQueryService(transactionRepository);
    }

    @Test
    @DisplayName("find by id - card token is masked in response")
    void findById_masksCard() {
        when(transactionRepository.findByIdWithAlerts(1L)).thenReturn(Optional.of(transaction("card-123456")));

        TransactionResponse response = service.findTransactionById(1L);

        assertThat(response.cardTokenMasked()).isEqualTo("**** 3456");
    }

    @Test
    @DisplayName("find by id - unknown id throws ResourceNotFoundException")
    void findById_notFound() {
        when(transactionRepository.findByIdWithAlerts(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTransactionById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("list without status - all transactions are queried")
    void list_noStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(transactionRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(transaction("card-1234"))));

        Page<TransactionResponse> result = service.findAllTransactions(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository, never()).findByStatus(any(), any());
    }

    @Test
    @DisplayName("list with status - filtered query is used")
    void list_withStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(transactionRepository.findByStatus(TransactionStatus.REVIEW, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction("card-1234"))));

        Page<TransactionResponse> result = service.findAllTransactions(TransactionStatus.REVIEW, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository, never()).findAll(any(Pageable.class));
    }

    private Transaction transaction(String cardToken) {
        return Transaction.builder()
                .externalId(UUID.randomUUID())
                .cardToken(cardToken)
                .amount(new BigDecimal("100"))
                .currencyCode(CurrencyCode.EUR)
                .merchantName("Shop")
                .merchantCountry(MerchantCountry.GERMANY)
                .occurredAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
