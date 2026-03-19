## Context

The application has user authentication (registration, login, JWT) and a user profile endpoint. The core domain — mind maps — has no implementation yet. We need to add mind map and node management as protected endpoints, following the existing patterns (ZIO services, H2 with ZIO JDBC, sealed error traits, ZIO Spec tests).

Mind maps are owned by users and contain a recursive tree of nodes. Each node has a type (text, link, picture), optional color, and a text field. Nodes of type "link" or "picture" have an additional "value" field — a URL for links, or a stored filename for pictures. Picture files are uploaded to the backend and stored on disk. The tree structure means nodes reference a parent node, with the root node having no parent.

## Goals / Non-Goals

**Goals:**
- CRUD endpoints for mind maps scoped to authenticated users
- Node management (add, update, delete) within a mind map
- Recursive cascade delete for nodes
- Automatic root node creation when a mind map is created
- Consistent API design with existing auth endpoints

**Non-Goals:**
- Real-time collaboration or WebSocket support
- Node reordering or drag-and-drop position tracking
- Sharing mind maps between users
- Pagination of mind map lists (can be added later)
- Image resizing or thumbnail generation

## Decisions

### 1. Database schema: Two tables with self-referential nodes

**Decision**: A `mind_maps` table and a `nodes` table where `nodes.parent_id` references `nodes.id` (nullable for root nodes).

**Rationale**: This is the simplest relational model for a recursive tree. H2 in PostgreSQL mode supports recursive CTEs for tree queries. Alternatives like adjacency list with path enumeration or nested sets add complexity we don't need yet.

**Schema**:
```sql
CREATE TABLE mind_maps (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id),
  name        VARCHAR(255) NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE nodes (
  id          UUID PRIMARY KEY,
  mind_map_id UUID NOT NULL REFERENCES mind_maps(id),
  parent_id   UUID REFERENCES nodes(id),
  node_type   VARCHAR(20) NOT NULL,  -- text, link, picture
  text        TEXT,
  value       VARCHAR(500),  -- URL for link, filename for picture; NULL for text nodes
  color       VARCHAR(50),
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 2. Picture file storage on disk

**Decision**: Store uploaded pictures in a configurable directory on the backend (e.g. `./uploads/`). Each file is saved with a UUID-based filename (e.g. `<uuid>.png`). The filename is stored in the node's `value` field. A static file serving endpoint exposes the uploads directory.

**Rationale**: Simple file-on-disk storage avoids external dependencies (S3, cloud storage) for the MVP. The UUID filename prevents collisions and path traversal issues. When a picture node or its mind map is deleted, the corresponding file is cleaned up.

### 3. Cascade delete via application-level recursive CTE

**Decision**: Delete nodes using a recursive CTE (`WITH RECURSIVE`) to find all descendants, then delete them in a single transaction.

**Rationale**: H2's `ON DELETE CASCADE` on self-referential foreign keys can be problematic. Application-level recursive delete gives explicit control and works reliably. The `mind_map_id` foreign key can use `ON DELETE CASCADE` to clean up all nodes when a mind map is deleted.

### 4. API structure under /api/mind-maps

**Decision**: Nest node operations under the mind map resource path.

```
POST   /api/mind-maps                          -- create mind map (with root node)
GET    /api/mind-maps                          -- list user's mind maps
GET    /api/mind-maps/:id                      -- get mind map with full node tree
PUT    /api/mind-maps/:id                      -- update mind map (name)
DELETE /api/mind-maps/:id                      -- delete mind map and all nodes

POST   /api/mind-maps/:id/nodes               -- add node (specify parent_id)
PUT    /api/mind-maps/:id/nodes/:nodeId        -- update node
DELETE /api/mind-maps/:id/nodes/:nodeId        -- delete node and descendants
POST   /api/mind-maps/:id/nodes/:nodeId/upload -- upload picture for a picture node
GET    /uploads/:filename                      -- serve uploaded picture files
```

**Rationale**: RESTful nesting reflects ownership. All endpoints are protected via `AuthMiddleware`. Mind map ownership is verified on every request.

### 5. Node tree returned as nested JSON

**Decision**: `GET /api/mind-maps/:id` returns the full node tree as nested JSON (each node contains a `children` array), assembled in-memory after a flat query.

**Rationale**: Mind maps are typically small enough to load fully. Assembling the tree in application code from a flat list (keyed by parent_id) is straightforward and avoids complex recursive JSON construction in SQL.

### 6. Service layer structure

**Decision**: Add `MindMapRepository`, `NodeRepository`, and `MindMapService` following existing patterns (trait + Live implementation + ZLayer).

**Rationale**: Consistent with `UserRepository`/`AuthService` patterns. The service layer handles authorization (ownership checks) and orchestration (create mind map + root node in one transaction).

## Risks / Trade-offs

- **[Large trees]** → Full tree loading could be slow for very large mind maps. Mitigation: acceptable for MVP; lazy loading can be added later.
- **[No optimistic locking]** → Concurrent edits could conflict. Mitigation: single-user access for now; versioning can be added later.
- **[Disk storage for pictures]** → Files on disk are not replicated or backed up automatically. Mitigation: acceptable for MVP; migration to cloud storage can be added later.
- **[Orphaned files]** → If deletion fails midway, picture files could be orphaned. Mitigation: delete files after successful DB transaction; a cleanup job can be added later.
- **[H2 recursive CTE support]** → H2's recursive CTE support is solid in PostgreSQL mode but less battle-tested than PostgreSQL. Mitigation: keep queries simple; migration to PostgreSQL is planned.
