package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.specification.InvoiceSpecification;
import jakarta.persistence.EntityNotFoundException;
import be.vercauteren.accounting.util.InMemoryMultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SupplierService supplierService;
    private final FileNameGenerator fileNameGenerator;
    private final FileStorageService fileStorageService;
    private final AuthService authService;
    private final ImageToPdfService imageToPdfService;
    private final PlatformTransactionManager transactionManager;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/png", "image/gif", "image/bmp", "image/tiff", "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp"
    );

    public List<InvoiceResponse> findByYear(Integer year) {
        return invoiceRepository.findByYearOrderByNumberAscSubNumberAsc(year).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<InvoiceResponse> search(Integer year, Long supplierId, BigDecimal amountMin,
                                         BigDecimal amountMax, LocalDate dateFrom, LocalDate dateTo,
                                         String keyword, ExpenseCategory category) {
        // Les criteres sont accumules puis combines: Specification.and n'accepte plus
        // de null, un point de depart neutre ne peut donc pas etre une specification.
        List<Specification<Invoice>> criteria = new ArrayList<>();

        if (year != null) {
            criteria.add(InvoiceSpecification.hasYear(year));
        }
        if (supplierId != null) {
            criteria.add(InvoiceSpecification.hasSupplier(supplierId));
        }
        if (amountMin != null || amountMax != null) {
            criteria.add(InvoiceSpecification.amountBetween(amountMin, amountMax));
        }
        if (dateFrom != null || dateTo != null) {
            criteria.add(InvoiceSpecification.receptionDateBetween(dateFrom, dateTo));
        }
        if (keyword != null && !keyword.isBlank()) {
            criteria.add(InvoiceSpecification.keywordSearch(keyword.trim()));
        }
        if (category != null) {
            criteria.add(InvoiceSpecification.hasCategory(category));
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "year")
            .and(Sort.by(Sort.Direction.ASC, "number"));

        return invoiceRepository.findAll(Specification.allOf(criteria), sort).stream()
            .map(this::toResponse)
            .toList();
    }

    /** Invoices whose document is still missing, optionally restricted to one year. */
    public List<InvoiceResponse> findMissingDocuments(Integer year) {
        Specification<Invoice> spec = InvoiceSpecification.missingDocument();
        if (year != null) {
            spec = spec.and(InvoiceSpecification.hasYear(year));
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "year")
            .and(Sort.by(Sort.Direction.ASC, "number"))
            .and(Sort.by(Sort.Direction.ASC, "subNumber"));

        return invoiceRepository.findAll(spec, sort).stream()
            .map(this::toResponse)
            .toList();
    }

    public InvoiceResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Creates an invoice with SERIALIZABLE isolation and retry logic for concurrent numbering conflicts.
     * Uses TransactionTemplate to avoid Spring proxy self-invocation issues.
     * When linkToNumber is set, creates a sub-invoice sharing the same number.
     */
    public InvoiceResponse create(InvoiceRequest request) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return txTemplate.execute(status -> {
                    Supplier supplier = supplierService.getOrThrow(request.supplierId());

                    int assignedNumber;
                    Integer assignedSubNumber;

                    if (request.linkToNumber() != null) {
                        // Sub-invoice mode
                        assignedNumber = request.linkToNumber();
                        List<Invoice> siblings = invoiceRepository.findByYearAndNumberOrderBySubNumberAsc(
                            request.year(), assignedNumber);

                        if (siblings.isEmpty()) {
                            throw new EntityNotFoundException(
                                "No invoice found with number " + assignedNumber + " for year " + request.year());
                        }

                        // Promote original invoice to .1 if it has no subNumber yet
                        Invoice first = siblings.getFirst();
                        if (first.getSubNumber() == null) {
                            first.setSubNumber(1);
                            invoiceRepository.save(first);
                            // Rename the file if it exists
                            if (first.getFilePath() != null) {
                                try {
                                    String newName = fileNameGenerator.generate(first);
                                    String newPath = fileStorageService.rename(
                                        first.getFilePath(), newName, first.getYear());
                                    first.setFilePath(newPath);
                                    invoiceRepository.save(first);
                                } catch (IOException e) {
                                    log.warn("Failed to rename file for promoted invoice {}.1: {}",
                                        assignedNumber, e.getMessage());
                                }
                            }
                        }

                        // Determine next sub-number with lock
                        int maxSub = invoiceRepository.findFirstByYearAndNumberOrderBySubNumberDesc(
                                request.year(), assignedNumber)
                            .map(inv -> inv.getSubNumber() != null ? inv.getSubNumber() : 1)
                            .orElse(1);
                        assignedSubNumber = maxSub + 1;
                    } else {
                        // Normal mode: auto-increment number
                        assignedNumber = invoiceRepository.findFirstByYearOrderByNumberDesc(request.year())
                            .map(inv -> inv.getNumber() + 1)
                            .orElse(1);
                        assignedSubNumber = null;
                    }

                    Invoice invoice = Invoice.builder()
                        .number(assignedNumber)
                        .subNumber(assignedSubNumber)
                        .year(request.year())
                        .type(request.type())
                        .supplier(supplier)
                        .amountIncVat(request.amountIncVat())
                        .amountExVat(request.amountExVat())
                        .vatAmount(request.vatAmount())
                        .receptionDate(request.receptionDate())
                        .paymentDate(request.paymentDate())
                        .peppol(request.peppol())
                        .comment(request.comment())
                        .filePath(null)
                        .dateScope(request.dateScope())
                        .scopeDate(request.scopeDate())
                        .fileDetail(request.fileDetail())
                        .falcoDocumentId(request.falcoDocumentId())
                        .createdBy(authService.getCurrentUser().orElse(null))
                        .build();

                    return toResponse(invoiceRepository.save(invoice));
                });
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                    throw new IllegalStateException("Failed to generate unique invoice number after " + MAX_RETRY_ATTEMPTS + " attempts", e);
                }
                log.warn("Invoice number conflict on attempt {}, retrying...", attempt + 1);
            }
        }
        throw new IllegalStateException("Unexpected state in invoice creation retry");
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = getOrThrow(id);
        Supplier supplier = supplierService.getOrThrow(request.supplierId());

        if (!invoice.getYear().equals(request.year())) {
            throw new IllegalArgumentException("Cannot change invoice year. Delete and recreate the invoice instead.");
        }

        if (invoice.getSubNumber() != null && request.subNumber() != null
                && !invoice.getSubNumber().equals(request.subNumber())) {
            throw new IllegalArgumentException("Cannot change sub-number of a sub-invoice.");
        }
        invoice.setType(request.type());
        invoice.setSupplier(supplier);
        invoice.setAmountIncVat(request.amountIncVat());
        invoice.setAmountExVat(request.amountExVat());
        invoice.setVatAmount(request.vatAmount());
        invoice.setReceptionDate(request.receptionDate());
        invoice.setPaymentDate(request.paymentDate());
        invoice.setPeppol(request.peppol());
        invoice.setComment(request.comment());
        invoice.setDateScope(request.dateScope());
        invoice.setScopeDate(request.scopeDate());
        invoice.setFileDetail(request.fileDetail());

        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse uploadFile(Long id, MultipartFile file) throws IOException {
        validateFileType(file);
        Invoice invoice = getOrThrow(id);
        String generatedName = fileNameGenerator.generate(invoice);

        if (invoice.getFilePath() != null) {
            fileStorageService.delete(invoice.getFilePath());
        }

        MultipartFile fileToStore = file;
        if (imageToPdfService.isImage(file)) {
            byte[] pdfBytes = imageToPdfService.convertToPdf(file);
            fileToStore = new InMemoryMultipartFile(
                file.getName(), generatedName, "application/pdf", pdfBytes);
        }

        String filePath = fileStorageService.store(fileToStore, generatedName, invoice.getYear());
        invoice.setFilePath(filePath);

        try {
            return toResponse(invoiceRepository.save(invoice));
        } catch (Exception e) {
            // Compensate: remove the stored file if DB update fails
            try {
                fileStorageService.delete(filePath);
            } catch (IOException deleteEx) {
                log.warn("Failed to clean up file '{}' after DB error: {}", filePath, deleteEx.getMessage());
            }
            throw e;
        }
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = getOrThrow(id);

        authService.getCurrentUser().ifPresent(currentUser -> {
            if (currentUser.getRole() != UserRole.ADMIN) {
                if (invoice.getCreatedBy() == null || !invoice.getCreatedBy().getId().equals(currentUser.getId())) {
                    throw new IllegalStateException("You can only delete invoices you created");
                }
            }
        });

        if (invoice.getFilePath() != null) {
            try {
                fileStorageService.delete(invoice.getFilePath());
            } catch (IOException e) {
                log.warn("Failed to delete file '{}' for invoice {}: {}", invoice.getFilePath(), id, e.getMessage());
            }
        }
        invoiceRepository.delete(invoice);
    }

    private void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("File type not allowed. Accepted: PDF, JPEG, PNG, GIF, BMP, TIFF, WebP");
        }
        String filename = file.getOriginalFilename();
        if (filename != null) {
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex <= 0) {
                throw new IllegalArgumentException("File must have a valid extension. Accepted: pdf, jpg, jpeg, png, gif, bmp, tiff, tif, webp");
            }
            String extension = filename.substring(dotIndex + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new IllegalArgumentException("File extension not allowed. Accepted: pdf, jpg, jpeg, png, gif, bmp, tiff, tif, webp");
            }
        }
    }

    private Invoice getOrThrow(Long id) {
        return invoiceRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
            invoice.getId(),
            invoice.getNumber(),
            invoice.getSubNumber(),
            invoice.getYear(),
            invoice.getType(),
            supplierService.toResponse(invoice.getSupplier()),
            invoice.getAmountIncVat(),
            invoice.getAmountExVat(),
            invoice.getVatAmount(),
            invoice.getReceptionDate(),
            invoice.getPaymentDate(),
            invoice.isPeppol(),
            invoice.getComment(),
            invoice.getFilePath(),
            invoice.getDateScope(),
            invoice.getScopeDate(),
            invoice.getFileDetail(),
            fileNameGenerator.generate(invoice),
            invoice.getFalcoDocumentId()
        );
    }
}
