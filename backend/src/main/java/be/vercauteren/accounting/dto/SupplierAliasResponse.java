package be.vercauteren.accounting.dto;

import java.util.List;

/**
 * Bilan de la generation des alias fournisseurs.
 *
 * @param dryRun     true si aucune modification n'a ete enregistree
 * @param considered fournisseurs examines
 * @param set        alias renseignes
 * @param alreadySet fournisseurs qui avaient deja un alias, laisses intacts
 * @param failed     fournisseurs dont le nom n'a produit aucun alias exploitable
 * @param details    une ligne par fournisseur, avec la source de l'alias
 */
public record SupplierAliasResponse(
    boolean dryRun,
    int considered,
    int set,
    int alreadySet,
    int failed,
    List<String> details
) {}
