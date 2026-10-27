package com.zverev.service;

import com.zverev.dto.AlertResponse;
import com.zverev.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
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
