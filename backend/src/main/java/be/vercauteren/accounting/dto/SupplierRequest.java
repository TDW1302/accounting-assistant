package be.vercauteren.accounting.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
    @NotBlank String name,
    String alias,
    String enterpriseNumber
) {}
