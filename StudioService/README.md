# StudioService

`StudioService` is the Kotlin/JVM application service for OpenRune Server Studio.

It provides the local project-aware backend used to inspect and work with an OpenRune Server checkout.

## Responsibilities

- OpenRune project validation and capability detection.
- Project-scoped sessions and filesystem boundaries.
- Content module and GameVal/RSCM indexing.
- Kotlin structural source indexing.
- Symbol-to-source resolution.
- Gradle wrapper task discovery through a bounded fixed command.
- Allowlisted finite Gradle operations for assemble, test, and cache build.
- LIVE/SERVER generated cache inspection through OpenRune FileStore.
- Loopback HTTP API, authentication, and stable API errors.
- Project index caching and explicit refresh.

## Current API

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

Opening a project is passive. It validates and inspects the checkout without executing Gradle or loading server code.

Gradle task discovery and execution happen only through explicit project-scoped endpoints. Execution accepts only the `assemble`, `test`, and `cache-build` operation IDs and never arbitrary Gradle arguments.

## Run

```bash
./gradlew :StudioService:run
```
