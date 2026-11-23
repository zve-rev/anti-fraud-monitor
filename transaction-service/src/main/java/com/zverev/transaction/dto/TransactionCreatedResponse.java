package com.zverev.transaction.dto;

import com.zverev.shared.entity.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "TransactionCreatedResponse",
        description = "Response returned after a transaction has been accepted for processing."
)
public record TransactionCreatedResponse(

        @Schema(
                description = "Unique internal identifier assigned to the newly created transaction.",
                example = "1"
        )
        Long transactionId,

        @Schema(
                description = "Initial processing status of the accepted transaction."
        )
        TransactionStatus status

) {}
