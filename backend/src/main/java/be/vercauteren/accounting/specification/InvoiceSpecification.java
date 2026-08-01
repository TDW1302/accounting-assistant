package be.vercauteren.accounting.specification;

import be.vercauteren.accounting.entity.ExpenseCategory;
import be.vercauteren.accounting.entity.Invoice;
import be.vercauteren.accounting.entity.Supplier;
import jakarta.persistence.criteria.Join;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceSpecification {

    public static Specification<Invoice> hasYear(Integer year) {
        return (root, query, cb) -> cb.equal(root.get("year"), year);
    }

    public static Specification<Invoice> hasSupplier(Long supplierId) {
        return (root, query, cb) -> cb.equal(root.get("supplier").get("id"), supplierId);
    }

    public static Specification<Invoice> hasCategory(ExpenseCategory category) {
        return (root, query, cb) -> cb.equal(root.get("supplier").get("category"), category);
    }

    /**
     * No file on disk. Les factures Peppol en sont exclues par defaut: leur document
     * reste chez Falco, leur absence de fichier n'est donc pas un oubli. Les inclure
     * sert a repasser derriere pour charger celles qu'on veut aussi en local.
     */
    public static Specification<Invoice> missingDocument(boolean includePeppol) {
        return (root, query, cb) -> includePeppol
            ? cb.isNull(root.get("filePath"))
            : cb.and(cb.isNull(root.get("filePath")), cb.isFalse(root.get("peppol")));
    }

    public static Specification<Invoice> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("amountIncVat"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("amountIncVat"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("amountIncVat"), max);
            }
        };
    }

    public static Specification<Invoice> receptionDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("receptionDate"), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("receptionDate"), from);
            } else {
                return cb.lessThanOrEqualTo(root.get("receptionDate"), to);
            }
        };
    }

    public static Specification<Invoice> keywordSearch(String keyword) {
        return (root, query, cb) -> {
            String escaped = keyword.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            Join<Invoice, Supplier> supplier = root.join("supplier");
            return cb.or(
                cb.like(cb.lower(root.get("comment")), pattern, '\\'),
                cb.like(cb.lower(supplier.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("fileDetail")), pattern, '\\')
            );
        };
    }
}
