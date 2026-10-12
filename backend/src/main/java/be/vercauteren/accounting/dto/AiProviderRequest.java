package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.AiProvider;
import jakarta.validation.constraints.NotNull;

public record AiProviderRequest(
    @NotNull AiProvider aiProvider
) {}
