package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.Invoice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByYearOrderByNumberAscSubNumberAsc(Integer year);

    Optional<Invoice> findFirstByYearOrderByNumberDesc(Integer year);

    boolean existsByFalcoDocumentId(String falcoDocumentId);

    List<Invoice> findByFalcoDocumentIdIn(List<String> falcoDocumentIds);
}
