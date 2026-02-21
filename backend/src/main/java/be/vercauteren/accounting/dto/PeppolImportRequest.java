package be.vercauteren.accounting.dto;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.InvoiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PeppolImportRequest(
    @NotBlank String falcoDocumentId,
    @NotNull Long supplierId,
    @NotNull Integer year,
    @NotNull InvoiceType type,
    @NotNull DateScope dateScope,
    LocalDate scopeDate,
    String fileDetail,
    String comment
) {}
