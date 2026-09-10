package be.vercauteren.accounting.entity;

/**
 * Rythme d'une depense contractuelle. Se projette sur {@link DateScope}, qui
 * porte deja la meme notion cote facture et sert a nommer les fichiers.
 */
public enum Periodicity {

    MONTHLY(DateScope.MONTHLY),
    QUARTERLY(DateScope.QUARTERLY),
    YEARLY(DateScope.YEARLY);

    private final DateScope dateScope;

    Periodicity(DateScope dateScope) {
        this.dateScope = dateScope;
    }

    public DateScope toDateScope() {
        return dateScope;
    }

    /** Nombre de mois entre deux echeances. */
    public int months() {
        return switch (this) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case YEARLY -> 12;
        };
    }
}
