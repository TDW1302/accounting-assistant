package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.InvoiceSeries;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByYearOrderByNumberAscSubNumberAsc(Integer year);

    @Query("SELECT DISTINCT i.year FROM Invoice i ORDER BY i.year DESC")
    List<Integer> findDistinctYears();

    long countByYear(Integer year);

    /** Chaque serie a son propre compteur annuel: 001 et D001 coexistent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invoice> findFirstBySeriesAndYearOrderByNumberDesc(InvoiceSeries series, Integer year);

    List<Invoice> findBySeriesAndYearAndNumberOrderBySubNumberAsc(
        InvoiceSeries series, Integer year, Integer number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invoice> findFirstBySeriesAndYearAndNumberOrderBySubNumberDesc(
        InvoiceSeries series, Integer year, Integer number);

    boolean existsByFalcoDocumentId(String falcoDocumentId);

    List<Invoice> findByFalcoDocumentIdIn(List<String> falcoDocumentIds);

    boolean existsBySupplierId(Long supplierId);

    // Rapprochement par numero de fichier et reprise de l'Excel: les deux ne
    // portent que sur le facturier documente, d'ou la serie explicite.

    boolean existsBySeriesAndYearAndNumberAndSubNumberIsNull(
        InvoiceSeries series, Integer year, Integer number);

    boolean existsBySeriesAndYearAndNumberAndSubNumber(
        InvoiceSeries series, Integer year, Integer number, Integer subNumber);

    Optional<Invoice> findBySeriesAndYearAndNumberAndSubNumberIsNull(
        InvoiceSeries series, Integer year, Integer number);

    Optional<Invoice> findBySeriesAndYearAndNumberAndSubNumber(
        InvoiceSeries series, Integer year, Integer number, Integer subNumber);

    List<Invoice> findBySupplierIdAndReceptionDateAndFilePathIsNullAndPeppolFalse(Long supplierId, LocalDate receptionDate);

    /**
     * Candidates for reconciling an inbox file: same supplier, no document yet.
     * Restreint au facturier documente: une depense contractuelle n'attend aucun
     * fichier, et un PDF depose ne doit donc jamais s'y rattacher.
     */
    List<Invoice> findBySupplierIdAndSeriesAndFilePathIsNull(Long supplierId, InvoiceSeries series);

    List<Invoice> findBySupplierIdAndFilePathIsNotNullOrderByYearDescNumberDesc(Long supplierId);

    List<Invoice> findBySupplierId(Long supplierId);

    int countBySupplierId(Long supplierId);

    int countBySupplierIdAndFilePathIsNotNull(Long supplierId);

    List<Invoice> findByRecurringExpenseIdOrderByScopeDateAsc(Long recurringExpenseId);

    boolean existsByRecurringExpenseId(Long recurringExpenseId);

    boolean existsBySupplierIdAndSeries(Long supplierId, InvoiceSeries series);

    /**
     * Lignes rattachables a un modele recurrent: meme fournisseur, sans document
     * et pas deja rattachees. Le document exclut, parce que rattacher fixe la
     * portee de date, donc le nom de fichier: une ligne qui en a un serait a
     * renommer, ce que le rattachement n'a pas a faire.
     */
    List<Invoice> findBySupplierIdAndFilePathIsNullAndRecurringExpenseIsNullOrderByYearDescNumberDesc(
        Long supplierId);
}
