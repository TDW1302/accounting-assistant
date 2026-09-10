# accounting-assistant

Personal accounting app replacing an Excel file for managing purchase/sale invoices.

## Stack
Les numeros ci-dessous sont indicatifs et datent vite. La source de verite est
`backend/build.gradle`, `frontend/package.json`, le `Dockerfile` et
`.github/workflows/docker.yml` — jamais ce fichier. Une montee de version proposee
par Dependabot ne doit pas etre refusee au motif qu'elle ne correspond pas a ce qui
est ecrit ici: c'est ce fichier qu'on met a jour ensuite.

- **Backend**: Java 25, Spring Boot 4.1.1, Gradle 9.7.1 — package `be.vercauteren.accounting`
  - Dependencies: Spring Web, Spring Data JPA, Spring Security, PostgreSQL driver, Flyway, Lombok, Validation, Apache PDFBox 3.0.8, Apache POI 5.5.1, Anthropic Java SDK 2.61.0, Google GenAI SDK 1.70.0
  - Tests: Testcontainers 1.21.4 sur `postgres:17-alpine` — un demon Docker doit tourner
- **Frontend**: Angular 21 (`^21.1.0`, resolu en 21.2.x), TypeScript `~5.9.2`, SCSS, npm 11.8.0
  - `ng test` tourne sur vitest 4: `@angular/build@21` declare `peerOptional vitest@"^4.0.8"`.
    vitest 5 suppose Angular 22 et TypeScript 6 — les trois se montent ensemble ou pas du tout.
- **Images**: `node:26-alpine` pour l'etage frontend, `eclipse-temurin:25-jdk-alpine`
  puis `25-jre-alpine` pour le backend. Le Node local n'a pas a coller a celui de
  l'image: seul le build de production passe par elle.
- **DB**: PostgreSQL 17 (propre conteneur), connexion via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`
- **Schema**: possede par Flyway (`backend/src/main/resources/db/migration`), Hibernate est
  `ddl-auto=validate` — jamais `update`. Toute evolution passe par un nouveau `Vn__*.sql`.
- **CI**: `.github/workflows/docker.yml`. Le job `test` lance `./gradlew test`, donc
  `AccountingAssistantApplicationTests` sur un PostgreSQL Testcontainers: c'est la que les
  migrations Flyway sont reellement jouees et confrontees aux entites JPA. Le job `build`
  compile l'etage frontend (`npm ci` puis `ng build`) et ne publie l'image que sur `main`.
  **`ng test` n'est execute nulle part en CI** — les tests frontend se lancent a la main.

## Key rules
- Currency: always EUR
- Invoice type determines party role (PURCHASE/SALE), not the party itself
- Purchases and sales share a single sequential numbering per year (restarts at 1)
- Two numbering series, each with its own yearly counter: `INVOICE` (documented invoices, 001…) and `EXPENSE` (contractual expenses with no document, D001…). A row's series is fixed at creation.
- Recurring expenses (rent, bank fees, PLCI) are templates; their instalments are only written to the ledger when the user generates them — never automatically
- An existing documentless row (an Excel-imported rent) can be linked to a template without being renumbered: linking only records the period it covers. Any series may carry the link; `scope_date` is what identifies an instalment.
- DTOs are Java records; entity↔DTO mapping via `toResponse()` in Service
- Frontend: standalone components, signals, lazy-loaded routes, locale `fr-BE`
- No Spring profiles currently; session/cookie auth with CSRF (XSRF-TOKEN)

## Contextual rules
Details split into `.claude/rules/` — loaded automatically by glob match.
