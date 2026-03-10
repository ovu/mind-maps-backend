## Requirements

### Requirement: Account Registration
WHEN a client submits a registration request with email, password, and an optional name,
the system SHALL create a user account provided the email is not already registered and the password meets minimum length.

#### Scenario: Successful Registration
GIVEN the email is not already registered
AND the password meets the minimum length requirement
WHEN the client submits a valid registration request
THEN the system creates a user record with normalized email, a one-way hash of the password, and the optional name (trimmed)
AND returns a success response (e.g. HTTP 201 or 200)

#### Scenario: Successful Registration with name
GIVEN the email is not already registered
AND the password meets the minimum length requirement
AND the request includes a name field
WHEN the client submits a valid registration request
THEN the system creates a user record with the provided name (trimmed of whitespace)
AND returns a success response (e.g. HTTP 201 or 200)

#### Scenario: Successful Registration without name
GIVEN the email is not already registered
AND the password meets the minimum length requirement
AND the request does not include a name field
WHEN the client submits a valid registration request
THEN the system creates a user record with name set to null
AND returns a success response (e.g. HTTP 201 or 200)

#### Scenario: Duplicate Email
GIVEN the email is already registered
WHEN the client submits a registration request with that email
THEN the system rejects the request
AND returns an error indicating the email is already in use (e.g. HTTP 409 or 400)

#### Scenario: Password Too Short
GIVEN the password is shorter than the minimum length
WHEN the client submits a registration request
THEN the system rejects the request
AND returns an error indicating the password does not meet requirements (e.g. HTTP 400)

### Requirement: Email Uniqueness and Normalization
WHEN the system stores or looks up a user by email,
the system SHALL treat email as unique and SHALL normalize it (trim whitespace, lowercase) before storage and lookup.

#### Scenario: Normalized Storage
GIVEN a registration request with email " User@Example.COM "
WHEN the system creates the user
THEN the system stores the email as "user@example.com"
AND enforces uniqueness on the normalized value

#### Scenario: Uniqueness Enforced by Database
GIVEN a user already exists with email "user@example.com"
WHEN a second registration attempt uses the same email (any casing or surrounding spaces)
THEN the system rejects the second registration
AND returns an error indicating the email is already in use

### Requirement: Secure Password Storage
WHEN the system stores a password (registration or change),
the system SHALL store only a one-way cryptographic hash with a unique salt per user and SHALL NOT store or log the plaintext password.

#### Scenario: Password Hashed on Registration
GIVEN a valid registration request with password "SecurePass123"
WHEN the system processes the registration
THEN the system stores only a hash of the password (e.g. bcrypt)
AND the stored value cannot be used to recover the original password

#### Scenario: Constant-Time Comparison on Login
GIVEN a login request
WHEN the system verifies the password against the stored hash
THEN the system uses constant-time comparison (e.g. MessageDigest.isEqual)
AND does not leak information via timing differences

### Requirement: User Login
WHEN a client submits a login request with email and password,
the system SHALL authenticate the user and, on success, issue a JWT that can be used to identify the user on subsequent requests.

#### Scenario: Successful Login
GIVEN a registered user with email "user@example.com" and the correct password
WHEN the client submits a login request with those credentials
THEN the system authenticates the user
AND returns a JWT containing the user identifier (e.g. sub claim)
AND returns a success response (e.g. HTTP 200)

#### Scenario: Invalid Credentials
GIVEN a login request with either an unregistered email or an incorrect password
WHEN the client submits the login request
THEN the system rejects the login
AND returns the same HTTP status (e.g. 401) and the same generic error message (e.g. "Invalid email or password") in both cases
AND does not issue a JWT

### Requirement: JWT Content and Lifetime
WHEN the system issues a JWT after successful login,
the system SHALL include the user identifier (sub), issuance time (iat), and expiration (exp), and SHALL sign the token so that tampering can be detected.

#### Scenario: Valid JWT Issued
GIVEN a successful login for user with id "550e8400-e29b-41d4-a716-446655440000"
WHEN the system issues the JWT
THEN the JWT contains sub equal to that user id
AND contains iat and exp claims
AND is signed with the configured secret (e.g. HS256)

#### Scenario: Expired or Invalid Token Rejected
GIVEN a request that presents an expired or signature-invalid JWT
WHEN the system verifies the token for a protected route
THEN the system rejects the request (e.g. HTTP 401)
AND does not treat the user as authenticated

### Requirement: Protected Route Authentication
WHERE a request targets a protected route,
the system SHALL require a valid JWT in the request (e.g. Authorization: Bearer <token>) and SHALL make the authenticated user id available to the handler; otherwise the system SHALL reject the request (e.g. HTTP 401).

#### Scenario: Valid Token Allows Access
GIVEN a request to a protected route with a valid, non-expired JWT whose sub is user id "550e8400-e29b-41d4-a716-446655440000"
WHEN the system processes the request
THEN the system accepts the request
AND the handler receives the user id "550e8400-e29b-41d4-a716-446655440000" as the current user

#### Scenario: Missing or Invalid Token Denied
GIVEN a request to a protected route with no Authorization header, or an invalid or expired token
WHEN the system processes the request
THEN the system rejects the request with HTTP 401 (or equivalent)
AND the protected handler is not invoked
