package com.zverev.riskassessment.dto;

import com.zverev.shared.entity.enums.AlertSeverity;

public record GuardViolation(
        AlertSeverity severity,
        String message
) {}
