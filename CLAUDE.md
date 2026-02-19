# accounting-assistant

## Description
Personal accounting helper application. Replaces an Excel file used to manage purchase and sale invoices.

## Current workflow
1. Receive invoices (some via Peppol, others manually)
2. Record in an Excel file with: supplier, amount, reception date, payment date, received via Peppol (yes/no), comments
3. Sequential numbering of invoices (purchases and sales share a single numbering sequence)
4. Rename PDF files according to the Excel ordering
5. Upload to Falco (accounting software)

## Current Excel structure (Facturier.xlsx)
- One sheet per year (2024, 2025, 2026)
- Numbering restarts at 1 each year
- Columns (most complete version, 2026):
  - Number (sequential order)
  - Supplier
  - Amount
  - Reception date
  - Payment date
  - Peppol ("V" if received via Peppol)
  - Comments
- Purchase/sale distinction: "Oliver James" (or "Facture Oliver James") invoices are sales, the rest are purchases
- Sales are highlighted with a green background (#C6EFCE) on the amount cell
- Source file: C:\Users\verca\OneDrive\Documents\Facturier.xlsx

## Decisions

### Clients / Suppliers
- Oliver James is the only current client, but the application should support multiple clients in the future
- The same party (Supplier) can be a supplier in one transaction and a client in another
- The role is determined by the invoice type (PURCHASE/SALE), not by the party

### Application type
- Web application (Java backend + Angular frontend)

### Technologies
- **Backend**: Java 25 (OpenJDK 25.0.2), Spring Boot 3.5.2, Gradle
  - Dependencies: Spring Web, Spring Data JPA, H2, Lombok, Validation
  - Package: be.vercauteren.accounting
- **Frontend**: Angular 21.1, Node.js 24.13.1 (scoop: nodejs-lts), TypeScript, SCSS
- **Note**: Node 24 is installed via scoop (`/c/Users/verca/scoop/apps/nodejs-lts/current`), PATH must prioritize it over the global Node 18

### File renaming
- Format: `NNN-[date/period]-Supplier[-detail].pdf`
- Number zero-padded to 3 digits (001, 002, 003...)
- Sub-numbers possible for multiple invoices: 008.1, 008.2 (e.g. 2 Amazon orders)
- The date/period depends on the invoice scope:
  - One-time (restaurant, single purchase): YYMMDD (e.g. 250114)
  - Monthly (subscription): YYMM (e.g. 2501)
  - Quarterly: YYYYQ# (e.g. 2025Q2)
  - Yearly (insurance, annual subscription): YYYY (e.g. 2025)
  - Absent in some cases
- The Supplier alias is used in the filename (e.g. "Cafe de la poste" -> alias "CafeDeLaPoste")
- Optional detail after the supplier (e.g. PneuHiver, MachineACafe, UgreenHDMI)
- Examples: `001-Auto5-PneuHiver.pdf`, `003-2412-OliverJames.PDF`, `008.1-2601-AmazonUgreenHDMI.pdf`

### File storage
- Direct PDF file upload from the invoice form
- Files are automatically renamed via `FileNameGenerator` and saved in a configurable directory (`app.upload.directory` in `application.properties`)
- Organized in subdirectories by year: `{uploadDir}/{year}/{file.pdf}`
- Dedicated endpoint `POST /api/invoices/{id}/upload` (upload happens after create/update since the filename depends on the assigned number)
- `FileStorageService` handles file storage/deletion on disk
- The frontend automatically chains the upload after save (create/update -> upload via `switchMap`)
- The database stores the full file path in `filePath`
- Legacy NAS storage (historical reference):
  - Before 2026: \\NAS\homes\VITe\{year}\Done\
  - Since 2026: \\NAS\homes\VITe\{year}\ (no more Done subfolder)

### Database
- Lightweight database (SQLite or H2) — no separate DB server

### Infrastructure
- Current NAS: Synology DS218play (ARM, 1 GB RAM, no Docker) — too limited to host the app
- Planned NAS: Ugreen DXP4800 Plus (Intel x86, 8 GB DDR5, Docker supported) — capable of hosting the app
- Hosting decision to be confirmed based on final NAS choice
- In the meantime, development and testing done locally on PC

### Currency
- Always EUR, no multi-currency needed

### v1 scope
- Replace the Excel: invoice entry and management (CRUD)
- Automate PDF file renaming based on numbering
- PDF document upload during invoice encoding (automatic renaming, storage by year)
- Year-based management with sequential numbering restarting at 1

### Future scope (beyond v1)
- Automated upload to Falco (accounting software)
- Automatic invoice import from Gmail (PDF attachments)
- PDF file backup strategy (NAS backup, copy to network device, etc. — TBD)
- Other automations TBD

## Data model

### Invoice
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| number | Integer | Sequential number (001, 002...) |
| subNumber | Integer (nullable) | Sub-number (1, 2 for 008.1, 008.2) |
| year | Integer | Accounting year |
| type | Enum (PURCHASE, SALE) | Purchase or sale |
| supplier | FK -> Supplier | The related party |
| amountIncVat | BigDecimal (nullable) | Amount including VAT |
| amountExVat | BigDecimal (nullable) | Amount excluding VAT |
| vatAmount | BigDecimal (nullable) | VAT amount |
| receptionDate | LocalDate | Reception date |
| paymentDate | LocalDate (nullable) | Payment date |
| peppol | Boolean | Received via Peppol |
| comment | String (nullable) | Comments |
| filePath | String (nullable) | File path on disk |
| dateScope | Enum (DAILY, MONTHLY, QUARTERLY, YEARLY, NONE) | Date scope for file naming |
| scopeDate | LocalDate (nullable) | Reference date for naming (manual input, depends on invoice content) |
| fileDetail | String (nullable) | Optional detail for the filename (e.g. "PneuHiver", "MachineACafe") |

Missing document = filePath is null AND peppol is false.

Generated filename = `{number padded 3}[.{subNumber}]-[{scopeDate formatted by dateScope}]-{supplier.alias}[-{fileDetail}].pdf`

### Supplier
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| name | String | Official name (e.g. "P&Partners", "Cafe de la poste") |
| alias | String (nullable) | Short name for file naming (e.g. "PPartners", "CafeDeLaPoste") |
| enterpriseNumber | String (nullable) | BCE enterprise number (format 0XXX.XXX.XXX, optional) |
