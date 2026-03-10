## ADDED Requirements

### Requirement: User display name storage
The system SHALL store an optional display name for each user. The name SHALL be a string of at most 100 characters, trimmed of leading and trailing whitespace before storage.

#### Scenario: Name stored on registration
- **WHEN** a user registers with name "Alice"
- **THEN** the system stores "Alice" as the user's display name

#### Scenario: Name trimmed before storage
- **WHEN** a user registers with name "  Bob  "
- **THEN** the system stores "Bob" as the user's display name

#### Scenario: No name provided
- **WHEN** a user registers without providing a name
- **THEN** the system stores null as the user's display name

### Requirement: User profile retrieval via /me
The system SHALL return the authenticated user's profile information (userId and name) from the `GET /api/me` endpoint.

#### Scenario: /me returns name when set
- **WHEN** an authenticated user with name "Alice" calls `GET /api/me`
- **THEN** the response includes `userId` and `name` equal to "Alice"

#### Scenario: /me returns null name when not set
- **WHEN** an authenticated user who registered without a name calls `GET /api/me`
- **THEN** the response includes `userId` and `name` equal to null
