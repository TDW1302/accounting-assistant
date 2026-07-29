package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByNameIgnoreCase(String name);

    List<Supplier> findByCategory(ExpenseCategory category);
}
