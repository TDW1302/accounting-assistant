package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByNameIgnoreCase(String name);

    List<Supplier> findByCategory(ExpenseCategory category);

    @Query("select s from Supplier s order by lower(s.name)")
    List<Supplier> findAllOrderByName();

    @Query("select s from Supplier s where s.category = :category order by lower(s.name)")
    List<Supplier> findByCategoryOrderByName(ExpenseCategory category);
}
