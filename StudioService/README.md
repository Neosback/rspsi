# StudioService

`StudioService` is the Kotlin/JVM application service for OpenRune Server Studio.

It provides the local project-aware backend used to inspect and work with an OpenRune Server checkout.

## Responsibilities

- OpenRune project validation and capability detection.
- Project-scoped sessions and filesystem boundaries.
- Content module and GameVal/RSCM indexing.
- Kotlin structural source indexing.
- Symbol-to-source resolution.
- LIVE/SERVER generated cache inspection through OpenRune FileStore.
- Loopback HTTP API, authentication, and stable API errors.
- Project index caching and explicit refresh.

## Current API

```text
GET  /api/v1/status
POST /api/v1/project/open
GET  /api/v1/project/{projectId}
POST /api/v1/project/{projectId}/content/index
POST /api/v1/project/{projectId}/content/resolve
POST /api/v1/project/{projectId}/source/index
POST /api/v1/project/{projectId}/index/refresh
GET  /api/v1/project/{projectId}/cache/live/inspect
GET  /api/v1/project/{projectId}/cache/server/inspect
```

Opening a project is passive. It validates and inspects the checkout without executing Gradle or loading server code.

## Run

```bash
./gradlew :StudioService:run
```
