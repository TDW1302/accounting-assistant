package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.config.InboxScheduler;
import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.service.InboxService;
import be.vercauteren.accounting.service.UserService;
import be.vercauteren.accounting.support.IntegrationTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * La boite de depot: chaque fichier depose est lu par l'IA (simulee ici), puis
 * rattache a une facture deja encodee ou transforme en nouvelle facture.
 */
class InboxApiTest extends IntegrationTest {

    @Autowired
    private InboxService inboxService;

    @Autowired
    private InboxScheduler inboxScheduler;

    private Supplier supplier;

    @BeforeEach
    void setUp() throws IOException {
        supplier = supplier("Ondes", "Ondes", null, null);
        Files.createDirectories(INBOX);
    }

    private Path drop(String name, byte[] content) throws IOException {
        return Files.write(INBOX.resolve(name), content);
    }

    private void extracts(String fileName, Long supplierId, String amount, LocalDate date) throws IOException {
        InvoiceExtractionResult result = new InvoiceExtractionResult(
            null, supplierId, "Ondes", amount == null ? null : new BigDecimal(amount),
            null, null, date, null, null, null, "Ref " + fileName, null);
        when(extractionService.extract(argThat(file -> file != null && fileName.equals(file.getOriginalFilename()))))
            .thenReturn(result);
    }

    @Test
    void eachFileIsMatchedCreatedOrSetAside() throws Exception {
        Invoice byAmount = save(invoiceOf(supplier, admin(), 2026, 1)
            .amountIncVat(new BigDecimal("60.50")).receptionDate(LocalDate.of(2026, 3, 1)).peppol(true));
        // Meme montant, mais plus loin en date: c'est l'autre qui doit etre retenu.
        save(invoiceOf(supplier, admin(), 2026, 2)
            .amountIncVat(new BigDecimal("60.50")).receptionDate(LocalDate.of(2026, 3, 8)));
        Invoice byDate = save(invoiceOf(supplier, admin(), 2026, 3)
            .amountIncVat(null).receptionDate(LocalDate.of(2026, 4, 1)));

        drop("amount.pdf", pdfBytes());
        extracts("amount.pdf", supplier.getId(), "60.5", LocalDate.of(2026, 3, 3));
        drop("date.pdf", pdfBytes());
        extracts("date.pdf", supplier.getId(), null, LocalDate.of(2026, 4, 1));
        drop("new.png", pngBytes());
        extracts("new.png", supplier.getId(), "99.99", LocalDate.of(2026, 5, 1));
        drop("unknown.pdf", pdfBytes());
        extracts("unknown.pdf", null, "1", null);
        drop("failing.pdf", pdfBytes());
        when(extractionService.extract(argThat(file -> file != null
                && "failing.pdf".equals(file.getOriginalFilename()))))
            .thenThrow(new IOException("AI down"));
        // Rapproche de la facture 2, mais refuse au depot: le contenu n'est pas un PDF.
        drop("corrupt.pdf", "garbage".getBytes());
        extracts("corrupt.pdf", supplier.getId(), "60.50", LocalDate.of(2026, 3, 10));
        drop("notes.txt", "ignored".getBytes());
        // Sans extension: ignore, et surtout sans faire echouer le scan.
        drop("README", "ignored".getBytes());

        mvc.perform(post("/api/inbox/scan").with(as(admin())).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filesProcessed").value(6))
            .andExpect(jsonPath("$.matched").value(2))
            .andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.errors").value(3));

        assertThat(invoiceRepository.findById(byAmount.getId()).orElseThrow().getFilePath()).isNotNull();
        assertThat(invoiceRepository.findById(byDate.getId()).orElseThrow().getFilePath()).isNotNull();
        Invoice created = invoiceRepository.findAll().stream()
            .filter(invoice -> invoice.getSource() == InvoiceSource.INBOX)
            .findFirst().orElseThrow();
        assertThat(created.getNumber()).isEqualTo(4);
        assertThat(created.getType()).isEqualTo(InvoiceType.PURCHASE);
        assertThat(created.getDateScope()).isEqualTo(DateScope.NONE);
        assertThat(created.getComment()).isEqualTo("Ref new.png");
        assertThat(created.getFilePath()).endsWith(".pdf");
        assertThat(created.getCreatedBy().getId()).isEqualTo(admin().getId());

        assertThat(INBOX.resolve("amount.pdf")).doesNotExist();
        assertThat(INBOX.resolve("errors/unknown.pdf")).exists();
        assertThat(INBOX.resolve("errors/failing.pdf")).exists();
        assertThat(INBOX.resolve("errors/corrupt.pdf")).exists();
        assertThat(INBOX.resolve("notes.txt")).exists();
        assertThat(INBOX.resolve("README")).exists();

        mvc.perform(get("/api/config").with(as(admin())))
            .andExpect(jsonPath("$.inboxErrorCount").value(3));
    }

    @Test
    void theNightlyScanCreatesInvoicesUnderTheTechnicalUser() throws Exception {
        drop("night.pdf", pdfBytes());
        extracts("night.pdf", supplier.getId(), "10", null);

        inboxScheduler.scheduledScan();

        Invoice created = invoiceRepository.findAll().getFirst();
        User system = userRepository.findByUsername(UserService.SYSTEM_USERNAME).orElseThrow();
        assertThat(created.getCreatedBy().getId()).isEqualTo(system.getId());
        // Sans date lue, la facture prend la date du jour.
        assertThat(created.getReceptionDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void aMissingInboxIsNotAnError() throws Exception {
        Files.delete(INBOX);

        assertThat(inboxService.scan().filesProcessed()).isZero();
        assertThat(inboxService.countErrors()).isZero();
    }
}
