package be.vercauteren.accounting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param until borne de la periode comparable, ou nul pour les annees entieres.
 *              Seuls le jour et le mois comptent: elle s'applique aux deux annees.
 * @param missingAmountCount factures retenues sans montant TVAC, absentes des sommes
 */
public record CategoryComparisonResponse(
    int year,
    int previousYear,
    LocalDate until,
    List<CategoryComparisonRow> rows,
    CategoryComparisonRow total,
    int missingAmountCount
) {
}
