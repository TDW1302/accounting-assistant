package be.vercauteren.accounting.dto;

import java.util.List;

public record ExcelImportResponse(
    int suppliersCreated,
    int invoicesImported,
    int rowsSkipped,
    List<String> warnings
) {}
