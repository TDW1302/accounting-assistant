package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserUpdateRequest(
    @NotBlank @Email String email,
    @NotNull UserRole role,
    @NotNull Boolean enabled
) {}
