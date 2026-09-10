package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.RecurringExpense;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    @Query("SELECT r FROM RecurringExpense r ORDER BY r.active DESC, r.label ASC")
    List<RecurringExpense> findAllOrderByActiveThenLabel();

    List<RecurringExpense> findByActiveTrueOrderByLabelAsc();

    boolean existsBySupplierId(Long supplierId);

    List<RecurringExpense> findBySupplierId(Long supplierId);
}
