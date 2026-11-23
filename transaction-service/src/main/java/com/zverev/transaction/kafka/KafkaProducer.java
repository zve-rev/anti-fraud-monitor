package com.zverev.transaction.kafka;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaProducer {

    @Value("${kafka.max-attempts}")
    private int maxAttempts;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public void send(OutboxEvent event) {
        Long eventId = event.getId();

        try {
            String topic = event.getTopic();
            String messageKey = event.getMessageKey();
            String payload = event.getPayload();

            kafkaTemplate.send(topic, messageKey, payload).get(10, TimeUnit.SECONDS);
            outboxEventRepository.updateEvent(eventId, OutboxEventStatus.SENT, Instant.now());
        } catch (Exception e) {
            int attempts = event.getAttempts();
            boolean giveUp = attempts + 1 >= maxAttempts;

            OutboxEventStatus newEventStatus = giveUp ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING;
            outboxEventRepository.updateEvent(eventId, newEventStatus, Instant.now());

            String message = e.getMessage();
            log.warn("Failed to publish outbox event {} (attempt {}): {}", eventId, attempts + 1, message);
        }
    }
}
