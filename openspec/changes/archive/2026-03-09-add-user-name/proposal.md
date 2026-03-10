## Why

Users currently register with only email and password, and the `/api/me` endpoint returns only the user ID. Adding an optional display name allows the UI to show a personalized greeting and identify users beyond their email address.

## What Changes

- Add an optional `name` field to the registration endpoint (`POST /auth/register`)
- Store the name in the `users` database table
- Return the `name` field in the `/api/me` response
- Update the OpenAPI specification to reflect the new field

## Capabilities

### New Capabilities

- `user-profile`: Covers the user's profile data (display name) — storage, submission during registration, and retrieval via the /me endpoint

### Modified Capabilities

- `authentication`: The registration request schema gains an optional `name` field, and the `/api/me` response now includes `name`

## Impact

- **Database**: `users` table gets a new nullable `name` column (requires schema migration / DDL update)
- **API**: `POST /auth/register` request body adds optional `name`; `GET /api/me` response adds `name` (nullable)
- **Code**: `User` model, `RegisterRequest`, `UserRepository`, `AuthRoutes` handlers all need updates
- **Tests**: Registration and /me tests need to cover the name field
