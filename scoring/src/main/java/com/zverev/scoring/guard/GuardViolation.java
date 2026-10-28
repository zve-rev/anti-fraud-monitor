package com.zverev.scoring.guard;

import com.zverev.entity.enums.AlertSeverity;

public record GuardViolation(
        AlertSeverity severity,
        String message
) {}
