package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.Periodicity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringExpenseRequest(
    @NotBlank String label,
    @NotNull Long supplierId,
    @DecimalMin("0") BigDecimal amountIncVat,
    @DecimalMin("0") BigDecimal amountExVat,
    @DecimalMin("0") BigDecimal vatAmount,
    @NotNull Periodicity periodicity,
    /** Premiere echeance: porte le jour, et le mois d'ancrage d'un rythme non mensuel. */
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @NotNull Boolean paidOnDueDate,
    String comment,
    String fileDetail,
    @NotNull Boolean active
) {}
