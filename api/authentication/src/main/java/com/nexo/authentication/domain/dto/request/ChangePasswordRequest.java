package com.nexo.authentication.domain.dto.request;

import com.nexo.authentication.domain.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Change the authenticated user's password")
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @StrongPassword
        String newPassword) {
}
