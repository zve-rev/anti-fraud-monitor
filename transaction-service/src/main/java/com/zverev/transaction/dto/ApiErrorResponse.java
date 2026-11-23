package com.zverev.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.util.Map;

@Schema(
        name = "ApiErrorResponse",
        description = "Standardized API error response based on RFC 9457 Problem Details."
)
public record ApiErrorResponse(

        @Schema(
                description = "URI identifying the type of error. Defaults to about:blank when no specific error type is defined.",
                example = "about:blank",
                format = "uri"
        )
        URI type,

        @Schema(
                description = "Short, human-readable summary of the error.",
                example = "Invalid request"
        )
        String title,

        @Schema(
                description = "HTTP status code associated with the error.",
                example = "400",
                minimum = "400",
                maximum = "599"
        )
        Integer status,

        @Schema(
                description = "Human-readable explanation of the specific error occurrence.",
                example = "Validation failed"
        )
        String detail,

        @Schema(
                description = "URI identifying the specific request that caused the error.",
                example = "/api/v1/transactions",
                format = "uri"
        )
        URI instance,

        @Schema(
                description = "Additional validation errors mapped by request field name. Present only when request validation fails.",
                example = """
                        {
                          "amount": "must be greater than 0",
                          "merchantName": "must not be blank"
                        }
                        """
        )
        Map<String, String> errors

) {}
