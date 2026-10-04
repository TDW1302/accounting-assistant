package be.vercauteren.accounting.dto;

import java.util.List;

public record RecurringAttachBatchResponse(
    List<InvoiceResponse> attached,
    /** Lignes demandees mais non rattachees, avec la raison. */
    List<String> skipped
) {}
