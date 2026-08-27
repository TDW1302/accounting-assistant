package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InboxScanResult;
import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSource;
import be.vercauteren.accounting.entity.User;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.util.InMemoryMultipartFile;
import be.vercauteren.accounting.util.MimeTypes;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

    private final InvoiceExtractionService extractionService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final AuthService authService;
    private final UserService userService;

    @Value("${app.inbox.directory:./inbox}")
    private String inboxDirectory;

    /** Ecart tolere entre la date lue sur le document et la date de reception encodee. */
    @Value("${app.inbox.match-window-days:7}")
    private long matchWindowDays;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private enum ProcessResult { MATCHED, CREATED, ERROR }

    public InboxScanResult scan() {
        if (!scanning.compareAndSet(false, true)) {
            log.warn("Inbox scan already in progress, skipping");
            return new InboxScanResult(0, 0, 0, 0, List.of());
        }

        try {
            Path inbox = Paths.get(inboxDirectory);
            if (!Files.exists(inbox)) {
                log.info("Inbox directory does not exist: {}", inbox.toAbsolutePath());
                return new InboxScanResult(0, 0, 0, 0, List.of());
            }

            Path errorsDir = inbox.resolve("errors");

            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inbox)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry) && isAllowedExtension(entry)) {
                        files.add(entry);
                    }
                }
            }

            int matched = 0;
            int created = 0;
            int errors = 0;
            List<String> errorFiles = new ArrayList<>();

            for (Path file : files) {
                ProcessResult result = processFile(file, errorsDir);
                switch (result) {
                    case MATCHED -> matched++;
                    case CREATED -> created++;
                    case ERROR -> {
                        errors++;
                        errorFiles.add(file.getFileName().toString());
                    }
                }
            }

            log.info("Inbox scan complete: {} processed, {} matched, {} created, {} errors",
                files.size(), matched, created, errors);

            return new InboxScanResult(files.size(), matched, created, errors, errorFiles);
        } catch (IOException e) {
            log.error("Error scanning inbox directory", e);
            return new InboxScanResult(0, 0, 0, 0, List.of());
        } finally {
            scanning.set(false);
        }
    }

    private ProcessResult processFile(Path file, Path errorsDir) {
        try {
            String fileName = file.getFileName().toString();
            String contentType = MimeTypes.forFileName(fileName);
            byte[] content = Files.readAllBytes(file);

            InMemoryMultipartFile multipartFile = new InMemoryMultipartFile(
                "file", fileName, contentType, content);

            // Step 1: AI extraction
            InvoiceExtractionResult extraction;
            try {
                extraction = extractionService.extract(multipartFile);
            } catch (Exception e) {
                log.warn("AI extraction failed for {}: {}", fileName, e.getMessage());
                moveToErrors(file, errorsDir);
                return ProcessResult.ERROR;
            }

            if (extraction.supplierId() == null) {
                log.warn("No supplier identified for {}", fileName);
                moveToErrors(file, errorsDir);
                return ProcessResult.ERROR;
            }

            // Step 2: Try matching an existing invoice without a document
            LocalDate receptionDate = extraction.receptionDate() != null
                ? extraction.receptionDate() : LocalDate.now();

            Optional<Invoice> match = findMatch(
                extraction.supplierId(), extraction.amountIncVat(), receptionDate);

            if (match.isPresent()) {
                // Match found — attach file to existing invoice
                Invoice invoice = match.get();
                invoiceService.uploadFile(invoice.getId(), multipartFile);
                Files.deleteIfExists(file);
                log.info("Matched {} to existing invoice #{} ({})",
                    fileName, invoice.getNumber(), invoice.getReceptionDate());
                return ProcessResult.MATCHED;
            }

            // Step 3: No match — create new invoice
            int year = receptionDate.getYear();
            InvoiceRequest request = new InvoiceRequest(
                null,
                year,
                extraction.type() != null ? extraction.type() : be.vercauteren.accounting.entity.InvoiceType.PURCHASE,
                extraction.supplierId(),
                extraction.amountIncVat(),
                extraction.amountExVat(),
                extraction.vatAmount(),
                receptionDate,
                extraction.paymentDate(),
                false,
                extraction.comment(),
                extraction.dateScope() != null ? extraction.dateScope() : be.vercauteren.accounting.entity.DateScope.NONE,
                extraction.scopeDate(),
                null,
                null,
                null
            );

            // Le scan tourne aussi sans session, a 3h: l'auteur est alors le compte
            // technique, pour qu'aucune facture ne naisse sans auteur.
            User author = authService.getCurrentUser().orElseGet(userService::getSystemUser);
            var created = invoiceService.create(request, InvoiceSource.INBOX, author);
            invoiceService.uploadFile(created.id(), multipartFile);
            Files.deleteIfExists(file);
            log.info("Created invoice #{} from {}", created.number(), fileName);
            return ProcessResult.CREATED;

        } catch (Exception e) {
            log.error("Error processing inbox file {}: {}", file.getFileName(), e.getMessage(), e);
            try {
                moveToErrors(file, errorsDir);
            } catch (IOException moveEx) {
                log.error("Failed to move {} to errors: {}", file.getFileName(), moveEx.getMessage());
            }
            return ProcessResult.ERROR;
        }
    }

    /**
     * Cherche la facture deja encodee que ce document vient completer.
     *
     * <p>Le montant TTC est le critere discriminant: la date lue sur le document
     * n'est pas celle de reception encodee a la main, d'ou la fenetre de quelques
     * jours. A defaut de montant lu, on retombe sur l'ancien rapprochement, qui
     * exige une date de reception exacte.
     *
     * <p>Les factures Peppol sont candidates: leur document n'est pas encore stocke
     * ici, et creer un doublon serait pire que de le rattacher.
     */
    private Optional<Invoice> findMatch(Long supplierId, BigDecimal amountIncVat, LocalDate documentDate) {
        List<Invoice> candidates = invoiceRepository.findBySupplierIdAndFilePathIsNull(supplierId);

        if (amountIncVat != null) {
            Optional<Invoice> byAmount = candidates.stream()
                .filter(invoice -> invoice.getAmountIncVat() != null
                    && invoice.getAmountIncVat().compareTo(amountIncVat) == 0)
                .filter(invoice -> daysBetween(invoice.getReceptionDate(), documentDate) <= matchWindowDays)
                // Plusieurs mensualites identiques peuvent coexister: la plus proche
                // en date, puis la plus ancienne, sinon le choix serait au hasard.
                .min(Comparator
                    .comparingLong((Invoice invoice) -> daysBetween(invoice.getReceptionDate(), documentDate))
                    .thenComparing(Invoice::getNumber));
            if (byAmount.isPresent()) {
                return byAmount;
            }
        }

        return candidates.stream()
            .filter(invoice -> !invoice.isPeppol())
            .filter(invoice -> invoice.getReceptionDate().isEqual(documentDate))
            .min(Comparator.comparing(Invoice::getNumber));
    }

    private static long daysBetween(LocalDate left, LocalDate right) {
        return Math.abs(ChronoUnit.DAYS.between(left, right));
    }

    public int countErrors() {
        Path errorsDir = Paths.get(inboxDirectory).resolve("errors");
        if (!Files.exists(errorsDir) || !Files.isDirectory(errorsDir)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(errorsDir)) {
            int count = 0;
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    count++;
                }
            }
            return count;
        } catch (IOException e) {
            log.warn("Error counting inbox errors: {}", e.getMessage());
            return 0;
        }
    }

    private boolean isAllowedExtension(Path file) {
        return MimeTypes.isSupported(file.getFileName().toString());
    }

    private void moveToErrors(Path file, Path errorsDir) throws IOException {
        Files.createDirectories(errorsDir);
        Files.move(file, errorsDir.resolve(file.getFileName()),
            StandardCopyOption.REPLACE_EXISTING);
    }
}
