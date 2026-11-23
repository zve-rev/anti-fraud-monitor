package com.zverev.transaction.outbox;

import com.zverev.entity.OutboxEvent;
import com.zverev.transaction.kafka.KafkaProducer;
import com.zverev.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducer kafkaProducer;

    @Transactional
    @Scheduled(fixedDelayString = "1000")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents();

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Publishing {} outbox events", pending.size());
        for (OutboxEvent event : pending) {
            kafkaProducer.send(event);
        }
    }
}