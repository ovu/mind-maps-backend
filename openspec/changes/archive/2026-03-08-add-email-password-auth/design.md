## Context

The mind maps backend is greenfield (Scala 3, Zio 2.x, H2 with ZIO JDBC). There is no existing auth or user model. This change introduces the first user identity mechanism: email/password registration and login with JWT so that future mind map APIs can be scoped by owner.

## Goals / Non-Goals

**Goals:**

- Allow users to register with email (unique) and password; store only a one-way hash of the password.
- Allow users to log in with email and password; on success issue a JWT.
- Provide a way for protected routes to verify the JWT and obtain the current user id.
- Apply security good practice: constant-time comparison, no user enumeration, normalized email, unique constraint on email.
- Document the auth API with Swagger/OpenAPI and expose interactive docs (per AGENTS.md).
- Write tests using ZIO Spec (per AGENTS.md).

**Non-Goals:**

- Email verification, password reset, or "forgot password."
- Rate limiting, account lockout, or CAPTCHA (can be added later).
- OAuth or social login.
- Changing mind map data model or APIs (integration with `owner_id` is a follow-up).

## Decisions

### Password hashing: bcrypt

- **Choice**: Use bcrypt with a configurable work factor (default 10).
- **Rationale**: One-way hashing (not encryption). Widely supported on the JVM, per-user salt built in, and work factor tunes cost. Argon2 was considered but bcrypt has better library availability and is sufficient for this scope.
- **Alternative**: Argon2 (stronger, memory-hard) — deferred for simplicity.

### Email uniqueness: database constraint

- **Choice**: Enforce uniqueness with a unique constraint on the `email` column; handle constraint violation on insert as "email already registered."
- **Rationale**: Avoids race conditions that a "check then insert" pattern would have. Atomic and reliable.
- **Alternative**: Application-level check only — rejected due to race risk.

### Login failure: generic message, constant-time path

- **Choice**: For invalid email or wrong password, return the same HTTP 401 and the same message ("Invalid email or password"). Use constant-time comparison for the hash (e.g. `MessageDigest.isEqual`). If user is not found, still perform a dummy hash comparison so response time does not leak existence.
- **Rationale**: Prevents user enumeration and timing attacks.
- **Alternative**: Different messages for "user not found" vs "wrong password" — rejected for security.

### Session: JWT, HS256, no revocation

- **Choice**: Issue a JWT signed with HS256. Store secret in configuration. Include `sub` (user id), `exp`, `iat`. No server-side revocation; rely on expiry (e.g. 24 hours).
- **Rationale**: Stateless, no session store. HS256 is simple for a single service. Short-lived token keeps revocation risk acceptable for v1.
- **Alternatives**: RS256 (better for multi-service; unnecessary here). Refresh tokens (adds complexity; can add later). Token blocklist (adds storage and lookup; deferred).

### Auth middleware / layer

- **Choice**: A ZIO layer or HTTP middleware that reads `Authorization: Bearer <token>`, verifies the JWT (signature and expiry), extracts `sub` as user id, and provides it to the request environment. Public routes (register, login) skip this.
- **Rationale**: Single place for verification; protected routes declare dependency on "current user" and get 401 if token is missing or invalid.

### User table schema

- **Choice**: `users` table: `id` (PK, UUID or auto), `email` (VARCHAR, UNIQUE NOT NULL), `password_hash` (VARCHAR NOT NULL), `created_at` (TIMESTAMP). Email normalized (trim, lowercase) before insert/lookup.
- **Rationale**: Minimal fields for registration and login. No email_verified or locked flags in v1.

### API documentation: OpenAPI / Swagger

- **Choice**: Document all HTTP APIs (including auth endpoints) using OpenAPI (e.g. 3.x) and expose interactive docs via Swagger UI (or equivalent). Auth endpoints `POST /auth/register` and `POST /auth/login` must appear in the spec with request/response schemas and status codes; document `Authorization: Bearer` for protected routes.
- **Rationale**: Project convention (AGENTS.md): API documentation is always Swagger/OpenAPI. Single source of truth for the API contract and consumer-friendly docs.
- **Alternative**: Ad-hoc or markdown-only docs — rejected per project policy.

### Testing: ZIO Spec

- **Choice**: All tests (unit and integration) are written using **ZIO Spec** (zio-test). Use the ZIO Test DSL (e.g. `suite`, `test`, `assertTrue`, `assertZIO`) and run tests with the ZIO runtime so that layers and effects are used consistently.
- **Rationale**: Project convention (AGENTS.md): tests use ZIO Spec. Fits the ZIO stack and allows testing services with real or test layers.
- **Alternative**: ScalaTest or other frameworks — rejected per project policy.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| JWT stolen or leaked | Rely on short expiry (e.g. 24h). No instant revoke without a blocklist (deferred). |
| Brute force on login | No rate limiting in v1; add per-email or per-IP limits in a follow-up. |
| Weak passwords | Enforce minimum length (e.g. 8 characters); optional max length to cap hashing cost. |
| Bcrypt performance | Work factor 10 is a reasonable default; tune if login latency is an issue. |

## Migration Plan

- Add `users` table via a migration or startup schema creation (project has no migration tooling yet; document SQL or use ZIO JDBC to run DDL once).
- No rollback of user data required for initial deploy; if we need to change hashing algorithm later, migration would re-hash on next login or run a one-off job.

## Open Questions

- Exact JWT expiry value (e.g. 24h vs 7d) — recommend 24h for v1.
- Whether to add a `jti` claim for future revocation — optional, can add when blocklist is introduced.
