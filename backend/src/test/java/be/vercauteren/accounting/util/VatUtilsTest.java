package be.vercauteren.accounting.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VatUtilsTest {

    @Test
    void normalizeVat_keeps_only_digits() {
        assertThat(VatUtils.normalizeVat("BE 0403.258.197")).isEqualTo("0403258197");
        assertThat(VatUtils.normalizeVat("BE0713774795")).isEqualTo("0713774795");
        assertThat(VatUtils.normalizeVat(null)).isNull();
        assertThat(VatUtils.normalizeVat("  ")).isNull();
        assertThat(VatUtils.normalizeVat("BE")).isNull();
    }

    @Test
    void formatEnterpriseNumber_accepts_real_numbers_whatever_their_notation() {
        // Numeros releves sur les factures: quatre notations, un seul format en sortie.
        assertThat(VatUtils.formatEnterpriseNumber("BE0713774795")).isEqualTo("0713.774.795");
        assertThat(VatUtils.formatEnterpriseNumber("BE0416.377.646")).isEqualTo("0416.377.646");
        assertThat(VatUtils.formatEnterpriseNumber("BE 0403.258.197")).isEqualTo("0403.258.197");
        assertThat(VatUtils.formatEnterpriseNumber("0403258197")).isEqualTo("0403.258.197");
    }

    @Test
    void formatEnterpriseNumber_rejects_a_contract_reference() {
        // Lu par l'IA sur un document PLCI: 12 chiffres, ce n'est pas une societe.
        assertThat(VatUtils.formatEnterpriseNumber("0018.0989.2684")).isNull();
    }

    @Test
    void formatEnterpriseNumber_rejects_a_wrong_check_digit() {
        // 0403.258.197 est valide; changer un chiffre casse la cle modulo 97.
        assertThat(VatUtils.formatEnterpriseNumber("0403258198")).isNull();
        assertThat(VatUtils.formatEnterpriseNumber("0403268197")).isNull();
    }

    @Test
    void formatEnterpriseNumber_rejects_wrong_lengths_and_blanks() {
        assertThat(VatUtils.formatEnterpriseNumber("040325819")).isNull();
        assertThat(VatUtils.formatEnterpriseNumber("04032581970")).isNull();
        assertThat(VatUtils.formatEnterpriseNumber(null)).isNull();
        assertThat(VatUtils.formatEnterpriseNumber("aucun")).isNull();
    }
}
