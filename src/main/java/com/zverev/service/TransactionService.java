package com.zverev.service;

import com.zverev.dto.TransactionCreatedResponse;
import com.zverev.dto.TransactionEvent;
import com.zverev.dto.TransactionRequest;
import com.zverev.dto.TransactionResponse;
import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.exception.DuplicateTransactionException;
import com.zverev.guard.Guard;
import com.zverev.kafka.TransactionEventProducer;
import com.zverev.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final List<Guard> guards;
    private final TransactionEventProducer eventProducer;


    public TransactionService(TransactionRepository transactionRepository,
                              List<Guard> guards,
                              TransactionEventProducer eventProducer) {
        this.transactionRepository = transactionRepository;
        this.guards = guards;
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

        guards.forEach(guard -> guard.evaluate(transaction)
                .ifPresent(violation -> {
                    Alert alert = new Alert(
                            guard.getRule(),
                            violation.severity(),
                            violation.message(),
                            Instant.now()
                    );
                    transaction.addAlert(alert);
                })
        );

        List<Alert> alerts = transaction.getAlerts();
        transaction.setStatus(decideStatus(alerts));

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

    @Transactional(readOnly = true)
    public List<TransactionResponse> findAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(transaction -> TransactionResponse.toTransactionResponse(
                        transaction,
                        maskCard(transaction.getCardToken())))
                .toList();
    }

    private TransactionStatus decideStatus(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            return TransactionStatus.APPROVED;
        }
        boolean hasCritical = alerts.stream().anyMatch(a -> a.getSeverity() == AlertSeverity.CRITICAL);
        return hasCritical ? TransactionStatus.DECLINED : TransactionStatus.REVIEW;
    }

    private String maskCard(String cardToken) {
        if (cardToken == null || cardToken.length() < 4) {
            return "****";
        }
        return "**** " + cardToken.substring(cardToken.length() - 4);
    }
}
