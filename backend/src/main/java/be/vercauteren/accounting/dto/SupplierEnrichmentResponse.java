package be.vercauteren.accounting.dto;

import java.util.List;

/**
 * Bilan de l'enrichissement des fournisseurs par lecture IA de leurs documents.
 *
 * @param dryRun                  true si aucune modification n'a ete enregistree —
 *                                les appels a l'IA ont bien eu lieu, et sont factures
 * @param considered              fournisseurs auxquels il manquait une donnee
 * @param analysed                fournisseurs pour lesquels un document a ete lu
 * @param enterpriseNumbersFilled numeros d'entreprise renseignes
 * @param numbersRejected         numeros lus mais ecartes, faute d'etre des numeros
 *                                d'entreprise belges valides — souvent une reference
 *                                de contrat prise pour un numero de societe
 * @param categoriesFilled        categories renseignees
 * @param withoutDocument         fournisseurs sans document rattache, non analyses
 * @param failed                  documents dont la lecture a echoue
 * @param lastId                  identifiant du dernier fournisseur du lot, a
 *                                repasser en afterId pour enchainer; null quand il
 *                                n'y avait plus rien a traiter
 * @param details                 une ligne par fournisseur examine
 */
public record SupplierEnrichmentResponse(
    boolean dryRun,
    int considered,
    int analysed,
    int enterpriseNumbersFilled,
    int numbersRejected,
    int categoriesFilled,
    int withoutDocument,
    int failed,
    Long lastId,
    List<String> details
) {}
