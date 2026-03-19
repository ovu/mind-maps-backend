## ADDED Requirements

### Requirement: Create mind map
The system SHALL allow an authenticated user to create a mind map by providing a name. The system SHALL generate a unique ID, record the creation datetime, associate the mind map with the authenticated user, and automatically create a root node of type "text" with empty text.

#### Scenario: Successful mind map creation
- **WHEN** an authenticated user submits a create request with name "My Ideas"
- **THEN** the system creates a mind map with the given name, a generated UUID, and the current timestamp
- **AND** the mind map is associated with the authenticated user
- **AND** a root node of type "text" with empty text is automatically created
- **AND** returns the created mind map with its ID, name, created_at, and root node

#### Scenario: Missing name rejected
- **WHEN** an authenticated user submits a create request without a name
- **THEN** the system rejects the request with HTTP 400
- **AND** returns an error indicating the name is required

#### Scenario: Unauthenticated request rejected
- **WHEN** a request to create a mind map has no valid JWT
- **THEN** the system rejects the request with HTTP 401

### Requirement: List user mind maps
The system SHALL allow an authenticated user to retrieve a list of all their mind maps. The response SHALL include each mind map's ID, name, and creation datetime. The system SHALL NOT return mind maps belonging to other users.

#### Scenario: User with mind maps
- **WHEN** an authenticated user who owns 3 mind maps requests the list
- **THEN** the system returns all 3 mind maps with their ID, name, and created_at
- **AND** does not include mind maps owned by other users

#### Scenario: User with no mind maps
- **WHEN** an authenticated user who owns no mind maps requests the list
- **THEN** the system returns an empty list

### Requirement: Get mind map with node tree
The system SHALL allow an authenticated user to retrieve a single mind map by ID, including the full node tree as a nested structure. Each node SHALL include its children recursively.

#### Scenario: Successful retrieval
- **WHEN** an authenticated user requests a mind map they own by its ID
- **THEN** the system returns the mind map with ID, name, created_at, and the full node tree
- **AND** the root node contains its children, each of which contains their children recursively

#### Scenario: Mind map not found
- **WHEN** an authenticated user requests a mind map ID that does not exist
- **THEN** the system rejects the request with HTTP 404

#### Scenario: Mind map owned by another user
- **WHEN** an authenticated user requests a mind map owned by a different user
- **THEN** the system rejects the request with HTTP 404
- **AND** does not reveal that the mind map exists

### Requirement: Update mind map
The system SHALL allow an authenticated user to update the name of a mind map they own.

#### Scenario: Successful name update
- **WHEN** an authenticated user updates their mind map's name to "Renamed Map"
- **THEN** the system updates the name and returns the updated mind map

#### Scenario: Update mind map owned by another user
- **WHEN** an authenticated user attempts to update a mind map they do not own
- **THEN** the system rejects the request with HTTP 404

### Requirement: Delete mind map
The system SHALL allow an authenticated user to delete a mind map they own. Deleting a mind map SHALL remove the mind map, all its nodes, and all associated picture files from disk.

#### Scenario: Successful deletion
- **WHEN** an authenticated user deletes a mind map they own
- **THEN** the system removes the mind map and all associated nodes
- **AND** returns a success response (e.g. HTTP 204 or 200)

#### Scenario: Delete mind map owned by another user
- **WHEN** an authenticated user attempts to delete a mind map they do not own
- **THEN** the system rejects the request with HTTP 404

### Requirement: Mind map ownership isolation
The system SHALL enforce that all mind map operations (list, get, update, delete) are scoped to the authenticated user. A user SHALL NOT be able to access, modify, or delete mind maps belonging to another user.

#### Scenario: Cross-user access denied
- **WHEN** user A attempts to access a mind map owned by user B
- **THEN** the system returns HTTP 404 as if the mind map does not exist
