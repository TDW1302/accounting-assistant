package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierSummary(
    Long supplierId,
    String supplierName,
    ExpenseCategory category,
    int count,
    BigDecimal amountIncVat,
    BigDecimal amountExVat,
    LocalDate firstDate,
    LocalDate lastDate
) {
}
