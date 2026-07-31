package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.SupplierDuplicatesResponse;
import be.vercauteren.accounting.dto.SupplierMergeResponse;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.InvoiceRepository;
import be.vercauteren.accounting.repository.SupplierRepository;
import be.vercauteren.accounting.util.NameSimilarity;
import be.vercauteren.accounting.util.VatUtils;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rapproche puis fusionne les fiches fournisseurs en double, heritees de la saisie
 * a la main dans l'Excel.
 *
 * <p>Detection et fusion sont deux operations distinctes, et la premiere ne declenche
 * jamais la seconde: deux noms proches peuvent designer deux societes reelles, et
 * fusionner a tort deplace des factures sans trace de l'etat anterieur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierDeduplicationService {

    private final SupplierRepository supplierRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * Couples de fiches susceptibles de designer la meme societe.
     *
     * <p>Emet des couples, pas des groupes: un triplet se traite en deux fusions
     * successives, la seconde apparaissant a la relance. Cela evite d'avoir a
     * arbitrer une transitivite ("A proche de B, B proche de C") qui n'a rien
     * d'evident quand les trois noms different.
     */
    public SupplierDuplicatesResponse findDuplicates() {
        List<Supplier> suppliers = supplierRepository.findAll().stream()
            .sorted(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        List<SupplierDuplicatesResponse.Pair> pairs = new ArrayList<>();
        List<String> withoutDocument = new ArrayList<>();

        for (Supplier supplier : suppliers) {
            int invoices = invoiceRepository.countBySupplierId(supplier.getId());
            if (invoices > 0 && invoiceRepository.countBySupplierIdAndFilePathIsNotNull(supplier.getId()) == 0) {
                withoutDocument.add(supplier.getName() + " (" + invoices + " factures, aucun document)");
            }
        }

        // O(n²) assume: le nombre de fournisseurs se compte en dizaines.
        for (int i = 0; i < suppliers.size(); i++) {
            for (int j = i + 1; j < suppliers.size(); j++) {
                Supplier left = suppliers.get(i);
                Supplier right = suppliers.get(j);

                String reason = compare(left, right);
                if (reason != null) {
                    pairs.add(new SupplierDuplicatesResponse.Pair(
                        reason, toCandidate(left), toCandidate(right)));
                }
            }
        }

        log.info("Duplicate detection: {} suppliers, {} pairs, {} without any document",
            suppliers.size(), pairs.size(), withoutDocument.size());

        return new SupplierDuplicatesResponse(suppliers.size(), pairs, withoutDocument);
    }

    /**
     * Transfere les factures de {@code removeId} vers {@code keepId}, complete les
     * champs vides de la fiche conservee, puis supprime l'autre.
     *
     * <p>La fiche conservee garde ses propres valeurs: la fusion ne comble que ses
     * trous, elle n'arbitre pas entre deux valeurs renseignees.
     */
    @Transactional
    public SupplierMergeResponse merge(Long keepId, Long removeId) {
        if (keepId.equals(removeId)) {
            throw new IllegalArgumentException("Cannot merge a supplier into itself");
        }

        Supplier keep = getOrThrow(keepId);
        Supplier remove = getOrThrow(removeId);

        List<Invoice> invoices = invoiceRepository.findBySupplierId(removeId);
        for (Invoice invoice : invoices) {
            invoice.setSupplier(keep);
        }
        invoiceRepository.saveAll(invoices);

        List<String> fieldsFilled = new ArrayList<>();
        if (isBlank(keep.getAlias()) && !isBlank(remove.getAlias())) {
            keep.setAlias(remove.getAlias());
            fieldsFilled.add("alias = " + remove.getAlias());
        }
        if (isBlank(keep.getEnterpriseNumber()) && !isBlank(remove.getEnterpriseNumber())) {
            keep.setEnterpriseNumber(remove.getEnterpriseNumber());
            fieldsFilled.add("numero d'entreprise = " + remove.getEnterpriseNumber());
        }
        if (keep.getCategory() == null && remove.getCategory() != null) {
            keep.setCategory(remove.getCategory());
            fieldsFilled.add("categorie = " + remove.getCategory());
        }
        if (keep.getDefaultDateScope() == null && remove.getDefaultDateScope() != null) {
            keep.setDefaultDateScope(remove.getDefaultDateScope());
            fieldsFilled.add("portee par defaut = " + remove.getDefaultDateScope());
        }
        supplierRepository.save(keep);

        // Les factures viennent d'etre reaffectees, mais le flush ne se fera qu'au
        // commit: la suppression doit attendre, sinon la contrainte de cle etrangere
        // porte encore sur l'ancienne fiche.
        supplierRepository.flush();
        supplierRepository.delete(remove);

        log.info("Merged supplier '{}' ({}) into '{}' ({}): {} invoices reassigned",
            remove.getName(), removeId, keep.getName(), keepId, invoices.size());

        return new SupplierMergeResponse(
            keep.getId(), keep.getName(), remove.getName(), invoices.size(), fieldsFilled);
    }

    /**
     * Deux numeros d'entreprise identiques designent la meme societe, quels que
     * soient les noms: c'est le signal le plus sur dont on dispose.
     *
     * <p>Deux numeros differents, en revanche, ne suffisent pas a ecarter un couple
     * dont les noms se ressemblent. Ces numeros sont lus par l'IA sur les documents,
     * et une facture porte celui de l'emetteur comme celui du destinataire: une
     * seule lecture erronee ferait disparaitre un doublon reel de la liste, sans un
     * mot. Le couple est donc conserve, avec la divergence signalee.
     */
    private String compare(Supplier left, Supplier right) {
        String leftVat = VatUtils.normalizeVat(left.getEnterpriseNumber());
        String rightVat = VatUtils.normalizeVat(right.getEnterpriseNumber());
        boolean bothNumbered = leftVat != null && rightVat != null;

        if (bothNumbered && leftVat.equals(rightVat)) {
            return "meme numero d'entreprise";
        }

        String byName = NameSimilarity.compareNames(left.getName(), right.getName());
        if (byName == null) {
            return null;
        }

        return bothNumbered
            ? byName + ", mais numeros d'entreprise differents"
            : byName;
    }

    private SupplierDuplicatesResponse.Candidate toCandidate(Supplier supplier) {
        return new SupplierDuplicatesResponse.Candidate(
            supplier.getId(),
            supplier.getName(),
            supplier.getAlias(),
            supplier.getEnterpriseNumber(),
            supplier.getCategory(),
            invoiceRepository.countBySupplierId(supplier.getId()),
            invoiceRepository.countBySupplierIdAndFilePathIsNotNull(supplier.getId()));
    }

    private Supplier getOrThrow(Long id) {
        return supplierRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Supplier not found: " + id));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
