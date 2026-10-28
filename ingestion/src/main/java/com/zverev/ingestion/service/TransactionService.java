package com.zverev.ingestion.service;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Transaction;
import com.zverev.ingestion.dto.TransactionCreatedResponse;
import com.zverev.ingestion.dto.TransactionRequest;
import com.zverev.ingestion.exception.DuplicateTransactionException;
import com.zverev.ingestion.kafka.TransactionEventProducer;
import com.zverev.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionEventProducer eventProducer;

    public TransactionService(TransactionRepository transactionRepository,
                              TransactionEventProducer eventProducer) {
        this.transactionRepository = transactionRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public TransactionCreatedResponse process(TransactionRequest request) {
        UUID externalId = request.externalId();

        if (transactionRepository.existsByExternalId(externalId)) {
            throw new DuplicateTransactionException(externalId);
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

        eventProducer.send(
                new TransactionEvent(
                        saved.getExternalId(),
                        saved.getCardToken(),
                        saved.getAmount(),
                        saved.getCurrencyCode(),
                        saved.getMerchantName(),
                        saved.getMerchantCountry(),
                        saved.getOccurredAt()
                )
        );

        return new TransactionCreatedResponse(saved.getId(), saved.getStatus());
    }
}