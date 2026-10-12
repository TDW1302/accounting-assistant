package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.vercauteren.accounting.dto.RecurringAttachRequest;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.Periodicity;
import be.vercauteren.accounting.entity.RecurringExpense;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.RecurringExpenseRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rattacher une ligne existante, remplacer celle qui occupe la periode, et lister
 * les modeles qui peuvent accueillir une facture.
 *
 * <p>Remplacer supprime la ligne en place. La regle qui dit laquelle est
 * supprimable est la seule chose qui separe un rattachement d'une perte
 * d'ecriture: elle est couverte sous tous ses angles.
 */
class RecurringLinkTest {

    private RecurringExpenseRepository recurringExpenseRepository;
    private InvoiceRepository invoiceRepository;
    private InvoiceService invoiceService;
    private RecurringExpenseService service;

    private static final LocalDate PERIOD = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        recurringExpenseRepository = mock(RecurringExpenseRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        invoiceService = mock(InvoiceService.class);

        FileNameGenerator fileNameGenerator = new FileNameGenerator();
        service = new RecurringExpenseService(
            recurringExpenseRepository,
            invoiceRepository,
            mock(SupplierService.class),
            invoiceService,
            mock(AuthService.class),
            fileNameGenerator);

        when(recurringExpenseRepository.findById(1L)).thenReturn(Optional.of(model()));
    }

    private RecurringExpense model() {
        return RecurringExpense.builder()
            .id(1L)
            .label("Loyer bureau")
            .supplier(Supplier.builder().id(9L).name("Proprietaire").build())
            .periodicity(Periodicity.MONTHLY)
            .startDate(LocalDate.of(2026, 1, 5))
            .active(true)
            .build();
    }

    private Invoice occupant(InvoiceSeries series, InvoiceSource source, String filePath) {
        return Invoice.builder()
            .id(50L)
            .number(7)
            .series(series)
            .year(2026)
            .source(source)
            .scopeDate(PERIOD)
            .receptionDate(LocalDate.of(2026, 7, 5))
            .filePath(filePath)
            .supplier(Supplier.builder().id(9L).name("Proprietaire").build())
            .build();
    }

    @Test
    @DisplayName("Une echeance engendree est supprimee, et la ligne visee prend sa periode")
    void replacesAGeneratedInstalment() {
        // La periode est occupee quand on la consulte, libre apres la suppression.
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(occupant(InvoiceSeries.EXPENSE, InvoiceSource.RECURRING, null)))
            .thenReturn(List.of());

        Invoice target = Invoice.builder()
            .id(77L)
            .number(42)
            .series(InvoiceSeries.INVOICE)
            .year(2026)
            .source(InvoiceSource.EXCEL_IMPORT)
            .receptionDate(LocalDate.of(2026, 7, 3))
            .supplier(Supplier.builder().id(9L).name("Proprietaire").build())
            .build();
        when(invoiceService.getForRecurringLink(77L)).thenReturn(target);

        service.replace(1L, new RecurringAttachRequest(77L, PERIOD));

        verify(invoiceService).delete(50L);
        verify(invoiceService).saveLinked(target);

