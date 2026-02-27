package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceResponse(
    Long id,
    Integer number,
    Integer subNumber,
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
    String falcoDocumentId
) {}
