package com.zverev.ingestion.outbox;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private static final int BATCH_SIZE = 50;
    private static final long OUTBOX_POLL_INTERVAL_MS = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventSender outboxEventSender;

    @Scheduled(fixedDelay = OUTBOX_POLL_INTERVAL_MS)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findPending(OutboxEventStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Publishing {} outbox events", pending.size());
        for (OutboxEvent event : pending) {
            outboxEventSender.send(event);
        }
    }
}