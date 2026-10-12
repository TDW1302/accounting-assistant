package be.vercauteren.accounting.entity;

/**
 * Serie de numerotation d'une ligne du facturier. Chaque serie a son propre
 * compteur annuel, qui repart a 1 comme dans l'Excel d'origine.
 */
public enum InvoiceSeries {

    /**
     * Le facturier documente: une ligne, un document, numerote 001, 002...
     * C'est cette serie qui doit rester continue, puisque ses numeros nomment
     * les PDF envoyes a la comptabilite.
     */
    INVOICE,

    /**
     * Depense sans document attendu — loyer, frais bancaires, PLCI — numerotee
     * D001, D002... Lui donner un numero de la serie INVOICE trouerait celle-ci
     * d'entrees auxquelles aucun fichier ne correspondra jamais.
     */
    EXPENSE
}
