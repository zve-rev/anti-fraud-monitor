package com.zverev.alerting.controller;

import com.zverev.alerting.dto.AlertResponse;
import com.zverev.alerting.service.AlertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/alerts")
@RequiredArgsConstructor
public class AlertQueryController {

    private final AlertQueryService alertQueryService;

    @GetMapping
    public Page<AlertResponse> listAllAlerts(
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return alertQueryService.findAllAlerts(pageable);
    }
}