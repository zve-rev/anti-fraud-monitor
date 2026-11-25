package com.zverev.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "User authentication credentials.")
public class LoginRequest {

    @NotBlank
    @Schema(
            description = "Unique user login.",
            example = "john.doe",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String login;

    @NotBlank
    @Schema(
            description = "User password.",
            example = "StrongPassword123!",
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "password"
    )
    private String password;
}
