# Companion API

## Purpose

The Companion API exposes local JVM/OpenRune tooling to the browser Studio without exposing internal Java/Kotlin/OpenRune object graphs.

The API is versioned under `/api/v1`. The Companion is local tooling, not a general network service.

## Transport

Use HTTP/JSON for bounded operations, WebSocket or SSE for future long-running operation events, and byte-range/binary HTTP when large cache data makes JSON wasteful.

All externally consumed contracts are versioned DTOs. Do not serialize PSI nodes, Gradle model objects, OpenRune FileStore objects, Java `Path`, or editor implementation classes directly.

## Local security model

The Companion binds to `127.0.0.1` and applies defense in depth against DNS rebinding and cross-origin access:

- every `/api/v1` request must use a loopback `Host` header;
- an `Origin`, when present, must also be localhost/loopback;
- every API request requires `X-OpenRune-Studio-Token`;
- the token comes from `OPENRUNE_STUDIO_TOKEN` or is generated securely per launch;
- arbitrary filesystem paths are accepted only by authenticated `POST /api/v1/project/open`;
- later project operations use an opaque project ID and paths derived from the opened project root;
- known project locations are rejected when real-path resolution escapes the selected root;
- arbitrary shell execution is never exposed.

For development, use a Vite proxy from the Studio origin to `127.0.0.1:8765` and inject the development token in the proxy. CORS is intentionally not enabled.

For packaged use, the intended model is for Companion to serve the built Studio from loopback so UI and API are same-origin. That serving layer is not implemented yet.

## Capability discovery

Before a project is opened:

```json
{
  "apiVersion": 1,
  "capabilities": ["project.open"]
}
```

Opening a project returns its actual capabilities:

```json
{
  "projectId": "opaque-session-id",
  "root": "/project/root",
  "capabilities": [
    "project.inspect",
    "cache.read",
    "content.index",
    "content.resolve",
    "source.index"
  ]
}
```

## Current resource families

```text
GET  /api/v1/status
POST /api/v1/project/open
GET  /api/v1/project/{projectId}

POST /api/v1/project/{projectId}/content/index
POST /api/v1/project/{projectId}/content/resolve
POST /api/v1/project/{projectId}/source/index

GET  /api/v1/project/{projectId}/cache/live/inspect
GET  /api/v1/project/{projectId}/cache/server/inspect
```

The project session is the filesystem security boundary. Do not reintroduce request-level arbitrary project paths.

## Error model

Errors use stable codes:

```json
{
  "code": "PROJECT_NOT_OPEN",
  "message": "Project session is not open.",
  "details": {}
}
```

The browser must not parse exception class names or free-form console output to determine state.

## Durable project ownership

The browser owns the live draft document, interactive state, undo/redo, and IndexedDB autosave/crash recovery.

Companion owns explicit durable save/publication to project files so changes remain visible to Git. Write support must perform stale-source checks, validation, atomic file replacement, optional OpenRune builds, and output verification.

The versioned edit/publish payload is still to be specified as a shared schema before write support is implemented.
