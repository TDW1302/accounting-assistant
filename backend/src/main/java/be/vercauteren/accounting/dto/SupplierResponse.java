package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;

public record SupplierResponse(
    Long id,
    String name,
    String alias,
    String enterpriseNumber,
    ExpenseCategory category,
    DateScope defaultDateScope,
    boolean defaultPeppol
) {}
