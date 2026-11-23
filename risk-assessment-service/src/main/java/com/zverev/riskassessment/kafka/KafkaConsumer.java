package com.zverev.riskassessment.kafka;

import com.zverev.shared.dto.TransactionEvent;
import com.zverev.shared.entity.Alert;
import com.zverev.shared.entity.Transaction;
import com.zverev.shared.entity.enums.TransactionStatus;
import com.zverev.shared.repository.TransactionRepository;
import com.zverev.shared.exception.TransactionNotFoundException;
import com.zverev.riskassessment.util.AlertFilter;
import com.zverev.riskassessment.util.TransactionStatusResolver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumer {

    private final TransactionRepository transactionRepository;
    private final AlertFilter alertFilter;
    private final TransactionStatusResolver transactionStatusResolver;
    private final MeterRegistry meterRegistry;

    @RetryableTopic(backOff = @BackOff(delay = 2000, multiplier = 2.0))
    @KafkaListener(topics = "transactions.raw", groupId = "antifraud-group")
    @Transactional
    public void consume(TransactionEvent transactionEvent) {
        UUID externalId = transactionEvent.externalId();

        Transaction transaction = transactionRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction with external id " + externalId + " not found")
                );

        if (transaction.getStatus() != TransactionStatus.NEW) {
            log.info("Event already processed, skipping: externalId={}, status={}", externalId, transaction.getStatus());
            return;
        }

        alertFilter.filter(transaction);

        List<Alert> alerts = transaction.getAlerts();
        TransactionStatus status = transactionStatusResolver.resolve(alerts);
        transaction.setStatus(status);

        meterRegistry.counter("antifraud.transactions.processed", "status", status.name()).increment();
        alerts.forEach(alert -> meterRegistry.counter("antifraud.alerts.created", "rule", alert.getRule().name()).increment());

        log.info("Transaction processed: externalId={}, status={}", externalId, status);
    }
}