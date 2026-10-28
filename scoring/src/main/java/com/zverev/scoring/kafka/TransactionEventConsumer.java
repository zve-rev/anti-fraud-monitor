package com.zverev.scoring.kafka;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.repository.TransactionRepository;
import com.zverev.scoring.guard.Guard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final List<Guard> guards;
    private final StringRedisTemplate redis;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 2000, multiplier = 2.0)
    )
    @KafkaListener(topics = "transactions.raw", groupId = "antifraud-group")
    @Transactional
    public void consume(TransactionEvent event) {
        String key = "processed:event:" + event.externalId();
        Boolean isNew = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("Duplicate event skipped: {}", event.externalId());
            return;
        }

        log.info("Processing transaction event: externalId={}", event.externalId());

        Transaction transaction = transactionRepository
                .findByExternalId(event.externalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction not found: " + event.externalId()));

        for (Guard guard : guards) {
            guard.evaluate(transaction).ifPresent(violation -> {
                Alert alert = new Alert(
                        guard.getRule(),
                        violation.severity(),
                        violation.message(),
                        Instant.now()
                );
                transaction.addAlert(alert);
            });
        }

        transaction.setStatus(decideStatus(transaction.getAlerts()));

        transactionRepository.save(transaction);

        log.info("Transaction processed successfully: externalId={}, status={}",
                event.externalId(), transaction.getStatus());
    }

    private TransactionStatus decideStatus(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            return TransactionStatus.APPROVED;
        }
        boolean hasCritical = alerts.stream()
                .anyMatch(a -> a.getSeverity() == AlertSeverity.CRITICAL);
        return hasCritical ? TransactionStatus.DECLINED : TransactionStatus.REVIEW;
    }
}