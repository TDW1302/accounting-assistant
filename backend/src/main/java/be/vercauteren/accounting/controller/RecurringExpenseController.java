package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.AttachableInvoice;
import be.vercauteren.accounting.dto.InvoiceResponse;
import be.vercauteren.accounting.dto.RecurringAttachRequest;
import be.vercauteren.accounting.dto.RecurringExpenseRequest;
import be.vercauteren.accounting.dto.RecurringExpenseResponse;
import be.vercauteren.accounting.dto.RecurringGenerationRequest;
import be.vercauteren.accounting.dto.RecurringGenerationResponse;
import be.vercauteren.accounting.dto.RecurringOccurrence;
import be.vercauteren.accounting.service.RecurringExpenseService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    public List<RecurringExpenseResponse> findAll() {
        return recurringExpenseService.findAll();
    }

    @GetMapping("/{id}")
    public RecurringExpenseResponse findById(@PathVariable Long id) {
        return recurringExpenseService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringExpenseResponse create(@Valid @RequestBody RecurringExpenseRequest request) {
        return recurringExpenseService.create(request);
    }

    @PutMapping("/{id}")
    public RecurringExpenseResponse update(@PathVariable Long id,
                                            @Valid @RequestBody RecurringExpenseRequest request) {
        return recurringExpenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        recurringExpenseService.delete(id);
    }

    /**
     * Les echeances echues et pas encore inscrites. {@code upTo} sert a preparer
     * l'avenir — le loyer de janvier avant le 1er — et vaut aujourd'hui par defaut.
     */
    @GetMapping("/due")
    public List<RecurringOccurrence> findDue(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate upTo) {
        return recurringExpenseService.findDue(upTo);
    }

    /** Les lignes du facturier deja rattachees a ce modele. */
    @GetMapping("/{id}/entries")
    public List<InvoiceResponse> findEntries(@PathVariable Long id) {
        return recurringExpenseService.findEntries(id);
    }

    /** Lignes du meme fournisseur, sans document, encore rattachables. */
    @GetMapping("/{id}/attachable")
    public List<AttachableInvoice> findAttachable(@PathVariable Long id) {
        return recurringExpenseService.findAttachable(id);
    }

    /**
     * Rattache une ligne existante — un loyer repris de l'Excel — a ce modele,
     * pour la periode indiquee. Son numero et son annee ne changent pas.
     */
    @PostMapping("/{id}/attach")
    public InvoiceResponse attach(@PathVariable Long id,
                                   @Valid @RequestBody RecurringAttachRequest request) {
        return recurringExpenseService.attach(id, request);
    }

    @DeleteMapping("/entries/{invoiceId}")
    public InvoiceResponse detach(@PathVariable Long invoiceId) {
        return recurringExpenseService.detach(invoiceId);
    }

    @PostMapping("/generate")
    public RecurringGenerationResponse generate(
        @Valid @RequestBody RecurringGenerationRequest request,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate upTo) {
        return recurringExpenseService.generate(request, upTo);
    }
}
