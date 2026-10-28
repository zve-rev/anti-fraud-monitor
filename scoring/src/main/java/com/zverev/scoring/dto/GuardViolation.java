package com.zverev.scoring.dto;

import com.zverev.entity.enums.AlertSeverity;

public record GuardViolation(
        AlertSeverity severity,
        String message
) {}
