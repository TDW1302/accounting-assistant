package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AliasGeneratorTest {

    @Test
    void fromName_collapses_words_and_strips_accents() {
        assertThat(AliasGenerator.fromName("Le café de la poste")).isEqualTo("LeCafeDeLaPoste");
        assertThat(AliasGenerator.fromName("P&Partners")).isEqualTo("PPartners");
        assertThat(AliasGenerator.fromName("Ag Insurance")).isEqualTo("AgInsurance");
        assertThat(AliasGenerator.fromName("Versement anticipés")).isEqualTo("VersementAnticipes");
    }

    @Test
    void fromName_keeps_existing_capitalisation_inside_words() {
        // "DKV" ne doit pas devenir "Dkv": seule la premiere lettre est forcee.
        assertThat(AliasGenerator.fromName("DKV")).isEqualTo("DKV");
        assertThat(AliasGenerator.fromName("SNCB Europe")).isEqualTo("SNCBEurope");
        assertThat(AliasGenerator.fromName("7ici")).isEqualTo("7ici");
    }

    @Test
    void fromName_returns_null_when_nothing_usable() {
        assertThat(AliasGenerator.fromName(null)).isNull();
        assertThat(AliasGenerator.fromName("   ")).isNull();
        assertThat(AliasGenerator.fromName("&&&")).isNull();
    }

    @Test
    void fromFileName_reads_the_alias_after_the_date_part() {
        assertThat(AliasGenerator.fromFileName("001-2601-DKV.pdf")).isEqualTo("DKV");
        assertThat(AliasGenerator.fromFileName("004-260116-schievelat.pdf")).isEqualTo("schievelat");
        assertThat(AliasGenerator.fromFileName("023-2601-OliverJames.PDF")).isEqualTo("OliverJames");
        assertThat(AliasGenerator.fromFileName("046-2026Q2-Auto5.pdf")).isEqualTo("Auto5");
        assertThat(AliasGenerator.fromFileName("008.1-2601-AmazonUgreenHDMI.pdf"))
            .isEqualTo("AmazonUgreenHDMI");
    }

    @Test
    void fromFileName_handles_a_missing_date_part() {
        // DateScope.NONE: l'alias suit directement le numero.
        assertThat(AliasGenerator.fromFileName("001-Auto5-PneuHiver.pdf")).isEqualTo("Auto5");
    }

    @Test
    void fromFileName_rejects_labels_that_are_not_aliases() {
        // Espaces, accents et ponctuation trahissent un libelle de document.
        assertThat(AliasGenerator.fromFileName("082-260605-ISOC - Déclaration 273A - 2025.pdf")).isNull();
        assertThat(AliasGenerator.fromFileName("030-26Q1-Décompte_-_Cotisations_sociales.pdf")).isNull();
        assertThat(AliasGenerator.fromFileName("25Q2-ElectriciteTesla.pdf")).isNull();
        assertThat(AliasGenerator.fromFileName("index.txt")).isNull();
    }

    @Test
    void mostFrequent_prefers_the_dominant_spelling() {
        List<String> files = List.of(
            "004-260116-schievelat.pdf",
            "017-260210-schievelat.pdf",
            "072-260522-Skievelat.pdf");
        assertThat(AliasGenerator.mostFrequentFromFileNames(files)).isEqualTo("schievelat");
    }

    @Test
    void mostFrequent_ignores_casing_when_grouping() {
        List<String> files = List.of("001-2601-DKV.pdf", "018-2602-dkv.pdf", "038-2603-DKV.pdf");
        assertThat(AliasGenerator.mostFrequentFromFileNames(files)).isEqualTo("DKV");
    }

    @Test
    void mostFrequent_gives_up_on_a_tie() {
        // Un depart au hasard figerait un choix arbitraire dans tous les fichiers a venir.
        List<String> files = List.of("001-2601-Voo.pdf", "002-2602-Telenet.pdf");
        assertThat(AliasGenerator.mostFrequentFromFileNames(files)).isNull();
    }

    @Test
    void mostFrequent_returns_null_without_usable_candidates() {
        assertThat(AliasGenerator.mostFrequentFromFileNames(List.of())).isNull();
        assertThat(AliasGenerator.mostFrequentFromFileNames(List.of("index.txt"))).isNull();
    }
}
