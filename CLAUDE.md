# accounting-assistant

Personal accounting app replacing an Excel file for managing purchase/sale invoices.

## Stack
- **Backend**: Java 25 (OpenJDK 25.0.2), Spring Boot 4.0.8, Gradle — package `be.vercauteren.accounting`
  - Dependencies: Spring Web, Spring Data JPA, Spring Security, PostgreSQL driver, Flyway, Lombok, Validation, Apache PDFBox 3.0.8, Apache POI 5.5.1, Anthropic Java SDK 2.58.0, Google GenAI SDK 1.68.0
- **Frontend**: Angular 21.1, Node.js 24.13.1 (scoop: `/c/Users/verca/scoop/apps/nodejs-lts/current`, prioritize over global Node 18), TypeScript, SCSS
- **DB**: PostgreSQL 17 (own container), connection via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
- **Schema**: owned by Flyway (`backend/src/main/resources/db/migration`), Hibernate is `ddl-auto=validate` — never `update`. Any schema change requires a new `Vn__*.sql`.
- **Tests**: `AccountingAssistantApplicationTests` boots on a Testcontainers PostgreSQL — requires a running Docker daemon.

## Key rules
- Currency: always EUR
- Invoice type determines party role (PURCHASE/SALE), not the party itself
- Purchases and sales share a single sequential numbering per year (restarts at 1)
- Two numbering series, each with its own yearly counter: `INVOICE` (documented invoices, 001…) and `EXPENSE` (contractual expenses with no document, D001…). A row's series is fixed at creation.
- Recurring expenses (rent, bank fees, PLCI) are templates; their instalments are only written to the ledger when the user generates them — never automatically
- DTOs are Java records; entity↔DTO mapping via `toResponse()` in Service
- Frontend: standalone components, signals, lazy-loaded routes, locale `fr-BE`
- No Spring profiles currently; session/cookie auth with CSRF (XSRF-TOKEN)

## Contextual rules
Details split into `.claude/rules/` — loaded automatically by glob match.
