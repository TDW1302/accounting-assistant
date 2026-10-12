package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.repository.InvoiceRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Ce que la base reelle ne produit pas a la demande: conflits de numero, disque
 * qui refuse un renommage ou une suppression, base qui refuse l'ecriture apres
 * le depot du fichier.
 */
class InvoiceServiceFailureTest {

    private InvoiceRepository invoiceRepository;
    private FileStorageService fileStorageService;
    private AuthService authService;
    private InvoiceService service;

    private final User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
    private final Supplier supplier = Supplier.builder().id(5L).name("Ondes").alias("Ondes").build();

    @BeforeEach
    void setUp() {
        invoiceRepository = mock(InvoiceRepository.class);
        fileStorageService = mock(FileStorageService.class);
        authService = mock(AuthService.class);
        SupplierService supplierService = mock(SupplierService.class);
        when(supplierService.getOrThrow(5L)).thenReturn(supplier);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        service = new InvoiceService(invoiceRepository, supplierService, new FileNameGenerator(),
            fileStorageService, authService, new ImageToPdfService(), transactionManager);
        when(authService.getCurrentUser()).thenReturn(Optional.of(admin));
    }

    private static InvoiceRequest request(Integer linkToNumber) {
        return new InvoiceRequest(null, InvoiceSeries.INVOICE, 2026, InvoiceType.PURCHASE, 5L,
            null, null, null, LocalDate.of(2026, 1, 1), null, false, null, DateScope.NONE,
            null, null, null, linkToNumber);
    }

    private Invoice existing() {
        return Invoice.builder().id(9L).number(1).year(2026).series(InvoiceSeries.INVOICE)
            .type(InvoiceType.PURCHASE).supplier(supplier).receptionDate(LocalDate.of(2026, 1, 1))
            .createdBy(admin).source(InvoiceSource.MANUAL).filePath("/data/2026/001-Ondes.pdf").build();
    }

    @Test
    void aNumberConflictIsRetriedThenGivenUp() {
        when(invoiceRepository.findFirstBySeriesAndYearOrderByNumberDesc(InvoiceSeries.INVOICE, 2026))
            .thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class)))
            .thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.create(request(null), InvoiceSource.MANUAL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("after 3 attempts");
        verify(invoiceRepository, times(3)).save(any(Invoice.class));
    }

    @Test
    void aNumberConflictResolvedOnRetryCreatesTheInvoice() {
        when(invoiceRepository.findFirstBySeriesAndYearOrderByNumberDesc(InvoiceSeries.INVOICE, 2026))
            .thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class)))
            .thenThrow(new DataIntegrityViolationException("uk"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceResponse created = service.create(request(null), InvoiceSource.MANUAL);

        assertThat(created.displayNumber()).isEqualTo("001");
    }

    @Test
    void aPromotedInvoiceKeepsItsOldFileNameWhenTheRenameFails() throws IOException {
        Invoice original = existing();
        when(invoiceRepository.findBySeriesAndYearAndNumberOrderBySubNumberAsc(InvoiceSeries.INVOICE, 2026, 1))
            .thenReturn(List.of(original));
        when(invoiceRepository.findFirstBySeriesAndYearAndNumberOrderBySubNumberDesc(
                InvoiceSeries.INVOICE, 2026, 1))
            .thenReturn(Optional.of(original));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageService.rename(anyString(), anyString(), anyInt())).thenThrow(new IOException("locked"));

        InvoiceResponse sub = service.create(request(1), InvoiceSource.MANUAL);

        assertThat(sub.displayNumber()).isEqualTo("001.2");
        assertThat(original.getSubNumber()).isEqualTo(1);
        assertThat(original.getFilePath()).isEqualTo("/data/2026/001-Ondes.pdf");
    }

    @Test
    void theStoredFileIsRemovedWhenTheDatabaseRefusesTheUpload() throws IOException {
        Invoice invoice = existing();
        invoice.setFilePath(null);
        when(invoiceRepository.findById(9L)).thenReturn(Optional.of(invoice));
        when(fileStorageService.store(any(), eq("001-Ondes.pdf"), eq(2026))).thenReturn("/data/2026/new.pdf");
        when(invoiceRepository.save(any(Invoice.class))).thenThrow(new IllegalStateException("db down"));
        doThrow(new IOException("cannot delete")).when(fileStorageService).delete("/data/2026/new.pdf");

        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf",
            "%PDF-1.4".getBytes());
        assertThatThrownBy(() -> service.uploadFile(9L, file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("db down");

        verify(fileStorageService).delete("/data/2026/new.pdf");
    }

    @Test
    void anInvoiceIsDeletedEvenIfItsFileCannotBe() throws IOException {
        Invoice invoice = existing();
        when(invoiceRepository.findById(9L)).thenReturn(Optional.of(invoice));
        doThrow(new IOException("busy")).when(fileStorageService).delete(invoice.getFilePath());

        service.delete(9L);

        verify(invoiceRepository).delete(invoice);
    }

    @Test
    void aCorruptImageIsRefusedBeforeAnythingIsStored() throws IOException {
        Invoice invoice = existing();
        when(invoiceRepository.findById(9L)).thenReturn(Optional.of(invoice));
        // Signature WebP valide, contenu tronque: le depot l'accepte, la conversion non.
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThatThrownBy(() -> service.uploadFile(9L,
                new MockMultipartFile("file", "a.webp", "image/webp", webp)))
            .isInstanceOf(IOException.class);
        verify(fileStorageService, never()).store(any(), anyString(), anyInt());
    }
}
