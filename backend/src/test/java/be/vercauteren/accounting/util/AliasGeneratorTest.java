package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AliasGeneratorTest {

    @Test
    void fromName_collapses_words_and_strips_accents() {
        assertThat(AliasGenerator.fromName("Le café du marché")).isEqualTo("LeCafeDuMarche");
        assertThat(AliasGenerator.fromName("K&Associes")).isEqualTo("KAssocies");
        assertThat(AliasGenerator.fromName("Ab Insurance")).isEqualTo("AbInsurance");
        assertThat(AliasGenerator.fromName("Versement anticipés")).isEqualTo("VersementAnticipes");
    }

    @Test
    void fromName_keeps_existing_capitalisation_inside_words() {
        // "ABX" ne doit pas devenir "Abx": seule la premiere lettre est forcee.
        assertThat(AliasGenerator.fromName("ABX")).isEqualTo("ABX");
        assertThat(AliasGenerator.fromName("TRNX Europe")).isEqualTo("TRNXEurope");
        assertThat(AliasGenerator.fromName("3tables")).isEqualTo("3tables");
    }

    @Test
    void fromName_returns_null_when_nothing_usable() {
        assertThat(AliasGenerator.fromName(null)).isNull();
        assertThat(AliasGenerator.fromName("   ")).isNull();
        assertThat(AliasGenerator.fromName("&&&")).isNull();
    }

    @Test
    void fromFileName_reads_the_alias_after_the_date_part() {
        assertThat(AliasGenerator.fromFileName("001-2601-ABX.pdf")).isEqualTo("ABX");
        assertThat(AliasGenerator.fromFileName("004-260116-schoravela.pdf")).isEqualTo("schoravela");
        assertThat(AliasGenerator.fromFileName("023-2601-AcmeConseil.PDF")).isEqualTo("AcmeConseil");
        assertThat(AliasGenerator.fromFileName("046-2026Q2-Garage7.pdf")).isEqualTo("Garage7");
        assertThat(AliasGenerator.fromFileName("008.1-2601-BoutiqueCableHDMI.pdf"))
            .isEqualTo("BoutiqueCableHDMI");
    }

    @Test
    void fromFileName_handles_a_missing_date_part() {
        // DateScope.NONE: l'alias suit directement le numero.
        assertThat(AliasGenerator.fromFileName("001-Garage7-PneuHiver.pdf")).isEqualTo("Garage7");
    }

    @Test
    void fromFileName_rejects_labels_that_are_not_aliases() {
        // Espaces, accents et ponctuation trahissent un libelle de document.
        assertThat(AliasGenerator.fromFileName("082-260605-ISOC - Déclaration 273A - 2025.pdf")).isNull();
        assertThat(AliasGenerator.fromFileName("030-26Q1-Décompte_-_Cotisations_sociales.pdf")).isNull();
        assertThat(AliasGenerator.fromFileName("25Q2-ElectriciteVolta.pdf")).isNull();
        assertThat(AliasGenerator.fromFileName("index.txt")).isNull();
    }

    @Test
    void mostFrequent_prefers_the_dominant_spelling() {
        List<String> files = List.of(
            "004-260116-schoravela.pdf",
            "017-260210-schoravela.pdf",
            "072-260522-Skoravela.pdf");
        assertThat(AliasGenerator.mostFrequentFromFileNames(files)).isEqualTo("schoravela");
    }

    @Test
    void mostFrequent_ignores_casing_when_grouping() {
        List<String> files = List.of("001-2601-ABX.pdf", "018-2602-abx.pdf", "038-2603-ABX.pdf");
        assertThat(AliasGenerator.mostFrequentFromFileNames(files)).isEqualTo("ABX");
    }

    @Test
    void mostFrequent_gives_up_on_a_tie() {
        // Un depart au hasard figerait un choix arbitraire dans tous les fichiers a venir.
        List<String> files = List.of("001-2601-Zoo.pdf", "002-2602-Ondes.pdf");
        assertThat(AliasGenerator.mostFrequentFromFileNames(files)).isNull();
    }

    @Test
    void mostFrequent_returns_null_without_usable_candidates() {
        assertThat(AliasGenerator.mostFrequentFromFileNames(List.of())).isNull();
        assertThat(AliasGenerator.mostFrequentFromFileNames(List.of("index.txt"))).isNull();
    }
}
