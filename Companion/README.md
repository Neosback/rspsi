# Companion

The Companion is the new Kotlin/JVM process behind OpenRune Studio.

Its job is to expose local-only capabilities that the browser editor cannot reliably own, such as OpenRune/JVM integration, FileStore operations, source indexing, project builds, and filesystem access.

This module must not depend on the legacy `Editor` module or on `Client/editor` rendering/tooling code.

## Current scope

The service is loopback-only and currently provides:

```
GET  http://127.0.0.1:8765/api/v1/status
POST http://127.0.0.1:8765/api/v1/openrune/inspect
```

The OpenRune inspection endpoint is passive. It detects project layout, expected cache/GameVal locations, and available source/build roots without running Gradle, loading server code, opening caches, or changing files.

FileStore, cache writes, SQLite, Compose, source indexing, build execution, and live-server integration will be added as separate focused changes.
