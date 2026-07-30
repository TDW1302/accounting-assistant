package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.FileAdoptionResponse;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.repository.InvoiceRepository;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rattache les documents deja presents dans {app.upload.directory}/{annee} aux
 * factures correspondantes, en renseignant simplement leur filePath.
 *
 * <p>Aucune ecriture sur le disque : pas de copie, pas de renommage, pas de
 * suppression. Les noms existants font foi. L'operation est donc rejouable, et
 * un echec ne peut pas abimer les documents.
 *
 * <p>Seul le numero est extrait du nom de fichier. Le reste (date, alias) suit
 * des conventions trop heterogenes d'une annee a l'autre pour etre deduit sans
 * risque d'inventer des donnees.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAdoptionService {

    private final InvoiceRepository invoiceRepository;

    @Value("${app.upload.directory}")
    private String uploadDirectory;

    /** Meme convention que le batch-upload du frontend : 001-..., 008.1-... */
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d{3,})(?:\\.(\\d+))?-");

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp"
    );

    private record Key(int year, Integer number, Integer subNumber) {}

    @Transactional
    public FileAdoptionResponse adopt(boolean dryRun) throws IOException {
        List<String> details = new ArrayList<>();
        int filesScanned = 0;
        int unnamed = 0;

        // Un numero peut etre porte par plusieurs fichiers (110-1-Amazon.pdf,
        // 110-2-Amazon.pdf...). On regroupe d'abord pour detecter ces cas.
        Map<Key, List<Path>> byKey = new LinkedHashMap<>();

        for (Integer year : invoiceRepository.findDistinctYears()) {
            Path yearDir = Path.of(uploadDirectory).resolve(String.valueOf(year));
            if (!Files.isDirectory(yearDir)) {
                details.add("Annee " + year + " : dossier absent (" + yearDir + ")");
                continue;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(yearDir)) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) continue;
                    String name = file.getFileName().toString();
                    if (!isDocument(name)) continue;

                    filesScanned++;
                    Matcher m = NUMBER_PREFIX.matcher(name);
                    if (!m.find()) {
                        unnamed++;
                        details.add("Annee " + year + " : '" + name
                            + "' ne suit pas la convention NNN[.sous-numero]-, ignore");
                        continue;
                    }
                    Integer number = Integer.valueOf(m.group(1));
                    Integer subNumber = m.group(2) == null ? null : Integer.valueOf(m.group(2));
                    byKey.computeIfAbsent(new Key(year, number, subNumber), k -> new ArrayList<>())
                        .add(file);
                }
            }
        }

        int linked = 0;
        int alreadyLinked = 0;
        int ambiguous = 0;
        int withoutInvoice = 0;

        for (Map.Entry<Key, List<Path>> entry : byKey.entrySet()) {
            Key key = entry.getKey();
            List<Path> files = entry.getValue();
            String label = "Annee " + key.year() + " facture " + format(key);

            if (files.size() > 1) {
                // Choisir arbitrairement serait pire que ne rien faire: on signale.
                ambiguous++;
                details.add(label + " : " + files.size() + " fichiers portent ce numero, "
                    + "aucun rattache -> " + files.stream().map(p -> p.getFileName().toString()).toList());
                continue;
            }

            Optional<Invoice> found = key.subNumber() == null
                ? invoiceRepository.findByYearAndNumberAndSubNumberIsNull(key.year(), key.number())
                : invoiceRepository.findByYearAndNumberAndSubNumber(key.year(), key.number(), key.subNumber());

            if (found.isEmpty()) {
                withoutInvoice++;
                details.add(label + " : aucune facture correspondante en base, '"
                    + files.getFirst().getFileName() + "' non rattache");
                continue;
            }

            Invoice invoice = found.get();
            Path file = files.getFirst();
            if (invoice.getFilePath() != null) {
                alreadyLinked++;
                if (!invoice.getFilePath().equals(file.toString())) {
                    details.add(label + " : pointe deja vers '" + invoice.getFilePath()
                        + "', '" + file.getFileName() + "' non rattache");
                }
                continue;
            }

            if (!dryRun) {
                invoice.setFilePath(file.toString());
                invoiceRepository.save(invoice);
            }
            linked++;
        }

        log.info("File adoption ({}): {} scanned, {} linked, {} already linked, "
                + "{} ambiguous, {} without invoice, {} unnamed",
            dryRun ? "dry run" : "applied", filesScanned, linked, alreadyLinked,
            ambiguous, withoutInvoice, unnamed);

        return new FileAdoptionResponse(dryRun, filesScanned, linked, alreadyLinked,
            ambiguous, withoutInvoice, unnamed, details);
    }

    private static String format(Key key) {
        return key.subNumber() == null
            ? String.valueOf(key.number())
            : key.number() + "." + key.subNumber();
    }

    private static boolean isDocument(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) return false;
        return DOCUMENT_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
