package com.zverev.scoring.kafka;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.exception.TransactionNotFoundException;
import com.zverev.repository.TransactionRepository;
import com.zverev.scoring.util.AlertFilter;
import com.zverev.scoring.util.TransactionStatusResolver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

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
                .orElseThrow(() -> new TransactionNotFoundException(externalId));

        if (transaction.getStatus() != TransactionStatus.NEW) {
            log.info("Event already processed, skipping: externalId={}, status={}",
                    externalId, transaction.getStatus());
            return;
        }

        alertFilter.filter(transaction);
        TransactionStatus status = transactionStatusResolver.resolve(transaction.getAlerts());
        transaction.setStatus(status);

        meterRegistry.counter("antifraud.transactions.processed", "status", status.name()).increment();
        transaction.getAlerts().forEach(alert ->
                meterRegistry.counter("antifraud.alerts.created", "rule", alert.getRule().name()).increment());

        log.info("Transaction processed: externalId={}, status={}", externalId, status);
    }

    @DltHandler
    public void handleDeadLetter(
            TransactionEvent transactionEvent,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String reason) {
        log.error("Event moved to DLT, transaction stays NEW: externalId={}, reason={}",
                transactionEvent.externalId(), reason);
        meterRegistry.counter("antifraud.events.dead").increment();
    }
}