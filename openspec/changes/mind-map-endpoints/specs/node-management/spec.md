## ADDED Requirements

### Requirement: Add node to mind map
The system SHALL allow an authenticated user to add a node to a mind map they own. The request SHALL specify a parent node ID (which MUST belong to the same mind map), a node type (text, link, or picture), a text field, and an optional color. Nodes of type "link" SHALL accept an optional value field for the URL. Nodes of type "picture" receive their value via a separate upload endpoint.

#### Scenario: Add text node
- **WHEN** an authenticated user adds a node with parent_id pointing to the root node, type "text", and text "My first idea"
- **THEN** the system creates a node with a generated UUID, the specified type, text, and parent
- **AND** returns the created node

#### Scenario: Add link node with color
- **WHEN** an authenticated user adds a node with type "link", text "Example site", value "https://example.com", and color "#FF5733"
- **THEN** the system creates the node with all specified properties
- **AND** returns the created node including color and value

#### Scenario: Add picture node
- **WHEN** an authenticated user adds a node with type "picture" and text "Architecture diagram"
- **THEN** the system creates the node with value set to null (picture to be uploaded separately)
- **AND** returns the created node

#### Scenario: Invalid node type rejected
- **WHEN** an authenticated user adds a node with type "video"
- **THEN** the system rejects the request with HTTP 400
- **AND** returns an error indicating the type must be one of: text, link, picture

#### Scenario: Parent node not in same mind map
- **WHEN** an authenticated user adds a node with a parent_id that belongs to a different mind map
- **THEN** the system rejects the request with HTTP 400

#### Scenario: Parent node does not exist
- **WHEN** an authenticated user adds a node with a parent_id that does not exist
- **THEN** the system rejects the request with HTTP 400

#### Scenario: Add node to mind map owned by another user
- **WHEN** an authenticated user attempts to add a node to a mind map they do not own
- **THEN** the system rejects the request with HTTP 404

### Requirement: Update node
The system SHALL allow an authenticated user to update a node's text, value, color, and type within a mind map they own.

#### Scenario: Update node text
- **WHEN** an authenticated user updates a node's text to "Updated idea"
- **THEN** the system updates the text and returns the updated node

#### Scenario: Update node color
- **WHEN** an authenticated user updates a node's color to "#00FF00"
- **THEN** the system updates the color and returns the updated node

#### Scenario: Update node type
- **WHEN** an authenticated user updates a node's type from "text" to "link" and sets value to "https://example.com"
- **THEN** the system updates the type and value and returns the updated node

#### Scenario: Update node with invalid type rejected
- **WHEN** an authenticated user updates a node's type to "video"
- **THEN** the system rejects the request with HTTP 400

#### Scenario: Update node in mind map owned by another user
- **WHEN** an authenticated user attempts to update a node in a mind map they do not own
- **THEN** the system rejects the request with HTTP 404

#### Scenario: Update non-existent node
- **WHEN** an authenticated user attempts to update a node that does not exist
- **THEN** the system rejects the request with HTTP 404

### Requirement: Delete node with recursive cascade
The system SHALL allow an authenticated user to delete a node from a mind map they own. Deleting a node SHALL also delete all its descendant nodes recursively. When deleting nodes of type "picture" that have an associated file, the system SHALL also delete the picture file from disk.

#### Scenario: Delete leaf node
- **WHEN** an authenticated user deletes a node that has no children
- **THEN** the system removes the node
- **AND** returns a success response

#### Scenario: Delete node with children
- **WHEN** an authenticated user deletes a node that has 2 children, one of which has 3 children
- **THEN** the system removes the node, its 2 children, and the 3 grandchildren (6 nodes total)
- **AND** returns a success response

#### Scenario: Delete picture node cleans up file
- **WHEN** an authenticated user deletes a node of type "picture" with value "abc123.png"
- **THEN** the system removes the node from the database
- **AND** deletes the file "abc123.png" from the uploads directory

#### Scenario: Delete root node rejected
- **WHEN** an authenticated user attempts to delete the root node of a mind map
- **THEN** the system rejects the request with HTTP 400
- **AND** returns an error indicating the root node cannot be deleted (delete the mind map instead)

#### Scenario: Delete node in mind map owned by another user
- **WHEN** an authenticated user attempts to delete a node in a mind map they do not own
- **THEN** the system rejects the request with HTTP 404

### Requirement: Node types
The system SHALL support exactly three node types: text, link, and picture. Every node SHALL have a text field for descriptive content. Nodes of type "link" and "picture" SHALL additionally have a value field — storing a URL for links, or a filename for pictures.

#### Scenario: Valid node types accepted
- **WHEN** a node is created or updated with type "text", "link", or "picture"
- **THEN** the system accepts the type

#### Scenario: Invalid node type rejected
- **WHEN** a node is created or updated with a type not in the allowed set
- **THEN** the system rejects the request with HTTP 400

### Requirement: Node color
The system SHALL support an optional color property on each node. The color is a free-form string (e.g. hex color code, color name). A missing color field means no color is set.

#### Scenario: Node with color
- **WHEN** a node is created with color "#FF5733"
- **THEN** the system stores the color and returns it on retrieval

#### Scenario: Node without color
- **WHEN** a node is created without specifying a color
- **THEN** the system stores null as the color and returns null on retrieval

### Requirement: Picture upload
The system SHALL allow an authenticated user to upload a picture file for a node of type "picture" in a mind map they own. The system SHALL store the file on disk in a configurable uploads directory with a UUID-based filename and store the filename in the node's value field.

#### Scenario: Successful picture upload
- **WHEN** an authenticated user uploads a picture file to a picture node
- **THEN** the system saves the file with a UUID-based filename (e.g. "<uuid>.png")
- **AND** stores the filename in the node's value field
- **AND** returns the updated node with the value set

#### Scenario: Upload to non-picture node rejected
- **WHEN** an authenticated user attempts to upload a picture to a node of type "text"
- **THEN** the system rejects the request with HTTP 400
- **AND** returns an error indicating uploads are only allowed for picture nodes

#### Scenario: Upload replaces existing picture
- **WHEN** an authenticated user uploads a new picture to a node that already has a picture file
- **THEN** the system deletes the old file from disk
- **AND** saves the new file with a new UUID-based filename
- **AND** updates the node's value field

#### Scenario: Picture file served via URL
- **WHEN** a client requests GET /uploads/<filename>
- **THEN** the system serves the file from the uploads directory
