package com.zverev.ingestion.service;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.ingestion.dto.TransactionRequest;
import com.zverev.ingestion.exception.TransactionAlreadyExistsException;
import com.zverev.ingestion.kafka.TransactionEventProducer;
import com.zverev.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private TransactionEventProducer eventProducer;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("new transaction - saved as NEW and event published")
    void processNew_savesAndPublishes() {
        UUID externalId = UUID.randomUUID();
        TransactionRequest request = request(externalId);

        when(transactionRepository.existsByExternalId(externalId)).thenReturn(false);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = transactionService.process(request);

        assertThat(response.status()).isEqualTo(TransactionStatus.NEW);
        verify(transactionRepository).save(any(Transaction.class));

        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(eventProducer).send(captor.capture());
        assertThat(captor.getValue().externalId()).isEqualTo(externalId);
        assertThat(captor.getValue().cardToken()).isEqualTo("card-1");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("duplicate externalId - exception, no save, no event")
    void processDuplicate_throwsException() {
        UUID externalId = UUID.randomUUID();
        TransactionRequest request = request(externalId);

        when(transactionRepository.existsByExternalId(externalId)).thenReturn(true);

        assertThatThrownBy(() -> transactionService.process(request))
                .isInstanceOf(TransactionAlreadyExistsException.class)
                .hasMessageContaining(externalId.toString());

        verify(transactionRepository, never()).save(any());
        verify(eventProducer, never()).send(any());
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