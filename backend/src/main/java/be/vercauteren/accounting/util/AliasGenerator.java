package be.vercauteren.accounting.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Fabrique l'alias d'un fournisseur, utilise pour nommer les fichiers.
 *
 * <p>Deux sources, dans cet ordre. Les noms de fichiers deja sur le disque font
 * foi : ils portent les alias reellement employes, y compris leurs irregularites
 * de casse, et s'en ecarter renommerait les prochains documents d'un fournisseur
 * dont les anciens gardent l'ancienne forme. A defaut, l'alias est derive du nom.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AliasGenerator {

    /** NNN[.sous-numero]-reste.extension, meme convention que FileAdoptionService. */
    private static final Pattern FILE_NAME = Pattern.compile("^\\d{3,}(?:\\.\\d+)?-(.+)\\.[^.]+$");

    /** YYMMDD, YYMM, YYYY, YYYYQ# ou YYQ#, selon le DateScope de la facture. */
    private static final Pattern DATE_PART = Pattern.compile("^(?:\\d{6}|\\d{4}|\\d{2,4}Q\\d)$");

    /**
     * Un alias exploitable ne contient ni espace, ni accent, ni ponctuation: c'est
     * ce que {@code FileNameGenerator.sanitize} laisserait passer intact. Le reste
     * ("Note de frais kwh 26Q1", "ISOC ", "Decompte_") est un libelle de document,
     * pas un alias, et serait une mauvaise base pour les fichiers a venir.
     */
    private static final Pattern CLEAN_ALIAS = Pattern.compile("^[A-Za-z0-9]{2,30}$");

    /**
     * Alias derive du nom: accents translittereres, mots colles et capitalises.
     * "Le cafe de la poste" donne "LeCafeDeLaPoste", "P&Partners" donne "PPartners".
     * Retourne null si le nom ne contient aucun caractere exploitable.
     */
    public static String fromName(String name) {
        if (name == null) return null;

        String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");

        StringBuilder sb = new StringBuilder();
        for (String word : withoutAccents.split("[^A-Za-z0-9]+")) {
            if (word.isEmpty()) continue;
            // Le reste du mot est conserve tel quel: "DKV" doit rester "DKV", pas "Dkv".
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Alias lu dans un nom de fichier {@code NNN[.sub]-[date]-Alias[-detail].pdf}.
     * Retourne null si le fichier ne suit pas la convention ou si le segment obtenu
     * n'a pas la forme d'un alias.
     */
    public static String fromFileName(String fileName) {
        if (fileName == null) return null;

        Matcher matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) return null;

        String[] segments = matcher.group(1).split("-");
        if (segments.length == 0) return null;

        // La date est optionnelle (DateScope.NONE): l'alias est le segment suivant
        // quand elle est presente, le premier sinon.
        int aliasIndex = DATE_PART.matcher(segments[0]).matches() ? 1 : 0;
        if (aliasIndex >= segments.length) return null;

        String candidate = segments[aliasIndex];
        return CLEAN_ALIAS.matcher(candidate).matches() ? candidate : null;
    }

    /**
     * Alias majoritaire parmi des noms de fichiers. Exige une majorite stricte des
     * candidats exploitables: deux orthographes a egalite ne designent pas un usage
     * etabli, et en departager une au hasard figerait un choix arbitraire dans tous
     * les fichiers a venir. Retourne null dans ce cas, laissant la main a
     * {@link #fromName(String)}.
     */
    public static String mostFrequentFromFileNames(List<String> fileNames) {
        List<String> candidates = new ArrayList<>();
        for (String fileName : fileNames) {
            String alias = fromFileName(fileName);
            if (alias != null) candidates.add(alias);
        }
        if (candidates.isEmpty()) return null;

        // Regroupe sans tenir compte de la casse, mais restitue la forme exacte la
        // plus employee: "DKV" et "dkv" sont le meme usage, ecrit de deux facons.
        Map<String, List<String>> byLowercase = new LinkedHashMap<>();
        for (String candidate : candidates) {
            byLowercase.computeIfAbsent(candidate.toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(candidate);
        }

        List<String> winner = byLowercase.values().stream()
            .max(Comparator.comparingInt(List::size))
            .orElseThrow();

        if (winner.size() * 2 <= candidates.size()) return null;

        return winner.stream()
            .collect(Collectors.groupingBy(form -> form, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElseThrow()
            .getKey();
    }
}
