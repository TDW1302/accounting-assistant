package be.vercauteren.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une ligne deja au facturier qu'on peut rattacher a un modele recurrent —
 * typiquement un loyer repris de l'Excel. Son numero et son annee ne bougent
 * pas: c'est un enregistrement passe, pas une ligne a requalifier.
 */
public record AttachableInvoice(
    Long invoiceId,
    String displayNumber,
    Integer year,
    LocalDate receptionDate,
    BigDecimal amountIncVat,
    String comment,
    /** Periode proposee, deduite de la date de reception. Le client peut la corriger. */
    LocalDate suggestedPeriodStart,
    String suggestedPeriodLabel,
    /** Faux quand la periode proposee est deja couverte, ou hors du modele. */
    boolean suggestionAvailable,
    /** Motif du refus quand la suggestion n'est pas retenable. */
    String suggestionIssue,
    /**
     * La ligne qui occupe deja la periode proposee, quand c'est ce qui bloque.
     * Nommer le concurrent est le minimum: un refus "periode deja couverte" sans
     * dire par quoi oblige a aller la chercher a la main dans le facturier.
     */
    ConflictingEntry conflict
) {

    /**
     * @param replaceable vrai uniquement pour une echeance engendree par le modele.
     *                    Remplacer la supprime, et une projection du modele se
     *                    refait d'un clic; une ligne d'une autre origine est un
     *                    enregistrement a part entiere, qui ne se supprime pas
     *                    au passage d'un rattachement.
     */
    public record ConflictingEntry(
        Long invoiceId,
        String displayNumber,
        Integer year,
        BigDecimal amountIncVat,
        LocalDate receptionDate,
        boolean replaceable,
        String notReplaceableReason
    ) {}
}
