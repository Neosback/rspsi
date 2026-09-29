# OpenRune Server Studio

This repository is now focused on one product: a local-first application for working with **OpenRune Server** projects.

It is not a general-purpose RSPS map editor. The legacy desktop renderer, map-editing tools, RuneLite reference tree, rendering parity infrastructure, and vendored OpenRune Server snapshot are legacy reference material, not active product code.

## Product scope

OpenRune Server Studio should make an OpenRune Server project easier to inspect, develop, run, validate, and maintain.

The current Kotlin/JVM application service provides the foundation for:

- opening and validating an OpenRune Server checkout;
- discovering project structure and capabilities;
- indexing OpenRune content modules, GameVals/RSCM, and Kotlin sources;
- resolving content symbols back to their owning source;
- inspecting generated LIVE/SERVER cache outputs through OpenRune FileStore;
- running supported Gradle build/test/run operations;
- watching project files and generated outputs;
- integrating with a future in-server Agent for runtime/plugin/event visibility;
- exposing versioned local APIs for the Studio UI.

Map rendering, terrain brushes, scene editing, camera tools, object placement, and renderer parity are intentionally outside the active product scope.

## Active modules

- `:Protocol` - neutral application/runtime contracts.
- `:Companion` - the current Kotlin/JVM application service and local API.

The `Companion` module name is transitional. New work should treat it as the OpenRune Server Studio backend rather than as a bridge for a separate map editor.

## OpenRune authority

Studio opens an existing OpenRune Server project. The project's source files, Gradle build, content modules, GameVals/RSCM, and generated cache outputs remain authoritative.

This repository does not vendor a copy of OpenRune Server as application code.

## Legacy reference

All branch tips that existed before the September 29, 2026 reset are preserved through:

`archive/pre-openrune-reset-2026-09-29`

Use that archive only when an old implementation is useful as reference. Do not reintroduce legacy editor architecture by default.

## Build

```bash
./gradlew foundationGate
```

See `docs/ARCHITECTURE.md`, `docs/OPENRUNE.md`, and `docs/ROADMAP.md`.
