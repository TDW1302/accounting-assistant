package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.Periodicity;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringExpenseResponse(
    Long id,
    String label,
    SupplierResponse supplier,
    BigDecimal amountIncVat,
    BigDecimal amountExVat,
    BigDecimal vatAmount,
    Periodicity periodicity,
    LocalDate startDate,
    LocalDate endDate,
    boolean paidOnDueDate,
    String comment,
    String fileDetail,
    boolean active,
    /** Nombre d'echeances deja inscrites au facturier. */
    int generatedCount,
    /** Debut de la derniere periode engendree, pour situer ou on en est. */
    LocalDate lastGeneratedPeriod,
    /** Echeances echues qui restent a inscrire. */
    int pendingCount,
    /** Faux tant qu'aucune echeance n'existe: seul cas ou le modele est supprimable. */
    boolean deletable
) {}
