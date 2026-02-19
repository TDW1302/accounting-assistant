package be.vercauteren.accounting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"\"year\"", "number", "sub_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Integer number;

    private Integer subNumber;

    @NotNull
    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceType type;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    private BigDecimal amountIncVat;

    private BigDecimal amountExVat;

    private BigDecimal vatAmount;

    @NotNull
    @Column(nullable = false)
    private LocalDate receptionDate;

    private LocalDate paymentDate;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean peppol = false;

    private String comment;

    private String filePath;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DateScope dateScope = DateScope.NONE;

    private LocalDate scopeDate;

    private String fileDetail;
}
