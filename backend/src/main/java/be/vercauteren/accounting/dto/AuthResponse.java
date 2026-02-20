package be.vercauteren.accounting.dto;

public record AuthResponse(
    UserResponse user,
    boolean passwordExpired
) {}
