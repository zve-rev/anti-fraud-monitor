package com.zverev.transaction.dto;

import com.zverev.entity.Alert;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.Rule;

import java.time.Instant;

public record AlertResponse(
        Long id,
        Rule rule,
        AlertSeverity severity,
        String message,
        Instant createdAt
) {
    public static AlertResponse toAlertResponse(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getRule(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getCreatedAt()
        );
    }
}
