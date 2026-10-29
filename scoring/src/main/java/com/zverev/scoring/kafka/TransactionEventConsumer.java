package com.zverev.scoring.kafka;

import com.zverev.dto.TransactionEvent;
import com.zverev.entity.Alert;
import com.zverev.entity.Transaction;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.TransactionStatus;
import com.zverev.exception.TransactionNotFoundException;
import com.zverev.repository.TransactionRepository;
import com.zverev.scoring.service.GuardFilterService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private static final String KAFKA_TOPIC = "transactions.raw";
    private static final String REDIS_KEY_PREFIX = "processed:event:";
    private static final Duration TTL = Duration.ofHours(24);

    private final TransactionRepository transactionRepository;
    private final GuardFilterService guardFilterService;
    private final StringRedisTemplate redisTemplate;

    @RetryableTopic(
            backOff = @BackOff(delay = 2000, multiplier = 2.0)
    )
    @KafkaListener(topics = KAFKA_TOPIC, groupId = "antifraud-group")
    @Transactional
    public void consume(TransactionEvent transactionEvent) {
        UUID externalId = transactionEvent.externalId();
        String key = REDIS_KEY_PREFIX + externalId;

        if (transactionEventIsAlreadyProcessed(key)) return;

        log.info("Processing transaction event: externalId={}", externalId);

        Transaction transaction = transactionRepository.findByExternalId(externalId)
                .orElseThrow(() -> new TransactionNotFoundException(externalId));

        guardFilterService.filter(transaction);

        List<Alert> alerts = transaction.getAlerts();
        TransactionStatus assignedStatus = assignStatus(alerts);
        transaction.setStatus(assignedStatus);

        transactionRepository.save(transaction);
        log.info("Transaction processed successfully: externalId={}, status={}",
                externalId, transaction.getStatus());
    }

    private boolean transactionEventIsAlreadyProcessed(String key) {
        Boolean isProcessed = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL);
        return Boolean.FALSE.equals(isProcessed);
    }

    private TransactionStatus assignStatus(List<Alert> alerts) {
        if (alerts.isEmpty()) {
            return TransactionStatus.APPROVED;
        }
        boolean hasCritical = alerts.stream()
                .anyMatch(a -> a.getSeverity() == AlertSeverity.CRITICAL);
        return hasCritical ? TransactionStatus.DECLINED : TransactionStatus.REVIEW;
    }
}