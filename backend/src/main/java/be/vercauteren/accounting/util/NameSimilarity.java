package be.vercauteren.accounting.util;

import java.text.Normalizer;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Rapprochement de noms de fournisseurs saisis a la main dans l'Excel, ou la meme
 * societe apparait sous plusieurs orthographes ("Ag Insurance" et "AG Assurance",
 * "Schievelat" et "Skievelat").
 *
 * <p>Sert a proposer des candidats a la fusion, jamais a fusionner d'office: deux
 * noms proches peuvent designer deux societes distinctes, et seul l'humain tranche.
 * Le reglage penche donc du cote des faux positifs.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameSimilarity {

    /** Casse, accents et ponctuation ne distinguent pas deux societes. */
    public static String normalize(String name) {
        if (name == null) return "";
        return Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Raison du rapprochement de deux noms, ou null s'ils n'ont rien a voir.
     * De la plus sure a la plus faible.
     */
    public static String compareNames(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) return null;

        if (a.equals(b)) {
            return "meme nom a la casse et aux accents pres";
        }

        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;

        // "Soleil" dans "Le soleil". En dessous de 4 caracteres, l'inclusion est
        // trop souvent fortuite pour valoir un signalement.
        if (shorter.length() >= 4 && longer.contains(shorter)) {
            return "un nom contient l'autre";
        }

        // Un nom court tolere moins d'ecart: sur 5 caracteres, deux differences
        // font deja deux mots differents.
        int maxDistance = shorter.length() <= 5 ? 1 : 2;
        int distance = levenshtein(a, b);
        if (distance <= maxDistance) {
            return "orthographes proches (distance " + distance + ")";
        }

        return null;
    }

    /** Distance d'edition, implementation a deux lignes pour rester en O(min) memoire. */
    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                int deletion = previous[j] + 1;
                int insertion = current[j - 1] + 1;
                current[j] = Math.min(substitution, Math.min(deletion, insertion));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[b.length()];
    }
}
