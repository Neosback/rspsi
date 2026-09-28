# OpenRune Studio Bridge

This repository is being refocused from a desktop map editor into the JVM companion for a browser-based OSRS content editor.

The browser application will own the editor experience: Svelte/TypeScript UI, WebGL2 rendering, interactive map state, tools, selection, undo/redo, overlays, and ordinary browser-side import/export.

This repository owns capabilities that are better kept on the JVM or require native local access:

- OpenRune FileStore and OSRS cache tooling;
- cache inspection, validation, encoding, packing, and explicit publication;
- OpenRune Server project discovery and compatibility inspection;
- Gradle task discovery/invocation and build verification;
- GameVal/RSCM and OpenRune source integration;
- Kotlin semantic/source indexing;
- local filesystem access and project/cache watching;
- a small loopback HTTP/WebSocket bridge for the web editor.

The bridge is optional for basic browser editing. It is required when the editor needs local OpenRune project integration, JVM tooling, direct cache publication, or build execution.

## Current transition state

The existing `Client` module contains the reusable cache, OSRS, world-format, and OpenRune integration code that will be extracted into headless modules.

The existing `Editor` module is legacy desktop UI/rendering code. It is frozen except for changes required to keep the repository buildable during extraction and will be removed after the headless core is proven independent.

Java-to-Kotlin migration continues leaf-first, but new migration work should prioritize code that survives this architecture: cache, OSRS formats, OpenRune integration, project inspection, protocol contracts, and bridge services.

## Target modules

The initial target is deliberately small:

- `:core` - neutral OSRS/cache domain and codecs;
- `:openrune` - OpenRune-specific project, FileStore, source, GameVal, and Gradle integration;
- `:server` - Ktor loopback API and WebSocket event stream.

Do not create these modules merely to move files. Extraction happens responsibility-by-responsibility with tests proving the boundary.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Bridge API](docs/API.md)
- [Cache model](docs/CACHE.md)
- [OpenRune integration](docs/OPENRUNE.md)
- [OpenRune server foundation reference](docs/OPENRUNE_SERVER_FOUNDATION.md)
- [Roadmap](docs/ROADMAP.md)

## Build

The repository still uses the current Gradle layout during migration:

```bash
./gradlew test
```

Desktop execution is not the target architecture and should not receive new product features.

## Reference trees

Vendored OpenRune Server and RuneLite sources remain references during the transition. They are not alternate application architectures and must not leak their implementation types through bridge-neutral contracts.

## License

See [LICENSE](LICENSE).
