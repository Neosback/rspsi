# Companion

The Companion is the new Kotlin/JVM process behind OpenRune Studio.

Its job is to expose local-only capabilities that the browser editor cannot reliably own, such as OpenRune/JVM integration, FileStore operations, source indexing, project builds, and filesystem access.

This module must not depend on the legacy `Editor` module or on `Client/editor` rendering/tooling code.

## Current scope

The service is loopback-only and currently provides:

```
GET  http://127.0.0.1:8765/api/v1/status
POST http://127.0.0.1:8765/api/v1/openrune/inspect
POST http://127.0.0.1:8765/api/v1/openrune/content/index
POST http://127.0.0.1:8765/api/v1/cache/inspect
```

OpenRune project inspection is passive. It detects project layout, expected cache/GameVal locations, and available source/build roots without running Gradle, loading server code, or changing files.

Content indexing discovers OpenRune content modules plus plugin-local `gamevals.toml` and generated `.data/gamevals/*.rscm` mappings. It emits neutral module/GameVal DTOs for the browser. The original Kotlin/TOML/RSCM files remain authoritative.

Cache inspection uses OpenRune FileStore in read-only mode and currently exposes archive/index structure plus revision metadata when `version.dat` provides it. The browser remains responsible for map/model decoding and rendering.

Cache writes, SQLite persistence, Compose, Kotlin semantic indexing, build execution, and live-server integration will be added as separate focused changes.
