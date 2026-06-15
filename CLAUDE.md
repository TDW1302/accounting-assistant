# accounting-assistant

Personal accounting app replacing an Excel file for managing purchase/sale invoices.

## Stack
- **Backend**: Java 25 (OpenJDK 25.0.2), Spring Boot 3.5.2, Gradle, H2 — package `be.vercauteren.accounting`
  - Dependencies: Spring Web, Spring Data JPA, Spring Security, H2, Lombok, Validation, Apache PDFBox 3.0.4, Anthropic Java SDK 2.11.0
- **Frontend**: Angular 21.1, Node.js 24.13.1 (scoop: `/c/Users/verca/scoop/apps/nodejs-lts/current`, prioritize over global Node 18), TypeScript, SCSS
- **DB**: H2 persistent file (`jdbc:h2:file:./data/accounting`), DDL auto `update`

## Key rules
- Currency: always EUR
- Invoice type determines party role (PURCHASE/SALE), not the party itself
- Purchases and sales share a single sequential numbering per year (restarts at 1)
- DTOs are Java records; entity↔DTO mapping via `toResponse()` in Service
- Frontend: standalone components, signals, lazy-loaded routes, locale `fr-BE`
- No Spring profiles currently; session/cookie auth with CSRF (XSRF-TOKEN)

## Contextual rules
Details split into `.claude/rules/` — loaded automatically by glob match.
