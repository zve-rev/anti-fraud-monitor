package com.zverev.dto;

import com.zverev.entity.enums.AlertSeverity;

public record GuardViolation(
        AlertSeverity severity,
        String message
) {}
