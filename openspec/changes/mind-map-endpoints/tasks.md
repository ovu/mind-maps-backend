## 1. Database Schema

- [x] 1.1 Add `mind_maps` table creation to schema initialization in Main.scala
- [x] 1.2 Add `nodes` table creation with self-referential parent_id and ON DELETE CASCADE from mind_maps

## 2. Domain Models

- [x] 2.1 Create `MindMap` case class (id, userId, name, createdAt) with JSON codecs
- [x] 2.2 Create `Node` case class (id, mindMapId, parentId, nodeType, text, value, color, createdAt) with JSON codecs
- [x] 2.3 Create `NodeType` enum (text, link, picture) with JSON codec
- [x] 2.4 Create request/response DTOs (CreateMindMapRequest, UpdateMindMapRequest, CreateNodeRequest, UpdateNodeRequest, MindMapResponse, MindMapListItem, NodeResponse with nested children)

## 3. Repository Layer

- [x] 3.1 Create `MindMapRepository` trait (insert, findByUserId, findByIdAndUserId, updateName, delete)
- [x] 3.2 Implement `MindMapRepositoryLive` with H2 JDBC queries
- [x] 3.3 Create `NodeRepository` trait (insert, findByMindMapId, findById, update, deleteWithDescendants)
- [x] 3.4 Implement `NodeRepositoryLive` with recursive CTE for cascade delete and flat node queries

## 4. Service Layer

- [x] 4.1 Create `MindMapService` trait with mind map CRUD operations and node operations
- [x] 4.2 Implement `MindMapServiceLive` with ownership checks, root node auto-creation, and tree assembly from flat node list
- [x] 4.3 Define sealed error trait (`MindMapError`) for not-found, unauthorized, validation errors

## 5. Picture Storage

- [x] 5.1 Create `FileStorageService` trait (save, delete, serve) for managing picture files on disk
- [x] 5.2 Implement `FileStorageServiceLive` with configurable uploads directory and UUID-based filenames
- [x] 5.3 Add cleanup logic to delete picture files when picture nodes or mind maps are deleted

## 6. HTTP Routes

- [x] 6.1 Create `MindMapRoutes` with mind map CRUD endpoints (POST/GET/GET/:id/PUT/:id/DELETE/:id under /api/mind-maps)
- [x] 6.2 Add node management endpoints (POST/PUT/DELETE under /api/mind-maps/:id/nodes)
- [x] 6.3 Add picture upload endpoint (POST /api/mind-maps/:id/nodes/:nodeId/upload)
- [x] 6.4 Add static file serving for uploads directory (GET /uploads/:filename)
- [x] 6.5 Wire routes into Main.scala server with all required ZLayers

## 7. OpenAPI Specification

- [x] 7.1 Update openapi.yaml with mind map, node, and picture upload endpoint definitions and schemas

## 8. Tests

- [x] 8.1 Write MindMapRepository tests (CRUD operations, ownership isolation)
- [x] 8.2 Write NodeRepository tests (add, update, delete with cascade, tree retrieval)
- [x] 8.3 Write MindMapService tests (root node creation, ownership enforcement, tree assembly)
- [x] 8.4 Write picture upload and file storage tests
- [x] 8.5 Write MindMapRoutes integration tests (HTTP request/response, auth enforcement, error responses)
