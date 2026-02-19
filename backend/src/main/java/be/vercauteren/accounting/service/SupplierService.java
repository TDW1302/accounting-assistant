package be.vercauteren.accounting.service;

import be.vercauteren.accounting.dto.SupplierRequest;
import be.vercauteren.accounting.dto.SupplierResponse;
import be.vercauteren.accounting.entity.Supplier;
import be.vercauteren.accounting.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<SupplierResponse> findAll() {
        return supplierRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    public SupplierResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = Supplier.builder()
            .name(request.name())
            .alias(request.alias())
            .enterpriseNumber(request.enterpriseNumber())
            .build();
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = getOrThrow(id);
        supplier.setName(request.name());
        supplier.setAlias(request.alias());
        supplier.setEnterpriseNumber(request.enterpriseNumber());
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(Long id) {
        if (!supplierRepository.existsById(id)) {
            throw new EntityNotFoundException("Supplier not found: " + id);
        }
        supplierRepository.deleteById(id);
    }

    Supplier getOrThrow(Long id) {
        return supplierRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Supplier not found: " + id));
    }

    SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
            supplier.getId(),
            supplier.getName(),
            supplier.getAlias(),
            supplier.getEnterpriseNumber()
        );
    }
}
