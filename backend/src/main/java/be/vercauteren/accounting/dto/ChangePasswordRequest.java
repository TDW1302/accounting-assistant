package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @StrongPassword String newPassword
) {}
