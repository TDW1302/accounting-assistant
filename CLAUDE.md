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
  - Dependencies: Spring Web, Spring Data JPA, Spring Security, H2, Lombok, Validation, Apache PDFBox 3.0.4, Anthropic Java SDK 2.11.0
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
- `security/` — Spring Security (`CustomUserDetails`, `CustomUserDetailsService`)
- `config/` — Configuration (`SecurityConfig`, `AdminInitializer`)
- `specification/` — JPA Specifications for dynamic search

**Backend patterns**:
- DTOs: Java records (not classes), separate Request/Response records
- Entity↔DTO mapping: `toResponse()` methods directly in the Service (no external mapper)
- Errors: `EntityNotFoundException` → 404, `IllegalArgumentException` → 400, `IllegalStateException` → 403, `FalcoApiException` → 502 (via `GlobalExceptionHandler` → `Map<String, String>`)
- Validation: Jakarta Validation annotations on DTO records
- Auto-numbering: `findFirstByYearOrderByNumberDesc` + 1 (in `InvoiceService.create`)

**Frontend structure** (Angular 21, standalone components):
- `app/models/` — TypeScript interfaces + types (Invoice, Supplier, InvoiceRequest...)
- `app/services/` — HTTP services (`@Injectable({ providedIn: 'root' })`, `inject(HttpClient)`)
- `app/invoices/` — Invoice components (invoice-list, invoice-form)
- `app/peppol/` — Peppol components (peppol-list)
- `app/suppliers/` — Supplier components (supplier-list, supplier-form)
- `app/auth/` — Auth components (login, register, change-password)
- `app/users/` — User management components (user-list, user-form)
- `app/guards/` — Route guards (`authGuard`, `roleGuard`)
- `app/interceptors/` — HTTP interceptors (`authInterceptor` — withCredentials + 401 redirect)
- Routing: lazy loading via `loadComponent` in `app.routes.ts`
- State: Angular signals (`signal<T>()`)
- Forms: `FormsModule` (template-driven) for lists, `ReactiveFormsModule` for form pages
- Locale: `fr-BE`, `provideHttpClient(withInterceptors([authInterceptor]))`
- UI: Custom CSS (no Material/PrimeNG), classes `.btn`, `.btn-primary`, `.form-group`, `.page-header`
- Navbar: in `app.html` with `RouterLink`/`RouterLinkActive`

**API endpoints**:
- `GET /api/invoices?year=` — list by year
- `GET /api/invoices/search?...` — multi-criteria search
- `GET/POST/PUT/DELETE /api/invoices/{id}` — CRUD
- `POST /api/invoices/extract` — AI PDF extraction
- `POST /api/invoices/{id}/upload` — file upload
- `GET /api/peppol/inbound` — list Peppol documents from Falco (with enrichment)
- `POST /api/peppol/import` — import a Peppol document as an invoice
- `POST /api/peppol/import-suppliers` — import suppliers from Falco Peppol senders
- `GET/POST/PUT/DELETE /api/suppliers/{id}` — supplier CRUD
- `POST /api/auth/login` — login (returns AuthResponse with user + passwordExpired)
- `POST /api/auth/register` — public registration (default role VIEWER)
- `POST /api/auth/logout` — logout (invalidate session)
- `GET /api/auth/me` — current user info
- `POST /api/auth/change-password` — change password
- `GET/POST/PUT/DELETE /api/users/{id}` — user CRUD (ADMIN only)

**Configuration** (`application.properties`):
- H2 persistent file: `jdbc:h2:file:./data/accounting`
- DDL auto: `update` (Hibernate manages the schema)
- Spring Security: session/cookie auth, BCrypt passwords, CSRF enabled (cookie-based `XSRF-TOKEN` for Angular SPA)
- Falco API: `app.falco.api-key` (from `FALCO_API_KEY` env var), `app.falco.app-secret` (from `FALCO_APP_SECRET` env var), `app.falco.base-url`
- No Spring profiles (dev/prod) currently

### Authentication & Authorization
- **Auth mechanism**: Session + Cookie (Spring Security)
- **Password hashing**: BCrypt (salt included)
- **Password expiration**: 3 months — user redirected to change-password page when expired
- **Roles**: ADMIN (full access), USER (CRUD invoices/suppliers), VIEWER (read-only)
- **Public endpoints**: `/api/auth/login`, `/api/auth/register`
- **Registration**: public, immediate access, default role VIEWER
- **Admin initial**: created at startup from `application.properties` (`app.admin.username/password/email`)
- **Session timeout**: 30 minutes
- **Invoice tracking**: `createdBy` field on Invoice (nullable for existing data)

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

### Falco Peppol integration
- Backend proxies the Falco API to list inbound Peppol documents and import them as invoices
- `FalcoApiClient` — RestClient-based HTTP client for Falco API (`GET /peppol/inbound`), configured via `@Value` + `@PostConstruct`
- `PeppolService` — Orchestration: enriches Falco documents with supplier matching (VAT number normalization, digits-only) and duplicate detection (`falcoDocumentId`)
- `PeppolController` — `GET /api/peppol/inbound` (proxy + enrichment), `POST /api/peppol/import` (creates invoice via `InvoiceService.create`)
- Import creates a standard Invoice with `peppol=true`, `falcoDocumentId` set, and `receptionDate=today`
- Duplicate prevention: `falcoDocumentId` is unique on Invoice entity; `existsByFalcoDocumentId` check before import
- Falco API auth: `X-Falco-Api-Key` + `X-Falco-App-Secret` headers
- Falco API response uses snake_case: mapped via `@JsonProperty` on `FalcoInboundDocument` record
- Client-side sender name filtering (Falco sandbox ignores `sender_name` query param)
- Frontend: dedicated `/peppol` page with filtering (date range, sender name), status badges (Importé/À importer), and inline import form with supplier pre-selection
- Supplier import from Falco: extracts unique senders from inbound Peppol documents and creates/updates Supplier entities
  - Matching by VAT number (digits-only normalization)
  - On duplicate: Falco data wins for name; alias kept from existing if Falco doesn't provide one
  - Endpoint: `POST /api/peppol/import-suppliers`
