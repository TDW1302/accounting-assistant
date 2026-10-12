package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.support.IntegrationTest;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Reprise de l'historique: le classeur Excel, puis les documents deja sur le disque. */
class ImportApiTest extends IntegrationTest {

    @Test
    void onlyAdminsImport() throws Exception {
        mvc.perform(post("/api/import/adopt-files").with(as(newUser("alice", UserRole.USER))).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Nested
    class Excel {

        private Workbook workbook;
        private CellStyle dateStyle;

        private Row row(Sheet sheet, int index, Object... values) {
            Row row = sheet.createRow(index);
            for (int c = 0; c < values.length; c++) {
                Object value = values[c];
                if (value == null) {
                    continue;
                }
                Cell cell = row.createCell(c);
                switch (value) {
                    case Number n -> cell.setCellValue(n.doubleValue());
                    case LocalDate d -> {
                        cell.setCellValue(d);
                        cell.setCellStyle(dateStyle);
                    }
                    default -> cell.setCellValue(value.toString());
                }
            }
            return row;
        }

        private byte[] build() throws Exception {
            workbook = new XSSFWorkbook();
            dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Sheet y2025 = workbook.createSheet("2025");
            row(y2025, 0, "N°", "Fournisseur", "TTC", "Reception", "Paiement", "Commentaire");
            row(y2025, 1, 1, "Mobile Nova", 12.5, LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 10), "Janvier");
            row(y2025, 2, 2, "MobileNova", "20,00", "2025-02-03", null, 42);
            // Pas de date de reception: celle du paiement la remplace.
            row(y2025, 3, 3, "Facture Oliver James", 1000, null, LocalDate.of(2025, 3, 31));
            // Aucune date: le 1er janvier de l'annee.
            row(y2025, 4, "54.1", "Ondes", "abc", "pas une date");
            // Ligne 6 vide: sautee sans avertissement.
            row(y2025, 6, null, "Orphelin", 10);
            row(y2025, 7, 7, " ", 10);
            row(y2025, 8, 8);

            Sheet y2026 = workbook.createSheet("2026");
            row(y2026, 0, "header");
            row(y2026, 1, 1, "Ondes", 50, LocalDate.of(2026, 1, 5), null, "V", "Peppol");
            row(y2026, 2, 2, "Oliverjames", 2000, LocalDate.of(2026, 1, 31), null, "", null);

            workbook.createSheet("Notes");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.close();
            return out.toByteArray();
        }

        private org.springframework.test.web.servlet.ResultActions upload(byte[] content) throws Exception {
            return mvc.perform(multipart("/api/import/excel")
                .file(new MockMultipartFile("file", "facturier.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content))
                .with(as(admin())).with(csrf()));
        }

        @Test
        void importsEveryYearSheetAndExplainsWhatItSkipped() throws Exception {
            supplier("ondes");
            byte[] content = build();

            upload(content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliersCreated").value(2))
                .andExpect(jsonPath("$.invoicesImported").value(6))
                .andExpect(jsonPath("$.rowsSkipped").value(4))
                .andExpect(jsonPath("$.warnings", hasItems(
                    "Onglet ignoré (nom non-numérique) : Notes",
                    "Onglet 2025 ligne 7 : numero manquant ou illisible en colonne A, ligne ignoree alors qu'elle contient des donnees",
                    "Onglet 2025 ligne 8 : fournisseur manquant en colonne B, ligne ignoree alors qu'elle contient des donnees",
                    "Onglet 2025 ligne 4 : date de reception manquante, date de paiement utilisee (2025-03-31)",
                    "Onglet 2025 ligne 5 : dates de reception et de paiement manquantes, 01/01/2025 utilise")));

            List<Invoice> invoices = invoiceRepository.findAll().stream()
                .sorted(Comparator.comparing(Invoice::getYear).thenComparing(Invoice::getNumber))
                .toList();
            assertThat(invoices).allSatisfy(invoice -> {
                assertThat(invoice.getSource()).isEqualTo(InvoiceSource.EXCEL_IMPORT);
                assertThat(invoice.getSeries()).isEqualTo(InvoiceSeries.INVOICE);
                assertThat(invoice.getCreatedBy()).isNull();
            });

            Invoice first = invoices.getFirst();
            assertThat(first.getAmountIncVat()).isEqualByComparingTo("12.50");
            assertThat(first.getComment()).isEqualTo("Janvier");
            Invoice second = invoices.get(1);
            assertThat(second.getAmountIncVat()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(second.getReceptionDate()).isEqualTo(LocalDate.of(2025, 2, 3));
            assertThat(second.getComment()).isEqualTo("42.0");
            assertThat(second.getSupplier().getId()).isEqualTo(first.getSupplier().getId());
            Invoice sale = invoices.get(2);
            assertThat(sale.getType()).isEqualTo(InvoiceType.SALE);
            assertThat(sale.getReceptionDate()).isEqualTo(LocalDate.of(2025, 3, 31));
            Invoice sub = invoices.get(3);
            assertThat(sub.getSubNumber()).isEqualTo(1);
            assertThat(sub.getAmountIncVat()).isNull();
            assertThat(sub.getReceptionDate()).isEqualTo(LocalDate.of(2025, 1, 1));
            Invoice peppol = invoices.get(4);
            assertThat(peppol.isPeppol()).isTrue();
            assertThat(peppol.getComment()).isEqualTo("Peppol");
            assertThat(invoices.get(5).isPeppol()).isFalse();

            List<String> supplierNames = supplierRepository.findAll().stream().map(Supplier::getName).toList();
            assertThat(supplierNames).containsExactlyInAnyOrder("ondes", "Mobile Nova", "Oliver James");

            // Rejouer le meme classeur ne cree aucun doublon.
            upload(content)
                .andExpect(jsonPath("$.invoicesImported").value(0))
                .andExpect(jsonPath("$.warnings", hasItem(startsWith("Onglet 2025 ligne 5 : numero 54.1 deja present"))));
        }

        @Test
        void aFileThatIsNotAWorkbookIsRefused() throws Exception {
            upload("not a workbook".getBytes())
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Adoption {

        private Path file(int year, String name) throws Exception {
            Path dir = Files.createDirectories(UPLOADS.resolve(String.valueOf(year)));
            return Files.write(dir.resolve(name), pdfBytes());
        }

        @Test
        void linksExistingDocumentsByTheirNumberWithoutTouchingThem() throws Exception {
            Supplier supplier = supplier("Ondes");
            Invoice plain = save(invoiceOf(supplier, null, 2026, 1));
            Invoice sub = save(invoiceOf(supplier, null, 2026, 2).subNumber(1));
            Path elsewhere = file(2026, "003-Ondes.pdf");
            save(invoiceOf(supplier, null, 2026, 3).filePath("/archive/003.pdf"));
            Invoice same = save(invoiceOf(supplier, null, 2026, 5));
            save(invoiceOf(supplier, null, 2026, 4));
            save(invoiceOf(supplier, null, 2025, 1));

            Path plainFile = file(2026, "001-2601-Ondes.pdf");
            Path subFile = file(2026, "002.1-Ondes.PDF");
            file(2026, "004-a.pdf");
            file(2026, "004-b.pdf");
            file(2026, "099-Nobody.pdf");
            file(2026, "scan.pdf");
            Path sameFile = file(2026, "005-Ondes.pdf");
            Files.writeString(UPLOADS.resolve("2026/notes.txt"), "x");
            Files.createDirectories(UPLOADS.resolve("2026/sub.pdf"));
            Files.write(UPLOADS.resolve("2026/.pdf"), pdfBytes());
            same.setFilePath(sameFile.toString());
            invoiceRepository.save(same);

            mvc.perform(post("/api/import/adopt-files").param("dryRun", "true").with(as(admin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.filesScanned").value(8))
                .andExpect(jsonPath("$.linked").value(2))
                .andExpect(jsonPath("$.alreadyLinked").value(2))
                .andExpect(jsonPath("$.ambiguous").value(1))
                .andExpect(jsonPath("$.withoutInvoice").value(1))
                .andExpect(jsonPath("$.unnamed").value(1))
                .andExpect(jsonPath("$.details", hasItem(startsWith("Annee 2025 : dossier absent"))));
            assertThat(invoiceRepository.findById(plain.getId()).orElseThrow().getFilePath()).isNull();

            mvc.perform(post("/api/import/adopt-files").with(as(admin())).with(csrf()))
                .andExpect(jsonPath("$.linked").value(2));

            assertThat(invoiceRepository.findById(plain.getId()).orElseThrow().getFilePath())
                .isEqualTo(plainFile.toString());
            assertThat(invoiceRepository.findById(sub.getId()).orElseThrow().getFilePath())
                .isEqualTo(subFile.toString());
            assertThat(elsewhere).exists();
        }
    }
}
