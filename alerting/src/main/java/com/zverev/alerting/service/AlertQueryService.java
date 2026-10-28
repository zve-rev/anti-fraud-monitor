package com.zverev.alerting.service;

import com.zverev.alerting.dto.AlertResponse;
import com.zverev.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertQueryService {

    private final AlertRepository alertRepository;

    public AlertQueryService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> findAllAlerts() {
        return alertRepository.findAll()
                .stream()
                .map(AlertResponse::toAlertResponse)
                .toList();
    }
}