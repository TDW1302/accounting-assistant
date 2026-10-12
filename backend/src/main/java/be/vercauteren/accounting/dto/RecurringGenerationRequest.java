package be.vercauteren.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Les echeances que l'utilisateur a retenues parmi celles proposees. Le serveur
 * ne fait pas confiance a cette liste: il recalcule ce qui est du pour chaque
 * modele et ignore tout ce qui n'y figure pas.
 */
public record RecurringGenerationRequest(
    @NotEmpty @Valid List<OccurrenceRef> occurrences
) {
    public record OccurrenceRef(
        @NotNull Long recurringExpenseId,
        @NotNull LocalDate periodStart
    ) {}
}
