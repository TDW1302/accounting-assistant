---
globs: backend/**
---

# Backend — Architecture & Patterns

## Package structure (`be.vercauteren.accounting`)
- `controller/` — `@RestController`, `@RequestMapping("/api/...")`, `@RequiredArgsConstructor`
- `dto/` — Java records for Request/Response (validation via `@NotNull`, `@NotBlank`)
- `entity/` — JPA entities (`@Entity`, Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`)
- `repository/` — `JpaRepository`, `JpaSpecificationExecutor`
- `service/` — `@Service`, `@RequiredArgsConstructor`, `@Transactional`
- `security/` — `CustomUserDetails`, `CustomUserDetailsService`
- `config/` — `SecurityConfig`, `AdminInitializer`
- `specification/` — JPA Specifications for dynamic search

## Patterns
- DTOs: Java records, separate Request/Response records
- Entity↔DTO: `toResponse()` in Service (no external mapper)
- Errors: `EntityNotFoundException` → 404, `IllegalArgumentException` → 400, `IllegalStateException` → 403, `FalcoApiException` → 502 (via `GlobalExceptionHandler` → `Map<String, String>`)
- Validation: Jakarta Validation annotations on DTO records
- Auto-numbering: `findFirstByYearOrderByNumberDesc` + 1, SERIALIZABLE isolation + retry via `TransactionTemplate`

## API endpoints
- `GET /api/invoices?year=` — list by year
- `GET /api/invoices/search?...` — multi-criteria search (criteria collected in a list then `Specification.allOf` — `Specification.and` rejects null)
- `GET /api/invoices/missing-documents?year=&includePeppol=` — invoices with no file; Peppol excluded unless `includePeppol=true`
- `GET/POST/PUT/DELETE /api/invoices/{id}` — CRUD
- `POST /api/invoices/extract` — AI PDF extraction
- `POST /api/invoices/{id}/upload` — file upload
- `GET /api/peppol/inbound` — Peppol documents from Falco
- `POST /api/peppol/import` — import Peppol document as invoice
- `POST /api/peppol/import-suppliers` — import suppliers from Falco senders
- `GET/POST/PUT/DELETE /api/suppliers/{id}` — supplier CRUD
- `POST /api/auth/login|logout`, `GET /api/auth/me`, `POST /api/auth/change-password` (pas d'endpoint d'inscription)
- `GET/POST/PUT/DELETE /api/users/{id}` — user CRUD (ADMIN only)

## Security
- Session/cookie auth, BCrypt, CSRF cookie-based (`XSRF-TOKEN`)
- Roles: ADMIN (full), USER (CRUD invoices/suppliers), VIEWER (read-only)
- Public endpoints: `/api/auth/login` uniquement (`/api/config` demande une session)
- Rate limiting: 5 attempts/15min/IP on login (`RateLimitFilter` with `@Scheduled` cleanup), IP prise au dernier hop de `X-Forwarded-For`
- Uploads: type MIME + extension déclarés, **et** signature du contenu vérifiée (`FileSignatures`)
- Password policy: 8+ chars, uppercase, lowercase, digit, special
- Session timeout: 30 minutes, secure cookie
- CORS: configured with allowed origins (`application.properties`)
- SQL wildcards escaped in LIKE queries (explicit escape char `'\\'`)

## Configuration (`application.properties`)
- `app.falco.api-key` (`FALCO_API_KEY`), `app.falco.app-secret` (`FALCO_APP_SECRET`), `app.falco.base-url`
- `app.anthropic.api-key` (`ANTHROPIC_API_KEY`), `app.anthropic.model`
- `app.upload.directory` — file storage root
- `app.inbox.directory`, `app.inbox.match-window-days` (défaut 7) — boîte de dépôt et tolérance de date au rapprochement
- `app.admin.username/password/email` — initial admin
