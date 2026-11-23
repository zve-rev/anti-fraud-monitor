package com.zverev.shared.entity;

import com.zverev.shared.entity.enums.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "topic", nullable = false, length = 100, updatable = false)
    private String topic;

    @Column(name = "message_key", updatable = false)
    private String messageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    @Setter
    private Instant processedAt;

    @Column(name = "attempts", nullable = false)
    @Setter
    private int attempts;

    public OutboxEvent(String aggregateId,
                       String payload,
                       String topic,
                       String messageKey) {
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.topic = topic;
        this.messageKey = messageKey;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }
}
