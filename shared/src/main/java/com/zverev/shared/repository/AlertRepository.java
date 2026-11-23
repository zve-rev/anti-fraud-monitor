package com.zverev.shared.repository;

import com.zverev.shared.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findByTransactionId(Long transactionId, Pageable pageable);
}
