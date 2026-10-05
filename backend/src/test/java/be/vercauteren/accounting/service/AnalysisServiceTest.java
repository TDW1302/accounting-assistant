package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalysisServiceTest {

    private static final Supplier RESTO = supplier(1L, "Chez Léon", ExpenseCategory.RESTAURANT);
    private static final Supplier BRASSERIE = supplier(2L, "Brasserie", ExpenseCategory.RESTAURANT);
    private static final Supplier PROXIMUS = supplier(3L, "Proximus", ExpenseCategory.TELECOM);
    private static final Supplier ASSUREUR = supplier(4L, "AG Insurance", ExpenseCategory.ASSURANCE);
    private static final Supplier INCONNU = supplier(5L, "Sans categorie", null);

    private final List<Invoice> ledger = new ArrayList<>();
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        InvoiceRepository repository = mock(InvoiceRepository.class);
        when(repository.findByTypeAndYearIn(eq(InvoiceType.PURCHASE), any()))
            .thenAnswer(call -> {
                java.util.Collection<Integer> years = call.getArgument(1);
                return ledger.stream()
                    .filter(i -> i.getType() == InvoiceType.PURCHASE && years.contains(i.getYear()))
                    .toList();
            });
        service = new AnalysisService(repository);
    }

    @Test
    void comparesCategoriesAcrossTwoYears() {
        add(RESTO, "2025-03-10", "40.00");
        add(BRASSERIE, "2025-06-01", "60.00");
        add(RESTO, "2026-02-01", "150.00");
        add(PROXIMUS, "2026-01-15", "50.00");

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, null);

        CategoryComparisonRow restaurant = row(response, ExpenseCategory.RESTAURANT);
        assertThat(restaurant.current().amountIncVat()).isEqualByComparingTo("150.00");
        assertThat(restaurant.previous().amountIncVat()).isEqualByComparingTo("100.00");
        assertThat(restaurant.previous().count()).isEqualTo(2);

        // Une categorie absente une annee y vaut zero, elle ne disparait pas.
        CategoryComparisonRow telecom = row(response, ExpenseCategory.TELECOM);
        assertThat(telecom.previous().amountIncVat()).isEqualByComparingTo("0");
        assertThat(telecom.previous().count()).isZero();

        // La plus grosse categorie d'abord.
        assertThat(response.rows().getFirst().category()).isEqualTo(ExpenseCategory.RESTAURANT);
        assertThat(response.total().current().amountIncVat()).isEqualByComparingTo("200.00");
        assertThat(response.total().previous().amountIncVat()).isEqualByComparingTo("100.00");
    }

    @Test
    void comparablePeriodCutsBothYearsOnTheSameCalendarDay() {
        add(RESTO, "2025-03-10", "40.00");
        add(RESTO, "2025-11-20", "500.00");
        add(RESTO, "2026-03-01", "70.00");

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, LocalDate.of(2026, 10, 5));

        // Sans la coupure, novembre 2025 ferait croire a une baisse.
        CategoryComparisonRow restaurant = row(response, ExpenseCategory.RESTAURANT);
        assertThat(restaurant.previous().amountIncVat()).isEqualByComparingTo("40.00");
        assertThat(restaurant.current().amountIncVat()).isEqualByComparingTo("70.00");
    }

    @Test
    void missingAmountsAreCountedNotSummedAsZero() {
        add(RESTO, "2026-01-10", "30.00");
        add(RESTO, "2026-01-11", null);
        add(INCONNU, "2026-01-12", "12.50");

        CategoryComparisonResponse response = service.compareCategories(2026, 2025, null);

        assertThat(response.missingAmountCount()).isEqualTo(1);
        assertThat(row(response, ExpenseCategory.RESTAURANT).current().count()).isEqualTo(2);
        // Un fournisseur sans categorie reste dans les totaux, sous une ligne nulle.
        assertThat(row(response, null).current().amountIncVat()).isEqualByComparingTo("12.50");
        assertThat(response.total().current().amountIncVat()).isEqualByComparingTo("42.50");
    }

    @Test
    void salesAreLeftOut() {
        add(RESTO, "2026-01-10", "30.00");
        Invoice sale = add(PROXIMUS, "2026-01-10", "999.00");
        sale.setType(InvoiceType.SALE);

        assertThat(service.compareCategories(2026, 2025, null).total().current().amountIncVat())
            .isEqualByComparingTo("30.00");
    }

    @Test
    void newSuppliersAreMeasuredAgainstTheWholePreviousYear() {
        add(RESTO, "2025-12-20", "40.00");
        add(RESTO, "2026-01-10", "45.00");
        add(PROXIMUS, "2026-02-01", "50.00");
        add(PROXIMUS, "2026-03-01", "50.00");

        SupplierChangesResponse response = service.compareSuppliers(2026, 2025, LocalDate.of(2026, 10, 5));

        // Chez Leon a ete vu en decembre 2025, apres la coupure: il n'est pas nouveau.
        assertThat(response.newSuppliers()).extracting(SupplierSummary::supplierName)
            .containsExactly("Proximus");
        SupplierSummary proximus = response.newSuppliers().getFirst();
        assertThat(proximus.count()).isEqualTo(2);
        assertThat(proximus.amountIncVat()).isEqualByComparingTo("100.00");
        assertThat(proximus.firstDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(proximus.lastDate()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void missingSuppliersOnlyCountTheComparablePeriod() {
        add(PROXIMUS, "2025-04-01", "50.00");
        add(ASSUREUR, "2025-11-15", "800.00");

        SupplierChangesResponse response = service.compareSuppliers(2026, 2025, LocalDate.of(2026, 10, 5));

        // L'assurance annuelle de novembre n'est pas encore en retard en octobre.
        assertThat(response.missingSuppliers()).extracting(SupplierSummary::supplierName)
            .containsExactly("Proximus");
        assertThat(service.compareSuppliers(2026, 2025, null).missingSuppliers())
            .extracting(SupplierSummary::supplierName)
            .containsExactly("AG Insurance", "Proximus");
    }

    @Test
    void inventoryGroupsBySupplierAndFiltersOnCategory() {
        add(RESTO, "2026-01-10", "30.00");
        add(RESTO, "2026-02-10", "20.00");
        add(BRASSERIE, "2026-01-05", "80.00");
        add(PROXIMUS, "2026-01-15", "50.00");
        add(RESTO, "2025-01-10", "999.00");

        SupplierInventoryResponse all = service.inventory(2026, null);
        assertThat(all.suppliers()).extracting(SupplierSummary::supplierName)
            // A montant egal, l'ordre alphabetique departage.
            .containsExactly("Brasserie", "Chez Léon", "Proximus");
        assertThat(all.total().amountIncVat()).isEqualByComparingTo("180.00");

        SupplierInventoryResponse restaurants = service.inventory(2026, ExpenseCategory.RESTAURANT);
        assertThat(restaurants.suppliers()).extracting(SupplierSummary::supplierName)
            .containsExactly("Brasserie", "Chez Léon");
        assertThat(restaurants.total().count()).isEqualTo(3);
    }

    private Invoice add(Supplier supplier, String date, String amountIncVat) {
        LocalDate reception = LocalDate.parse(date);
        Invoice invoice = Invoice.builder()
            .year(reception.getYear())
            .number(ledger.size() + 1)
            .type(InvoiceType.PURCHASE)
            .supplier(supplier)
            .receptionDate(reception)
            .amountIncVat(amountIncVat == null ? null : new BigDecimal(amountIncVat))
            .amountExVat(amountIncVat == null ? null : new BigDecimal(amountIncVat))
            .build();
        ledger.add(invoice);
        return invoice;
    }

    private static CategoryComparisonRow row(CategoryComparisonResponse response, ExpenseCategory category) {
        return response.rows().stream()
            .filter(r -> r.category() == category)
            .findFirst()
            .orElseThrow();
    }

    private static Supplier supplier(Long id, String name, ExpenseCategory category) {
        return Supplier.builder().id(id).name(name).category(category).build();
    }
}
