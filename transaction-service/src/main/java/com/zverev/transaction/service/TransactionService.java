package com.zverev.transaction.service;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.OutboxEvent;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.transaction.dto.AlertResponse;
import com.zverev.transaction.dto.TransactionCreatedResponse;
import com.zverev.transaction.dto.TransactionRequest;
import com.zverev.transaction.dto.TransactionResponse;
import com.zverev.transaction.exception.TransactionAlreadyExistsException;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.repository.TransactionRepository;
import com.zverev.util.CardMasker;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static com.zverev.transaction.dto.TransactionResponse.toTransactionResponse;

@Service
@RequiredArgsConstructor
public class TransactionService {

    @Value("${kafka.topic}")
    private String kafkaTopic;

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final AlertService alertService;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAllTransactions(TransactionStatus status, Pageable pageable) {
        Page<Transaction> page = (status == null)
                ? transactionRepository.findAll(pageable)
                : transactionRepository.findByStatus(status, pageable);
        return page.map(t -> toTransactionResponse(t, CardMasker.mask(t.getCardToken())));
    }

    @Transactional(readOnly = true)
    public TransactionResponse findTransactionById(Long id) {
        Transaction transaction = transactionRepository.findByIdWithAlerts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
        String cardTokenMasked = CardMasker.mask(transaction.getCardToken());
        return toTransactionResponse(transaction, cardTokenMasked);
    }

    @Transactional(readOnly = true)
    public Page<AlertResponse> findTransactionAlerts(Long transactionId, Pageable pageable) {
        return alertService.findTransactionAlerts(transactionId, pageable);
    }

    @Transactional
    public TransactionCreatedResponse process(TransactionRequest request) {
        UUID externalId = request.externalId();

        if (transactionRepository.existsByExternalId(externalId)) {
            throw new TransactionAlreadyExistsException(
                    "Transaction with external id " + externalId + " already exists"
            );
        }

        Transaction transaction = Transaction.builder()
                .externalId(externalId)
                .cardToken(request.cardToken())
                .amount(request.amount())
                .currencyCode(request.currency())
                .merchantName(request.merchantName())
                .merchantCountry(request.merchantCountry())
                .occurredAt(request.occurredAt())
                .createdAt(Instant.now())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        TransactionEvent event = new TransactionEvent(
                saved.getExternalId(),
                saved.getCardToken(),
                saved.getAmount(),
                saved.getCurrencyCode(),
                saved.getMerchantName(),
                saved.getMerchantCountry(),
                saved.getOccurredAt()
        );

        OutboxEvent outboxEvent = new OutboxEvent(
                saved.getExternalId().toString(),
                jsonMapper.writeValueAsString(event),
                kafkaTopic,
                saved.getCardToken()
        );

        outboxEventRepository.save(outboxEvent);
        return new TransactionCreatedResponse(saved.getId(), saved.getStatus());
    }
}