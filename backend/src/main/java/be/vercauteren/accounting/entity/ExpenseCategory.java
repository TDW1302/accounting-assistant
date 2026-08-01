package be.vercauteren.accounting.entity;

public enum ExpenseCategory {
    RESTAURANT,
    TRANSPORT,
    PARKING,
    CARBURANT,
    CONSOMMABLE,
    INFORMATIQUE,
    TELECOM,
    ABONNEMENT,
    ASSURANCE,
    LOYER,
    HONORAIRES,
    MARKETING,
    /** Prelevements: TVA, impots, precompte, taxe de circulation. */
    TAXES,
    /** Frais rendus par une administration: enregistrement d'un bail, depot BNB, greffe. */
    ADMINISTRATIF,
    AUTRE
}
