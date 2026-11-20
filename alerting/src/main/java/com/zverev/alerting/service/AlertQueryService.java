package com.zverev.alerting.service;

import com.zverev.alerting.dto.AlertResponse;
import com.zverev.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertQueryService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public Page<AlertResponse> findAllAlerts(Pageable pageable) {
        return alertRepository.findAll(pageable)
                .map(AlertResponse::toAlertResponse);
    }
}