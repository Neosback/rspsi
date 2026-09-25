# OpenRune Studio

OpenRune Studio (repository `RSPSiSuite`, root Gradle project `RSPSi`) is a from-scratch Java
OSRS map editor and content-authoring studio. It has its own per-triangle software renderer,
a native OpenGL renderer, a Dear ImGui/GLFW desktop shell, a modular core runtime, typed settings,
and an external extension architecture.
It is not a RuneLite plugin and does not embed RuneLite's client.

## Modules

- **`Client`** - cache/definition loading, the world document model, the rendering pipeline
  (software and OpenGL-neutral packet builders), editor tools, selection, and the public neutral
  `EditorPlugin` extension point. No UI toolkit code; buildable and testable headless.
- **`Editor`** - the native Dear ImGui/GLFW desktop shell ("Studio"): panels, HUDs, toolbars,
  and the OpenGL scene renderer. Launched through `com.rspsi.studio.StudioMain`.

The older JavaFX editor still ships inside `Editor` but is legacy; the native Studio shell is
the product surface.

## Build, test, run

Java 21 is required. Gradle pins the Java 21 toolchain, so a newer system JDK is not used by
accident.

```bash
./gradlew :Client:compileJava :Editor:compileJava   # compile both modules
./gradlew :Client:test :Editor:test                  # full test suite
./gradlew foundationGate                             # the CI gate
./gradlew :Editor:run                                # launch Studio
```

`foundationGate` is the required local/CI baseline. It runs the test suites, `check` (including
the Client architecture-boundary checks), `verifyNativeBoundary`, and `renderingAuditGate`.
It needs no external cache.

Always compile and test **both** modules after a change, even a Client-only one: Editor consumes
Client's public API and breaks silently otherwise.

## Real-cache verification (opt-in)

Real OSRS caches stay outside the repository. The following tasks are opt-in and never mutate the
selected cache. Without `RSPSI_OSRS_CACHE`, `verifyOsrsRevision` runs only the deterministic
fixtures and reports real-cache parity as pending.

```bash
RSPSI_OSRS_CACHE=/path/to/cache \
RSPSI_OSRS_REGION_X=50 RSPSI_OSRS_REGION_Y=50 RSPSI_OSRS_REVISION=240 \
./gradlew verifyOsrsRevision
```

`RSPSI_OSRS_REGION_X`, `RSPSI_OSRS_REGION_Y`, and `RSPSI_OSRS_REVISION` must be set together.
A pinned reference cache can be acquired through OpenRune FileStore with
`./gradlew prepareOsrsReferenceCache` (network access). See
[PHASE0_LUMBRIDGE_ACCEPTANCE.md](docs/PHASE0_LUMBRIDGE_ACCEPTANCE.md) for the full recipe and the
evidence a PR should record. Related tasks: `verifyOsrsRevisionMatrix`, `verifyOsrsTextures`,
`verifyOsrsInstance`, `parityGate`.

## Documentation

Start with [AGENTS.md](AGENTS.md) for repository orientation and conventions. When documents
disagree, the order of authority is:

1. current production code plus passing tests
2. [AI_ARCHITECTURE_OVERVIEW.md](docs/AI_ARCHITECTURE_OVERVIEW.md) - system mental model and canonical ownership
3. [AI_CHANGE_PLAYBOOK.md](docs/AI_CHANGE_PLAYBOOK.md) - deterministic implementation routes for common changes
4. [EDITOR_DEVELOPMENT_ARCHITECTURE.md](docs/EDITOR_DEVELOPMENT_ARCHITECTURE.md) - internal modular-monolith code placement
5. [RENDERING_PARITY_MANIFEST.json](docs/RENDERING_PARITY_MANIFEST.json) - live rendering-correctness backlog
6. [ROADMAP.md](docs/ROADMAP.md) - product order and near-term PR sequence
7. [STUDIO_SEMANTIC_API.md](docs/STUDIO_SEMANTIC_API.md) - authored-world / resolved-scene API contract
8. [UI_WORKSPACE_CONTRACT.md](docs/UI_WORKSPACE_CONTRACT.md) - in-project editor shell and extension UI placement
9. [CACHE_RENDERING_WORKSPACE_OWNERSHIP.md](docs/CACHE_RENDERING_WORKSPACE_OWNERSHIP.md) - cache, rendering, server, and shell ownership
10. [PROJECT_LAUNCHER_AND_DASHBOARD.md](docs/PROJECT_LAUNCHER_AND_DASHBOARD.md) - startup and project lifecycle
11. [OPENRUNE_ECOSYSTEM_INTEGRATION.md](docs/OPENRUNE_ECOSYSTEM_INTEGRATION.md) - OpenRune Server/cache guardrails
12. [OPENRUNE_MAVEN_CATALOG.md](docs/OPENRUNE_MAVEN_CATALOG.md) - OpenRune dependency inventory

Reference material:

- [RUNELITE_REFERENCE_GUIDE.md](docs/RUNELITE_REFERENCE_GUIDE.md) - problem-to-source lookup into the vendored RuneLite tree
- [TERRAINI_REFERENCE.md](docs/TERRAINI_REFERENCE.md) - written algorithm notes (no vendored source)
- [TEXTURE_PARITY_FIXTURE.md](docs/TEXTURE_PARITY_FIXTURE.md), [MAP_STUDIO_1_0_ACCEPTANCE.md](docs/MAP_STUDIO_1_0_ACCEPTANCE.md) - fixture and acceptance recipes

## Reference sources and licensing

`RuneLite-melxin/` is a genuine BSD 2-Clause-licensed RuneLite fork vendored as read-only
reference material for OSRS-accurate behavior. It is not a build dependency. Material with
unclear licensing (for example decompiled third-party output) is deliberately kept out of the
repository; see `docs/TERRAINI_REFERENCE.md` for how that is handled instead.
