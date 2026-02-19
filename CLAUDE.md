# accounting-assistant

## Description
Application personnelle d'aide à la comptabilité. Remplace un fichier Excel utilisé pour gérer les factures d'achat et de vente.

## Workflow actuel
1. Réception des factures (certaines via Peppol, d'autres manuellement)
2. Enregistrement dans un Excel avec : fournisseur, montant, date de réception, date de paiement, reçu via Peppol (oui/non), commentaires
3. Numérotation ordonnée des factures (achat et vente dans un seul ordre commun)
4. Renommage des fichiers PDF selon l'ordre de l'Excel
5. Upload dans Falco (logiciel comptable)

## Structure Excel actuelle (Facturier.xlsx)
- Une feuille par année (2024, 2025, 2026)
- Numérotation qui redémarre à 1 chaque année
- Colonnes (version la plus complète, 2026) :
  - Numéro (ordre séquentiel)
  - Fournisseur
  - Montant
  - Date réception
  - Date paiement
  - Peppol ("V" si reçu via Peppol)
  - Commentaires
- Distinction achat/vente : les factures "Oliver James" (ou "Facture Oliver James") sont des ventes, le reste sont des achats
- Les ventes sont mises en évidence par une coloration verte (#C6EFCE) sur la cellule du montant
- Fichier source : C:\Users\verca\OneDrive\Documents\Facturier.xlsx

## Décisions

### Clients / Fournisseurs
- Oliver James est le seul client actuel, mais l'application doit prévoir plusieurs clients à l'avenir
- Un même tiers (Supplier) peut être fournisseur dans une transaction et client dans une autre
- Le rôle est déterminé par le type de la facture (PURCHASE/SALE), pas par le tiers

### Type d'application
- Application web (Java backend + Angular frontend)

### Technologies
- **Backend** : Java 25 (OpenJDK 25.0.2), Spring Boot 3.5.2, Gradle
  - Dépendances : Spring Web, Spring Data JPA, H2, Lombok, Validation
  - Package : be.vercauteren.accounting
- **Frontend** : Angular 21.1, Node.js 24.13.1 (scoop: nodejs-lts), TypeScript, SCSS
- **Note** : Node 24 est installé via scoop (`/c/Users/verca/scoop/apps/nodejs-lts/current`), le PATH doit le prioriser sur le Node 18 global

### Renommage des fichiers
- Format : `NNN-[date/période]-Fournisseur[-détail].pdf`
- Numéro sur 3 chiffres avec zéros initiaux (001, 002, 003...)
- Sous-numéros possibles pour factures multiples : 008.1, 008.2 (ex: 2 commandes Amazon)
- La date/période dépend de la portée de la facture :
  - Ponctuelle (restaurant, achat unique) : YYMMDD (ex: 250114)
  - Mensuelle (abonnement) : YYMM (ex: 2501)
  - Trimestrielle : YYYYQ# (ex: 2025Q2)
  - Annuelle (assurance, abonnement annuel) : YYYY (ex: 2025)
  - Absente dans certains cas
- L'alias du Supplier est utilisé dans le nom du fichier (ex: "Café de la poste" → alias "CafeDeLaPoste")
- Détail optionnel après le fournisseur (ex: PneuHiver, MachineACafe, UgreenHDMI)
- Exemples : `001-Auto5-PneuHiver.pdf`, `003-2412-OliverJames.PDF`, `008.1-2601-AmazonUgreenHDMI.pdf`

### Stockage des fichiers
- Upload direct de fichiers PDF depuis le formulaire de facture
- Les fichiers sont renommés automatiquement via `FileNameGenerator` et sauvegardés dans un répertoire configurable (`app.upload.directory` dans `application.properties`)
- Organisation en sous-dossiers par année : `{uploadDir}/{année}/{fichier.pdf}`
- Endpoint dédié `POST /api/invoices/{id}/upload` (l'upload se fait après la création/modification car le nom dépend du numéro attribué)
- `FileStorageService` gère le stockage/suppression des fichiers sur disque
- Le frontend chaîne automatiquement l'upload après le save (create/update → upload via `switchMap`)
- La base de données stocke le chemin complet vers le fichier dans `filePath`
- Ancien stockage NAS (référence historique) :
  - Avant 2026 : \\NAS\homes\VITe\{année}\Done\
  - Depuis 2026 : \\NAS\homes\VITe\{année}\ (plus de sous-dossier Done)

### Base de données
- Base de données légère (SQLite ou H2) — pas de serveur DB séparé

### Infrastructure
- NAS actuel : Synology DS218play (ARM, 1 Go RAM, pas de Docker) — trop limité pour héberger l'app
- NAS envisagé : Ugreen DXP4800 Plus (Intel x86, 8 Go DDR5, Docker supporté) — capable d'héberger l'app
- Décision d'hébergement à confirmer selon le NAS final
- En attendant, développement et tests en local sur PC

### Devise
- Toujours en EUR, pas besoin de multi-devise

### Périmètre v1
- Remplacer l'Excel : saisie et gestion des factures (CRUD)
- Automatiser le renommage des fichiers PDF selon la numérotation
- Upload de documents PDF lors de l'encodage de factures (renommage automatique, stockage par année)
- Gestion par année avec numérotation séquentielle redémarrant à 1

### Périmètre futur (hors v1)
- Upload automatisé vers Falco (logiciel comptable)
- Import automatique de factures depuis Gmail (pièces jointes PDF)
- Stratégie de backup des fichiers PDF (backup NAS, copie vers périphérique réseau, etc. — à définir)
- Autres automatisations à définir

## Modèle de données

### Invoice
| Champ | Type | Description |
|---|---|---|
| id | Long | PK auto-générée |
| number | Integer | Numéro d'ordre (001, 002...) |
| subNumber | Integer (nullable) | Sous-numéro (1, 2 pour 008.1, 008.2) |
| year | Integer | Année comptable |
| type | Enum (PURCHASE, SALE) | Achat ou vente |
| supplier | FK → Supplier | Le tiers concerné |
| amountIncVat | BigDecimal (nullable) | Montant TTC |
| amountExVat | BigDecimal (nullable) | Montant HTVA |
| vatAmount | BigDecimal (nullable) | Montant TVA |
| receptionDate | LocalDate | Date de réception |
| paymentDate | LocalDate (nullable) | Date de paiement |
| peppol | Boolean | Reçu via Peppol |
| comment | String (nullable) | Commentaires |
| filePath | String (nullable) | Chemin fichier sur le NAS |
| dateScope | Enum (DAILY, MONTHLY, QUARTERLY, YEARLY, NONE) | Portée de la date pour le nommage |
| scopeDate | LocalDate (nullable) | Date de référence pour le nommage (saisie manuelle, dépend du contenu de la facture) |
| fileDetail | String (nullable) | Détail optionnel pour le nom du fichier (ex: "PneuHiver", "MachineACafe") |

Document manquant = filePath est null ET peppol est false.

Nom de fichier généré = `{number padded 3}[.{subNumber}]-[{scopeDate formaté selon dateScope}]-{supplier.alias}[-{fileDetail}].pdf`

### Supplier
| Champ | Type | Description |
|---|---|---|
| id | Long | PK auto-générée |
| name | String | Nom officiel (ex: "P&Partners", "Café de la poste") |
| alias | String (nullable) | Nom court pour le nommage fichier (ex: "PPartners", "CafeDeLaPoste") |
| enterpriseNumber | String (nullable) | Numéro d'entreprise BCE (format 0XXX.XXX.XXX, optionnel) |
