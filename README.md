# OpenRune Server Studio

This repository is focused on one product: a local-first application for working with **OpenRune Server** projects.

It is not a general-purpose RSPS map editor. Legacy editor/rendering work is reference material, not active product code.

## Product scope

OpenRune Server Studio should make an OpenRune Server project easier to inspect, develop, run, validate, and maintain.

The Kotlin/JVM service provides the foundation for project discovery, OpenRune content/GameVal/source indexing, generated cache inspection, bounded Gradle operations, project watching, future runtime Agent integration, and the versioned local API used by Studio clients.

Map rendering, terrain brushes, scene editing, camera tools, object placement, and renderer parity are intentionally outside the active product scope.

## Active modules

- `:Protocol` - neutral application/runtime contracts.
- `:StudioService` - the Kotlin/JVM application service and local API.

## OpenRune authority

Studio opens an existing OpenRune Server project. The project's source files, Gradle build, content modules, GameVals/RSCM, and generated cache outputs remain authoritative.

This repository does not vendor a copy of OpenRune Server as application code.

## Legacy reference

All branch tips that existed before the September 29, 2026 reset are preserved through `archive/pre-openrune-reset-2026-09-29`.

Use that archive only when an old implementation is useful as reference. Do not reintroduce legacy editor architecture by default.

## Build

```bash
./gradlew foundationGate
```

See `docs/ARCHITECTURE.md`, `docs/OPENRUNE.md`, and `docs/ROADMAP.md`.
