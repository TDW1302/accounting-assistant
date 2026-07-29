package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.Invoice;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByYearOrderByNumberAscSubNumberAsc(Integer year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invoice> findFirstByYearOrderByNumberDesc(Integer year);

    List<Invoice> findByYearAndNumberOrderBySubNumberAsc(Integer year, Integer number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invoice> findFirstByYearAndNumberOrderBySubNumberDesc(Integer year, Integer number);

    boolean existsByFalcoDocumentId(String falcoDocumentId);

    List<Invoice> findByFalcoDocumentIdIn(List<String> falcoDocumentIds);

    boolean existsBySupplierId(Long supplierId);

    boolean existsByYearAndNumberAndSubNumberIsNull(Integer year, Integer number);

    List<Invoice> findBySupplierIdAndReceptionDateAndFilePathIsNullAndPeppolFalse(Long supplierId, LocalDate receptionDate);
}
