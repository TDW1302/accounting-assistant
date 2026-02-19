package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.specification.InvoiceSpecification;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SupplierService supplierService;
    private final FileNameGenerator fileNameGenerator;

    public List<InvoiceResponse> findByYear(Integer year) {
        return invoiceRepository.findByYearOrderByNumberAscSubNumberAsc(year).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<InvoiceResponse> search(Integer year, Long supplierId, BigDecimal amountMin,
                                         BigDecimal amountMax, LocalDate dateFrom, LocalDate dateTo,
                                         String keyword) {
        Specification<Invoice> spec = Specification.where(null);

        if (year != null) {
            spec = spec.and(InvoiceSpecification.hasYear(year));
        }
        if (supplierId != null) {
            spec = spec.and(InvoiceSpecification.hasSupplier(supplierId));
        }
        if (amountMin != null || amountMax != null) {
            spec = spec.and(InvoiceSpecification.amountBetween(amountMin, amountMax));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(InvoiceSpecification.receptionDateBetween(dateFrom, dateTo));
        }
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(InvoiceSpecification.keywordSearch(keyword.trim()));
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "year")
            .and(Sort.by(Sort.Direction.ASC, "number"));

        return invoiceRepository.findAll(spec, sort).stream()
            .map(this::toResponse)
            .toList();
    }

    public InvoiceResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        Supplier supplier = supplierService.getOrThrow(request.supplierId());

        int nextNumber = invoiceRepository.findFirstByYearOrderByNumberDesc(request.year())
            .map(inv -> inv.getNumber() + 1)
            .orElse(1);

        Invoice invoice = Invoice.builder()
            .number(nextNumber)
            .subNumber(request.subNumber())
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
            .filePath(request.filePath())
            .dateScope(request.dateScope())
            .scopeDate(request.scopeDate())
            .fileDetail(request.fileDetail())
            .build();

        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest request) {
        Invoice invoice = getOrThrow(id);
        Supplier supplier = supplierService.getOrThrow(request.supplierId());

        invoice.setSubNumber(request.subNumber());
        invoice.setYear(request.year());
        invoice.setType(request.type());
        invoice.setSupplier(supplier);
        invoice.setAmountIncVat(request.amountIncVat());
        invoice.setAmountExVat(request.amountExVat());
        invoice.setVatAmount(request.vatAmount());
        invoice.setReceptionDate(request.receptionDate());
        invoice.setPaymentDate(request.paymentDate());
        invoice.setPeppol(request.peppol());
        invoice.setComment(request.comment());
        invoice.setFilePath(request.filePath());
        invoice.setDateScope(request.dateScope());
        invoice.setScopeDate(request.scopeDate());
        invoice.setFileDetail(request.fileDetail());

        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(Long id) {
        if (!invoiceRepository.existsById(id)) {
            throw new EntityNotFoundException("Invoice not found: " + id);
        }
        invoiceRepository.deleteById(id);
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
            invoice.getPeppol(),
            invoice.getComment(),
            invoice.getFilePath(),
            invoice.getDateScope(),
            invoice.getScopeDate(),
            invoice.getFileDetail(),
            fileNameGenerator.generate(invoice)
        );
    }
}
