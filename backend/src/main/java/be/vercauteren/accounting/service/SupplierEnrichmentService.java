package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.SupplierAiData;
import be.vercauteren.accounting.dto.SupplierAliasResponse;
import be.vercauteren.accounting.dto.SupplierEnrichmentResponse;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.SupplierRepository;
import be.vercauteren.accounting.util.AliasGenerator;
import be.vercauteren.accounting.util.InMemoryMultipartFile;
import be.vercauteren.accounting.util.MimeTypes;
import be.vercauteren.accounting.util.VatUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complete les fiches fournisseurs creees par l'import Excel, qui n'en renseigne
 * que le nom.
 *
 * <p>Deux passes independantes, volontairement separees: les alias sont derives
 * sans appel externe et peuvent etre rejoues sans cout, l'enrichissement IA coute
 * un appel par fournisseur. Aucune des deux n'ecrase une valeur existante — une
 * saisie manuelle fait toujours autorite sur une deduction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierEnrichmentService {

    private final SupplierRepository supplierRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceExtractionService extractionService;

    /**
     * Renseigne l'alias des fournisseurs qui n'en ont pas. La source privilegiee
     * est le nom des documents deja rattaches, qui portent les alias reellement
     * utilises; le nom du fournisseur ne sert que de repli.
     */
    @Transactional
    public SupplierAliasResponse generateAliases(boolean dryRun) {
        List<String> details = new ArrayList<>();
        int set = 0;
        int alreadySet = 0;
        int failed = 0;

        List<Supplier> suppliers = sortedByName();

        for (Supplier supplier : suppliers) {
            if (supplier.getAlias() != null && !supplier.getAlias().isBlank()) {
                alreadySet++;
                continue;
            }

            List<String> fileNames = invoiceRepository
                .findBySupplierIdAndFilePathIsNotNullOrderByYearDescNumberDesc(supplier.getId())
                .stream()
                .map(Invoice::getFilePath)
                .map(path -> Path.of(path).getFileName().toString())
                .toList();

            String alias = AliasGenerator.mostFrequentFromFileNames(fileNames);
            String source = "fichiers";
            if (alias == null) {
                alias = AliasGenerator.fromName(supplier.getName());
                source = "nom";
            }

            if (alias == null) {
                failed++;
                details.add(supplier.getName() + " : aucun alias exploitable, laisse vide");
                continue;
            }

            if (!dryRun) {
                supplier.setAlias(alias);
                supplierRepository.save(supplier);
            }
            set++;
            details.add(supplier.getName() + " -> " + alias + " (d'apres le " + source + ")");
        }

        log.info("Alias generation ({}): {} considered, {} set, {} already set, {} failed",
            dryRun ? "dry run" : "applied", suppliers.size(), set, alreadySet, failed);

        return new SupplierAliasResponse(dryRun, suppliers.size(), set, alreadySet, failed, details);
    }

    /**
     * Lit un document de chaque fournisseur incomplet pour en tirer le numero
     * d'entreprise et la categorie.
     *
     * <p>{@code dryRun} n'evite que l'ecriture: les appels a l'IA ont lieu, et sont
     * donc factures. {@code limit} borne le nombre de fournisseurs traites, pour
     * essayer sur quelques fiches avant de lancer le lot complet.
     *
     * <p>Volontairement hors transaction, contrairement au reste des services: un
     * appel reseau par fournisseur tiendrait une connexion ouverte plusieurs minutes
     * sur un lot complet. Chaque fiche est donc enregistree pour elle-meme, et une
     * interruption en cours de route conserve le travail deja fait.
     */
    public SupplierEnrichmentResponse enrichFromDocuments(boolean dryRun, Integer limit) {
        List<String> details = new ArrayList<>();
        int analysed = 0;
        int enterpriseNumbersFilled = 0;
        int numbersRejected = 0;
        int categoriesFilled = 0;
        int withoutDocument = 0;
        int failed = 0;

        List<Supplier> incomplete = sortedByName().stream()
            .filter(s -> s.getEnterpriseNumber() == null || s.getCategory() == null)
            .toList();

        List<Supplier> batch = limit == null || limit >= incomplete.size()
            ? incomplete
            : incomplete.subList(0, limit);

        for (Supplier supplier : batch) {
            Invoice document = firstReadableDocument(supplier);
            if (document == null) {
                withoutDocument++;
                details.add(supplier.getName() + " : aucun document rattache, non analyse");
                continue;
            }

            SupplierAiData data;
            String fileName = Path.of(document.getFilePath()).getFileName().toString();
            try {
                byte[] content = Files.readAllBytes(Path.of(document.getFilePath()));
                data = extractionService.extractSupplierData(
                    new InMemoryMultipartFile("file", fileName,
                        MimeTypes.forFileName(fileName), content),
                    supplier.getName());
            } catch (Exception e) {
                failed++;
                details.add(supplier.getName() + " : lecture de '" + fileName + "' impossible ("
                    + e.getMessage() + ")");
                continue;
            }
            analysed++;

            List<String> applied = new ArrayList<>();
            if (supplier.getEnterpriseNumber() == null && data.enterpriseNumber() != null) {
                String number = VatUtils.formatEnterpriseNumber(data.enterpriseNumber());
                if (number == null) {
                    numbersRejected++;
                    details.add(supplier.getName() + " : numero '" + data.enterpriseNumber()
                        + "' ignore, ce n'est pas un numero d'entreprise belge (lu dans '"
                        + fileName + "')");
                } else {
                    if (!dryRun) supplier.setEnterpriseNumber(number);
                    enterpriseNumbersFilled++;
                    applied.add("n° " + number);
                }
            }
            if (supplier.getCategory() == null && data.category() != null) {
                if (!dryRun) supplier.setCategory(data.category());
                categoriesFilled++;
                applied.add("categorie " + data.category());
            }

            if (applied.isEmpty()) {
                details.add(supplier.getName() + " : rien trouve dans '" + fileName + "'");
                continue;
            }

            if (!dryRun) {
                supplierRepository.save(supplier);
            }
            details.add(supplier.getName() + " -> " + String.join(", ", applied)
                + " (d'apres '" + fileName + "')");
        }

        log.info("Supplier enrichment ({}): {} considered, {} analysed, {} numbers, "
                + "{} numbers rejected, {} categories, {} without document, {} failed",
            dryRun ? "dry run" : "applied", batch.size(), analysed, enterpriseNumbersFilled,
            numbersRejected, categoriesFilled, withoutDocument, failed);

        return new SupplierEnrichmentResponse(dryRun, batch.size(), analysed,
            enterpriseNumbersFilled, numbersRejected, categoriesFilled, withoutDocument,
            failed, details);
    }

    /**
     * Premier document effectivement lisible parmi les factures du fournisseur.
     * Un filePath peut pointer vers un fichier absent — l'adoption ne verifie que
     * le nom — ou vers un format que l'IA n'accepte pas.
     */
    private Invoice firstReadableDocument(Supplier supplier) {
        return invoiceRepository
            .findBySupplierIdAndFilePathIsNotNullOrderByYearDescNumberDesc(supplier.getId())
            .stream()
            .filter(invoice -> {
                Path path = Path.of(invoice.getFilePath());
                return Files.isReadable(path) && MimeTypes.isSupported(path.getFileName().toString());
            })
            .findFirst()
            .orElse(null);
    }

    /** Ordre stable, pour que deux executions produisent des rapports comparables. */
    private List<Supplier> sortedByName() {
        return supplierRepository.findAll().stream()
            .sorted(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }
}
