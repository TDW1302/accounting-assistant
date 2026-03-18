package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceRequest(
    Integer subNumber,
    @NotNull @Min(2000) @Max(2100) Integer year,
    @NotNull InvoiceType type,
    @NotNull Long supplierId,
    @DecimalMin("0") BigDecimal amountIncVat,
    @DecimalMin("0") BigDecimal amountExVat,
    @DecimalMin("0") BigDecimal vatAmount,
    @NotNull LocalDate receptionDate,
    LocalDate paymentDate,
    @NotNull Boolean peppol,
    String comment,
    @NotNull DateScope dateScope,
    LocalDate scopeDate,
    String fileDetail,
    String falcoDocumentId
) {}
