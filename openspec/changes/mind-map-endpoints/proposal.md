## Why

The application currently only supports user authentication. Users need the ability to create and manage mind maps — the core domain of the application. Mind maps with recursive node structures enable users to organize ideas visually with rich content types.

## What Changes

- Add mind map CRUD endpoints (create, read, update, delete) scoped to authenticated users
- Add node management endpoints (add, update, delete nodes within a mind map)
- Introduce recursive node structure where each node can contain child nodes
- Support node types: text, link, picture — every node has a text field; link and picture nodes have an additional value field
- Support optional node color
- Picture upload: store pictures on backend disk with UUID-based filenames
- Cascade delete: removing a node removes all its descendants and associated picture files
- Every mind map is created with a root node automatically
- Add database tables for mind maps and nodes

## Capabilities

### New Capabilities

- `mind-map-management`: CRUD operations for mind maps (create, list, get, update, delete) belonging to authenticated users. Each mind map has a name, creation datetime, and a root node created automatically.
- `node-management`: Add, update, and delete nodes within a mind map. Nodes have a recursive tree structure, support types (text, link, picture), optional color, and a text field. Link and picture nodes have an additional value field. Pictures can be uploaded and are stored on disk. Deleting a node cascades to all descendants and cleans up picture files.

### Modified Capabilities

_(none)_

## Impact

- **Database**: New `mind_maps` and `nodes` tables with foreign keys to `users` and self-referential parent-child relationships
- **API**: New protected endpoints under `/api/mind-maps` and nested node routes
- **Dependencies**: No new library dependencies expected (ZIO JDBC supports recursive queries via CTEs)
- **OpenAPI**: New endpoint definitions added to `openapi.yaml`
