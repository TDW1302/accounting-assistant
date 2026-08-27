package be.vercauteren.accounting.entity;

/**
 * Par ou la facture est entree dans l'application. Sert d'abord a exprimer en
 * base la regle d'auteur: toute facture porte son createdBy, sauf celles reprises
 * de l'Excel, qui n'ont ete encodees par personne ici.
 */
public enum InvoiceSource {

    /** Saisie a l'ecran par un utilisateur. */
    MANUAL,

    /** Importee depuis un document Peppol recu chez Falco. */
    PEPPOL,

    /** Creee par le scan de la boite de depot, planifie ou lance a la main. */
    INBOX,

    /**
     * Reprise du fichier Excel d'origine. Seul cas ou createdBy est nul: ces
     * factures preexistent a l'application, les attribuer a qui a lance l'import
     * en ferait son auteur a tort.
     */
    EXCEL_IMPORT,

    /**
     * Anterieure a la migration V5, origine indeterminee. Jamais ecrite par
     * l'application: son seul role est de laisser passer les lignes sans auteur
     * deja en base, qu'on ne peut plus rattacher a un import ou a un scan.
     */
    LEGACY
}
