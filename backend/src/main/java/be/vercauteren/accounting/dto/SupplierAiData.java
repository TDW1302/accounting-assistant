package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.ExpenseCategory;

/**
 * Identite d'un fournisseur lue sur un de ses documents.
 *
 * @param enterpriseNumber numero d'entreprise ou de TVA tel qu'imprime, ou null
 * @param category         categorie de depense deduite de l'activite, ou null
 */
public record SupplierAiData(
    String enterpriseNumber,
    ExpenseCategory category
) {}
