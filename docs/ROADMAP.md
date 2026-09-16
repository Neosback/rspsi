# RSPSi Stabilization and Modernization Roadmap

This is the canonical technical progress ledger. Statuses are evidence-based:

- `not-started` — no implementation exists
- `in-progress` — implementation has begun
- `implemented-unverified` — code exists but lacks behavioral coverage
- `verified` — automated and/or documented manual acceptance is passing
- `blocked` — progress requires an external decision or dependency
- `deferred` — intentionally postponed

The source-priority and ownership record is maintained in
[`REFERENCE_ECOSYSTEM.md`](REFERENCE_ECOSYSTEM.md). It is research context;
this file is the executable progress ledger.

The resource intake and provenance ledger is maintained in
[`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md). A catalog entry is not
permission to copy code, bundle assets, or add a runtime dependency.

## Current baseline

| Area | Status | Evidence / next action |
|---|---|---|
| Gradle multi-module build | verified | `./gradlew test` compiles all modules; tests now run on JUnit Platform |
| JavaFX editor and software renderer | implemented-unverified | Existing `Editor` and `Client` modules; retain during stabilization |
| Four-plane terrain and shaped tiles | implemented-unverified | `MapRegion` and `SceneGraph`; add fixture coverage |
| Underlays, overlays, flags, bridges | implemented-unverified | Existing map arrays and encode/decode paths; add semantic tests |
| Object placement/deletion | implemented-unverified | Existing `SceneGraph`/`MapRegion` paths; add object fixture coverage |
| Selection and copy/import/export | implemented-unverified | Existing `SceneGraph` operations; add grouped-edit tests |
| Undo/redo | implemented-unverified | Existing `TileChange` hierarchy and static `SceneGraph` stacks |
| Autosave | implemented-unverified | Existing `AutoSaveJob`; add recovery smoke test |
| Legacy/317 cache loading | implemented-unverified | Existing Displee-backed `Cache`; protect before migration |
| OSRS cache support | in-progress | `OSRSPlugin` exists; OSRS map-index methods still contain unsupported stubs |
| Neutral cache boundary | in-progress | `CacheStore` facade introduced; migrate consumers incrementally |
| Neutral definitions | in-progress | Definition views/provider introduced; expand only as consumers migrate |
| Command/session editing core | in-progress | Core model, command history, and session introduced; adapt existing tools next |
| UI-neutral editor contracts | in-progress | Neutral pointer, tool, inspector, viewport, and renderer seams introduced; command-backed underlay brush is the first migrated tool path |
| Live legacy document bridge | in-progress | MapRegion terrain import and underlay synchronization are implemented; UI activation remains opt-in |
| OpenRune backend | in-progress | 2.4.19 compatibility spike is isolated behind `CacheStore`; legacy remains default |
| Resource catalog and provenance | in-progress | [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md) separates dependencies, donors, oracles, visual references, and license-review items; intake evidence remains |
| RuneLite/TSPS parity harness | not-started | Add golden fixtures after cache seam is stable |
| Lua, plugin permissions, Plugin Hub | deferred | Begin only after native command/plugin API is stable |
| Renderer/UI rewrite | deferred | Current JavaFX renderer remains the compatibility surface |

## Phase gates

### Phase 0 — Safety net

- Add JUnit 5 tests and fixture-backed semantic map tests.
- Document the manual smoke checklist in `docs/MANUAL_SMOKE_TEST.md`.
- Keep the baseline commit before structural refactors.

### Phase 1 — Cache and definition seams

- Keep Displee behind `LegacyDispleeCacheStore`.
- Remove Displee types from new editor-facing APIs.
- Preserve current loader behavior until adapter parity tests pass.

### Phase 2 — Editing core

- Route new edits through `EditCommand` and `CommandHistory`.
- Use `EditorSession` for world, selection, dirty state, and history.
- Keep `SceneGraph` as a compatibility facade while behavior migrates.
- Keep the core free of JavaFX, ImGui, renderer backend, and cache-library
  imports; see [`ARCHITECTURE.md`](ARCHITECTURE.md).

### Phase 3 — Modern OSRS backend

- Add OpenRune only behind neutral interfaces. The first milestone is a
  read-only compatibility spike; writable support is not implied by a green
  compile.
- Complete OSRS map-index support.
- Require legacy regression and OSRS parity fixtures before switching defaults.

### Resource intake gate

- Record upstream URL, revision, license evidence, and intended role before
  importing code, assets, or dependencies.
- Gather the first RuneLite DevTools, TSPS, OpenRune-Server, revision-240,
  and visual-map evidence set described in [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md).
- Do not bundle or depend on resources marked `license-review`.

### Phase 4 — Editing improvements

Prioritize terrain sculpting, object transforms, richer selection, collision tools, then region/asset workflows. Every operation must use the command/history path.
