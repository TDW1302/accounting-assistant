package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;
import java.util.List;

/**
 * Candidats a la fusion, pour relecture humaine. Rien n'est modifie.
 *
 * @param suppliers       fournisseurs en base
 * @param pairs           couples rapproches, du plus sur au plus faible
 * @param withoutDocument fournisseurs dont aucune facture ne porte de document —
 *                        souvent des ecritures qui ne sont pas des fournisseurs
 *                        commerciaux (TVA, cotisations, versements anticipes)
 */
public record SupplierDuplicatesResponse(
    int suppliers,
    List<Pair> pairs,
    List<String> withoutDocument
) {

    /**
     * @param reason pourquoi les deux fiches ont ete rapprochees
     */
    public record Pair(String reason, Candidate left, Candidate right) {}

    /**
     * @param invoiceCount  factures rattachees, pour choisir la fiche a conserver
     * @param documentCount factures portant un document
     */
    public record Candidate(
        Long id,
        String name,
        String alias,
        String enterpriseNumber,
        ExpenseCategory category,
        int invoiceCount,
        int documentCount
    ) {}
}
