package com.zverev.transaction.service;

import com.zverev.transaction.dto.AlertResponse;
import com.zverev.repository.AlertRepository;
import com.zverev.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<AlertResponse> findTransactionAlerts(Long transactionId, Pageable pageable) {
        if (!transactionRepository.existsById(transactionId)) {
            throw new ResourceNotFoundException("Transaction " + transactionId + " not found");
        }
        return alertRepository.findByTransactionId(transactionId, pageable)
                .map(AlertResponse::toAlertResponse);
    }
}