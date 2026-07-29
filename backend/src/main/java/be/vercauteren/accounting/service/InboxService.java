package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.InboxScanResult;
import be.vercauteren.accounting.dto.InvoiceExtractionResult;
import be.vercauteren.accounting.dto.InvoiceRequest;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.util.InMemoryMultipartFile;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Value("${app.inbox.directory:./inbox}")
    private String inboxDirectory;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp"
    );

    private static final Map<String, String> EXTENSION_TO_MIME = Map.ofEntries(
        Map.entry("pdf", "application/pdf"),
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("tiff", "image/tiff"),
        Map.entry("tif", "image/tiff"),
        Map.entry("webp", "image/webp")
    );

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
            String extension = getExtension(fileName);
            String contentType = EXTENSION_TO_MIME.get(extension);
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

            List<Invoice> candidates = invoiceRepository
                .findBySupplierIdAndReceptionDateAndFilePathIsNullAndPeppolFalse(
                    extraction.supplierId(), receptionDate);

            if (!candidates.isEmpty()) {
                // Match found — attach file to existing invoice
                Invoice match = candidates.getFirst();
                invoiceService.uploadFile(match.getId(), multipartFile);
                Files.deleteIfExists(file);
                log.info("Matched {} to existing invoice #{}", fileName, match.getNumber());
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

            var created = invoiceService.create(request);
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
        String ext = getExtension(file.getFileName().toString());
        return ext != null && ALLOWED_EXTENSIONS.contains(ext);
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) return null;
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private void moveToErrors(Path file, Path errorsDir) throws IOException {
        Files.createDirectories(errorsDir);
        Files.move(file, errorsDir.resolve(file.getFileName()),
            StandardCopyOption.REPLACE_EXISTING);
    }
}
