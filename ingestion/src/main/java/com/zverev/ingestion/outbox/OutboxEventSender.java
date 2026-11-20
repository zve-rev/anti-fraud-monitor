package com.zverev.ingestion.outbox;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventSender {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void send(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
            outboxEventRepository.markProcessed(event.getId(), OutboxEventStatus.SENT, Instant.now());
        } catch (Exception e) {
            boolean giveUp = event.getAttempts() + 1 >= MAX_ATTEMPTS;
            OutboxEventStatus next = giveUp ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING;
            log.warn("Failed to publish outbox event {} (attempt {}): {}",
                    event.getId(), event.getAttempts() + 1, e.getMessage());
            outboxEventRepository.markProcessed(event.getId(), next, Instant.now());
        }
    }
}