- No UBL file download for now (document stays in Falco)

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
- User authentication (session/cookie) with role-based access control (ADMIN, USER, VIEWER)
- User management (CRUD, admin only) with password expiration (3 months)
- Falco Peppol integration: list and import inbound Peppol documents with supplier matching and duplicate detection

### Future scope (beyond v1)
- Automated upload to Falco (accounting software) — outbound invoice sending:
  - API docs: https://docs.falco-app.be/docs/getting-started
  - Developer portal: https://dev.falco-app.be
  - Auth: `X-Falco-Api-Key` + `X-Falco-App-Secret` headers
  - Supports: PDF upload with metadata, UBL upload, Peppol sending, delivery status tracking
  - Falco does NOT extract data from PDFs — all fields must be provided in the request
  - Sandbox environment available for testing
- Automatic invoice import from Gmail (PDF attachments)
- PDF file backup strategy (NAS backup, copy to network device, etc. — TBD)
- Batch invoice upload: upload multiple PDFs at once, each creating a separate invoice (with AI extraction per file)
- Bank statement-based invoice numbering: number invoices in the order they appear on bank statements (CODA files) to match the accounting sequence. Requires matching invoices to bank transactions (by IBAN + amount + payment date). Blocked: Falco API does not expose bank statements/CODA endpoints as of Feb 2026 — revisit when/if the API adds support. Alternative fallback: manual .cod file upload and parsing.
- OCR for scanned/image-based PDFs: integrate an OCR solution (e.g. Tesseract) to extract text from image PDFs, enabling AI extraction for invoices that currently fall back silently due to empty text
- Automated tests: unit tests (backend services, controllers) + end-to-end tests (frontend) to secure future evolutions and prevent regressions
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
| createdBy | FK -> User (nullable) | User who created the invoice |
| falcoDocumentId | String (nullable, unique) | Falco document ID for Peppol import deduplication |

Missing document = filePath is null AND peppol is false.

Generated filename = `{number padded 3}[.{subNumber}]-[{scopeDate formatted by dateScope}]-{supplier.alias}[-{fileDetail}].pdf`

### Supplier
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| name | String | Official name (e.g. "P&Partners", "Cafe de la poste") |
| alias | String (nullable) | Short name for file naming (e.g. "PPartners", "CafeDeLaPoste") |
| enterpriseNumber | String (nullable) | BCE enterprise number (format 0XXX.XXX.XXX, optional) |

### User
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| username | String (unique) | Login username |
| email | String (unique) | User email |
| password | String | BCrypt-hashed password |
| role | Enum (ADMIN, USER, VIEWER) | User role |
| enabled | Boolean | Account active/disabled |
| passwordChangedAt | LocalDateTime | Last password change |
| passwordExpiresAt | LocalDateTime | Password expiration date (3 months after change) |
| createdAt | LocalDateTime | Account creation date |

## Audit - Completed (2026-03-16)

All issues resolved. Summary of fixes applied:
- **CSRF protection** enabled (cookie-based XSRF-TOKEN for Angular SPA) — `SecurityConfig.java`
- **Rate limiting** on login/register (5 attempts per 15min per IP) — `RateLimitFilter.java`
- **Invoice numbering** race condition fixed (SERIALIZABLE isolation + retry) — `InvoiceService.java`
- **File type validation** on upload (extension + content type whitelist) — `InvoiceService.java`
- **Authorization on delete** (creator or ADMIN only) — `InvoiceService.java`
- **Year change prevented** in invoice update — `InvoiceService.java`
- **Session cookie** defaults to secure=true — `application.properties`
- **Password policy** strengthened (8+ chars, uppercase, lowercase, digit, special) — DTOs
- **CORS** configured with allowed origins — `SecurityConfig.java`, `application.properties`
- **Year/amount validation** added (@Min/@Max, @DecimalMin) — `InvoiceRequest.java`
- **Security audit logging** on login/logout — `AuthService.java`
- **File+DB transaction** compensation on failure — `InvoiceService.java`
- **Exception logging** instead of swallowing — `InvoiceService.java`
- **CSP header** added — `nginx.conf`
- **Source maps** disabled in production — `angular.json`
- **@NotNull on primitive** removed — `Invoice.java`
- **Optional\<User\>** instead of nullable return — `AuthService.java`
- **Peppol pagination** for supplier import — `PeppolService.java`
- **SQL wildcards** escaped in keyword search — `InvoiceSpecification.java`

Round 2 fixes (regression audit):
- **RateLimitFilter memory leak** fixed with `@Scheduled` cleanup every 15min + `@EnableScheduling`
- **LIKE escape char** declared explicitly (`'\\' `) in JPA Criteria `cb.like()` calls
- **File extension validation** fixed edge case (no dot in filename)
- **CORS origins** trimmed to avoid whitespace in env vars
- **Peppol pagination** guarded with max 50 pages to prevent infinite loop
- **TransactionTemplate** used for invoice creation (avoids Spring proxy self-invocation bug)
