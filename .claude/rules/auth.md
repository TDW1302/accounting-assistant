---
globs: "**/auth*,**/Auth*,**/security*,**/Security*,**/user*,**/User*"
---

# Authentication & Users

## User entity
| Field | Type | Description |
|---|---|---|
| id | Long | Auto-generated PK |
| username | String (unique) | Login |
| email | String (unique) | Email |
| password | String | BCrypt-hashed |
| role | Enum (ADMIN, USER, VIEWER) | Role |
| enabled | Boolean | Active/disabled |
| passwordChangedAt | LocalDateTime | Last password change |
| passwordExpiresAt | LocalDateTime | Expiration (3 months after change) |
| createdAt | LocalDateTime | Creation date |

## Auth mechanism
- Session + Cookie (Spring Security), BCrypt, CSRF cookie-based (`XSRF-TOKEN`)
- Password expiration: 3 months → `PasswordExpirationFilter` returns 403 `{"passwordExpired":true}` on every `/api/**` except change-password, logout and me; the frontend interceptor redirects to change-password
- Password policy: 8+ chars, uppercase, lowercase, digit, special
- Session timeout: 30 minutes

## Roles
- ADMIN: full access + user management
- USER: CRUD invoices/suppliers
- VIEWER: read-only

## Endpoints
- Public: `/api/auth/login` only. Pas d'auto-inscription: les comptes sont créés par un ADMIN via `/api/users`
- `POST /api/auth/logout`, `GET /api/auth/me`, `POST /api/auth/change-password`
- `GET/POST/PUT/DELETE /api/users/{id}` (ADMIN only)

## Admin initializer
- Created at startup from `app.admin.username/password/email` in `application.properties`

## Security audit
- Login/logout logging in `AuthService`
- Rate limiting: 5 attempts/15min/IP sur le login (`RateLimitFilter`, `@Scheduled` cleanup). L'IP vient du **dernier** élément de `X-Forwarded-For` — celui ajouté par le proxy; les précédents sont fournis par le client
