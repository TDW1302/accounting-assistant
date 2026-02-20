package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6) String password,
    @NotNull UserRole role,
    @NotNull Boolean enabled
) {}
