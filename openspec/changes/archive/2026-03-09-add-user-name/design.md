## Context

The application currently supports email/password registration and JWT-based authentication. The `users` table has columns: `id`, `email`, `password_hash`, `created_at`. The `GET /api/me` endpoint returns only `userId`. Users want to provide a display name at registration time and see it returned from `/api/me`.

## Goals / Non-Goals

**Goals:**
- Allow users to optionally provide a display name during registration
- Persist the name in the database
- Return the name in the `/api/me` response alongside `userId`

**Non-Goals:**
- Updating the name after registration (profile editing — future work)
- Name validation beyond basic length limits
- Using the name for authentication or authorization purposes

## Decisions

### 1. Name field is optional and nullable

The `name` field is optional in the registration request and stored as a nullable `VARCHAR(100)` column. Users who don't provide a name simply have `null` in the database and in the `/api/me` response.

**Rationale**: Keeps registration simple — existing clients don't break, and new clients can opt in. A 100-character limit prevents abuse without being restrictive.

**Alternative considered**: Making name required — rejected because it would be a breaking change to the registration API.

### 2. Add column directly to DDL (no migration)

The project uses H2 with schema created on startup via `CREATE TABLE IF NOT EXISTS`. We add the `name` column directly to the DDL statement.

**Rationale**: The project is pre-production with no persistent data to migrate. Adding a migration framework would be premature.

### 3. Name stored as-is (trimmed only)

The name is trimmed of leading/trailing whitespace but otherwise stored as provided — no lowercasing or other normalization.

**Rationale**: Unlike email, display names are case-sensitive and freeform. Trimming prevents accidental whitespace issues.

### 4. /me response includes name field

The `/api/me` response gains a `name` field (nullable string) alongside the existing `userId`.

**Rationale**: Minimal API surface change. The field is always present in the response but may be `null`.

## Risks / Trade-offs

- **[No update endpoint]** → Users cannot change their name after registration. Acceptable for now; a profile update endpoint can be added later.
- **[No uniqueness on name]** → Multiple users can have the same display name. This is intentional — names are not identifiers.
