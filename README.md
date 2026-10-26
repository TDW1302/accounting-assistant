# accounting-assistant

A personal accounting app that replaces an Excel workbook for tracking purchase and
sales invoices: numbering, filing supporting documents, recurring expenses, and
invoice pre-filling by an LLM from the PDF.

<!-- Screenshot: generate it with fictitious data (no real supplier in this public repository) -->
![Invoice list](docs/screenshot.png)

## What it does

- **Invoice ledger** for purchases and sales, with one sequential numbering per year
  shared by both, and a second series (`D001…`) for expenses that have no document.
- **AI extraction**: drop a PDF or a photo, the LLM returns supplier, net/VAT/gross
  amounts, dates and the period covered; the form is pre-filled and the supplier is
  matched by company number, then by name.
- **Document filing**: every file is renamed after a naming convention
  (`003-2412-AcmeConseil.pdf`) and stored by year; a dedicated screen lists invoices
  whose document is missing.
- **Drop box**: files dropped into a folder are automatically matched to their
  invoice (supplier, amount, date within ±7 days).
- **Recurring expenses** (rent, bank fees…) as templates whose instalments are only
  written to the ledger on demand.
- **Excel import** of the history, **Peppol** e-invoice reception through the Falco
  API (disabled by default), merging of duplicate suppliers.
- Multi-user accounts with ADMIN / USER / VIEWER roles.

## Stack

| | |
|---|---|
| Backend | Java 25, Spring Boot 4, Spring Security (session + CSRF), Spring Data JPA |
| Database | PostgreSQL 17, schema versioned by Flyway (Hibernate in `validate` mode) |
| Frontend | Angular 22 — standalone components, signals, lazy-loaded routes |
| AI | Official Anthropic (Claude) and Google GenAI (Gemini) Java SDKs, PDFBox |
| Tests | JUnit + Testcontainers (236 tests, ~99% line coverage) · Vitest (118 tests, 100% line coverage) |
| Deployment | Single Docker image (nginx + JAR), GitHub Actions, self-hosted on a NAS |

## Why the LLM is called without a framework

The AI does exactly one thing here: turn a document into a JSON object whose shape is
known in advance. No agent, no tools, no RAG. For that, a layer such as Spring AI or
LangChain4j would mostly have put distance between the code and what actually
matters — all of which each provider's SDK exposes directly:

- **Guaranteed structured output**: a JSON Schema is enforced at decoding time, on
  Claude as on Gemini, instead of being requested in the prompt and hoped for. The
  same schema (a plain `Map`) is sent to both providers.
- **Controlled prompt caching**: the instructions and the list of known suppliers form
  a stable prefix, marked cacheable and billed at a tenth of the price from the second
  call on. That requires controlling the exact order of what is sent (sorted list,
  ordered maps), which an abstraction tends to hide.
- **Text first, vision as a fallback**: a PDF's text is extracted locally with PDFBox
  (few tokens); a scan or a photo is sent as a document or image block.
- **Per-model settings**: turning reasoning off, sizing the output cap — parameters
  specific to each model, available in the SDK as soon as the model ships.

Claude or Gemini is chosen per user, with an automatic fallback when a key is missing.
It all fits in a single service,
[`InvoiceExtractionService`](backend/src/main/java/be/vercauteren/accounting/service/InvoiceExtractionService.java).
Extraction is *best-effort*: a failure simply leaves the form empty.

## Running locally

Requirement: Docker.

```bash
cp .env.example .env   # fill in at least ADMIN_*, DB_PASSWORD and one AI key
docker compose up -d --build
```

The app is served on <http://localhost:8080>, with the admin account defined in `.env`.

> The user interface is in French (locale `fr-BE`).

### Tests

```bash
cd backend && ./gradlew test          # requires a Docker daemon (Testcontainers)
cd frontend && npx ng test --watch=false
```

Backend tests run against a real PostgreSQL: that is where the Flyway migrations are
applied and checked against the JPA entities, in CI, on every push and pull request.
