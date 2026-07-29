package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.AdminStatsResponse;
import be.vercauteren.accounting.dto.AdminYearSummary;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.SupplierRepository;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final InvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;
    private final FileStorageService fileStorageService;

    public AdminStatsResponse getStats() {
        List<AdminYearSummary> years = invoiceRepository.findDistinctYears().stream()
            .map(year -> new AdminYearSummary(year, invoiceRepository.countByYear(year)))
            .toList();
        return new AdminStatsResponse(supplierRepository.count(), years);
    }

    @Transactional
    public void deleteInvoicesByYear(Integer year) {
        List<Invoice> invoices = invoiceRepository.findByYearOrderByNumberAscSubNumberAsc(year);
        if (invoices.isEmpty()) {
            throw new IllegalArgumentException("No invoices found for year " + year);
        }

        for (Invoice invoice : invoices) {
            if (invoice.getFilePath() != null) {
                try {
                    fileStorageService.delete(invoice.getFilePath());
                } catch (IOException e) {
                    log.warn("Failed to delete file '{}' for invoice {}: {}",
                        invoice.getFilePath(), invoice.getId(), e.getMessage());
                }
            }
        }
        invoiceRepository.deleteAll(invoices);
    }

    @Transactional
    public void deleteAllSuppliers() {
        if (invoiceRepository.count() > 0) {
            throw new IllegalStateException("Cannot delete suppliers while invoices still exist");
        }
        supplierRepository.deleteAll();
    }
}
