package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.AnalysisTotal;
import be.vercauteren.accounting.dto.CategoryComparisonResponse;
import be.vercauteren.accounting.dto.CategoryComparisonRow;
import be.vercauteren.accounting.dto.SupplierChangesResponse;
import be.vercauteren.accounting.dto.SupplierInventoryResponse;
import be.vercauteren.accounting.dto.SupplierSummary;
import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceType;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregats sur les achats. Le calcul reste ici, en BigDecimal: ce sont ces chiffres
 * qu'un ecran ou un assistant restitue, et aucun ne doit etre refait ailleurs.
 *
 * <p>Seuls les achats comptent, toutes series confondues: une depense contractuelle
 * sans document reste une depense. L'annee est celle du facturier, pas celle de la
 * date de reception.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private final InvoiceRepository invoiceRepository;

    /**
     * Depenses par categorie sur deux annees. Avec {@code until}, chaque annee est
     * coupee au meme jour du calendrier, sur la date de reception: comparer une
     * annee en cours a une annee complete ferait croire a une baisse.
     */
    public CategoryComparisonResponse compareCategories(int year, int previousYear, LocalDate until) {
        Map<Integer, List<Invoice>> byYear = purchases(year, previousYear);
        List<Invoice> current = within(byYear.get(year), until);
        List<Invoice> previous = within(byYear.get(previousYear), until);

        Map<ExpenseCategory, List<Invoice>> currentByCategory = groupByCategory(current);
        Map<ExpenseCategory, List<Invoice>> previousByCategory = groupByCategory(previous);

        List<CategoryComparisonRow> rows = Stream
            .concat(currentByCategory.keySet().stream(), previousByCategory.keySet().stream())
            .distinct()
            .map(category -> new CategoryComparisonRow(
                category,
                total(currentByCategory.getOrDefault(category, List.of())),
                total(previousByCategory.getOrDefault(category, List.of()))))
            .sorted(Comparator.comparing(
                (CategoryComparisonRow row) -> row.current().amountIncVat()
                    .max(row.previous().amountIncVat()))
                .reversed())
            .toList();

        return new CategoryComparisonResponse(
            year, previousYear, until, rows,
            new CategoryComparisonRow(null, total(current), total(previous)),
            missingAmountCount(current) + missingAmountCount(previous));
    }

    /**
     * Fournisseurs apparus ou pas encore revus. Un nouveau fournisseur l'est par
     * rapport a toute l'annee precedente. Un absent ne l'est qu'au regard de la
     * meme periode: une facture annuelle de novembre n'est pas encore en retard en
     * octobre.
     */
    public SupplierChangesResponse compareSuppliers(int year, int previousYear, LocalDate until) {
        Map<Integer, List<Invoice>> byYear = purchases(year, previousYear);
        List<Invoice> current = byYear.get(year);
        List<Invoice> previous = byYear.get(previousYear);

        Set<Long> previousSuppliers = supplierIds(previous);
        Set<Long> currentSuppliers = supplierIds(current);

        List<SupplierSummary> newSuppliers = summarize(
            current.stream().filter(i -> !previousSuppliers.contains(i.getSupplier().getId())).toList());
        List<SupplierSummary> missingSuppliers = summarize(
            within(previous, until).stream()
                .filter(i -> !currentSuppliers.contains(i.getSupplier().getId())).toList());

        return new SupplierChangesResponse(year, previousYear, until, newSuppliers, missingSuppliers);
    }

    /** Achats d'une annee par fournisseur, du plus gros au plus petit. */
    public SupplierInventoryResponse inventory(int year, ExpenseCategory category) {
        List<Invoice> invoices = purchases(year).get(year).stream()
            .filter(i -> category == null || category == i.getSupplier().getCategory())
            .toList();
        return new SupplierInventoryResponse(
            year, category, summarize(invoices), total(invoices), missingAmountCount(invoices));
    }

    // --- Agregation ---

    private Map<Integer, List<Invoice>> purchases(Integer... years) {
        List<Integer> wanted = List.of(years);
        Map<Integer, List<Invoice>> byYear = invoiceRepository
            .findByTypeAndYearIn(InvoiceType.PURCHASE, Set.copyOf(wanted)).stream()
            .collect(Collectors.groupingBy(Invoice::getYear));
        wanted.forEach(y -> byYear.putIfAbsent(y, List.of()));
        return byYear;
    }

    private static List<Invoice> within(List<Invoice> invoices, LocalDate until) {
        if (until == null) return invoices;
        MonthDay limit = MonthDay.from(until);
        return invoices.stream()
            .filter(i -> !MonthDay.from(i.getReceptionDate()).isAfter(limit))
            .toList();
    }

    /** Cle nulle pour les fournisseurs sans categorie: ils restent dans les totaux. */
    private static Map<ExpenseCategory, List<Invoice>> groupByCategory(List<Invoice> invoices) {
        Map<ExpenseCategory, List<Invoice>> grouped = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            grouped.computeIfAbsent(invoice.getSupplier().getCategory(), c -> new ArrayList<>())
                .add(invoice);
        }
        return grouped;
    }

    private static Set<Long> supplierIds(List<Invoice> invoices) {
        return invoices.stream().map(i -> i.getSupplier().getId()).collect(Collectors.toSet());
    }

    private static List<SupplierSummary> summarize(List<Invoice> invoices) {
        Map<Long, List<Invoice>> bySupplier = invoices.stream()
            .collect(Collectors.groupingBy(i -> i.getSupplier().getId()));
        return bySupplier.values().stream()
            .map(AnalysisService::summary)
            .sorted(Comparator.comparing(SupplierSummary::amountIncVat).reversed()
                .thenComparing(SupplierSummary::supplierName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static SupplierSummary summary(List<Invoice> invoices) {
        Supplier supplier = invoices.getFirst().getSupplier();
        AnalysisTotal total = total(invoices);
        return new SupplierSummary(
            supplier.getId(),
            supplier.getName(),
            supplier.getCategory(),
            total.count(),
            total.amountIncVat(),
            total.amountExVat(),
            invoices.stream().map(Invoice::getReceptionDate).min(Comparator.naturalOrder()).orElseThrow(),
            invoices.stream().map(Invoice::getReceptionDate).max(Comparator.naturalOrder()).orElseThrow());
    }

    private static AnalysisTotal total(List<Invoice> invoices) {
        return new AnalysisTotal(
            sum(invoices, Invoice::getAmountIncVat),
            sum(invoices, Invoice::getAmountExVat),
            invoices.size());
    }

    private static BigDecimal sum(List<Invoice> invoices, Function<Invoice, BigDecimal> amount) {
        return invoices.stream().map(amount).filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int missingAmountCount(List<Invoice> invoices) {
        return (int) invoices.stream().filter(i -> i.getAmountIncVat() == null).count();
    }
}
