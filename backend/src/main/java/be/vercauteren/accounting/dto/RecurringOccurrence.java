package be.vercauteren.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une echeance due mais pas encore inscrite au facturier. C'est un calcul, pas
 * une ligne en base: elle n'existe que le temps d'etre proposee puis validee.
 */
public record RecurringOccurrence(
    Long recurringExpenseId,
    String label,
    String supplierName,
    /** Debut de la periode couverte. Identifie l'echeance, et date le nom de fichier. */
    LocalDate periodStart,
    /** "09/2026", "2026 T3", "2026". */
    String periodLabel,
    LocalDate dueDate,
    Integer year,
    BigDecimal amountIncVat
) {}
