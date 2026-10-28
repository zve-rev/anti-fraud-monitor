package com.zverev.alerting.controller;

import com.zverev.alerting.dto.AlertResponse;
import com.zverev.alerting.service.AlertQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertQueryService alertQueryService;

    public AlertController(AlertQueryService alertQueryService) {
        this.alertQueryService = alertQueryService;
    }

    @GetMapping
    public List<AlertResponse> findAll() {
        return alertQueryService.findAllAlerts();
    }
}