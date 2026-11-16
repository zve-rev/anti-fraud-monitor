package com.zverev.ingestion.service;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.OutboxEvent;
import com.zverev.entity.Transaction;
import com.zverev.ingestion.dto.TransactionCreatedResponse;
import com.zverev.ingestion.dto.TransactionRequest;
import com.zverev.ingestion.exception.TransactionAlreadyExistsException;
import com.zverev.repository.OutboxEventRepository;
import com.zverev.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@AllArgsConstructor
public class TransactionService {

    private static final String TOPIC = "transactions.raw";
    private static final String EVENT_TYPE = "TransactionCreated";

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public TransactionCreatedResponse process(TransactionRequest request) {
        UUID externalId = request.externalId();
        if (transactionRepository.existsByExternalId(externalId)) {
            throw new TransactionAlreadyExistsException(externalId);
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
                EVENT_TYPE,
                jsonMapper.writeValueAsString(event),
                TOPIC,
                saved.getCardToken()
        );
        outboxEventRepository.save(outboxEvent);

        return new TransactionCreatedResponse(saved.getId(), saved.getStatus());
    }
}