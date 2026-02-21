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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        List<FalcoInboundDocument> documents = response.data();
        if (senderName != null && !senderName.isBlank()) {
            String filter = senderName.toLowerCase();
            documents = documents.stream()
                .filter(doc -> doc.senderName() != null
                    && doc.senderName().toLowerCase().contains(filter))
                .toList();
        }

        List<String> falcoIds = documents.stream()
            .map(FalcoInboundDocument::id)
            .toList();

        Set<String> importedIds = invoiceRepository.findByFalcoDocumentIdIn(falcoIds).stream()
            .map(Invoice::getFalcoDocumentId)
            .collect(Collectors.toSet());

        List<Supplier> suppliers = supplierRepository.findAll();

        return documents.stream()
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

    public List<Supplier> importSuppliers() {
        FalcoInboundListResponse response = falcoApiClient.listInbound(null, null, null, 0, 200);

        if (response.data() == null || response.data().isEmpty()) {
            return List.of();
        }

        // Deduplicate senders by normalized VAT number, keeping the first occurrence
        Map<String, FalcoInboundDocument> uniqueSenders = new LinkedHashMap<>();
        for (FalcoInboundDocument doc : response.data()) {
            String vat = normalizeVat(doc.senderVatNumber());
            if (vat != null && !uniqueSenders.containsKey(vat)) {
                uniqueSenders.put(vat, doc);
            }
        }

        List<Supplier> existingSuppliers = supplierRepository.findAll();
        Map<String, Supplier> existingByVat = existingSuppliers.stream()
            .filter(s -> normalizeVat(s.getEnterpriseNumber()) != null)
            .collect(Collectors.toMap(
                s -> normalizeVat(s.getEnterpriseNumber()),
                s -> s,
                (a, b) -> a
            ));

        List<Supplier> result = new ArrayList<>();
        for (var entry : uniqueSenders.entrySet()) {
            String normalizedVat = entry.getKey();
            FalcoInboundDocument doc = entry.getValue();

            Supplier existing = existingByVat.get(normalizedVat);
            if (existing != null) {
                existing.setName(doc.senderName());
                if (existing.getAlias() == null || existing.getAlias().isBlank()) {
                    existing.setAlias(null);
                }
                existing.setEnterpriseNumber(doc.senderVatNumber());
                result.add(supplierRepository.save(existing));
            } else {
                Supplier created = supplierRepository.save(Supplier.builder()
                    .name(doc.senderName())
                    .enterpriseNumber(doc.senderVatNumber())
                    .build());
                result.add(created);
                existingByVat.put(normalizedVat, created);
            }
        }

        return result;
    }

    private String normalizeVat(String vatNumber) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return null;
        }
        String normalized = vatNumber.replaceAll("[^0-9]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private Supplier matchSupplier(String vatNumber, List<Supplier> suppliers) {
        String normalized = normalizeVat(vatNumber);
        if (normalized == null) {
            return null;
        }
        return suppliers.stream()
            .filter(s -> normalized.equals(normalizeVat(s.getEnterpriseNumber())))
            .findFirst()
            .orElse(null);
    }
}
