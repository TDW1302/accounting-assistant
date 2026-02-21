package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.dto.PeppolDocumentResponse;
import be.vercauteren.accounting.dto.PeppolImportRequest;
import be.vercauteren.accounting.service.PeppolService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/peppol")
@RequiredArgsConstructor
public class PeppolController {

    private final PeppolService peppolService;

    @GetMapping("/inbound")
    public List<PeppolDocumentResponse> listInbound(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedAfter,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedBefore,
        @RequestParam(required = false) String senderName,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int pageSize) {
        return peppolService.listInbound(receivedAfter, receivedBefore, senderName, page, pageSize);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse importDocument(@Valid @RequestBody PeppolImportRequest request) {
        return peppolService.importDocument(request);
    }
}
