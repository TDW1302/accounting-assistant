package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;

/** {@code category} nul: fournisseurs sans categorie, ou ligne de total. */
public record CategoryComparisonRow(
    ExpenseCategory category,
    AnalysisTotal current,
    AnalysisTotal previous
) {
}
