package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.Invoice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByYearOrderByNumberAscSubNumberAsc(Integer year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invoice> findFirstByYearOrderByNumberDesc(Integer year);

    boolean existsByFalcoDocumentId(String falcoDocumentId);

    List<Invoice> findByFalcoDocumentIdIn(List<String> falcoDocumentIds);

    boolean existsBySupplierId(Long supplierId);
}
