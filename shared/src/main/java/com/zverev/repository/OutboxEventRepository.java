package com.zverev.repository;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT 10
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxEvent> findPendingEvents();

    @Modifying
    @Query("""
        UPDATE OutboxEvent o
        SET o.status = :newStatus, o.processedAt = :processedAt, o.attempts = o.attempts + 1
        WHERE o.id = :id
    """)
    void updateEvent(@Param("id") Long id,
                     @Param("newStatus") OutboxEventStatus newStatus,
                     @Param("processedAt") Instant processedAt);
}
