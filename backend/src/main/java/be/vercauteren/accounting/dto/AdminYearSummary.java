package be.vercauteren.accounting.dto;

public record AdminYearSummary(
    Integer year,
    long invoiceCount
) {
}
