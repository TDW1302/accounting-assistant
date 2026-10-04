package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.Periodicity;
import java.time.LocalDate;

/**
 * Le miroir de {@link AttachableInvoice}: les modeles qui peuvent accueillir une
 * ligne donnee, pour partir de la facture plutot que du modele.
 *
 * <p>La periode proposee est calculee par le serveur et non par le client: la
 * ramener au debut de sa periode depend du rythme — mois, trimestre, annee — et
 * reecrire cette regle cote navigateur la ferait diverger a la premiere retouche.
 */
public record RecurringLinkOption(
    Long recurringExpenseId,
    String label,
    Periodicity periodicity,
    LocalDate suggestedPeriodStart,
    String suggestedPeriodLabel,
    /** Faux quand la periode proposee est deja couverte, ou hors du modele. */
    boolean available,
    String issue,
    /** La ligne qui occupe deja cette periode, quand c'est ce qui bloque. */
    AttachableInvoice.ConflictingEntry conflict
) {}
