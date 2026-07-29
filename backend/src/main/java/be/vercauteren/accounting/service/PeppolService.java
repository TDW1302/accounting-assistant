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
import be.vercauteren.accounting.util.VatUtils;
import be.vercauteren.accounting.dto.SupplierResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.falco.enabled", havingValue = "true")
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
            request.amountIncVat(),
            null,
            null,
            request.receptionDate() != null ? request.receptionDate() : LocalDate.now(),
            null,
            true,
            request.comment(),
            request.dateScope(),
            request.scopeDate(),
            request.fileDetail(),
            request.falcoDocumentId(),
            null
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
            } catch (NumberFormatException e) {
                log.warn("Failed to parse Peppol amount: {}", doc.amount());
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

    @Transactional
    public List<SupplierResponse> importSuppliers() {
        List<FalcoInboundDocument> allDocuments = fetchAllInboundDocuments();

        if (allDocuments.isEmpty()) {
            return List.of();
        }

        // Deduplicate senders by normalized VAT number, keeping the first occurrence
        Map<String, FalcoInboundDocument> uniqueSenders = new LinkedHashMap<>();
        for (FalcoInboundDocument doc : allDocuments) {
            String vat = VatUtils.normalizeVat(doc.senderVatNumber());
            if (vat != null && !uniqueSenders.containsKey(vat)) {
                uniqueSenders.put(vat, doc);
            }
        }

        List<Supplier> existingSuppliers = supplierRepository.findAll();
        Map<String, Supplier> existingByVat = existingSuppliers.stream()
            .filter(s -> VatUtils.normalizeVat(s.getEnterpriseNumber()) != null)
            .collect(Collectors.toMap(
                s -> VatUtils.normalizeVat(s.getEnterpriseNumber()),
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

        return result.stream().map(this::toSupplierResponse).toList();
    }

    private SupplierResponse toSupplierResponse(Supplier supplier) {
        return new SupplierResponse(
            supplier.getId(),
            supplier.getName(),
            supplier.getAlias(),
            supplier.getEnterpriseNumber(),
            supplier.getCategory()
        );
    }

    private static final int MAX_PAGES = 50;

    private List<FalcoInboundDocument> fetchAllInboundDocuments() {
        List<FalcoInboundDocument> allDocuments = new ArrayList<>();
        int page = 0;
        int pageSize = 200;
        while (page < MAX_PAGES) {
            FalcoInboundListResponse response = falcoApiClient.listInbound(null, null, null, page, pageSize);
            if (response.data() == null || response.data().isEmpty()) {
                break;
            }
            allDocuments.addAll(response.data());
            if (response.data().size() < pageSize) {
                break;
            }
            page++;
        }
        return allDocuments;
    }

    private Supplier matchSupplier(String vatNumber, List<Supplier> suppliers) {
        String normalized = VatUtils.normalizeVat(vatNumber);
        if (normalized == null) {
            return null;
        }
        return suppliers.stream()
            .filter(s -> normalized.equals(VatUtils.normalizeVat(s.getEnterpriseNumber())))
            .findFirst()
            .orElse(null);
    }
}
