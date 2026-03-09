# Implementation Tasks

## 1. Project and database setup

- [x] 1.1 Add build dependencies: JWT library (e.g. jwt-core), bcrypt (e.g. jbcrypt or equivalent for Scala/JVM), zio-test (ZIO Spec) for tests
- [x] 1.2 Create `users` table schema: id (PK), email (UNIQUE NOT NULL), password_hash (NOT NULL), created_at; document or run DDL (e.g. via ZIO JDBC) with PostgreSQL-compatible mode

## 2. Auth domain and password hashing

- [x] 2.1 Define user model (id, email, password_hash, created_at) and email normalization (trim, lowercase)
- [x] 2.2 Implement password hashing: bcrypt with configurable work factor (default 10), and constant-time comparison helper for login

## 3. User repository

- [x] 3.1 Implement insert user: normalize email, hash password, insert; handle unique constraint violation as "email already registered"
- [x] 3.2 Implement find user by email (normalized): return Option of user for login lookup

## 4. Registration and login logic

- [x] 4.1 Implement registration: validate email not duplicate (via insert), validate password length (e.g. min 8); return success or appropriate error
- [x] 4.2 Implement login: lookup by email, constant-time password check (or dummy if user not found), return user id on success or generic failure (no user enumeration)

## 5. JWT create and verify

- [x] 5.1 Implement JWT creation: sign with HS256, include sub (user id), iat, exp (e.g. 24h); secret from config
- [x] 5.2 Implement JWT verification: verify signature and exp, extract sub as user id; return Either/Option for use in middleware

## 6. HTTP layer

- [x] 6.1 Add POST /auth/register route: accept email + password, call registration logic, return 201/200 on success or 400/409 on validation/duplicate error
- [x] 6.2 Add POST /auth/login route: accept email + password, call login logic, return 200 + JWT on success or 401 with generic message on failure
- [x] 6.3 Implement auth middleware/layer: read Authorization Bearer token, verify JWT, provide current user id to request context; return 401 if missing or invalid
- [x] 6.4 Wire public routes (register, login) without auth; document or add one protected route example that requires auth
- [x] 6.5 Add OpenAPI/Swagger spec for auth API: document POST /auth/register and POST /auth/login (request/response schemas, status codes), document Bearer auth security scheme for protected routes; expose Swagger UI (or equivalent) for the API

## 7. Configuration and wiring

- [x] 7.1 Add configuration for JWT secret and optional bcrypt work factor and JWT expiry
- [x] 7.2 Wire auth layer, user repository, and routes into the application entrypoint

## 8. Tests (ZIO Spec)

- [x] 8.1 Add ZIO Spec tests for registration: successful sign-up, duplicate email returns error, password too short returns error (use ZIO Spec DSL and test layers)
- [x] 8.2 Add ZIO Spec tests for login: valid credentials return JWT; invalid email or wrong password return same 401/generic message (no user enumeration)
- [x] 8.3 Add ZIO Spec tests for protected route: request with valid JWT succeeds and receives user context; request without token or with invalid/expired token returns 401
