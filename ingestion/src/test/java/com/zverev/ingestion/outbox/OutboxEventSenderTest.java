package com.zverev.ingestion.outbox;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import com.zverev.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventSenderTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxEventSender sender;

    @BeforeEach
    void setUp() {
        sender = new OutboxEventSender(outboxEventRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("kafka ok - event marked SENT")
    void success_sent() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        sender.send(event(0));

        verify(outboxEventRepository).markProcessed(any(), eq(OutboxEventStatus.SENT), any(Instant.class));
    }

    @Test
    @DisplayName("kafka fails, attempts left - event stays PENDING")
    void failure_stillPending() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        sender.send(event(0));

        verify(outboxEventRepository).markProcessed(any(), eq(OutboxEventStatus.PENDING), any(Instant.class));
    }

    @Test
    @DisplayName("kafka fails on last attempt - event marked FAILED")
    void failure_lastAttempt_failed() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        sender.send(event(4));

        verify(outboxEventRepository).markProcessed(any(), eq(OutboxEventStatus.FAILED), any(Instant.class));
    }

    private OutboxEvent event(int attempts) {
        OutboxEvent event = new OutboxEvent("agg-1", "TransactionCreated", "{}", "transactions.raw", "card-1");
        event.setAttempts(attempts);
        return event;
    }
}
