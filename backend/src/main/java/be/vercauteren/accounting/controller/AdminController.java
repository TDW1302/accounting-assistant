package be.vercauteren.accounting.controller;

import be.vercauteren.accounting.dto.AdminStatsResponse;
import be.vercauteren.accounting.dto.SupplierAliasResponse;
import be.vercauteren.accounting.dto.SupplierDuplicatesResponse;
import be.vercauteren.accounting.dto.SupplierEnrichmentResponse;
import be.vercauteren.accounting.dto.SupplierMergeResponse;
import be.vercauteren.accounting.service.AdminService;
import be.vercauteren.accounting.service.SupplierDeduplicationService;
import be.vercauteren.accounting.service.SupplierEnrichmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SupplierEnrichmentService supplierEnrichmentService;
    private final SupplierDeduplicationService supplierDeduplicationService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminService.getStats();
    }

    @DeleteMapping("/invoices")
    public ResponseEntity<Void> deleteInvoicesByYear(@RequestParam Integer year) {
        adminService.deleteInvoicesByYear(year);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/suppliers")
    public ResponseEntity<Void> deleteAllSuppliers() {
        adminService.deleteAllSuppliers();
        return ResponseEntity.noContent().build();
    }

    /**
     * Renseigne l'alias des fournisseurs qui n'en ont pas, sans appel externe.
     * Appeler avec dryRun=true pour obtenir les alias proposes sans les enregistrer.
     */
    @PostMapping("/suppliers/aliases")
    public SupplierAliasResponse generateAliases(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return supplierEnrichmentService.generateAliases(dryRun);
    }

    /**
     * Complete numero d'entreprise et categorie en faisant lire un document de
     * chaque fournisseur incomplet par l'IA.
     *
     * <p>Attention: dryRun=true n'evite que l'enregistrement, les appels a l'IA ont
     * lieu et sont factures. Utiliser limit pour essayer sur un echantillon d'abord.
     */
    @PostMapping("/suppliers/enrich")
    public SupplierEnrichmentResponse enrichSuppliers(
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(required = false) Integer limit) {
        return supplierEnrichmentService.enrichFromDocuments(dryRun, limit);
    }

    /** Couples de fiches susceptibles de designer la meme societe. Ne modifie rien. */
    @GetMapping("/suppliers/duplicates")
    public SupplierDuplicatesResponse findDuplicateSuppliers() {
        return supplierDeduplicationService.findDuplicates();
    }

    /**
     * Transfere les factures de removeId vers keepId, complete les champs vides de
     * la fiche conservee, puis supprime l'autre. Irreversible.
     */
    @PostMapping("/suppliers/merge")
    public SupplierMergeResponse mergeSuppliers(
            @RequestParam Long keepId,
            @RequestParam Long removeId) {
        return supplierDeduplicationService.merge(keepId, removeId);
    }
}
