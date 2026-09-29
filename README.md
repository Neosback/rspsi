# OpenRune Server Studio

OpenRune Server Studio is a local development application for **OpenRune Server** projects.

It provides a structured view of an OpenRune project and exposes project-aware tooling through a local Kotlin/JVM service.

## Current capabilities

- Open and validate an OpenRune Server project.
- Detect project structure and available capabilities.
- Discover content modules.
- Index GameVals and generated RSCM data.
- Index Kotlin content source with PSI-backed structural analysis.
- Resolve OpenRune symbols such as `content.rock` to source, handlers, references, modules, and GameVals.
- Inspect generated `.data/cache/LIVE` and `.data/cache/SERVER` caches through OpenRune FileStore.
- Discover the tasks exposed by an opened project's Gradle wrapper on explicit request.
- Run bounded allowlisted Gradle operations for `assemble`, `test`, and `:or-cache:buildCache`.
- Maintain project-scoped index snapshots and refresh them when project inputs change.
- Expose the functionality through a loopback-only, token-protected HTTP API.

## Modules

### `:StudioService`

The Kotlin/JVM application service. It owns project sessions, OpenRune inspection, content/source indexing, Gradle project discovery, cache inspection, API security, and local transport.

### `:Protocol`

Neutral contracts shared by Studio components and runtime integrations.

## Project authority

The opened OpenRune Server checkout remains authoritative.

Studio reads project structure and source directly from the checkout. Generated LIVE and SERVER caches are treated as build outputs. Studio does not replace OpenRune's Gradle build, source layout, GameVals, or cache tooling.

Gradle is never executed merely because a project is opened. Task discovery and execution are explicit project-scoped operations. Execution is limited to the `assemble`, `test`, and `cache-build` operation IDs; callers cannot provide arbitrary Gradle tasks or arguments.

## Local API

The current API is versioned under `/api/v1`.

```text
GET  /api/v1/status
POST /api/v1/project/open
GET  /api/v1/project/{projectId}
GET  /api/v1/project/{projectId}/gradle/tasks
GET  /api/v1/project/{projectId}/gradle/operations
POST /api/v1/project/{projectId}/gradle/operations
GET  /api/v1/project/{projectId}/gradle/operations/{operationId}

POST /api/v1/project/{projectId}/content/index
POST /api/v1/project/{projectId}/content/resolve
POST /api/v1/project/{projectId}/source/index
POST /api/v1/project/{projectId}/index/refresh

GET  /api/v1/project/{projectId}/cache/live/inspect
GET  /api/v1/project/{projectId}/cache/server/inspect
```

The service binds to loopback and requires an OpenRune Studio session token.

## Requirements

- Java 21
- Gradle 8.14.3

## Build and test

```bash
./gradlew foundationGate
```

Run StudioService directly with:

```bash
./gradlew :StudioService:run
```

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/API.md`
- `docs/OPENRUNE.md`
- `docs/CACHE.md`
- `docs/OPENRUNE_SERVER_FOUNDATION.md`
- `docs/ROADMAP.md`
