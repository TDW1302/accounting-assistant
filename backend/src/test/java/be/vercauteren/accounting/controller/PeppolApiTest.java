package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.dto.FalcoInboundDocument;
import be.vercauteren.accounting.dto.FalcoInboundListResponse;
import be.vercauteren.accounting.dto.PeppolImportRequest;
import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.service.FalcoApiClient.FalcoApiException;
import be.vercauteren.accounting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Les documents Peppol recus chez Falco: consultation, import, et reprise des expediteurs. */
class PeppolApiTest extends IntegrationTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = newUser("alice", UserRole.USER);
    }

    private static FalcoInboundDocument document(String id, String sender, String vat, String amount) {
        return new FalcoInboundDocument(id, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 30), amount, sender, vat, "0208:" + vat, "EUR", "+++123+++",
            "BE00", "INV-" + id, false);
    }

    @Test
    void inboundDocumentsAreEnrichedWithTheMatchingSupplierAndImportStatus() throws Exception {
        Supplier ondes = supplier("Ondes", null, "0456.789.034", null);
        save(invoiceOf(ondes, user, 2026, 1).falcoDocumentId("doc-1").peppol(true));
        when(falcoApiClient.listInbound(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new FalcoInboundListResponse(List.of(
                document("doc-1", "Ondes SA", "BE0456789034", "121.00"),
                document("doc-2", "Unknown Ltd", null, "not a number"),
                document("doc-3", null, "0765432146", ""))));

        mvc.perform(get("/api/peppol/inbound").with(as(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].alreadyImported").value(true))
            .andExpect(jsonPath("$[0].matchedSupplierId").value(ondes.getId()))
            .andExpect(jsonPath("$[0].amount").value(121.00))
            .andExpect(jsonPath("$[1].alreadyImported").value(false))
            .andExpect(jsonPath("$[1].amount").doesNotExist())
            .andExpect(jsonPath("$[1].matchedSupplierName").doesNotExist());

        // Le bac a sable ignore le filtre sur l'expediteur: il est donc reapplique ici.
        mvc.perform(get("/api/peppol/inbound").param("senderName", "onde")
                .param("receivedAfter", "2026-01-01").param("receivedBefore", "2026-12-31")
                .with(as(user)))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("doc-1"));
    }

    @Test
    void anEmptyInboxAnswersAnEmptyList() throws Exception {
        when(falcoApiClient.listInbound(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new FalcoInboundListResponse(null));

        mvc.perform(get("/api/peppol/inbound").with(as(user)))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aFalcoOutageIsABadGateway() throws Exception {
        when(falcoApiClient.listInbound(any(), any(), any(), anyInt(), anyInt()))
            .thenThrow(new FalcoApiException("down", new RuntimeException()));

        mvc.perform(get("/api/peppol/inbound").with(as(user)))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("Failed to communicate with the accounting service"));
    }

    @Test
    void aDocumentIsImportedOnceAsAPeppolInvoice() throws Exception {
        Supplier supplier = supplier("Ondes");
        PeppolImportRequest request = new PeppolImportRequest("doc-9", supplier.getId(), 2026,
            InvoiceType.PURCHASE, DateScope.MONTHLY, LocalDate.of(2026, 2, 1), null, "Fibre",
            new BigDecimal("50.00"), null);

        mvc.perform(post("/api/peppol/import").with(as(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.peppol").value(true))
            .andExpect(jsonPath("$.falcoDocumentId").value("doc-9"))
            .andExpect(jsonPath("$.receptionDate").value(LocalDate.now().toString()));

        Invoice imported = invoiceRepository.findAll().getFirst();
        assertThat(imported.getSource()).isEqualTo(InvoiceSource.PEPPOL);

        mvc.perform(post("/api/peppol/import").with(as(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("This Peppol document has already been imported"));
    }

    @Test
    void anImportKeepsTheGivenReceptionDate() throws Exception {
        Supplier supplier = supplier("Ondes");
        PeppolImportRequest request = new PeppolImportRequest("doc-10", supplier.getId(), 2026,
            InvoiceType.PURCHASE, DateScope.NONE, null, null, null, null, LocalDate.of(2026, 2, 3));

        mvc.perform(post("/api/peppol/import").with(as(user)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
            .andExpect(jsonPath("$.receptionDate").value("2026-02-03"));
    }

    @Test
    void sendersBecomeSuppliersAcrossEveryPage() throws Exception {
        Supplier existing = supplier("Old name", null, "BE 0456.789.034", null);
        List<FalcoInboundDocument> firstPage = new ArrayList<>(IntStream.range(0, 199)
            .mapToObj(i -> document("p" + i, "Ondes SA", "0456789034", "1"))
            .toList());
        firstPage.add(document("no-vat", "Anonymous", null, "1"));
        when(falcoApiClient.listInbound(isNull(), isNull(), isNull(), eq(0), eq(200)))
            .thenReturn(new FalcoInboundListResponse(firstPage));
        when(falcoApiClient.listInbound(isNull(), isNull(), isNull(), eq(1), eq(200)))
            .thenReturn(new FalcoInboundListResponse(List.of(
                document("n1", "New Supplier", "0765.432.146", "1"),
                document("n2", "New Supplier bis", "0765432146", "1"))));

        mvc.perform(post("/api/peppol/import-suppliers").with(as(user)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(existing.getId()))
            .andExpect(jsonPath("$[0].name").value("Ondes SA"))
            .andExpect(jsonPath("$[1].name").value("New Supplier"));

        assertThat(supplierRepository.count()).isEqualTo(2);
    }

    @Test
    void noDocumentsMeansNoSuppliers() throws Exception {
        when(falcoApiClient.listInbound(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new FalcoInboundListResponse(List.of()));

        mvc.perform(post("/api/peppol/import-suppliers").with(as(user)).with(csrf()))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aViewerReadsButDoesNotImport() throws Exception {
        User viewer = newUser("victor", UserRole.VIEWER);

        mvc.perform(post("/api/peppol/import-suppliers").with(as(viewer)).with(csrf()))
            .andExpect(status().isForbidden());
    }
}
