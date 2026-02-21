package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.FalcoInboundDocument;
import be.vercauteren.accounting.dto.FalcoInboundListResponse;
import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.dto.PeppolDocumentResponse;
import be.vercauteren.accounting.dto.PeppolImportRequest;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.SupplierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeppolService {

    private final FalcoApiClient falcoApiClient;
    private final InvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;
    private final InvoiceService invoiceService;

    public List<PeppolDocumentResponse> listInbound(LocalDate receivedAfter, LocalDate receivedBefore,
                                                     String senderName, int page, int pageSize) {
        FalcoInboundListResponse response = falcoApiClient.listInbound(
            receivedAfter, receivedBefore, senderName, page, pageSize);

        if (response.data() == null || response.data().isEmpty()) {
            return List.of();
        }

        List<String> falcoIds = response.data().stream()
            .map(FalcoInboundDocument::id)
            .toList();

        Set<String> importedIds = invoiceRepository.findByFalcoDocumentIdIn(falcoIds).stream()
            .map(Invoice::getFalcoDocumentId)
            .collect(Collectors.toSet());

        List<Supplier> suppliers = supplierRepository.findAll();

        return response.data().stream()
            .map(doc -> toResponse(doc, importedIds.contains(doc.id()), suppliers))
            .toList();
    }

    public InvoiceResponse importDocument(PeppolImportRequest request) {
        if (invoiceRepository.existsByFalcoDocumentId(request.falcoDocumentId())) {
            throw new IllegalArgumentException("This Peppol document has already been imported");
        }

        InvoiceRequest invoiceRequest = new InvoiceRequest(
            null,
            request.year(),
            request.type(),
            request.supplierId(),
            null,
            null,
            null,
            LocalDate.now(),
            null,
            true,
            request.comment(),
            null,
            request.dateScope(),
            request.scopeDate(),
            request.fileDetail(),
            request.falcoDocumentId()
        );

        return invoiceService.create(invoiceRequest);
    }

    private PeppolDocumentResponse toResponse(FalcoInboundDocument doc, boolean alreadyImported,
                                               List<Supplier> suppliers) {
        Supplier matched = matchSupplier(doc.senderVatNumber(), suppliers);

        BigDecimal amount = null;
        if (doc.amount() != null && !doc.amount().isBlank()) {
            try {
                amount = new BigDecimal(doc.amount());
            } catch (NumberFormatException ignored) {
            }
        }

        return new PeppolDocumentResponse(
            doc.id(),
            doc.receivedAt(),
            doc.invoiceDate(),
            doc.invoiceDueDate(),
            amount,
            doc.senderName(),
            doc.senderVatNumber(),
            doc.currency(),
            doc.invoiceReference(),
            doc.isCreditNote(),
            alreadyImported,
            matched != null ? matched.getId() : null,
            matched != null ? matched.getName() : null
        );
    }

    private Supplier matchSupplier(String vatNumber, List<Supplier> suppliers) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return null;
        }
        String normalized = vatNumber.replaceAll("[^0-9]", "");
        if (normalized.isEmpty()) {
            return null;
        }
        return suppliers.stream()
            .filter(s -> s.getEnterpriseNumber() != null
                && s.getEnterpriseNumber().replaceAll("[^0-9]", "").equals(normalized))
            .findFirst()
            .orElse(null);
    }
}
