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
  - Dependencies: Spring Web, Spring Data JPA, H2, Lombok, Validation, Apache PDFBox 3.0.4, Anthropic Java SDK 2.11.0
  - Package: be.vercauteren.accounting
- **Frontend**: Angular 21.1, Node.js 24.13.1 (scoop: nodejs-lts), TypeScript, SCSS
- **Note**: Node 24 is installed via scoop (`/c/Users/verca/scoop/apps/nodejs-lts/current`), PATH must prioritize it over the global Node 18

### Architecture & Patterns

**Backend structure** (package `be.vercauteren.accounting`):
- `controller/` — REST controllers (`@RestController`, `@RequestMapping("/api/...")`, `@RequiredArgsConstructor`)
- `dto/` — Java records for Request/Response (validation via `@NotNull`, `@NotBlank`)
- `entity/` — JPA entities (`@Entity`, Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`)
- `repository/` — Spring Data JPA (`JpaRepository`, `JpaSpecificationExecutor`)
- `service/` — Business services (`@Service`, `@RequiredArgsConstructor`, `@Transactional`)
- `specification/` — JPA Specifications for dynamic search

**Backend patterns**:
- DTOs: Java records (not classes), separate Request/Response records
- Entity↔DTO mapping: `toResponse()` methods directly in the Service (no external mapper)
- Errors: `EntityNotFoundException` → `GlobalExceptionHandler` → 404 with `Map<String, String>`
- Validation: Jakarta Validation annotations on DTO records
- Auto-numbering: `findFirstByYearOrderByNumberDesc` + 1 (in `InvoiceService.create`)

**Frontend structure** (Angular 21, standalone components):
- `app/models/` — TypeScript interfaces + types (Invoice, Supplier, InvoiceRequest...)
- `app/services/` — HTTP services (`@Injectable({ providedIn: 'root' })`, `inject(HttpClient)`)
- `app/invoices/` — Invoice components (invoice-list, invoice-form)
- `app/suppliers/` — Supplier components (supplier-list, supplier-form)
- Routing: lazy loading via `loadComponent` in `app.routes.ts`
- State: Angular signals (`signal<T>()`)
- Forms: `FormsModule` (template-driven) for lists, `ReactiveFormsModule` for form pages
- Locale: `fr-BE`, `provideHttpClient()` without interceptors currently
- UI: Custom CSS (no Material/PrimeNG), classes `.btn`, `.btn-primary`, `.form-group`, `.page-header`
- Navbar: in `app.html` with `RouterLink`/`RouterLinkActive`

**API endpoints**:
- `GET /api/invoices?year=` — list by year
- `GET /api/invoices/search?...` — multi-criteria search
- `GET/POST/PUT/DELETE /api/invoices/{id}` — CRUD
- `POST /api/invoices/extract` — AI PDF extraction
- `POST /api/invoices/{id}/upload` — file upload
- `GET/POST/PUT/DELETE /api/suppliers/{id}` — supplier CRUD

**Configuration** (`application.properties`):
- H2 persistent file: `jdbc:h2:file:./data/accounting`
- DDL auto: `update` (Hibernate manages the schema)
- No Spring Security currently
- No Spring profiles (dev/prod) currently

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

### AI-powered invoice extraction
- When adding a new invoice, selecting a PDF triggers automatic data extraction via Claude API
- Flow: PDF text extracted with PDFBox → sent to Claude with known supplier list → structured JSON response → form pre-filled
- Endpoint: `POST /api/invoices/extract` (multipart file upload)
- `InvoiceExtractionService` orchestrates extraction, Claude API call, and supplier matching (by enterprise number, then name/alias)
- Best-effort: failures are silent, user can always fill the form manually
- Configuration: `app.anthropic.api-key` (from `ANTHROPIC_API_KEY` env var), `app.anthropic.model` in `application.properties`
- Scanned/image PDFs return empty text → graceful fallback (no extraction, no error)

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
- AI-powered PDF invoice data extraction (auto-fill form from uploaded PDF)

### Future scope (beyond v1)
- Automated upload to Falco (accounting software) — API confirmed available:
  - API docs: https://docs.falco-app.be/docs/getting-started
  - Developer portal: https://dev.falco-app.be
  - Auth: Bearer Token + `X-Falco-Application-Id` + `X-Falco-App-Secret`
  - Supports: PDF upload with metadata, UBL upload, Peppol sending, delivery status tracking
  - Falco does NOT extract data from PDFs — all fields must be provided in the request
  - Sandbox environment available for testing
- Automatic invoice import from Gmail (PDF attachments)
- PDF file backup strategy (NAS backup, copy to network device, etc. — TBD)
- Batch invoice upload: upload multiple PDFs at once, each creating a separate invoice (with AI extraction per file)
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
