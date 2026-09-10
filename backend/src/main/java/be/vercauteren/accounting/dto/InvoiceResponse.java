package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
    Long id,
    Integer number,
    Integer subNumber,
    /** Numero mis en forme pour l'affichage: "001", "008.1", "D003". */
    String displayNumber,
    InvoiceSeries series,
    Integer year,
    InvoiceType type,
    SupplierResponse supplier,
    BigDecimal amountIncVat,
    BigDecimal amountExVat,
    BigDecimal vatAmount,
    LocalDate receptionDate,
    LocalDate paymentDate,
    boolean peppol,
    String comment,
    String filePath,
    DateScope dateScope,
    LocalDate scopeDate,
    String fileDetail,
    String generatedFileName,
    String falcoDocumentId,
    Long recurringExpenseId,
    String recurringExpenseLabel
) {}
