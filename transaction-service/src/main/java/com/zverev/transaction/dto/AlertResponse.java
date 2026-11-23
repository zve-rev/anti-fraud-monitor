package com.zverev.transaction.dto;

import com.zverev.entity.Alert;
import com.zverev.entity.enums.AlertSeverity;
import com.zverev.entity.enums.Rule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(
        name = "AlertResponse",
        description = "Response containing information about a fraud detection alert generated for a transaction."
)
public record AlertResponse(

        @Schema(
                description = "Unique identifier of the alert.",
                example = "101"
        )
        Long id,

        @Schema(
                description = "Fraud detection rule that triggered the alert."
        )
        Rule rule,

        @Schema(
                description = "Severity level assigned to the alert."
        )
        AlertSeverity severity,

        @Schema(
                description = "Human-readable explanation of why the alert was generated.",
                example = "Transaction amount exceeds the configured threshold."
        )
        String message,

        @Schema(
                description = "Timestamp when the alert was generated.",
                example = "2026-11-20T10:15:31Z",
                type = "string",
                format = "date-time"
        )
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
