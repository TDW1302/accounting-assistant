package be.vercauteren.accounting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * @param newSuppliers fournisseurs factures en {@code year} et jamais en {@code previousYear}
 * @param missingSuppliers fournisseurs factures en {@code previousYear}, avant {@code until}
 *                         s'il est donne, et pas encore en {@code year}
 */
public record SupplierChangesResponse(
    int year,
    int previousYear,
    LocalDate until,
    List<SupplierSummary> newSuppliers,
    List<SupplierSummary> missingSuppliers
) {
}
