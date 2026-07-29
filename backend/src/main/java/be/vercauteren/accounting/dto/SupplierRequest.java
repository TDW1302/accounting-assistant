package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;
import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
    @NotBlank String name,
    String alias,
    String enterpriseNumber,
    ExpenseCategory category
) {}
