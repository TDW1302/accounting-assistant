package be.vercauteren.accounting.dto;

import java.util.List;

public record RecurringGenerationResponse(
    List<InvoiceResponse> created,
    /** Echeances demandees mais non creees, avec la raison. */
    List<String> skipped
) {}