        // Le numero et l'annee de la ligne reprise ne bougent pas: seule la periode
        // qu'elle couvre est inscrite.
        assertThat(target.getNumber()).isEqualTo(42);
        assertThat(target.getYear()).isEqualTo(2026);
        assertThat(target.getSeries()).isEqualTo(InvoiceSeries.INVOICE);
        assertThat(target.getScopeDate()).isEqualTo(PERIOD);
        assertThat(target.getRecurringExpense()).isNotNull();
        assertThat(target.getRecurringExpense().getLabel()).isEqualTo("Loyer bureau");
    }

    @Test
    @DisplayName("Une ligne saisie a la main n'est pas supprimee par un remplacement")
    void refusesToReplaceAManualEntry() {
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(occupant(InvoiceSeries.INVOICE, InvoiceSource.MANUAL, null)));

        assertThatThrownBy(() -> service.replace(1L, new RecurringAttachRequest(77L, PERIOD)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("n'a pas ete engendree par le modele");

        verify(invoiceService, never()).delete(any());
    }

    @Test
    @DisplayName("Une ligne reprise de l'Excel non plus")
    void refusesToReplaceAnExcelImportedEntry() {
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(occupant(InvoiceSeries.INVOICE, InvoiceSource.EXCEL_IMPORT, null)));

        assertThatThrownBy(() -> service.replace(1L, new RecurringAttachRequest(77L, PERIOD)))
            .isInstanceOf(IllegalArgumentException.class);

        verify(invoiceService, never()).delete(any());
    }

    @Test
    @DisplayName("Une echeance engendree sur laquelle un document a ete depose est protegee")
    void refusesToReplaceAGeneratedInstalmentCarryingADocument() {
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(
                occupant(InvoiceSeries.EXPENSE, InvoiceSource.RECURRING, "2026/D007-2607-Proprio.pdf")));

        assertThatThrownBy(() -> service.replace(1L, new RecurringAttachRequest(77L, PERIOD)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("document");

        verify(invoiceService, never()).delete(any());
    }

    @Test
    @DisplayName("Sans ligne en place, il n'y a rien a remplacer")
    void refusesWhenNothingCoversThePeriod() {
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.replace(1L, new RecurringAttachRequest(77L, PERIOD)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nothing to replace");

        verify(invoiceService, never()).delete(any());
    }

    @Test
    @DisplayName("Une ligne ne se remplace pas par elle-meme")
    void refusesToReplaceAnEntryByItself() {
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(occupant(InvoiceSeries.EXPENSE, InvoiceSource.RECURRING, null)));

        assertThatThrownBy(() -> service.replace(1L, new RecurringAttachRequest(50L, PERIOD)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already covers");

        verify(invoiceService, never()).delete(any());
    }

    @Test
    @DisplayName("Seuls les modeles du meme fournisseur peuvent accueillir une ligne")
    void linkOptionsKeepOnlyModelsOfTheSameSupplier() {
        RecurringExpense other = RecurringExpense.builder()
            .id(2L)
            .label("Frais bancaires")
            .supplier(Supplier.builder().id(99L).name("Banque").build())
            .periodicity(Periodicity.MONTHLY)
            .startDate(LocalDate.of(2026, 1, 1))
            .active(true)
            .build();
        when(recurringExpenseRepository.findByActiveTrueOrderByLabelAsc())
            .thenReturn(List.of(model(), other));
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L)).thenReturn(List.of());
        when(invoiceService.getForRecurringLink(77L)).thenReturn(documentlessInvoice());

        var options = service.findLinkOptions(77L);

        assertThat(options).singleElement()
            .satisfies(o -> {
                assertThat(o.label()).isEqualTo("Loyer bureau");
                assertThat(o.available()).isTrue();
                assertThat(o.suggestedPeriodStart()).isEqualTo(PERIOD);
            });
    }

    @Test
    @DisplayName("Une ligne qui porte un document n'a aucun modele a rejoindre")
    void linkOptionsAreEmptyForAnInvoiceCarryingADocument() {
        Invoice withDocument = documentlessInvoice();
        withDocument.setFilePath("2026/042-Proprio.pdf");
        when(invoiceService.getForRecurringLink(77L)).thenReturn(withDocument);

        assertThat(service.findLinkOptions(77L)).isEmpty();
    }

    @Test
    @DisplayName("Une ligne deja rattachee non plus")
    void linkOptionsAreEmptyForAnAlreadyLinkedInvoice() {
        Invoice linked = documentlessInvoice();
        linked.setRecurringExpense(model());
        when(invoiceService.getForRecurringLink(77L)).thenReturn(linked);

        assertThat(service.findLinkOptions(77L)).isEmpty();
    }

    @Test
    @DisplayName("Un modele dont la periode est prise nomme la ligne qui l'occupe")
    void linkOptionsNameTheOccupant() {
        when(recurringExpenseRepository.findByActiveTrueOrderByLabelAsc()).thenReturn(List.of(model()));
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(occupant(InvoiceSeries.EXPENSE, InvoiceSource.RECURRING, null)));
        when(invoiceService.getForRecurringLink(77L)).thenReturn(documentlessInvoice());

        var options = service.findLinkOptions(77L);

        assertThat(options).singleElement().satisfies(o -> {
            assertThat(o.available()).isFalse();
            assertThat(o.issue()).isEqualTo("Periode deja couverte");
            assertThat(o.conflict()).isNotNull();
            assertThat(o.conflict().displayNumber()).isEqualTo("D007");
            assertThat(o.conflict().replaceable()).isTrue();
        });
    }

    private Invoice documentlessInvoice() {
        return Invoice.builder()
            .id(77L)
            .number(42)
            .series(InvoiceSeries.INVOICE)
            .year(2026)
            .source(InvoiceSource.EXCEL_IMPORT)
            .receptionDate(LocalDate.of(2026, 7, 3))
            .supplier(Supplier.builder().id(9L).name("Proprietaire").build())
            .build();
    }

    @Test
    @DisplayName("La periode couverte reste connue du modele")
    void coverageIsKeyedByPeriod() {
        when(invoiceRepository.findByRecurringExpenseIdOrderByScopeDateAsc(1L))
            .thenReturn(List.of(occupant(InvoiceSeries.EXPENSE, InvoiceSource.RECURRING, null)));

        var attachable = service.findAttachable(1L);
        assertThat(attachable).isEmpty();
    }
}
