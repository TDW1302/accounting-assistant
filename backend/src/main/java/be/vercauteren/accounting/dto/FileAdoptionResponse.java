package be.vercauteren.accounting.dto;

import java.util.List;

/**
 * Bilan du rattachement des fichiers deja presents sur le disque.
 *
 * @param dryRun         true si aucune modification n'a ete enregistree
 * @param filesScanned   fichiers examines dans les dossiers d'annee
 * @param linked         factures dont le filePath a ete renseigne
 * @param alreadyLinked  factures qui pointaient deja vers un fichier
 * @param ambiguous      numeros portes par plusieurs fichiers, laisses de cote
 * @param withoutInvoice numeros presents sur disque mais absents en base
 * @param unnamed        fichiers ne suivant pas la convention NNN[.sub]-
 * @param details        une ligne par cas necessitant une decision humaine
 */
public record FileAdoptionResponse(
    boolean dryRun,
    int filesScanned,
    int linked,
    int alreadyLinked,
    int ambiguous,
    int withoutInvoice,
    int unnamed,
    List<String> details
) {}
