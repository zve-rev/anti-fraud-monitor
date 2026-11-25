package com.zverev.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing a JWT access token.")
public record TokenResponse(

        @Schema(
                description = "Signed JWT access token used for authenticated requests.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huLmRvZSJ9.signature"
        )
        String token
) {}

