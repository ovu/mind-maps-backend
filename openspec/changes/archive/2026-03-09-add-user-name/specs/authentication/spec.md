## MODIFIED Requirements

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
