package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceRequest(
    Integer subNumber,
    @NotNull Integer year,
    @NotNull InvoiceType type,
    @NotNull Long supplierId,
    BigDecimal amountIncVat,
    BigDecimal amountExVat,
    BigDecimal vatAmount,
    @NotNull LocalDate receptionDate,
    LocalDate paymentDate,
    @NotNull Boolean peppol,
    String comment,
    String filePath,
    @NotNull DateScope dateScope,
    LocalDate scopeDate,
    String fileDetail
) {}
