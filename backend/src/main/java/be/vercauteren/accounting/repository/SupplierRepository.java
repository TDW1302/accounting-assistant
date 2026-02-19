package be.vercauteren.accounting.repository;

import be.vercauteren.accounting.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
