package com.zverev.transaction.service;

import com.zverev.shared.exception.TransactionNotFoundException;
import com.zverev.transaction.dto.AlertResponse;
import com.zverev.shared.repository.AlertRepository;
import com.zverev.shared.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
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
            throw new TransactionNotFoundException("Transaction " + transactionId + " not found");
        }
        return alertRepository.findByTransactionId(transactionId, pageable)
                .map(AlertResponse::toAlertResponse);
    }
}