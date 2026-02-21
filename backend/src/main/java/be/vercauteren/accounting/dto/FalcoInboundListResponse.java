package be.vercauteren.accounting.dto;

import java.util.List;

public record FalcoInboundListResponse(
    List<FalcoInboundDocument> data
) {}
