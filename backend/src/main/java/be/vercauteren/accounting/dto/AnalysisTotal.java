package be.vercauteren.accounting.dto;

import java.math.BigDecimal;

/**
 * Somme d'un groupe de factures. Les montants manquants sont ignores, pas comptes
 * comme zero: {@code count} compte les factures, avec ou sans montant.
 */
public record AnalysisTotal(
    BigDecimal amountIncVat,
    BigDecimal amountExVat,
    int count
) {
}
