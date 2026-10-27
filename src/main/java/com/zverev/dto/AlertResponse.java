package com.zverev.dto;

import com.zverev.entity.Alert;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.Rule;

import java.time.Instant;

public record AlertResponse(
        Rule rule,
        AlertSeverity severity,
        String message,
        Instant createdAt
) {
    public static AlertResponse toAlertResponse(Alert alert) {
        return new AlertResponse(
                alert.getRule(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getCreatedAt()
        );
    }
}

