package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.service.InvoiceExtractionService;
import be.vercauteren.accounting.service.InvoiceService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceExtractionService invoiceExtractionService;

    @GetMapping
    public List<InvoiceResponse> findByYear(@RequestParam Integer year) {
        return invoiceService.findByYear(year);
    }

    @GetMapping("/search")
    public List<InvoiceResponse> search(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Long supplierId,
        @RequestParam(required = false) BigDecimal amountMin,
        @RequestParam(required = false) BigDecimal amountMax,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) String keyword) {
        return invoiceService.search(year, supplierId, amountMin, amountMax, dateFrom, dateTo, keyword);
    }

    @GetMapping("/{id}")
    public InvoiceResponse findById(@PathVariable Long id) {
        return invoiceService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse create(@Valid @RequestBody InvoiceRequest request) {
        return invoiceService.create(request);
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        invoiceService.delete(id);
    }

    @PostMapping("/extract")
    public InvoiceExtractionResult extract(@RequestParam("file") MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new IllegalArgumentException("Only PDF files are accepted for extraction");
        }
        return invoiceExtractionService.extract(file);
    }

    @PostMapping("/{id}/upload")
    public InvoiceResponse uploadFile(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        return invoiceService.uploadFile(id, file);
    }
}
