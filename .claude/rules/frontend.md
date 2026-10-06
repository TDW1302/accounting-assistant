---
globs: frontend/**
---

# Frontend — Angular 21, Standalone Components

## Structure (`frontend/src/app/`)
- `models/` — TypeScript interfaces + types (Invoice, Supplier, InvoiceRequest...)
- `services/` — HTTP services (`@Injectable({ providedIn: 'root' })`, `inject(HttpClient)`)
- `invoices/` — invoice-list, invoice-form, batch-upload, missing-documents
- `admin/` — danger-zone, supplier-merge (ADMIN only)
- `peppol/` — peppol-list
- `recurring/` — recurring-list, recurring-form, recurring-attach (recurring expense templates)
- `suppliers/` — supplier-list, supplier-form
- `auth/` — login, change-password (pas d'écran d'inscription: les comptes sont créés dans `users/`)
- `users/` — user-list, user-form
- `guards/` — `authGuard`, `roleGuard`
- `interceptors/` — `authInterceptor` (withCredentials + 401 redirect)

## Conventions
- Routing: lazy loading via `loadComponent` in `app.routes.ts`
- State: Angular signals (`signal<T>()`)
- Forms: `FormsModule` (template-driven) for lists, `ReactiveFormsModule` for form pages
- Locale: `fr-BE`, `provideHttpClient(withInterceptors([authInterceptor]))`
- UI: custom CSS (no Material/PrimeNG), classes `.btn`, `.btn-primary`, `.form-group`, `.page-header`
- Layout ≥769px: `body`/`.container` flex column, the routed component host relays the height, `.table-wrapper` takes the remaining space — its horizontal scrollbar stays on screen and `thead th` is sticky
- Navbar: in `app.html` with `RouterLink`/`RouterLinkActive`
- File upload: frontend chains upload after save (create/update → upload via `switchMap`)
