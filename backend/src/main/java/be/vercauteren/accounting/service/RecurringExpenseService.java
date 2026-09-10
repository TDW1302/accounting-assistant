package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.dto.RecurringExpenseRequest;
import be.vercauteren.accounting.dto.RecurringExpenseResponse;
import be.vercauteren.accounting.dto.RecurringGenerationRequest;
import be.vercauteren.accounting.dto.RecurringGenerationResponse;
import be.vercauteren.accounting.dto.RecurringOccurrence;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.RecurringExpense;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.RecurringExpenseRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Depenses contractuelles recurrentes: loyer, frais bancaires, PLCI. Rien n'est
 * inscrit au facturier sans un geste explicite — le service propose les echeances
 * echues, l'utilisateur retient celles qu'il veut. C'est volontaire: un montant
 * indexe ou un contrat termine produirait sinon des lignes fausses en silence,
 * et consommerait des numeros sans qu'on l'ait voulu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final SupplierService supplierService;
    private final InvoiceService invoiceService;
    private final AuthService authService;

    public List<RecurringExpenseResponse> findAll() {
        return recurringExpenseRepository.findAllOrderByActiveThenLabel().stream()
            .map(this::toResponse)
            .toList();
    }

    public RecurringExpenseResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public RecurringExpenseResponse create(RecurringExpenseRequest request) {
        User author = authService.getCurrentUser().orElseThrow(() -> new IllegalStateException(
            "Cannot create a recurring expense without an author: no authenticated user"));
        Supplier supplier = supplierService.getOrThrow(request.supplierId());
        validatePeriod(request);

        RecurringExpense expense = RecurringExpense.builder()
            .label(request.label())
            .supplier(supplier)
            .amountIncVat(request.amountIncVat())
            .amountExVat(request.amountExVat())
            .vatAmount(request.vatAmount())
            .periodicity(request.periodicity())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .paidOnDueDate(Boolean.TRUE.equals(request.paidOnDueDate()))
            .comment(request.comment())
            .fileDetail(request.fileDetail())
            .active(Boolean.TRUE.equals(request.active()))
            .createdBy(author)
            .build();

        return toResponse(recurringExpenseRepository.save(expense));
    }

    @Transactional
    public RecurringExpenseResponse update(Long id, RecurringExpenseRequest request) {
        RecurringExpense expense = getOrThrow(id);
        Supplier supplier = supplierService.getOrThrow(request.supplierId());
        validatePeriod(request);

        // Le rythme et la date de debut definissent l'identite des echeances: les
        // changer apres coup ferait glisser les periodes et rendrait orphelines
        // celles deja inscrites, qu'on ne pourrait plus reconnaitre comme generees.
        if (invoiceRepository.existsByRecurringExpenseId(id)) {
            if (expense.getPeriodicity() != request.periodicity()) {
                throw new IllegalArgumentException(
                    "Cannot change the periodicity of a recurring expense that already has entries. "
                        + "Close this one (end date) and create a new model instead.");
            }
            if (!expense.getStartDate().equals(request.startDate())) {
                throw new IllegalArgumentException(
                    "Cannot change the start date of a recurring expense that already has entries. "
                        + "Close this one (end date) and create a new model instead.");
            }
        }

        expense.setLabel(request.label());
        expense.setSupplier(supplier);
        // Une indexation ne touche que les echeances a venir: celles deja inscrites
        // portent le montant qui etait du a l'epoque.
        expense.setAmountIncVat(request.amountIncVat());
        expense.setAmountExVat(request.amountExVat());
        expense.setVatAmount(request.vatAmount());
        expense.setPeriodicity(request.periodicity());
        expense.setStartDate(request.startDate());
        expense.setEndDate(request.endDate());
        expense.setPaidOnDueDate(Boolean.TRUE.equals(request.paidOnDueDate()));
        expense.setComment(request.comment());
        expense.setFileDetail(request.fileDetail());
        expense.setActive(Boolean.TRUE.equals(request.active()));

        return toResponse(recurringExpenseRepository.save(expense));
    }

    /**
     * Supprimable tant qu'aucune echeance n'a ete inscrite. Au-dela, la fin de
     * contrat se dit avec une date de fin ou en desactivant le modele: supprimer
     * couperait le lien des lignes deja au facturier avec ce qui les explique.
     */
    @Transactional
    public void delete(Long id) {
        RecurringExpense expense = getOrThrow(id);
        if (invoiceRepository.existsByRecurringExpenseId(id)) {
            throw new IllegalArgumentException(
                "Cannot delete a recurring expense that already produced entries. "
                    + "Set an end date or deactivate it instead.");
        }
        recurringExpenseRepository.delete(expense);
    }

    /** Toutes les echeances echues au {@code upTo} donne et pas encore inscrites. */
    public List<RecurringOccurrence> findDue(LocalDate upTo) {
        LocalDate horizon = upTo != null ? upTo : LocalDate.now();
        return recurringExpenseRepository.findByActiveTrueOrderByLabelAsc().stream()
            .flatMap(expense -> pendingOccurrences(expense, horizon).stream())
            .sorted(Comparator.comparing(RecurringOccurrence::periodStart)
                .thenComparing(RecurringOccurrence::label))
            .toList();
    }

    /**
     * Inscrit les echeances retenues. Chacune est reverifiee contre ce que son
     * modele doit reellement: la liste vient du client, elle ne fait pas foi.
     *
     * <p>Volontairement hors transaction: {@link InvoiceService#create} ouvre la
     * sienne, en SERIALIZABLE, pour attribuer le numero sans collision. L'englober
     * ici la ferait rejoindre la notre — isolation perdue, et un conflit de numero
     * condamnerait toute la generation au lieu d'etre rejoue. Chaque echeance est
     * donc validee pour elle-meme, et la reponse dit ce qui est passe et ce qui non.
     */
    public RecurringGenerationResponse generate(RecurringGenerationRequest request, LocalDate upTo) {
        LocalDate horizon = upTo != null ? upTo : LocalDate.now();
        User author = authService.getCurrentUser().orElseThrow(() -> new IllegalStateException(
            "Cannot generate entries without an author: no authenticated user"));

        // Chronologique: les numeros D001, D002... doivent suivre les periodes,
        // pas l'ordre dans lequel le client a coche les cases.
        List<RecurringGenerationRequest.OccurrenceRef> refs = request.occurrences().stream()
            .distinct()
            .sorted(Comparator.comparing(RecurringGenerationRequest.OccurrenceRef::periodStart)
                .thenComparing(RecurringGenerationRequest.OccurrenceRef::recurringExpenseId))
            .toList();

        List<InvoiceResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (RecurringGenerationRequest.OccurrenceRef ref : refs) {
            RecurringExpense expense = recurringExpenseRepository.findById(ref.recurringExpenseId())
                .orElse(null);
            if (expense == null) {
                skipped.add("Modele " + ref.recurringExpenseId() + " introuvable");
                continue;
            }

            RecurringOccurrence due = pendingOccurrences(expense, horizon).stream()
                .filter(occurrence -> occurrence.periodStart().equals(ref.periodStart()))
                .findFirst()
                .orElse(null);
            if (due == null) {
                skipped.add(expense.getLabel() + " "
                    + RecurringSchedule.periodLabel(expense.getPeriodicity(), ref.periodStart())
                    + " : deja inscrite ou hors periode du modele");
                continue;
            }

            created.add(createOccurrence(expense, due, author));
        }

        return new RecurringGenerationResponse(created, skipped);
    }

    private InvoiceResponse createOccurrence(RecurringExpense expense, RecurringOccurrence due, User author) {
        InvoiceRequest request = new InvoiceRequest(
            null,
            InvoiceSeries.EXPENSE,
            due.year(),
            InvoiceType.PURCHASE,
            expense.getSupplier().getId(),
            expense.getAmountIncVat(),
            expense.getAmountExVat(),
            expense.getVatAmount(),
            due.dueDate(),
            expense.isPaidOnDueDate() ? due.dueDate() : null,
            false,
            expense.getComment(),
            expense.getPeriodicity().toDateScope(),
            due.periodStart(),
            expense.getFileDetail(),
            null,
            null
        );
        return invoiceService.create(request, InvoiceSource.RECURRING, author, expense);
    }

    /** Echeances dues au {@code horizon} dont la periode n'est pas deja inscrite. */
    private List<RecurringOccurrence> pendingOccurrences(RecurringExpense expense, LocalDate horizon) {
        Set<LocalDate> generated = invoiceRepository
            .findByRecurringExpenseIdOrderByScopeDateAsc(expense.getId()).stream()
            .map(Invoice::getScopeDate)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RecurringOccurrence> pending = new ArrayList<>();
        List<LocalDate> due = RecurringSchedule.dueDates(
            expense.getStartDate(), expense.getEndDate(), expense.getPeriodicity(), horizon);
        for (LocalDate dueDate : due) {
            LocalDate periodStart = RecurringSchedule.periodStart(expense.getPeriodicity(), dueDate);
            if (generated.contains(periodStart)) {
                continue;
            }
            pending.add(new RecurringOccurrence(
                expense.getId(),
                expense.getLabel(),
                expense.getSupplier().getName(),
                periodStart,
                RecurringSchedule.periodLabel(expense.getPeriodicity(), periodStart),
                dueDate,
                dueDate.getYear(),
                expense.getAmountIncVat()
            ));
        }
        return pending;
    }

    private void validatePeriod(RecurringExpenseRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot precede start date");
        }
    }

    RecurringExpense getOrThrow(Long id) {
        return recurringExpenseRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Recurring expense not found: " + id));
    }

    private RecurringExpenseResponse toResponse(RecurringExpense expense) {
        List<Invoice> entries = invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(expense.getId());
        LocalDate lastPeriod = entries.isEmpty() ? null : entries.getLast().getScopeDate();

        return new RecurringExpenseResponse(
            expense.getId(),
            expense.getLabel(),
            supplierService.toResponse(expense.getSupplier()),
            expense.getAmountIncVat(),
            expense.getAmountExVat(),
            expense.getVatAmount(),
            expense.getPeriodicity(),
            expense.getStartDate(),
            expense.getEndDate(),
            expense.isPaidOnDueDate(),
            expense.getComment(),
            expense.getFileDetail(),
            expense.isActive(),
            entries.size(),
            lastPeriod,
            expense.isActive() ? pendingOccurrences(expense, LocalDate.now()).size() : 0,
            entries.isEmpty()
        );
    }
}
