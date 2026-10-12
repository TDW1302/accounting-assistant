package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Periodicity;
import be.vercauteren.accounting.entity.RecurringExpense;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.support.IntegrationTest;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Le facturier de bout en bout: numerotation, sous-factures, regles de
 * modification, depot de document et recherche.
 */
class InvoiceApiTest extends IntegrationTest {

    private User owner;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        owner = newUser("alice", UserRole.USER);
        supplier = supplier("Ondes", "Ondes", "0456.789.034", ExpenseCategory.TELECOM);
    }

    private InvoiceRequest request(int year, Integer linkToNumber) {
        return request(year, InvoiceSeries.INVOICE, linkToNumber, true);
    }

    private InvoiceRequest request(int year, InvoiceSeries series, Integer linkToNumber, boolean peppol) {
        return new InvoiceRequest(
            null, series, year, InvoiceType.PURCHASE, supplier.getId(),
            new BigDecimal("121.00"), new BigDecimal("100.00"), new BigDecimal("21.00"),
            LocalDate.of(year, 3, 10), null, peppol, "Abonnement", DateScope.MONTHLY,
            LocalDate.of(year, 3, 1), "Fibre", null, linkToNumber);
    }

    private MvcResult create(User author, InvoiceRequest request) throws Exception {
        return mvc.perform(post("/api/invoices").with(as(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    @Nested
    @DisplayName("Numerotation")
    class Numbering {

        @Test
        void numbersFollowEachOtherWithinTheYearAndRestartTheNextYear() throws Exception {
            MvcResult first = create(owner, request(2026, null));
            MvcResult second = create(owner, request(2026, null));
            MvcResult otherYear = create(owner, request(2027, null));

            assertThat((Integer) read(first, "$.number")).isEqualTo(1);
            assertThat((String) read(second, "$.displayNumber")).isEqualTo("002");
            assertThat((Integer) read(otherYear, "$.number")).isEqualTo(1);
            assertThat((String) read(first, "$.generatedFileName")).isEqualTo("001-2603-Ondes-Fibre.pdf");
            assertThat((Boolean) read(first, "$.peppol")).isTrue();
        }

        @Test
        void eachSeriesKeepsItsOwnCounterAndAnExpenseIsNeverPeppol() throws Exception {
            create(owner, request(2026, null));
            MvcResult expense = create(owner, request(2026, InvoiceSeries.EXPENSE, null, true));

            assertThat((String) read(expense, "$.displayNumber")).isEqualTo("D001");
            assertThat((Boolean) read(expense, "$.peppol")).isFalse();
        }

        @Test
        void aMissingSeriesMeansTheDocumentedLedger() throws Exception {
            MvcResult created = create(owner, request(2026, null, null, false));

            assertThat((String) read(created, "$.series")).isEqualTo("INVOICE");
        }

        @Test
        void linkingToANumberPromotesTheOriginalAndRenamesItsFile() throws Exception {
            long originalId = idOf(create(owner, request(2026, null)));
            mvc.perform(multipart("/api/invoices/{id}/upload", originalId)
                    .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isOk());

            MvcResult sub = create(owner, request(2026, 1));
            MvcResult third = create(owner, request(2026, 1));

            assertThat((String) read(sub, "$.displayNumber")).isEqualTo("001.2");
            assertThat((String) read(third, "$.displayNumber")).isEqualTo("001.3");

            Invoice original = invoiceRepository.findById(originalId).orElseThrow();
            assertThat(original.getSubNumber()).isEqualTo(1);
            assertThat(Path.of(original.getFilePath()).getFileName().toString())
                .isEqualTo("001.1-2603-Ondes-Fibre.pdf");
            assertThat(Path.of(original.getFilePath())).exists();
        }

        @Test
        void linkingToAnUnknownNumberIsNotFound() throws Exception {
            mvc.perform(post("/api/invoices").with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(2026, 42))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("No invoice found with number 42")));
        }

        @Test
        void anInvalidRequestListsTheOffendingFields() throws Exception {
            String body = """
                {"year": 1990, "type": "PURCHASE", "receptionDate": "2026-01-01",
                 "peppol": false, "dateScope": "NONE"}""";

            mvc.perform(post("/api/invoices").with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.year").exists())
                .andExpect(jsonPath("$.errors.supplierId").exists());
        }

        @Test
        void anUnknownSupplierIsNotFound() throws Exception {
            InvoiceRequest request = new InvoiceRequest(
                null, null, 2026, InvoiceType.PURCHASE, 999L, null, null, null,
                LocalDate.of(2026, 1, 1), null, false, null, DateScope.NONE, null, null, null, null);

            mvc.perform(post("/api/invoices").with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Lecture et recherche")
    class Reading {

        @Test
        void listsByYearAndFindsById() throws Exception {
            Invoice invoice = save(invoiceOf(supplier, owner, 2026, 1));
            save(invoiceOf(supplier, owner, 2025, 1));

            mvc.perform(get("/api/invoices").param("year", "2026").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

            mvc.perform(get("/api/invoices/{id}", invoice.getId()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplier.name").value("Ondes"));

            mvc.perform(get("/api/invoices/{id}", 9999).with(as(owner)))
                .andExpect(status().isNotFound());
        }

        @Test
        void anonymousCallersAreRejected() throws Exception {
            mvc.perform(get("/api/invoices").param("year", "2026"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void searchCombinesEveryCriterion() throws Exception {
            Supplier restaurant = supplier("Cafe du Marche", null, null, ExpenseCategory.RESTAURANT);
            save(invoiceOf(supplier, owner, 2026, 1)
                .amountIncVat(new BigDecimal("50.00"))
                .receptionDate(LocalDate.of(2026, 2, 1))
                .comment("Facture 100%_fibre"));
            save(invoiceOf(restaurant, owner, 2026, 2)
                .amountIncVat(new BigDecimal("500.00"))
                .receptionDate(LocalDate.of(2026, 6, 1))
                .fileDetail("Lunch"));

            mvc.perform(get("/api/invoices/search").with(as(owner))
                    .param("year", "2026")
                    .param("supplierId", supplier.getId().toString())
                    .param("amountMin", "10").param("amountMax", "100")
                    .param("dateFrom", "2026-01-01").param("dateTo", "2026-03-01")
                    .param("keyword", " 100%_ ")
                    .param("category", "TELECOM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").value(1));

            // Bornes ouvertes d'un seul cote.
            mvc.perform(get("/api/invoices/search").with(as(owner))
                    .param("amountMin", "100").param("dateFrom", "2026-05-01"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").value(2));
            mvc.perform(get("/api/invoices/search").with(as(owner))
                    .param("amountMax", "100").param("dateTo", "2026-05-01"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").value(1));

            // Le mot-cle porte aussi sur le fournisseur et le detail de fichier.
            mvc.perform(get("/api/invoices/search").with(as(owner)).param("keyword", "lunch"))
                .andExpect(jsonPath("$", hasSize(1)));
            mvc.perform(get("/api/invoices/search").with(as(owner)).param("keyword", "marche"))
                .andExpect(jsonPath("$", hasSize(1)));

            // Un % n'est pas un joker: il est echappe.
            mvc.perform(get("/api/invoices/search").with(as(owner)).param("keyword", "%"))
                .andExpect(jsonPath("$", hasSize(1)));

            // Sans critere, tout revient; un mot-cle blanc est ignore.
            mvc.perform(get("/api/invoices/search").with(as(owner)).param("keyword", "  "))
                .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void missingDocumentsExcludeFilesExpensesRecurringAndPeppolUnlessAsked() throws Exception {
            save(invoiceOf(supplier, owner, 2026, 1));
            save(invoiceOf(supplier, owner, 2026, 2).peppol(true));
            save(invoiceOf(supplier, owner, 2026, 3).filePath("/tmp/003.pdf"));
            save(invoiceOf(supplier, owner, 2026, 1).series(InvoiceSeries.EXPENSE));
            RecurringExpense rent = save(recurringOf(supplier, Periodicity.MONTHLY, LocalDate.of(2026, 1, 1)));
            save(invoiceOf(supplier, owner, 2026, 4).recurringExpense(rent).scopeDate(LocalDate.of(2026, 1, 1)));
            save(invoiceOf(supplier, owner, 2025, 9));

            mvc.perform(get("/api/invoices/missing-documents").with(as(owner)).param("year", "2026"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].number").value(1));

            mvc.perform(get("/api/invoices/missing-documents").with(as(owner))
                    .param("year", "2026").param("includePeppol", "true"))
                .andExpect(jsonPath("$", hasSize(2)));

            mvc.perform(get("/api/invoices/missing-documents").with(as(owner)))
                .andExpect(jsonPath("$", hasSize(2)));
        }
    }

    @Nested
    @DisplayName("Modification")
    class Updating {

        @Test
        void theAuthorUpdatesTheirInvoice() throws Exception {
            long id = idOf(create(owner, request(2026, null)));
            InvoiceRequest changed = new InvoiceRequest(
                null, InvoiceSeries.INVOICE, 2026, InvoiceType.SALE, supplier.getId(),
                new BigDecimal("242.00"), null, null, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30),
                false, "Corrige", DateScope.NONE, null, null, null, null);

            mvc.perform(put("/api/invoices/{id}", id).with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SALE"))
                .andExpect(jsonPath("$.amountIncVat").value(242.00))
                .andExpect(jsonPath("$.comment").value("Corrige"))
                .andExpect(jsonPath("$.generatedFileName").value("001-Ondes.pdf"));
        }

        @Test
        void theYearSeriesAndSubNumberAreFixed() throws Exception {
            long id = idOf(create(owner, request(2026, null)));

            mvc.perform(put("/api/invoices/{id}", id).with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(2027, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Cannot change invoice year")));

            mvc.perform(put("/api/invoices/{id}", id).with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(request(2026, InvoiceSeries.EXPENSE, null, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("series")));

            Invoice sub = save(invoiceOf(supplier, owner, 2026, 5).subNumber(2));
            InvoiceRequest otherSub = new InvoiceRequest(
                3, null, 2026, InvoiceType.PURCHASE, supplier.getId(), null, null, null,
                LocalDate.of(2026, 1, 1), null, false, null, DateScope.NONE, null, null, null, null);
            mvc.perform(put("/api/invoices/{id}", sub.getId()).with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(otherSub)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("sub-number")));
        }

        @Test
        void aRecurringInstalmentKeepsItsPeriod() throws Exception {
            RecurringExpense rent = save(recurringOf(supplier, Periodicity.MONTHLY, LocalDate.of(2026, 1, 1)));
            Invoice instalment = save(invoiceOf(supplier, owner, 2026, 1)
                .series(InvoiceSeries.EXPENSE)
                .recurringExpense(rent)
                .scopeDate(LocalDate.of(2026, 1, 1)));
            InvoiceRequest cleared = new InvoiceRequest(
                null, InvoiceSeries.EXPENSE, 2026, InvoiceType.PURCHASE, supplier.getId(), null, null, null,
                LocalDate.of(2026, 1, 1), null, true, null, DateScope.NONE, null, null, null, null);

            mvc.perform(put("/api/invoices/{id}", instalment.getId()).with(as(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(cleared)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("period")));
        }

        @Test
        void anotherUserCannotTouchTheInvoiceButAnAdminCan() throws Exception {
            long id = idOf(create(owner, request(2026, null)));
            User intruder = newUser("bob", UserRole.USER);

            mvc.perform(put("/api/invoices/{id}", id).with(as(intruder)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(2026, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can only modify invoices you created"));

            mvc.perform(delete("/api/invoices/{id}", id).with(as(intruder)).with(csrf()))
                .andExpect(status().isForbidden());

            mvc.perform(put("/api/invoices/{id}", id).with(as(admin())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(2026, null))))
                .andExpect(status().isOk());
        }

        @Test
        void anInvoiceWithoutAuthorIsReservedToAdmins() throws Exception {
            Invoice imported = save(invoiceOf(supplier, null, 2024, 1));

            mvc.perform(delete("/api/invoices/{id}", imported.getId()).with(as(owner)).with(csrf()))
                .andExpect(status().isForbidden());
        }

        @Test
        void aViewerCannotWrite() throws Exception {
            User viewer = newUser("victor", UserRole.VIEWER);

            mvc.perform(post("/api/invoices").with(as(viewer)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(2026, null))))
                .andExpect(status().isForbidden());
        }

        @Test
        void writesRequireTheCsrfToken() throws Exception {
            mvc.perform(post("/api/invoices").with(as(owner))
                    .contentType(MediaType.APPLICATION_JSON).content(json(request(2026, null))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Documents")
    class Documents {

        private long id;

        @BeforeEach
        void createInvoice() throws Exception {
            id = idOf(create(owner, request(2026, null)));
        }

        private MockMultipartFile file(String name, String type, byte[] content) {
            return new MockMultipartFile("file", name, type, content);
        }

        @Test
        void aPdfIsStoredUnderItsGeneratedNameAndReplacedOnReupload() throws Exception {
            MvcResult first = mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("scan.pdf", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
            Path stored = Path.of((String) read(first, "$.filePath"));
            assertThat(stored).exists();
            assertThat(stored.getFileName().toString()).isEqualTo("001-2603-Ondes-Fibre.pdf");
            assertThat(stored.getParent().getFileName().toString()).isEqualTo("2026");

            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("other.pdf", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isOk());
            assertThat(stored).exists();
        }

        @Test
        void anImageIsConvertedToPdf() throws Exception {
            MvcResult result = mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("photo.PNG", "image/png", pngBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

            Path stored = Path.of((String) read(result, "$.filePath"));
            assertThat(new String(Files.readAllBytes(stored), 0, 5)).isEqualTo("%PDF-");
        }

        @Test
        void aWebpPhotoIsConvertedToPdf() throws Exception {
            byte[] webp = java.util.Base64.getDecoder().decode(
                be.vercauteren.accounting.service.ImageToPdfServiceTest.LOSSY_WEBP);

            MvcResult result = mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("ticket.webp", "image/webp", webp))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

            Path stored = Path.of((String) read(result, "$.filePath"));
            assertThat(stored.getFileName().toString()).endsWith(".pdf");
            assertThat(new String(Files.readAllBytes(stored), 0, 5)).isEqualTo("%PDF-");
        }

        @Test
        void refusesAnUnacceptedTypeOrExtensionOrAMismatchingContent() throws Exception {
            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("doc.txt", "text/plain", "hello".getBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("File type not allowed")));

            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("noextension", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("valid extension")));

            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("doc.exe", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("extension not allowed")));

            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("doc.pdf", "application/pdf", "not a pdf".getBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("not a valid PDF or image")));

            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("doc.png", "image/png", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("does not match")));

            mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("doc.pdf", null, pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest());
        }

        @Test
        void deletingAnInvoiceRemovesItsDocument() throws Exception {
            MvcResult uploaded = mvc.perform(multipart("/api/invoices/{id}/upload", id)
                    .file(file("scan.pdf", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andReturn();
            Path stored = Path.of((String) read(uploaded, "$.filePath"));

            mvc.perform(delete("/api/invoices/{id}", id).with(as(owner)).with(csrf()))
                .andExpect(status().isNoContent());

            assertThat(stored).doesNotExist();
            assertThat(invoiceRepository.findById(id)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Extraction IA")
    class Extraction {

        @Test
        void aGenuinePdfIsHandedToTheExtraction() throws Exception {
            InvoiceExtractionResult extracted = new InvoiceExtractionResult(
                InvoiceType.PURCHASE, supplier.getId(), "Ondes", new BigDecimal("12.10"),
                null, null, LocalDate.of(2026, 5, 2), null, DateScope.MONTHLY, null, null, null);
            when(extractionService.extract(any())).thenReturn(extracted);

            mvc.perform(multipart("/api/invoices/extract")
                    .file(new MockMultipartFile("file", "f.pdf", "application/pdf", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(supplier.getId()))
                .andExpect(jsonPath("$.receptionDate").value("2026-05-02"));

            verify(extractionService).extract(any());
        }

        @Test
        void aDisguisedOrUnsupportedFileIsRefusedBeforeAnyAiCall() throws Exception {
            mvc.perform(multipart("/api/invoices/extract")
                    .file(new MockMultipartFile("file", "f.txt", "text/plain", "x".getBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest());

            mvc.perform(multipart("/api/invoices/extract")
                    .file(new MockMultipartFile("file", "f.pdf", null, pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest());

            mvc.perform(multipart("/api/invoices/extract")
                    .file(new MockMultipartFile("file", "f.jpg", "image/jpeg", pdfBytes()))
                    .with(as(owner)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("does not match")));

            verifyNoInteractions(extractionService);
        }
    }

    @Test
    void anInvoiceCreatedByTheApiRecordsItsAuthorAndSource() throws Exception {
        long id = idOf(create(owner, request(2026, null)));

        Invoice saved = invoiceRepository.findById(id).orElseThrow();
        assertThat(saved.getSource()).isEqualTo(InvoiceSource.MANUAL);
        assertThat(saved.getCreatedBy().getId()).isEqualTo(owner.getId());
    }
}
