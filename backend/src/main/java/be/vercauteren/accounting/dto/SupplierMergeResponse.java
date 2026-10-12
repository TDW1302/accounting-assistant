package be.vercauteren.accounting.dto;

import java.util.List;

/**
 * Bilan de la fusion de deux fiches fournisseurs.
 *
 * @param keptId              fiche conservee
 * @param keptName            son nom
 * @param removedName         nom de la fiche supprimee
 * @param invoicesReassigned  factures transferees vers la fiche conservee
 * @param fieldsFilled        champs de la fiche conservee completes par l'autre
 */
public record SupplierMergeResponse(
    Long keptId,
    String keptName,
    String removedName,
    int invoicesReassigned,
    List<String> fieldsFilled
) {}
