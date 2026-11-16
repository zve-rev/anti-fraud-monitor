package com.zverev.repository;

import com.zverev.entity.OutboxEvent;
import com.zverev.entity.enums.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = :status
        ORDER BY o.createdAt ASC
    """)
    List<OutboxEvent> findPending(@Param("status") OutboxEventStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :newStatus, o.processedAt = :processedAt, o.attempts = o.attempts + 1 WHERE o.id = :id")
    int markProcessed(@Param("id") Long id,
                      @Param("newStatus") OutboxEventStatus newStatus,
                      @Param("processedAt") java.time.Instant processedAt);
}
