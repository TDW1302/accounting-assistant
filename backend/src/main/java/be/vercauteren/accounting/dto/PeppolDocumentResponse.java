package be.vercauteren.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeppolDocumentResponse(
    String id,
    LocalDate receivedAt,
    LocalDate invoiceDate,
    LocalDate invoiceDueDate,
    BigDecimal amount,
    String senderName,
    String senderVatNumber,
    String currency,
    String invoiceReference,
    Boolean isCreditNote,
    boolean alreadyImported,
    Long matchedSupplierId,
    String matchedSupplierName
) {}
