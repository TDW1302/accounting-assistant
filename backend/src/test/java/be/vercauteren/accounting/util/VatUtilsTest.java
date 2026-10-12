package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VatUtilsTest {

    @Test
    void normalizeVat_keeps_only_digits() {
        assertThat(VatUtils.normalizeVat("BE 0123.456.749")).isEqualTo("0123456749");
        assertThat(VatUtils.normalizeVat("BE0765432146")).isEqualTo("0765432146");
        assertThat(VatUtils.normalizeVat(null)).isNull();
        assertThat(VatUtils.normalizeVat("  ")).isNull();
        assertThat(VatUtils.normalizeVat("BE")).isNull();
    }

    @Test
    void formatEnterpriseNumber_accepts_real_numbers_whatever_their_notation() {
        // Numeros fictifs mais valides: quatre notations, un seul format en sortie.
        assertThat(VatUtils.formatEnterpriseNumber("BE0765432146")).isEqualTo("0765.432.146");
        assertThat(VatUtils.formatEnterpriseNumber("BE0456.789.034")).isEqualTo("0456.789.034");
        assertThat(VatUtils.formatEnterpriseNumber("BE 0123.456.749")).isEqualTo("0123.456.749");
        assertThat(VatUtils.formatEnterpriseNumber("0123456749")).isEqualTo("0123.456.749");
    }

    @Test
    void formatEnterpriseNumber_rejects_a_contract_reference() {
        // Lu par l'IA sur un document PLCI: 12 chiffres, ce n'est pas une societe.
        assertThat(VatUtils.formatEnterpriseNumber("0018.0989.2684")).isNull();
    }

    @Test
    void formatEnterpriseNumber_rejects_a_wrong_check_digit() {
        // 0123.456.749 est valide; changer un chiffre casse la cle modulo 97.
        assertThat(VatUtils.formatEnterpriseNumber("0123456750")).isNull();
        assertThat(VatUtils.formatEnterpriseNumber("0123466749")).isNull();
    }

    @Test
    void formatEnterpriseNumber_rejects_wrong_lengths_and_blanks() {
        assertThat(VatUtils.formatEnterpriseNumber("012345674")).isNull();
        assertThat(VatUtils.formatEnterpriseNumber("01234567490")).isNull();
        assertThat(VatUtils.formatEnterpriseNumber(null)).isNull();
        assertThat(VatUtils.formatEnterpriseNumber("aucun")).isNull();
    }
}
