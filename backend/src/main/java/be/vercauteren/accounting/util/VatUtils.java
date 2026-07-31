package be.vercauteren.accounting.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VatUtils {

    public static String normalizeVat(String vatNumber) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return null;
        }
        String normalized = vatNumber.replaceAll("[^0-9]", "");
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Met un numero d'entreprise belge au format documente {@code 0XXX.XXX.XXX},
     * ou rend null s'il n'en est pas un.
     *
     * <p>Deux controles: dix chiffres, et la cle de controle modulo 97 que porte
     * tout numero belge. Ce second filtre existe parce que ces numeros sont lus par
     * l'IA sur des documents qui portent aussi des numeros de contrat, de police ou
     * de client: une reference etrangere passe le test de longueur, presque jamais
     * celui de la cle. Et un faux numero coute plus cher qu'un numero absent — il
     * peut faire passer deux fiches distinctes pour la meme societe.
     */
    public static String formatEnterpriseNumber(String value) {
        String digits = normalizeVat(value);
        if (digits == null || digits.length() != 10) {
            return null;
        }

        long base = Long.parseLong(digits.substring(0, 8));
        int checkDigits = Integer.parseInt(digits.substring(8));
        if (97 - (base % 97) != checkDigits) {
            return null;
        }

        return digits.substring(0, 4) + "." + digits.substring(4, 7) + "." + digits.substring(7);
    }
}
