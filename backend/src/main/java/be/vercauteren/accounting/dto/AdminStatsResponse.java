package be.vercauteren.accounting.dto;

import java.util.List;

public record AdminStatsResponse(
    long supplierCount,
    List<AdminYearSummary> years
) {
}
