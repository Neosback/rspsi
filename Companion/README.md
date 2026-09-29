# OpenRune Server Studio application service

This module is the current Kotlin/JVM backend for OpenRune Server Studio.

It owns machine-local OpenRune capabilities that should not be duplicated in UI code:

- project open/inspection and capability detection;
- content module and GameVal/RSCM indexing;
- Kotlin structural source indexing;
- content-symbol resolution;
- generated LIVE/SERVER cache inspection through OpenRune FileStore;
- project-scoped filesystem access;
- local API security and transport.

It must not grow map editing, renderer, terrain, camera, scene-authoring, or desktop-editor responsibilities.

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

Project opening is passive: it must not run Gradle, load server code, or mutate files.

The next backend priorities are build/task discovery and invocation, project watching, source-safe write/publish operations, and the minimal live OpenRune Agent.
