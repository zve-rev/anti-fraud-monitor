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

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void send(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
            outboxEventRepository.markProcessed(event.getId(), OutboxEventStatus.SENT, Instant.now());
        } catch (Exception e) {
            log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
            outboxEventRepository.markProcessed(event.getId(), OutboxEventStatus.FAILED, Instant.now());
        }
    }
}
