package be.vercauteren.accounting.dto;

public record SupplierResponse(
    Long id,
    String name,
    String alias,
    String enterpriseNumber
) {}
