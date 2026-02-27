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
}
