package be.vercauteren.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Les lignes a rattacher en une passe, pour la reprise d'un historique entier.
 * Le serveur revalide chacune: une periode hors modele ou deja occupee est
 * ecartee et dite dans la reponse, elle n'emporte pas les autres.
 */
public record RecurringAttachBatchRequest(
    @NotEmpty @Valid List<RecurringAttachRequest> attachments
) {}
