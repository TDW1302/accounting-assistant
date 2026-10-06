package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NameSimilarityTest {

    @Test
    void normalize_drops_case_accents_and_punctuation() {
        assertThat(NameSimilarity.normalize("Le café du marché")).isEqualTo("lecafedumarche");
        assertThat(NameSimilarity.normalize("K&Associes")).isEqualTo("kassocies");
        assertThat(NameSimilarity.normalize("AB Assurance ")).isEqualTo("abassurance");
        assertThat(NameSimilarity.normalize(null)).isEmpty();
    }

    @Test
    void matches_names_differing_only_by_case_or_accents() {
        assertThat(NameSimilarity.compareNames("Ab Insurance", "AB INSURANCE"))
            .isEqualTo("meme nom a la casse et aux accents pres");
        assertThat(NameSimilarity.compareNames("Le café du marché", "Le cafe du marche"))
            .isEqualTo("meme nom a la casse et aux accents pres");
    }

    @Test
    void matches_a_name_contained_in_another() {
        assertThat(NameSimilarity.compareNames("Le moulin", "Moulin"))
            .isEqualTo("un nom contient l'autre");
    }

    @Test
    void matches_the_real_spelling_variants_from_the_excel() {
        // Les deux cas releves a l'import: substitution et suppression de lettres.
        assertThat(NameSimilarity.compareNames("Ab Insurance", "AB Assurance"))
            .startsWith("orthographes proches");
        assertThat(NameSimilarity.compareNames("Schoravela", "Skoravela"))
            .startsWith("orthographes proches");
    }

    @Test
    void keeps_distinct_companies_apart() {
        assertThat(NameSimilarity.compareNames("Socialis", "Anthropic")).isNull();
        assertThat(NameSimilarity.compareNames("Zoo", "Trab")).isNull();
        assertThat(NameSimilarity.compareNames("Volta", "Telco")).isNull();
    }

    @Test
    void short_names_tolerate_less_variation() {
        // "Zoo"/"Zos" ne differe que d'une lettre, mais sur trois: rapproche.
        assertThat(NameSimilarity.compareNames("Zoo", "Zos")).startsWith("orthographes proches");
        // Deux differences sur un nom court sont deux societes differentes.
        assertThat(NameSimilarity.compareNames("Zoo", "Zas")).isNull();
    }

    @Test
    void ignores_a_blank_side() {
        assertThat(NameSimilarity.compareNames("Socialis", "")).isNull();
        assertThat(NameSimilarity.compareNames(null, "Socialis")).isNull();
    }

    @Test
    void levenshtein_counts_edits() {
        assertThat(NameSimilarity.levenshtein("", "abc")).isEqualTo(3);
        assertThat(NameSimilarity.levenshtein("abc", "abc")).isZero();
        assertThat(NameSimilarity.levenshtein("schoravela", "skoravela")).isEqualTo(2);
        assertThat(NameSimilarity.levenshtein("abinsurance", "abassurance")).isEqualTo(2);
    }
}
