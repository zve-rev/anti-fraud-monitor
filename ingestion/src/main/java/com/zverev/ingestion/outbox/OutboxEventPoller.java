package com.zverev.ingestion.outbox;

import com.zverev.entity.OutboxEvent;
import com.zverev.ingestion.kafka.KafkaProducer;
import com.zverev.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPoller {

    @Value("${outbox.batch-size}")
    private int batchSize;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducer kafkaProducer;

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents(PageRequest.of(0, batchSize));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Publishing {} outbox events", pending.size());
        for (OutboxEvent event : pending) {
            kafkaProducer.send(event);
        }
    }
}