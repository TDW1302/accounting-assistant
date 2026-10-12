---
globs: "**/peppol*,**/Peppol*,**/Falco*,**/falco*"
---

# Falco Peppol Integration

> **Disabled by default.** Set `app.falco.enabled=true` in `application.properties` (or `FALCO_ENABLED=true` env var) to re-enable. When disabled, `FalcoApiClient`, `PeppolService`, and `PeppolController` beans are not created (`@ConditionalOnProperty`), and the Peppol link is hidden in the frontend navbar.

## Architecture
- `FalcoApiClient` — RestClient HTTP client for Falco API (`GET /peppol/inbound`), `@Value` + `@PostConstruct`
- `PeppolService` — enriches Falco documents with supplier matching (VAT normalization, digits-only) + duplicate detection
- `PeppolController` — `GET /api/peppol/inbound` (proxy + enrichment), `POST /api/peppol/import`, `POST /api/peppol/import-suppliers`

## Import flow
- Creates Invoice with `peppol=true`, `falcoDocumentId` set, `receptionDate=today`
- Duplicate prevention: `falcoDocumentId` unique on Invoice; `existsByFalcoDocumentId` check

## Falco API
- Auth: `X-Falco-Api-Key` + `X-Falco-App-Secret` headers
- Response snake_case → `@JsonProperty` on `FalcoInboundDocument` record
- Client-side sender name filtering (sandbox ignores `sender_name` param)
- Pagination guarded with max 50 pages

## Supplier import from Falco
- Extracts unique senders → creates/updates Supplier entities
- Matching by VAT number (digits-only normalization)
- On duplicate: Falco data wins for name; alias kept from existing if Falco doesn't provide one

## Limitations
- No UBL file download for now (document stays in Falco)

## Frontend
- Dedicated `/peppol` page: filtering (date range, sender name), status badges (Importé/À importer), inline import with supplier pre-selection
