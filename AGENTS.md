# AGENTS.md

Context for AI agents working on this codebase.

## Project Overview

This is the **backend** of a mind map solution. It is built with:

- **Scala 3**
- **Zio 2.x**

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Scala 3 |
| Effect system | Zio 2.x |
| Database | H2 (file-based) |
| JDBC / DB access | ZIO JDBC |
| API documentation | Swagger / OpenAPI |
| Testing | ZIO Spec (zio-test) |
| Domain | Mind maps backend |

## Database

The project uses **H2** in **file-based** mode for persistent storage. H2 runs embedded (in-process) and writes data to disk.

- **Mode:** File-based (not in-memory)
- **PostgreSQL compatibility:** Use `MODE=PostgreSQL` in the JDBC URL so SQL is compatible with PostgreSQL for easier migration later
- **Example URL:** `jdbc:h2:file:./data/mindmaps;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`

## Database access: ZIO JDBC

We use **ZIO JDBC** for database access. Reasons:

- **Lightweight and direct:** Raw SQL with minimal abstraction—ideal for recursive CTEs used to traverse mind map trees (subtree queries, path-from-root, depth).
- **Native ZIO integration:** Idiomatic ZIO 2.x interface, connection pooling, and SQL interpolation for safety.
- **Good fit for trees:** Mind map nodes are hierarchical; recursive CTEs are the natural way to query them, and ZIO JDBC makes it straightforward to run that SQL without an ORM layer.

## API documentation

API documentation MUST be created and maintained using **Swagger / OpenAPI**. Every HTTP API (including new or changed endpoints) must be described in OpenAPI (e.g. 3.x) and exposed via Swagger UI or equivalent so that consumers have a single, up-to-date contract and interactive docs.

## Testing

Tests MUST be written using **ZIO Spec** (zio-test). Use `Spec` and the ZIO Test DSL (e.g. `suite`, `test`, `assertTrue`, `assertZIO`) for unit and integration tests so that tests run inside the ZIO runtime and can use layers, effects, and resource management consistently.
