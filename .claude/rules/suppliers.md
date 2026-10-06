---
globs: "**/supplier*,**/Supplier*"
---

# Suppliers — Data Model

## Supplier entity
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| name | String | Official name (e.g. "P&Partners", "Cafe de la poste") |
| alias | String (nullable) | Short name for file naming (e.g. "PPartners", "CafeDeLaPoste") |
| enterpriseNumber | String (nullable) | BCE enterprise number (format 0XXX.XXX.XXX) |
| category | Enum ExpenseCategory (nullable) | Expense category |
| defaultDateScope | Enum DateScope (nullable) | Default date scope for new invoices (DKV monthly, Vanbrada yearly...) |
| defaultPeppol | boolean (not null) | Pre-checks "reçu via Peppol" on new invoices |

## Rules
- Oliver James is the only current client, but the app should support multiple clients in the future
- Same party can be supplier in one invoice and client in another — role determined by invoice type (PURCHASE/SALE)
- Alias is used in generated filenames
- Enterprise number used for matching during AI extraction and Peppol import (digits-only normalization)
- `defaultDateScope` pre-fills the invoice date scope (invoice form, batch upload, Peppol import) and wins over the scope guessed by the AI
- `defaultPeppol` pre-fills the invoice Peppol flag (invoice form, batch upload); backfilled to true for every non-RESTAURANT supplier by `V3__supplier_default_peppol.sql`
- Listed alphabetically by name (`lower(name)`) by the API; `/suppliers` also sorts on name, alias and category
- Merging two records: ADMIN-only screen `/admin/suppliers/merge` on top of `GET/POST /api/admin/suppliers/duplicates|merge`
