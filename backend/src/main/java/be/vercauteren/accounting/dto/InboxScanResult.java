package be.vercauteren.accounting.dto;

import java.util.List;

public record InboxScanResult(
    int filesProcessed,
    int matched,
    int created,
    int errors,
    List<String> errorFiles
) {}
