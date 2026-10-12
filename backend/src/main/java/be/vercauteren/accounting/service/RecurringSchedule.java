package be.vercauteren.accounting.service;

import be.vercauteren.accounting.entity.Periodicity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Le calendrier d'une depense recurrente: quand elle tombe, et quelle periode
 * chaque echeance couvre. Isole du service parce que c'est la seule partie ou
 * une erreur se voit tard — un mois saute, une periode comptee deux fois — et
 * la seule qui se verifie sans base ni contexte Spring.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RecurringSchedule {

    /**
     * Garde-fou de boucle: 100 ans de mensualites. Une date de debut saisie de
     * travers ne doit pas faire tourner la generation indefiniment.
     */
    static final int MAX_OCCURRENCES = 1200;

    /**
     * Les dates d'echeance de {@code startDate} jusqu'a {@code horizon}, bornees
     * par {@code endDate} s'il y en a une.
     *
     * <p>Chaque date se calcule depuis la date de debut, jamais de proche en
     * proche: un 31 ramene au 28 en fevrier y resterait sinon pour tous les mois
     * suivants, et un loyer du 31 deviendrait un loyer du 28.
     */
    static List<LocalDate> dueDates(LocalDate startDate, LocalDate endDate,
                                     Periodicity periodicity, LocalDate horizon) {
        LocalDate limit = endDate != null && endDate.isBefore(horizon) ? endDate : horizon;

        List<LocalDate> dates = new ArrayList<>();
        int step = periodicity.months();
        for (int n = 0; n < MAX_OCCURRENCES; n++) {
            LocalDate due = startDate.plusMonths((long) n * step);
            if (due.isAfter(limit)) {
                break;
            }
            dates.add(due);
        }
        return dates;
    }

    /**
     * Debut de la periode couverte par une echeance. C'est cette date qui
     * identifie l'echeance — deux echeances ne peuvent pas couvrir la meme
     * periode — et qui date le nom de fichier via la portee de date.
     */
    static LocalDate periodStart(Periodicity periodicity, LocalDate dueDate) {
        return switch (periodicity) {
            case MONTHLY -> dueDate.withDayOfMonth(1);
            case QUARTERLY -> dueDate.withDayOfMonth(1)
                .withMonth((dueDate.getMonthValue() - 1) / 3 * 3 + 1);
            case YEARLY -> LocalDate.of(dueDate.getYear(), 1, 1);
        };
    }

    /**
     * La date d'echeance qui couvre exactement {@code periodStart}, si le modele
     * en a bien une. Sert au rattachement d'une ligne existante: la periode vient
     * du client, et seule une periode reellement prevue par le modele est
     * acceptable.
     *
     * <p>L'horizon depasse volontairement la periode demandee: une echeance
     * tombe apres le premier jour de la periode qu'elle couvre — un loyer du 5
     * couvre le mois entier — et s'arreter a {@code periodStart} la manquerait.
     */
    static Optional<LocalDate> dueDateFor(LocalDate startDate, LocalDate endDate,
                                           Periodicity periodicity, LocalDate periodStart) {
        LocalDate horizon = periodStart.plusMonths(periodicity.months());
        return dueDates(startDate, endDate, periodicity, horizon).stream()
            .filter(due -> periodStart(periodicity, due).equals(periodStart))
            .findFirst();
    }

    /** "09/2026", "2026 T3", "2026". */
    static String periodLabel(Periodicity periodicity, LocalDate periodStart) {
        return switch (periodicity) {
            case MONTHLY -> String.format("%02d/%d", periodStart.getMonthValue(), periodStart.getYear());
            case QUARTERLY -> String.format("%d T%d", periodStart.getYear(),
                (periodStart.getMonthValue() - 1) / 3 + 1);
            case YEARLY -> String.valueOf(periodStart.getYear());
        };
    }
}
