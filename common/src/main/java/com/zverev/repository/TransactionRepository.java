package com.zverev.repository;

import com.zverev.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END
        FROM Transaction t
        WHERE t.externalId = :externalId
        """)
    boolean existsByExternalId(@Param("externalId") UUID externalId);

    Optional<Transaction> findByExternalId(UUID externalId);
}
