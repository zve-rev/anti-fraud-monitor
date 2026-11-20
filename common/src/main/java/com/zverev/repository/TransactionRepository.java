package com.zverev.repository;

import com.zverev.entity.Transaction;
import com.zverev.entity.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    boolean existsByExternalId(UUID externalId);

    @Query("SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH t.alerts WHERE t.id = :id")
    Optional<Transaction> findByIdWithAlerts(@Param("id") Long id);

    Optional<Transaction> findByExternalId(UUID externalId);
}
