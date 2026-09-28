# AGENTS.md

This repository is transitioning from the old native OpenRune Studio application into the JVM companion for a Svelte/TypeScript/WebGL2 editor.

## Read first

Before architecture-affecting work read:

1. `docs/ARCHITECTURE.md`
2. the concern-specific document: `API.md`, `CACHE.md`, or `OPENRUNE.md`
3. `docs/ROADMAP.md` when sequencing matters

Production code plus passing tests describes what exists now. The documents above describe the intended ownership during extraction.

## Product boundary

The browser editor owns:

- UI/workspace;
- WebGL2 rendering;
- camera and viewport behavior;
- selection and interaction tools;
- active editor state and ordinary undo/redo;
- client-side previews and overlays.

The JVM companion owns:

- OpenRune FileStore integration;
- OSRS cache codecs and validation that rely on JVM/OpenRune tooling;
- explicit cache publication and verification;
- OpenRune project inspection;
- Gradle invocation;
- GameVal/RSCM integration;
- Kotlin/source semantic indexing;
- local filesystem/project watching;
- loopback HTTP/WebSocket transport.

Do not move browser responsibilities into the JVM service merely because equivalent desktop code exists today.

## Legacy Editor module

`Editor/` is frozen legacy desktop code. Do not add features, panels, render paths, workspace concepts, or new architectural dependencies to it.

Only make changes there when required to:

- preserve build/test compatibility during extraction;
- delete obsolete code;
- remove a dependency on reusable headless code.

The module is removed once the reusable backend no longer depends on it.

## Java-to-Kotlin migration

Migration continues leaf-first and one ownership boundary at a time.

Prioritize code expected to survive the pivot:

1. neutral cache/OSRS value types and codecs;
2. OpenRune adapters and project inspection;
3. build/source/GameVal services;
4. bridge protocol and service code.

Rules:

- new production files are Kotlin unless a concrete interop constraint requires Java;
- do not mix broad redesign with mechanical migration;
- preserve Java call shape with `@JvmRecord`, `@JvmStatic`, `@JvmOverloads`, or explicit accessors where existing callers require it;
- delete dead code instead of converting it;
- keep hot binary/codec paths allocation-aware;
- migrate tests with the responsibility where useful;
- keep each PR small enough to review and revert independently.

## Canonical responsibilities

| Concern | Current/target owner |
| --- | --- |
| OpenRune cache access | `OpenRuneCacheStore` and its extracted successor |
| terrain/location encoding | `OsrsRegionEncoder` |
| terrain/location decoding | `OsrsRegionDecoder` |
| map archive access | `MapService` / `OsrsMapService` |
| OpenRune project inspection | `OpenRuneServerAdapter` / `ServerProjectInspection` |
| Gradle model/build integration | `server/gradle` plus build-runner services |
| OpenRune semantic/source integration | `server/openrune` |
| web transport | future `:server` Ktor module |
| editor rendering/UI | separate web-editor repository |

Before adding another service, adapter, codec, build path, or project detector, search for the existing owner and extend it.

## Cache publication

Opening a cache is not permission to mutate it.

Normal reads remain read-only. Direct writes require an explicit writable output/staging cache, validation, flush, reopen, and verification. Connected OpenRune LIVE/SERVER caches are generated project outputs, not ordinary mutable workspaces.

## OpenRune project publication

For OpenRune-owned resources:

1. determine the authoritative source;
2. detect stale external edits;
3. write the supported source form atomically;
4. invoke the project's detected build;
5. reopen the generated output;
6. verify the expected semantic result.

Never silently patch generated LIVE/SERVER caches as a fallback.

## Bridge rules

The eventual bridge is local tooling, not a public internet service.

- bind loopback by default;
- expose explicit versioned DTOs, not internal JVM/OpenRune classes;
- use HTTP for request/response operations and WebSocket for events/logs;
- make long-running build operations observable and cancellable where practical;
- keep browser-only editor workflows usable when the bridge is absent;
- report capabilities rather than forcing the frontend to guess.

## PR discipline

Keep at most one or two focused PRs active. Finish validation and merge before starting additional work.

A good migration PR reduces ambiguity: fewer duplicate paths, clearer ownership, and no new dependency on the old desktop architecture.
