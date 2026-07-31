package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameSimilarityTest {

    @Test
    void normalize_drops_case_accents_and_punctuation() {
        assertThat(NameSimilarity.normalize("Le café de la poste")).isEqualTo("lecafedelaposte");
        assertThat(NameSimilarity.normalize("P&Partners")).isEqualTo("ppartners");
        assertThat(NameSimilarity.normalize("AG Assurance ")).isEqualTo("agassurance");
        assertThat(NameSimilarity.normalize(null)).isEmpty();
    }

    @Test
    void matches_names_differing_only_by_case_or_accents() {
        assertThat(NameSimilarity.compareNames("Ag Insurance", "AG INSURANCE"))
            .isEqualTo("meme nom a la casse et aux accents pres");
        assertThat(NameSimilarity.compareNames("Le café de la poste", "Le cafe de la poste"))
            .isEqualTo("meme nom a la casse et aux accents pres");
    }

    @Test
    void matches_a_name_contained_in_another() {
        assertThat(NameSimilarity.compareNames("Le soleil", "Soleil"))
            .isEqualTo("un nom contient l'autre");
    }

    @Test
    void matches_the_real_spelling_variants_from_the_excel() {
        // Les deux cas releves a l'import: substitution et suppression de lettres.
        assertThat(NameSimilarity.compareNames("Ag Insurance", "AG Assurance"))
            .startsWith("orthographes proches");
        assertThat(NameSimilarity.compareNames("Schievelat", "Skievelat"))
            .startsWith("orthographes proches");
    }

    @Test
    void keeps_distinct_companies_apart() {
        assertThat(NameSimilarity.compareNames("Acerta", "Anthropic")).isNull();
        assertThat(NameSimilarity.compareNames("Voo", "Stib")).isNull();
        assertThat(NameSimilarity.compareNames("Tesla", "Telco")).isNull();
    }

    @Test
    void short_names_tolerate_less_variation() {
        // "Voo"/"Vos" ne differe que d'une lettre, mais sur trois: rapproche.
        assertThat(NameSimilarity.compareNames("Voo", "Vos")).startsWith("orthographes proches");
        // Deux differences sur un nom court sont deux societes differentes.
        assertThat(NameSimilarity.compareNames("Voo", "Vas")).isNull();
    }

    @Test
    void ignores_a_blank_side() {
        assertThat(NameSimilarity.compareNames("Acerta", "")).isNull();
        assertThat(NameSimilarity.compareNames(null, "Acerta")).isNull();
    }

    @Test
    void levenshtein_counts_edits() {
        assertThat(NameSimilarity.levenshtein("", "abc")).isEqualTo(3);
        assertThat(NameSimilarity.levenshtein("abc", "abc")).isZero();
        assertThat(NameSimilarity.levenshtein("schievelat", "skievelat")).isEqualTo(2);
        assertThat(NameSimilarity.levenshtein("aginsurance", "agassurance")).isEqualTo(2);
    }
}
