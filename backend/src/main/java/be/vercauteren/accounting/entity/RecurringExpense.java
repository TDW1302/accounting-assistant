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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Depense contractuelle recurrente sans facture: loyer, frais bancaires, PLCI.
 * Le modele decrit ce qui est du et a quel rythme; les echeances elles-memes
 * sont des lignes de la serie {@link InvoiceSeries#EXPENSE}, creees a la demande.
 *
 * <p>Le montant vit sur le modele et est recopie sur chaque echeance au moment
 * de l'engendrer. Une indexation de loyer se traite donc en modifiant le modele:
 * les echeances deja creees gardent le montant qui etait alors du.
 */
@Entity
@Table(name = "recurring_expense")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ce que la depense est, en clair: "Loyer bureau", "Frais bancaires BNP". */
    @NotBlank
    @Column(nullable = false)
    private String label;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    private BigDecimal amountIncVat;

    private BigDecimal amountExVat;

    private BigDecimal vatAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Periodicity periodicity;

    /**
     * Premiere echeance. Porte aussi le jour d'echeance et, pour un rythme
     * trimestriel ou annuel, le mois d'ancrage: les suivantes s'en deduisent
     * par addition, sans champ separe a tenir coherent.
     */
    @NotNull
    @Column(nullable = false)
    private LocalDate startDate;

    /** Derniere echeance possible — fin de bail, resiliation. Nul si sans terme. */
    private LocalDate endDate;

    /**
     * Domiciliation ou ordre permanent: le paiement tombe le jour de l'echeance.
     * Evite de repasser derriere chaque mois pour saisir une date connue d'avance.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean paidOnDueDate = true;

    private String comment;

    private String fileDetail;

    /**
     * Un modele arrete n'engendre plus rien mais reste consultable, et ses
     * echeances passees gardent leur lien. Preferable a la suppression, qui
     * n'est possible que tant qu'aucune echeance n'a ete creee.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;
}
