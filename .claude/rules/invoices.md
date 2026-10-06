---
globs: "**/invoice*,**/Invoice*,**/FileStorage*,**/FileNameGenerator*"
---

# Invoices — Data Model, File Naming & Storage

## Invoice entity
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| number | Integer | Sequential number (001, 002...) |
| subNumber | Integer (nullable) | Sub-number (1, 2 for 008.1, 008.2) |
| year | Integer | Accounting year |
| type | Enum (PURCHASE, SALE) | Purchase or sale |
| supplier | FK → Supplier | The related party |
| amountIncVat | BigDecimal (nullable) | Amount including VAT |
| amountExVat | BigDecimal (nullable) | Amount excluding VAT |
| vatAmount | BigDecimal (nullable) | VAT amount |
| receptionDate | LocalDate | Reception date |
| paymentDate | LocalDate (nullable) | Payment date |
| peppol | Boolean | Received via Peppol |
| comment | String (nullable) | Comments |
| filePath | String (nullable) | File path on disk |
| dateScope | Enum (DAILY, MONTHLY, QUARTERLY, YEARLY, NONE) | Date scope for naming |
| scopeDate | LocalDate (nullable) | Reference date for naming |
| fileDetail | String (nullable) | Optional filename detail (e.g. "PneuHiver") |
| createdBy | FK → User (nullable) | Creator |
| falcoDocumentId | String (nullable, unique) | Peppol import dedup |

**Missing document** = filePath is null AND peppol is false. L'écran `/invoices/missing` permet d'inclure les Peppol sans fichier (`includePeppol=true`) pour charger leur document en local.

## File naming
Format: `NNN[.sub]-[date]-Alias[-detail].pdf`
- Number zero-padded to 3 digits; sub-numbers: 008.1, 008.2
- Date format by scope:
  - DAILY (one-time: restaurant, single purchase): YYMMDD (e.g. 250114)
  - MONTHLY (subscription): YYMM (e.g. 2501)
  - QUARTERLY: YYYYQ# (e.g. 2025Q2)
  - YEARLY (insurance, annual subscription): YYYY (e.g. 2025)
  - NONE: omitted
- Supplier alias used (e.g. "CafeDeLaPoste")
- Examples: `001-Auto5-PneuHiver.pdf`, `003-2412-OliverJames.PDF`, `008.1-2601-AmazonUgreenHDMI.pdf`

## File storage
- `FileNameGenerator` builds the filename; `FileStorageService` stores/deletes on disk
- Directory: `{app.upload.directory}/{year}/{file.pdf}`
- Upload endpoint: `POST /api/invoices/{id}/upload` (after create/update, since filename depends on number)
- File type validation: extension + content type whitelist
- File+DB transaction compensation on failure

## Boîte de dépôt (inbox)
- Rapprochement d'un fichier déposé : fournisseur + montant TTC identique + date de réception à ±`app.inbox.match-window-days` (7 par défaut), parmi les factures sans fichier, Peppol comprises
- À défaut de montant lu par l'IA : fournisseur + date de réception exacte, hors Peppol (ancien comportement)
- Sans candidat, une nouvelle facture est créée — un document déjà rattaché ailleurs produit donc toujours un doublon

## AI extraction
- PDF text via PDFBox → Claude API with supplier list → JSON → form pre-fill
- `InvoiceExtractionService`: supplier matching by enterprise number, then name/alias
- Best-effort: failures silent, scanned/image PDFs → graceful fallback
- Config: `app.anthropic.api-key`, `app.anthropic.model`

## Business rules
- Year change prevented on update
- Delete: creator or ADMIN only
- Auto-numbering: SERIALIZABLE isolation + retry via TransactionTemplate
