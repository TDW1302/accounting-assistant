package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceExtractionResult(
    InvoiceType type,
    Long supplierId,
    String supplierName,
    BigDecimal amountIncVat,
    BigDecimal amountExVat,
    BigDecimal vatAmount,
    LocalDate receptionDate,
    LocalDate paymentDate,
    DateScope dateScope,
    LocalDate scopeDate,
    String comment,
    ExpenseCategory suggestedCategory
) {}
