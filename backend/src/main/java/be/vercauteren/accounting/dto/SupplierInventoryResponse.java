package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;
import java.util.List;

/** @param missingAmountCount factures retenues sans montant TVAC, absentes des sommes */
public record SupplierInventoryResponse(
    int year,
    ExpenseCategory category,
    List<SupplierSummary> suppliers,
    AnalysisTotal total,
    int missingAmountCount
) {
}
