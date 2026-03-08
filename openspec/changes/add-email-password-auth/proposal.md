## Why

The mind maps backend needs to identify who is making requests so that mind maps can be owned by users and access controlled. Today there is no notion of users or sessions. Adding email/password registration and login with JWT will establish user identity and enable future authorization (e.g. "only the owner can edit this mind map").

## What Changes

- **User registration**: Simple sign-up with email and password. Email must be unique; password is stored only as a one-way hash. No email verification or complex rules in this change.
- **User login**: Authenticate with email and password; on success issue a JWT. Reject with a generic error for invalid credentials to avoid user enumeration.
- **Session model**: JWT in `Authorization: Bearer <token>`. No server-side session store; stateless verification.
- **Security**: Bcrypt for password hashing, constant-time comparison, unique constraint on email, normalized email (trim, lowercase).
- **Integration**: Auth middleware that verifies JWT and exposes current user id for protected routes. Mind maps will later be scoped by `owner_id` (out of scope for this change).

## Capabilities

### New Capabilities

- `authentication`: Registration (email uniqueness, hashed password), login (email + password, JWT issuance), and JWT verification for protected routes.

### Modified Capabilities

- *(none)*

## Impact

- **Code**: New auth module (user model, registration/login logic, password hashing, JWT create/verify), HTTP routes for `POST /auth/register` and `POST /auth/login`, and auth middleware/layer for protected routes. Tests must be written using ZIO Spec (see AGENTS.md).
- **Database**: New `users` table (id, email unique, password_hash, created_at). No change to mind map tables in this change.
- **APIs**: New public endpoints for register and login; protected routes will require valid JWT (exact route set TBD when mind map APIs are added). All API endpoints must be documented with Swagger/OpenAPI (see AGENTS.md).
- **Dependencies**: JWT library, bcrypt (or equivalent) for password hashing; OpenAPI/Swagger tooling for API documentation; zio-test (ZIO Spec) for tests.
- **Security**: Passwords never stored in plaintext; generic login error message; constant-time hash comparison.
