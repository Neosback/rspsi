# Companion

The Companion is the new Kotlin/JVM process behind OpenRune Studio.

Its job is to expose local-only capabilities that the browser editor cannot reliably own, such as OpenRune/JVM integration, FileStore operations, source indexing, project builds, and filesystem access.

This module must not depend on the legacy `Editor` module or on `Client/editor` rendering/tooling code.

## Current scope

The first slice intentionally provides only a loopback Ktor service with a versioned status endpoint:

```
GET http://127.0.0.1:8765/api/v1/status
```

OpenRune, FileStore, SQLite, Compose, source indexing, and build execution will be added as separate focused changes.
