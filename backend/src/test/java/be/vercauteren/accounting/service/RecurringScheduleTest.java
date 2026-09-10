package be.vercauteren.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;

import be.vercauteren.accounting.entity.Periodicity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringScheduleTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    @DisplayName("Une mensualite tombe chaque mois, date de debut comprise, horizon compris")
    void monthlyIncludesBothEnds() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("2026-01-05"), null, Periodicity.MONTHLY, d("2026-04-05"));

        assertThat(dates).containsExactly(
            d("2026-01-05"), d("2026-02-05"), d("2026-03-05"), d("2026-04-05"));
    }

    @Test
    @DisplayName("Une echeance posterieure a l'horizon n'est pas due")
    void stopsBeforeHorizon() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("2026-01-05"), null, Periodicity.MONTHLY, d("2026-03-04"));

        assertThat(dates).containsExactly(d("2026-01-05"), d("2026-02-05"));
    }

    @Test
    @DisplayName("Un loyer du 31 reste au 31 apres etre passe par fevrier")
    void shortMonthDoesNotShiftFollowingDueDates() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("2026-01-31"), null, Periodicity.MONTHLY, d("2026-04-30"));

        assertThat(dates).containsExactly(
            d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"));
    }

    @Test
    @DisplayName("La date de fin borne les echeances avant l'horizon")
    void endDateBoundsTheSchedule() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("2026-01-10"), d("2026-03-01"), Periodicity.MONTHLY, d("2026-12-31"));

        assertThat(dates).containsExactly(d("2026-01-10"), d("2026-02-10"));
    }

    @Test
    @DisplayName("Un modele qui n'a pas commence ne doit rien")
    void nothingDueBeforeStart() {
        assertThat(RecurringSchedule.dueDates(
            d("2027-01-01"), null, Periodicity.MONTHLY, d("2026-12-31"))).isEmpty();
    }

    @Test
    @DisplayName("Le trimestriel avance de trois mois depuis son mois d'ancrage")
    void quarterlyStepsByThreeMonths() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("2026-02-15"), null, Periodicity.QUARTERLY, d("2026-12-31"));

        assertThat(dates).containsExactly(
            d("2026-02-15"), d("2026-05-15"), d("2026-08-15"), d("2026-11-15"));
    }

    @Test
    @DisplayName("L'annuel avance d'un an")
    void yearlyStepsByOneYear() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("2024-06-30"), null, Periodicity.YEARLY, d("2026-12-31"));

        assertThat(dates).containsExactly(d("2024-06-30"), d("2025-06-30"), d("2026-06-30"));
    }

    @Test
    @DisplayName("La periode couverte se ramene a son premier jour")
    void periodStartNormalisesTheDueDate() {
        assertThat(RecurringSchedule.periodStart(Periodicity.MONTHLY, d("2026-09-15")))
            .isEqualTo(d("2026-09-01"));
        assertThat(RecurringSchedule.periodStart(Periodicity.QUARTERLY, d("2026-08-15")))
            .isEqualTo(d("2026-07-01"));
        assertThat(RecurringSchedule.periodStart(Periodicity.YEARLY, d("2026-06-30")))
            .isEqualTo(d("2026-01-01"));
    }

    @Test
    @DisplayName("Deux echeances trimestrielles consecutives couvrent deux trimestres distincts")
    void quarterlyOccurrencesFallInDistinctQuarters() {
        List<LocalDate> periods = RecurringSchedule.dueDates(
                d("2026-02-15"), null, Periodicity.QUARTERLY, d("2026-12-31")).stream()
            .map(due -> RecurringSchedule.periodStart(Periodicity.QUARTERLY, due))
            .toList();

        assertThat(periods).containsExactly(
            d("2026-01-01"), d("2026-04-01"), d("2026-07-01"), d("2026-10-01"));
    }

    @Test
    @DisplayName("Le libelle de periode se lit tel qu'affiche")
    void periodLabelIsHumanReadable() {
        assertThat(RecurringSchedule.periodLabel(Periodicity.MONTHLY, d("2026-09-01"))).isEqualTo("09/2026");
        assertThat(RecurringSchedule.periodLabel(Periodicity.QUARTERLY, d("2026-07-01"))).isEqualTo("2026 T3");
        assertThat(RecurringSchedule.periodLabel(Periodicity.YEARLY, d("2026-01-01"))).isEqualTo("2026");
    }

    @Test
    @DisplayName("Une date de debut lointaine ne fait pas tourner la boucle sans fin")
    void loopIsBounded() {
        List<LocalDate> dates = RecurringSchedule.dueDates(
            d("1800-01-01"), null, Periodicity.MONTHLY, d("2026-12-31"));

        assertThat(dates).hasSize(RecurringSchedule.MAX_OCCURRENCES);
    }
}
