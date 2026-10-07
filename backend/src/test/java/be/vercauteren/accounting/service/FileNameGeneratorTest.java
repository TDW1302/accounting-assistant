package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;

import be.vercauteren.accounting.entity.DateScope;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import be.vercauteren.accounting.entity.Supplier;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** Le nom de fichier d'un document: NNN[.sub]-[date]-Alias[-detail].pdf. */
class FileNameGeneratorTest {

    private final FileNameGenerator generator = new FileNameGenerator();

    private static Invoice.InvoiceBuilder invoice() {
        return Invoice.builder()
            .number(8)
            .series(InvoiceSeries.INVOICE)
            .supplier(Supplier.builder().name("Garage 7").alias("Garage7").build());
    }

    @ParameterizedTest
    @CsvSource({
        "DAILY, 2025-01-14, 008-250114-Garage7.pdf",
        "MONTHLY, 2025-01-14, 008-2501-Garage7.pdf",
        "QUARTERLY, 2025-05-14, 008-2025Q2-Garage7.pdf",
        "QUARTERLY, 2025-12-31, 008-2025Q4-Garage7.pdf",
        "YEARLY, 2025-05-14, 008-2025-Garage7.pdf",
        "NONE, 2025-05-14, 008-Garage7.pdf",
    })
    void theDateFollowsTheScope(DateScope scope, LocalDate date, String expected) {
        assertThat(generator.generate(invoice().dateScope(scope).scopeDate(date).build())).isEqualTo(expected);
    }

    @Test
    void aScopeWithoutDateOrNoScopeAtAllOmitsTheDate() {
        assertThat(generator.generate(invoice().dateScope(DateScope.MONTHLY).scopeDate(null).build()))
            .isEqualTo("008-Garage7.pdf");
        assertThat(generator.generate(invoice().dateScope(null).scopeDate(LocalDate.of(2025, 1, 1)).build()))
            .isEqualTo("008-Garage7.pdf");
    }

    @Test
    void withoutAliasTheNameIsUsedAndEverythingIsSanitized() {
        Invoice invoice = invoice()
            .supplier(Supplier.builder().name("Café du Marché").alias(" ").build())
            .subNumber(2)
            .fileDetail("Pneu hiver/été")
            .build();

        assertThat(generator.generate(invoice)).isEqualTo("008.2-CafduMarch-Pneuhivert.pdf");
    }

    @Test
    void aBlankDetailIsIgnored() {
        assertThat(generator.generate(invoice().fileDetail("  ").build())).isEqualTo("008-Garage7.pdf");
    }

    @Test
    void contractualExpensesCarryTheirPrefix() {
        assertThat(generator.formatNumber(invoice().series(InvoiceSeries.EXPENSE).number(3).build()))
            .isEqualTo("D003");
    }
}
