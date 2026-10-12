package be.vercauteren.accounting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.vercauteren.accounting.dto.RecurringAttachBatchRequest;
import be.vercauteren.accounting.dto.RecurringAttachRequest;
import be.vercauteren.accounting.dto.RecurringExpenseRequest;
import be.vercauteren.accounting.dto.RecurringGenerationRequest;
import be.vercauteren.accounting.dto.RecurringGenerationRequest.OccurrenceRef;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.Periodicity;
import be.vercauteren.accounting.entity.RecurringExpense;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.entity.UserRole;
import be.vercauteren.accounting.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Les modeles de depenses recurrentes et leurs echeances, vus depuis l'API:
 * c'est la que la contrainte d'unicite par periode et les regles de
 * remplacement se confrontent a la vraie base.
 */
class RecurringExpenseApiTest extends IntegrationTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);

    private User user;
    private Supplier landlord;

    @BeforeEach
    void setUp() {
        user = newUser("alice", UserRole.USER);
        landlord = supplier("Proprietaire", "Proprio", null, null);
    }

    private RecurringExpenseRequest request(Periodicity periodicity, LocalDate start, LocalDate end) {
        return new RecurringExpenseRequest("Loyer bureau", landlord.getId(), new BigDecimal("800.00"),
            null, null, periodicity, start, end, true, "Bail", "Loyer", true);
    }

    private ResultActions postJson(String uri, Object body, Object... vars) throws Exception {
        return mvc.perform(post(uri, vars).with(as(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private long createModel(LocalDate end) throws Exception {
        MvcResult result = postJson("/api/recurring-expenses", request(Periodicity.MONTHLY, START, end))
            .andExpect(status().isCreated())
            .andReturn();
        return idOf(result);
    }

    private ResultActions generate(long modelId, LocalDate... periods) throws Exception {
        List<OccurrenceRef> refs = java.util.Arrays.stream(periods)
            .map(period -> new OccurrenceRef(modelId, period))
            .toList();
        return mvc.perform(post("/api/recurring-expenses/generate").param("upTo", "2026-03-31")
            .with(as(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json(new RecurringGenerationRequest(refs))));
    }

    @Test
    void aModelIsCreatedReadUpdatedAndListed() throws Exception {
        long id = createModel(null);

        mvc.perform(get("/api/recurring-expenses/{id}", id).with(as(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Loyer bureau"))
            .andExpect(jsonPath("$.deletable").value(true))
            .andExpect(jsonPath("$.generatedCount").value(0));

        RecurringExpenseRequest changed = new RecurringExpenseRequest("Loyer indexe", landlord.getId(),
            new BigDecimal("820.00"), null, null, Periodicity.QUARTERLY, START, null, false, null, null, false);
        postJsonPut(id, changed)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.periodicity").value("QUARTERLY"))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.pendingCount").value(0));

        mvc.perform(get("/api/recurring-expenses").with(as(user)))
            .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(get("/api/recurring-expenses/{id}", 999).with(as(user)))
            .andExpect(status().isNotFound());
    }

    private ResultActions postJsonPut(long id, Object body) throws Exception {
        return mvc.perform(put("/api/recurring-expenses/{id}", id).with(as(user)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void theEndDateCannotPrecedeTheStart() throws Exception {
        postJson("/api/recurring-expenses", request(Periodicity.MONTHLY, START, START.minusDays(1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("End date cannot precede start date"));
    }

    @Test
    void dueInstalmentsAreProposedThenGeneratedInChronologicalOrder() throws Exception {
        long id = createModel(null);

        mvc.perform(get("/api/recurring-expenses/due").param("upTo", "2026-03-31").with(as(user)))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].periodLabel").value("01/2026"))
            .andExpect(jsonPath("$[2].dueDate").value("2026-03-05"));

        // Cochees dans le desordre, une en double et une hors modele.
        generate(id, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
                LocalDate.of(2025, 12, 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created", hasSize(2)))
            .andExpect(jsonPath("$.created[0].displayNumber").value("D001"))
            .andExpect(jsonPath("$.created[0].scopeDate").value("2026-01-01"))
            .andExpect(jsonPath("$.created[0].paymentDate").value("2026-01-05"))
            .andExpect(jsonPath("$.created[0].generatedFileName").value("D001-2601-Proprio-Loyer.pdf"))
            .andExpect(jsonPath("$.created[1].displayNumber").value("D002"))
            .andExpect(jsonPath("$.skipped", hasSize(1)))
            .andExpect(jsonPath("$.skipped[0]", containsString("12/2025")));

        // Deja inscrite: plus proposee, et refusee si on insiste.
        generate(id, LocalDate.of(2026, 1, 1))
            .andExpect(jsonPath("$.created", hasSize(0)))
            .andExpect(jsonPath("$.skipped[0]", containsString("deja inscrite")));
        mvc.perform(get("/api/recurring-expenses/due").param("upTo", "2026-03-31").with(as(user)))
            .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(get("/api/recurring-expenses/{id}/entries", id).with(as(user)))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].recurringExpenseLabel").value("Loyer bureau"));

        mvc.perform(get("/api/recurring-expenses/{id}", id).with(as(user)))
            .andExpect(jsonPath("$.generatedCount").value(2))
            .andExpect(jsonPath("$.lastGeneratedPeriod").value("2026-03-01"))
            .andExpect(jsonPath("$.deletable").value(false));

        Invoice entry = invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(id).getFirst();
        assertThat(entry.getSource()).isEqualTo(InvoiceSource.RECURRING);
        assertThat(entry.getSeries()).isEqualTo(InvoiceSeries.EXPENSE);
    }

    @Test
    void anUnknownModelIsReportedNotFailed() throws Exception {
        generate(424242L, LocalDate.of(2026, 1, 1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skipped[0]").value("Modele 424242 introuvable"));
    }

    @Test
    void aModelWithEntriesKeepsItsRhythmAndCannotBeDeleted() throws Exception {
        long id = createModel(null);
        generate(id, LocalDate.of(2026, 1, 1)).andExpect(status().isOk());

        postJsonPut(id, request(Periodicity.QUARTERLY, START, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("periodicity")));
        postJsonPut(id, request(Periodicity.MONTHLY, START.plusDays(1), null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("start date")));
        // Le montant, lui, peut suivre une indexation.
        postJsonPut(id, request(Periodicity.MONTHLY, START, LocalDate.of(2026, 12, 31)))
            .andExpect(status().isOk());

        mvc.perform(delete("/api/recurring-expenses/{id}", id).with(as(user)).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aModelWithoutEntriesCanBeDeleted() throws Exception {
        long id = createModel(null);

        mvc.perform(delete("/api/recurring-expenses/{id}", id).with(as(user)).with(csrf()))
            .andExpect(status().isNoContent());

        assertThat(recurringExpenseRepository.findById(id)).isEmpty();
    }

    @Test
    void anExcelRowIsAttachedWithoutBeingRenumberedThenDetached() throws Exception {
        long id = createModel(null);
        Invoice excelRent = save(invoiceOf(landlord, null, 2026, 12)
            .receptionDate(LocalDate.of(2026, 2, 3)));

        mvc.perform(get("/api/recurring-expenses/{id}/attachable", id).with(as(admin())))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].suggestedPeriodStart").value("2026-02-01"))
            .andExpect(jsonPath("$[0].suggestionAvailable").value(true));
        mvc.perform(get("/api/recurring-expenses/options/{invoiceId}", excelRent.getId()).with(as(admin())))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].available").value(true));

        mvc.perform(post("/api/recurring-expenses/{id}/attach", id).with(as(admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RecurringAttachRequest(excelRent.getId(), LocalDate.of(2026, 2, 1)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayNumber").value("012"))
            .andExpect(jsonPath("$.scopeDate").value("2026-02-01"))
            .andExpect(jsonPath("$.dateScope").value("MONTHLY"));

        // Rattachee: elle n'est plus proposee, ni le modele pour elle.
        mvc.perform(get("/api/recurring-expenses/{id}/attachable", id).with(as(admin())))
            .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/recurring-expenses/options/{invoiceId}", excelRent.getId()).with(as(admin())))
            .andExpect(jsonPath("$", hasSize(0)));

        // Et la periode n'est plus due.
        mvc.perform(get("/api/recurring-expenses/due").param("upTo", "2026-02-28").with(as(user)))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].periodLabel").value("01/2026"));

        mvc.perform(delete("/api/recurring-expenses/entries/{invoiceId}", excelRent.getId())
                .with(as(admin())).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recurringExpenseId").doesNotExist())
            .andExpect(jsonPath("$.scopeDate").value("2026-02-01"));

        mvc.perform(delete("/api/recurring-expenses/entries/{invoiceId}", excelRent.getId())
                .with(as(admin())).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("not linked")));

        assertThat(invoiceRepository.findById(excelRent.getId())).isPresent();
    }

    @Test
    void attachingChecksTheRowTheSupplierAndThePeriod() throws Exception {
        long id = createModel(LocalDate.of(2026, 6, 30));
        Supplier other = supplier("Banque");
        Invoice foreign = save(invoiceOf(other, user, 2026, 1));
        Invoice withDocument = save(invoiceOf(landlord, user, 2026, 2).filePath("/tmp/002.pdf"));
        Invoice row = save(invoiceOf(landlord, user, 2026, 3));

        attach(id, foreign.getId(), LocalDate.of(2026, 1, 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("same supplier")));
        attach(id, withDocument.getId(), LocalDate.of(2026, 1, 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("document")));
        attach(id, row.getId(), LocalDate.of(2026, 9, 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("not one of this recurring expense's periods")));

        attach(id, row.getId(), LocalDate.of(2026, 1, 1)).andExpect(status().isOk());
        attach(id, row.getId(), LocalDate.of(2026, 2, 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("already linked")));

        Invoice second = save(invoiceOf(landlord, user, 2026, 4));
        attach(id, second.getId(), LocalDate.of(2026, 1, 1))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("already covered")));

        // Un autre utilisateur ne rattache pas une ligne qui n'est pas la sienne.
        User intruder = newUser("bob", UserRole.USER);
        mvc.perform(post("/api/recurring-expenses/{id}/attach", id).with(as(intruder)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new RecurringAttachRequest(second.getId(), LocalDate.of(2026, 3, 1)))))
            .andExpect(status().isForbidden());
    }

    private ResultActions attach(long modelId, long invoiceId, LocalDate period) throws Exception {
        return postJson("/api/recurring-expenses/{id}/attach",
            new RecurringAttachRequest(invoiceId, period), modelId);
    }

    @Test
    void attachableRowsExplainWhyTheirSuggestionIsBlocked() throws Exception {
        long id = createModel(LocalDate.of(2026, 3, 31));
        generate(id, LocalDate.of(2026, 1, 1)).andExpect(status().isOk());
        Invoice handEntered = save(invoiceOf(landlord, user, 2026, 1).receptionDate(LocalDate.of(2026, 2, 2)));
        attach(id, handEntered.getId(), LocalDate.of(2026, 2, 1)).andExpect(status().isOk());

        save(invoiceOf(landlord, user, 2026, 2).receptionDate(LocalDate.of(2026, 1, 20)));
        save(invoiceOf(landlord, user, 2026, 3).receptionDate(LocalDate.of(2026, 2, 20)));
        save(invoiceOf(landlord, user, 2026, 4).receptionDate(LocalDate.of(2026, 9, 1)));

        mvc.perform(get("/api/recurring-expenses/{id}/attachable", id).with(as(user)))
            .andExpect(jsonPath("$", hasSize(3)))
            // Tri: annee puis numero decroissants.
            .andExpect(jsonPath("$[0].suggestionIssue").value("Hors des periodes du modele"))
            .andExpect(jsonPath("$[1].suggestionIssue").value("Periode deja couverte"))
            .andExpect(jsonPath("$[1].conflict.replaceable").value(false))
            .andExpect(jsonPath("$[1].conflict.notReplaceableReason", containsString("saisie")))
            .andExpect(jsonPath("$[2].conflict.displayNumber").value("D001"))
            .andExpect(jsonPath("$[2].conflict.replaceable").value(true));
    }

    @Test
    void linkOptionsExplainTheBlockingEntry() throws Exception {
        long id = createModel(LocalDate.of(2026, 3, 31));
        generate(id, LocalDate.of(2026, 1, 1)).andExpect(status().isOk());
        Invoice january = save(invoiceOf(landlord, user, 2026, 1).receptionDate(LocalDate.of(2026, 1, 9)));
        Invoice late = save(invoiceOf(landlord, user, 2026, 2).receptionDate(LocalDate.of(2026, 8, 9)));
        Invoice withDocument = save(invoiceOf(landlord, user, 2026, 3).filePath("/tmp/003.pdf"));

        mvc.perform(get("/api/recurring-expenses/options/{invoiceId}", january.getId()).with(as(user)))
            .andExpect(jsonPath("$[0].issue").value("Periode deja couverte"))
            .andExpect(jsonPath("$[0].conflict.replaceable").value(true));
        mvc.perform(get("/api/recurring-expenses/options/{invoiceId}", late.getId()).with(as(user)))
            .andExpect(jsonPath("$[0].issue").value("Hors des periodes du modele"));
        mvc.perform(get("/api/recurring-expenses/options/{invoiceId}", withDocument.getId()).with(as(user)))
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aGeneratedInstalmentIsReplacedByTheRealRow() throws Exception {
        long id = createModel(null);
        generate(id, LocalDate.of(2026, 1, 1)).andExpect(status().isOk());
        Invoice generated = invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(id).getFirst();
        Invoice real = save(invoiceOf(landlord, user, 2026, 7).receptionDate(LocalDate.of(2026, 1, 6)));

        postJson("/api/recurring-expenses/{id}/replace",
                new RecurringAttachRequest(real.getId(), LocalDate.of(2026, 1, 1)), id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(real.getId()))
            .andExpect(jsonPath("$.recurringExpenseId").value(id));

        assertThat(invoiceRepository.findById(generated.getId())).isEmpty();

        // La ligne en place est maintenant un enregistrement a part entiere.
        Invoice another = save(invoiceOf(landlord, user, 2026, 8));
        postJson("/api/recurring-expenses/{id}/replace",
                new RecurringAttachRequest(another.getId(), LocalDate.of(2026, 1, 1)), id)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("n'a pas ete engendree")));
        assertThat(invoiceRepository.findById(real.getId())).isPresent();
    }

    @Test
    void aBatchAttachesWhatItCanAndReportsTheRest() throws Exception {
        long id = createModel(null);
        Invoice january = save(invoiceOf(landlord, user, 2026, 1));
        Invoice alsoJanuary = save(invoiceOf(landlord, user, 2026, 2));
        Invoice february = save(invoiceOf(landlord, user, 2026, 3));

        RecurringAttachBatchRequest batch = new RecurringAttachBatchRequest(List.of(
            new RecurringAttachRequest(february.getId(), LocalDate.of(2026, 2, 1)),
            new RecurringAttachRequest(alsoJanuary.getId(), LocalDate.of(2026, 1, 1)),
            new RecurringAttachRequest(january.getId(), LocalDate.of(2026, 1, 1))));

        postJson("/api/recurring-expenses/{id}/attach-batch", batch, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attached", hasSize(2)))
            .andExpect(jsonPath("$.attached[0].id").value(january.getId()))
            .andExpect(jsonPath("$.skipped", hasSize(1)))
            .andExpect(jsonPath("$.skipped[0]", containsString("01/2026 : This period is already covered")));
    }

    @Test
    void anEmptyBatchOrGenerationIsInvalid() throws Exception {
        long id = createModel(null);

        postJson("/api/recurring-expenses/{id}/attach-batch", new RecurringAttachBatchRequest(List.of()), id)
            .andExpect(status().isBadRequest());
        postJson("/api/recurring-expenses/generate", new RecurringGenerationRequest(List.of()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void aSupplierReferencedByAModelCannotBeDeleted() throws Exception {
        createModel(null);

        mvc.perform(delete("/api/suppliers/{id}", landlord.getId()).with(as(user)).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("recurring expenses")));
    }

    @Test
    void anInactiveModelProposesNothing() throws Exception {
        RecurringExpense stopped = save(recurringOf(landlord, Periodicity.YEARLY, LocalDate.of(2024, 1, 1))
            .active(false));

        mvc.perform(get("/api/recurring-expenses/due").with(as(user)))
            .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/recurring-expenses/{id}", stopped.getId()).with(as(user)))
            .andExpect(jsonPath("$.pendingCount").value(0));
    }
}
