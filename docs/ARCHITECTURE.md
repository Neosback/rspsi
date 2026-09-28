# Architecture

## Direction

OpenRune Studio is split into two independently useful pieces:

```text
Browser editor (Svelte + TypeScript + WebGL2)
        |
        | versioned HTTP / WebSocket
        v
Local JVM companion (Kotlin)
        |
        +-- OpenRune FileStore / cache tooling
        +-- OpenRune project + Gradle tooling
        +-- GameVal/RSCM + source indexing
        +-- filesystem/build/watch capabilities
```

The browser editor is the product UI. The JVM process is a local tooling companion.

## Ownership

### Browser

The browser owns interactive authoring:

- workspace and panels;
- viewport and WebGL2 renderer;
- selection and picking;
- brushes and editing tools;
- active document state;
- undo/redo;
- visual overlays and previews;
- browser-side serialization that does not require JVM tooling.

Interactive pointer movement, camera changes, hover state, and ordinary render frames must never depend on RPC latency.

### JVM companion

The companion owns machine-local and JVM/OpenRune capabilities:

- FileStore-backed cache access;
- revision-aware OSRS cache validation and codecs;
- writable output/staging caches;
- project and source-tree inspection;
- OpenRune build discovery/invocation;
- source provenance and Kotlin semantic indexing;
- GameVal/RSCM resolution;
- local file watching;
- publication verification;
- bridge API/event transport.

It should be headless and useful from CLI/tests without the web editor.

## State model

Do not recreate the old architecture where the JVM owns every live editor object.

The browser owns the active authoring document. The JVM should prefer bounded operations:

```text
read resource -> neutral DTO
validate candidate -> diagnostics
publish candidate -> verified output
inspect project -> capabilities/provenance
run build -> streamed status
```

The companion may keep caches, indexes, project sessions, fingerprints, and watch state for performance and safety, but it is not the authoritative owner of pointer/tool/render state.

## Progressive capability

The editor must support two modes.

### Browser-only

Available:

- editing/rendering;
- manually selected/imported resources;
- previews;
- project files that are representable in browser APIs.

Unavailable:

- JVM OpenRune libraries;
- local Gradle execution;
- Kotlin PSI/source indexing;
- unrestricted local filesystem watching;
- direct local cache publication.

### Bridge-connected

Adds:

- OpenRune project discovery;
- cache read/write/pack/verify;
- Gradle builds;
- GameVal/source navigation;
- server-content overlays;
- local file watching and external-change diagnostics.

The frontend queries capabilities. It does not infer them from product names or paths.

## Dependency direction

Target dependency direction:

```text
Browser DTOs ----+
                 |
Companion -------+--> Protocol
                 |
OpenRune Agent --+

Companion -> openrune -> core
                    |
                    +---- external OpenRune libraries
```

The checked-in `:Protocol` module is the neutral contract seam. It must not expose Ktor, OpenRune, PSI, Gradle, FileStore, renderer, or editor implementation types.

No core API should require Ktor, UI, GLFW, ImGui, OpenGL, or Svelte concepts.

OpenRune-specific types should be reduced at the integration boundary before crossing into neutral protocol/domain contracts.

## Migration strategy

Do not perform a rewrite.

1. freeze new desktop features;
2. identify reusable headless seams in `Client`;
3. continue Java-to-Kotlin conversion along those seams;
4. prove headless cache and OpenRune operations with tests;
5. extract modules when dependency boundaries are real;
6. add Ktor transport over existing services;
7. integrate the web editor;
8. remove `Editor` and desktop-only dependencies.

A module extraction is successful only when it reduces dependencies. Moving files without changing dependency direction is not progress.
