package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.dto.SupplierAiData;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Periodicity;
import be.vercauteren.accounting.entity.RecurringExpense;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.support.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** L'ecran d'administration: statistiques, purges, alias, enrichissement et fusion. */
class AdminApiTest extends IntegrationTest {

    private Path document(int year, String name) throws IOException {
        Path dir = Files.createDirectories(UPLOADS.resolve(String.valueOf(year)));
        return Files.write(dir.resolve(name), pdfBytes());
    }

    @Test
    void onlyAdminsReachTheAdminScreen() throws Exception {
        mvc.perform(get("/api/admin/stats").with(as(newUser("alice", UserRole.USER))))
            .andExpect(status().isForbidden());
    }

    @Nested
    class Purges {

        @Test
        void statsCountSuppliersAndInvoicesPerYear() throws Exception {
            Supplier supplier = supplier("Ondes");
            supplier("Lumen");
            save(invoiceOf(supplier, admin(), 2026, 1));
            save(invoiceOf(supplier, admin(), 2026, 2));
            save(invoiceOf(supplier, admin(), 2025, 1));

            mvc.perform(get("/api/admin/stats").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierCount").value(2))
                .andExpect(jsonPath("$.years[0].year").value(2026))
                .andExpect(jsonPath("$.years[0].invoiceCount").value(2))
                .andExpect(jsonPath("$.years[1].year").value(2025));
        }

        @Test
        void deletingAYearRemovesItsInvoicesAndTheirDocuments() throws Exception {
            Supplier supplier = supplier("Ondes");
            Path file = document(2026, "001-Ondes.pdf");
            save(invoiceOf(supplier, admin(), 2026, 1).filePath(file.toString()));
            // Un fichier deja disparu ne bloque pas la purge.
            save(invoiceOf(supplier, admin(), 2026, 2).filePath(UPLOADS.resolve("2026/gone.pdf").toString()));
            // Un chemin qui designe un dossier non vide ne se supprime pas: la purge continue.
            Path folder = Files.createDirectories(UPLOADS.resolve("2026/folder"));
            Files.write(folder.resolve("inner.pdf"), pdfBytes());
            save(invoiceOf(supplier, admin(), 2026, 3).filePath(folder.toString()));
            save(invoiceOf(supplier, admin(), 2025, 1));

            mvc.perform(delete("/api/admin/invoices").param("year", "2026").with(as(admin())).with(csrf()))
                .andExpect(status().isNoContent());

            assertThat(file).doesNotExist();
            assertThat(invoiceRepository.count()).isEqualTo(1);

            mvc.perform(delete("/api/admin/invoices").param("year", "2026").with(as(admin())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No invoices found for year 2026"));
        }

        @Test
        void suppliersArePurgedOnlyOnceNothingReferencesThem() throws Exception {
            Supplier supplier = supplier("Ondes");
            Invoice invoice = save(invoiceOf(supplier, admin(), 2026, 1));

            mvc.perform(delete("/api/admin/suppliers").with(as(admin())).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("invoices")));

            invoiceRepository.delete(invoice);
            RecurringExpense rent = save(recurringOf(supplier, Periodicity.MONTHLY, LocalDate.of(2026, 1, 1)));
            mvc.perform(delete("/api/admin/suppliers").with(as(admin())).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", containsString("recurring expenses")));

            recurringExpenseRepository.delete(rent);
            mvc.perform(delete("/api/admin/suppliers").with(as(admin())).with(csrf()))
                .andExpect(status().isNoContent());
            assertThat(supplierRepository.count()).isZero();
        }
    }

    @Nested
    class Aliases {

        @Test
        void aliasesComeFromDocumentNamesThenFromTheName() throws Exception {
            Supplier fromFiles = supplier("ABX Euro Service GmbH");
            save(invoiceOf(fromFiles, admin(), 2026, 1).filePath("/x/2026/001-2601-ABX.pdf"));
            save(invoiceOf(fromFiles, admin(), 2026, 2).filePath("/x/2026/002-2602-ABX.pdf"));
            Supplier fromName = supplier("Café du Marché");
            supplier("Has alias", "Kept", null, null);
            supplier("---");

            mvc.perform(post("/api/admin/suppliers/aliases").param("dryRun", "true")
                    .with(as(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.considered").value(4))
                .andExpect(jsonPath("$.set").value(2))
                .andExpect(jsonPath("$.alreadySet").value(1))
                .andExpect(jsonPath("$.failed").value(1));
            assertThat(supplierRepository.findById(fromFiles.getId()).orElseThrow().getAlias()).isNull();

            mvc.perform(post("/api/admin/suppliers/aliases").with(as(admin())).with(csrf()))
                .andExpect(jsonPath("$.details", hasItem("ABX Euro Service GmbH -> ABX (d'apres le fichiers)")));

            assertThat(supplierRepository.findById(fromFiles.getId()).orElseThrow().getAlias()).isEqualTo("ABX");
            assertThat(supplierRepository.findById(fromName.getId()).orElseThrow().getAlias())
                .isEqualTo("CafeDuMarche");
        }
    }

    @Nested
    class Enrichment {

        private Supplier withDocument(String name, int number) throws IOException {
            Supplier supplier = supplier(name);
            Path file = document(2026, String.format("%03d-%s.pdf", number, name));
            save(invoiceOf(supplier, admin(), 2026, number).filePath(file.toString()));
            return supplier;
        }

        @Test
        void readsOneDocumentPerIncompleteSupplierAndKeepsOnlyCredibleNumbers() throws Exception {
            supplier("Complete", "Complete", "0456.789.034", ExpenseCategory.TELECOM);
            Supplier noDocument = supplier("NoDocument");
            Supplier good = withDocument("Good", 1);
            Supplier invalid = withDocument("Invalid", 2);
            Supplier ours = withDocument("Ours", 3);
            Supplier broken = withDocument("Broken", 4);
            Supplier empty = withDocument("Empty", 5);
            // Un document pointe vers un fichier absent: le fournisseur est sans document lisible.
            Supplier missingFile = supplier("MissingFile");
            save(invoiceOf(missingFile, admin(), 2026, 6).filePath(UPLOADS.resolve("2026/none.pdf").toString()));

            when(extractionService.extractSupplierData(any(), eq("Good")))
                .thenReturn(new SupplierAiData("BE 0765.432.146", ExpenseCategory.ASSURANCE));
            when(extractionService.extractSupplierData(any(), eq("Invalid")))
                .thenReturn(new SupplierAiData("POLICE-123", null));
            when(extractionService.extractSupplierData(any(), eq("Ours")))
                .thenReturn(new SupplierAiData(OWN_ENTERPRISE_NUMBER, null));
            when(extractionService.extractSupplierData(any(), eq("Broken")))
                .thenThrow(new IOException("unreadable"));
            when(extractionService.extractSupplierData(any(), eq("Empty")))
                .thenReturn(new SupplierAiData(null, null));

            mvc.perform(post("/api/admin/suppliers/enrich").with(as(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.considered").value(7))
                .andExpect(jsonPath("$.analysed").value(4))
                .andExpect(jsonPath("$.enterpriseNumbersFilled").value(1))
                .andExpect(jsonPath("$.numbersRejected").value(2))
                .andExpect(jsonPath("$.categoriesFilled").value(1))
                .andExpect(jsonPath("$.withoutDocument").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.lastId").value(missingFile.getId()));

            Supplier enriched = supplierRepository.findById(good.getId()).orElseThrow();
            assertThat(enriched.getEnterpriseNumber()).isEqualTo("0765.432.146");
            assertThat(enriched.getCategory()).isEqualTo(ExpenseCategory.ASSURANCE);
            assertThat(supplierRepository.findById(ours.getId()).orElseThrow().getEnterpriseNumber()).isNull();
            assertThat(supplierRepository.findById(invalid.getId()).orElseThrow().getEnterpriseNumber()).isNull();
            assertThat(noDocument.getId()).isNotNull();
            assertThat(broken.getId()).isNotNull();
            assertThat(empty.getId()).isNotNull();
        }

        @Test
        void aDryRunWritesNothingAndBatchesChainOnTheLastId() throws Exception {
            Supplier first = withDocument("First", 1);
            Supplier second = withDocument("Second", 2);
            when(extractionService.extractSupplierData(any(), any()))
                .thenReturn(new SupplierAiData("0765.432.146", ExpenseCategory.AUTRE));

            mvc.perform(post("/api/admin/suppliers/enrich").param("dryRun", "true").param("limit", "1")
                    .with(as(admin())).with(csrf()))
                .andExpect(jsonPath("$.considered").value(1))
                .andExpect(jsonPath("$.enterpriseNumbersFilled").value(1))
                .andExpect(jsonPath("$.lastId").value(first.getId()));
            assertThat(supplierRepository.findById(first.getId()).orElseThrow().getEnterpriseNumber()).isNull();

            mvc.perform(post("/api/admin/suppliers/enrich").param("afterId", first.getId().toString())
                    .param("limit", "5").with(as(admin())).with(csrf()))
                .andExpect(jsonPath("$.considered").value(1))
                .andExpect(jsonPath("$.lastId").value(second.getId()));

            mvc.perform(post("/api/admin/suppliers/enrich").param("afterId", second.getId().toString())
                    .with(as(admin())).with(csrf()))
                .andExpect(jsonPath("$.considered").value(0))
                .andExpect(jsonPath("$.lastId").doesNotExist());
        }
    }

    @Nested
    class Deduplication {

        @Test
        void pairsSameNumbersAndCloseNamesAndListsSuppliersWithoutDocuments() throws Exception {
            supplier("Ondes", null, "0456.789.034", null);
            supplier("Ondes SA", null, "BE0456789034", null);
            supplier("Mobile Nova", null, "0765.432.146", null);
            supplier("Mobile Novas", null, "0456.789.034", null);
            Supplier tax = supplier("Administration fiscale");
            save(invoiceOf(tax, admin(), 2026, 1));
            supplier("Unrelated");

            mvc.perform(get("/api/admin/suppliers/duplicates").with(as(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliers").value(6))
                .andExpect(jsonPath("$.pairs[?(@.reason == \"meme numero d'entreprise\")]").exists())
                .andExpect(jsonPath("$.pairs[?(@.reason =~ /.*mais numeros d'entreprise differents/)]").exists())
                .andExpect(jsonPath("$.withoutDocument", hasSize(1)))
                .andExpect(jsonPath("$.withoutDocument[0]", containsString("Administration fiscale")));
        }

        @Test
        void mergingMovesInvoicesAndModelsAndFillsOnlyEmptyFields() throws Exception {
            Supplier keep = supplier("Ondes", null, null, null);
            Supplier remove = supplierRepository.save(Supplier.builder()
                .name("Ondes SA").alias("Ondes").enterpriseNumber("0456.789.034")
                .category(ExpenseCategory.TELECOM).defaultDateScope(DateScope.MONTHLY).build());
            Invoice invoice = save(invoiceOf(remove, admin(), 2026, 1));
            RecurringExpense model = save(recurringOf(remove, Periodicity.MONTHLY, LocalDate.of(2026, 1, 1)));

            mvc.perform(post("/api/admin/suppliers/merge")
                    .param("keepId", keep.getId().toString()).param("removeId", remove.getId().toString())
                    .with(as(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keptName").value("Ondes"))
                .andExpect(jsonPath("$.removedName").value("Ondes SA"))
                .andExpect(jsonPath("$.invoicesReassigned").value(1))
                .andExpect(jsonPath("$.fieldsFilled", hasSize(4)));

            assertThat(supplierRepository.findById(remove.getId())).isEmpty();
            assertThat(invoiceRepository.findBySupplierId(keep.getId())).extracting(Invoice::getId)
                .containsExactly(invoice.getId());
            assertThat(recurringExpenseRepository.findBySupplierId(keep.getId()))
                .extracting(RecurringExpense::getId).containsExactly(model.getId());
            Supplier merged = supplierRepository.findById(keep.getId()).orElseThrow();
            assertThat(merged.getEnterpriseNumber()).isEqualTo("0456.789.034");
            assertThat(merged.getDefaultDateScope()).isEqualTo(DateScope.MONTHLY);
        }

        @Test
        void theKeptRecordKeepsItsOwnValues() throws Exception {
            Supplier keep = supplier("Ondes", "Od", "0456.789.034", ExpenseCategory.TELECOM);
            keep.setDefaultDateScope(DateScope.YEARLY);
            supplierRepository.save(keep);
            Supplier remove = supplier("Ondes SA", "Other", "0765.432.146", ExpenseCategory.AUTRE);

            mvc.perform(post("/api/admin/suppliers/merge")
                    .param("keepId", keep.getId().toString()).param("removeId", remove.getId().toString())
                    .with(as(admin())).with(csrf()))
                .andExpect(jsonPath("$.fieldsFilled", hasSize(0)));

            assertThat(supplierRepository.findById(keep.getId()).orElseThrow().getAlias()).isEqualTo("Od");
        }

        @Test
        void aRecordCannotBeMergedIntoItselfOrIntoNothing() throws Exception {
            Supplier supplier = supplier("Ondes");

            mvc.perform(post("/api/admin/suppliers/merge")
                    .param("keepId", supplier.getId().toString()).param("removeId", supplier.getId().toString())
                    .with(as(admin())).with(csrf()))
                .andExpect(status().isBadRequest());
            mvc.perform(post("/api/admin/suppliers/merge")
                    .param("keepId", supplier.getId().toString()).param("removeId", "9999")
                    .with(as(admin())).with(csrf()))
                .andExpect(status().isNotFound());
        }
    }
}
