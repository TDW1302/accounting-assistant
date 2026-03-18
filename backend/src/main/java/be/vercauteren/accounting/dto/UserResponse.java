package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.AiProvider;
import be.vercauteren.accounting.entity.UserRole;
import java.time.LocalDateTime;

public record UserResponse(
    Long id,
    String username,
    String email,
    UserRole role,
    boolean enabled,
    LocalDateTime passwordChangedAt,
    LocalDateTime passwordExpiresAt,
    LocalDateTime createdAt,
    AiProvider aiProvider
) {}
