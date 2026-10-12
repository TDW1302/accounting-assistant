package be.vercauteren.accounting.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Rattache une ligne existante a un modele recurrent, pour la periode indiquee.
 * La periode est reverifiee contre le calendrier du modele: elle vient du client.
 */
public record RecurringAttachRequest(
    @NotNull Long invoiceId,
    @NotNull LocalDate periodStart
) {}
