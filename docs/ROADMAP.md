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

The locked product direction and workspace design are maintained in
[`PRODUCT_DESIGN.md`](PRODUCT_DESIGN.md).

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
| OSRS cache support | implemented-unverified | `OSRSPlugin` discovers named `mX_Y`/`lX_Y` archives through `CacheStore`; real-cache parity and writable packing remain |
| Neutral cache boundary | in-progress | `CacheStore` facade introduced; migrate consumers incrementally |
| Neutral map service | implemented-unverified | `MapIndexTable` and `OsrsMapService` provide named OSRS map discovery, file-0/file-1 reads, and safe writes to existing regions; `OsrsRegionDecoder`/`OsrsRegionEncoder` convert canonical terrain and objects with semantic round-trip coverage; real-cache fixture remains |
| OSRS region save coordination | implemented-unverified | `OsrsRegionSaveCoordinator` encodes both region payloads before writing, flushes through the neutral map service, and marks `EditorSession` saved only after success; writable OpenRune packing remains gated |
| Neutral definitions | implemented-unverified | Object/floor views plus texture/model and collision contracts and an OpenRune object/floor/texture/collision adapter exist; real-cache fixture parity remains |
| Command/session editing core | in-progress | Core model, command history, and session introduced; adapt existing tools next |
| Session state notifications | implemented-unverified | Neutral edit/save-state and selection listeners now support synchronized frontend panels; thread/FX scheduling and full legacy binding remain |
| WorldFragment copy/paste | implemented-unverified | Canonical multi-plane fragment capture and atomic paste/undo command added; UI import/export wiring remains |
| Dirty-region invalidation | implemented-unverified | `EditorSession` merges affected tiles into 8×8 `DirtyRegion` batches; renderer/cache consumers remain |
| Unified selection service | implemented-unverified | Tile, tile-set, area, vertex, single-object, multi-object, and fragment selection values are available; viewport/tool migration remains |
| OSRS coordinate and tile inspector contract | implemented-unverified | `WorldWindow`, `WorldTileAddress`, and `TileInspectorSnapshot` provide world/region/chunk breakdowns and raw flag semantics for frontend overlays; live hover wiring remains |
| First-party terrain tools | implemented-unverified | Command-backed overlay, vertex-aware raise/lower with radius/falloff, ramp, bilinear height sampling, tile-flag, flatten, and neighbour-aware smoothing brushes are tested; legacy UI wiring remains |
| First-party object commands | implemented-unverified | Place/delete/move/rotate plus atomic multi-object moves, rotations, and definition replacement are tested through `EditorSession`; richer transform coverage remains |
| First-party object tools | implemented-unverified | Place/delete/rotate plus pick-on-drag move, duplicate, box object selection, move-selection, rotate-selection, and replace-selection tools invoke canonical commands; multi-object duplicate remains |
| Canonical terrain mesh topology | implemented-unverified | RSPSi-owned `TerrainMeshBuilder` covers 13 shapes × 4 rotations, corners, and integer midpoint heights; a 52-case deterministic golden matrix now locks topology, while TSPS/RuneLite parity remains |
| Canonical collision map | implemented-unverified | RSPSi-owned flags/map plus `OsrsCollisionBuilder` provide bridge-aware terrain masks, roof semantics, rotated footprint collision, and wall/ground categories; route/LOS parity and complete definition fixtures remain |
| Route and line-of-sight preview | implemented-unverified | Neutral bounded `RouteFinder` provides collision-aware routes, corner-cutting protection, and projectile LOS checks; OpenRune-Server parity and reach strategies remain |
| Neutral world validation | implemented-unverified | `WorldValidator` reports broken shared edges, unsupported OSRS map values, duplicate/invalid objects, missing definitions, and definition-backed footprint bounds; the controlled workspace now exposes live Validation diagnostics, while full parity rules remain |
| UI-neutral editor contracts | in-progress | Neutral pointer, tool, inspector, viewport, and renderer seams introduced; command-backed underlay brush is the first migrated tool path |
| Live legacy document bridge | in-progress | MapRegion terrain import and underlay synchronization are implemented; UI activation remains opt-in |
| OpenRune backend | in-progress | 2.4.19 compatibility spike and neutral OSRS region decoder are isolated behind `CacheStore`; legacy remains default and real-cache parity remains pending |
| Resource catalog and provenance | implemented-unverified | [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md) and [`RESOURCE_INTAKE_2026-09-16.md`](RESOURCE_INTAKE_2026-09-16.md) record roles, commits, license evidence, inspected paths, and current adoption tests |
| OSRS-only product scope | in-progress | Scope and migration policy are locked in [`PRODUCT_DESIGN.md`](PRODUCT_DESIGN.md); legacy paths remain quarantined during parity work |
| Project/cache identity metadata | implemented-unverified | Neutral `OsrsCacheMetadata`, `ProjectMetadata`, JSON persistence, and explicit read-only mismatch assessment added; cache discovery and UI remain |
| Controlled workspace contracts | implemented-unverified | UI-neutral dock/panel/placement types, validated presets, `ControlledWorkspaceShell`, and an opt-in `MainWindow` bridge that reuses the legacy renderer; session-backed inspector/history/validation panels bind after map-ready, while full UI smoke coverage remains |
| RuneLite/TSPS parity harness | in-progress | `OsrsRevisionVerifier` can inspect an explicitly supplied OpenRune cache and run region decode/validation/collision/semantic round-trip checks; licensed golden comparisons remain |
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
- Discover named `mX_Y`/`lX_Y` archive IDs through the neutral
  cache boundary; prove the path against a licensed representative cache.
- Require legacy regression and OSRS parity fixtures before switching defaults.

### Product-scope gate

- Treat OSRS/OpenRune as the only production target.
- Quarantine legacy 317/custom paths; do not add new features to them.
- Retire legacy product UI only after OSRS parity and migration coverage pass.

### Resource intake gate

- Record upstream URL, revision, license evidence, and intended role before
  importing code, assets, or dependencies.
- Gather the first RuneLite DevTools, TSPS, OpenRune-Server, revision-240,
  and visual-map evidence set described in [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md).
- Do not bundle or depend on resources marked `license-review`.

### Phase 4 — Editing improvements

Prioritize terrain sculpting, object transforms, richer selection, collision tools, then region/asset workflows. Every operation must use the command/history path.

The first controlled JavaFX workspace shell is now available as a frontend
adapter. It materializes the neutral workspace presets into fixed tool and
inspector rails, a centered viewport, and controlled bottom tabs. It does not
own document state, renderer state, or arbitrary docking; wiring it into the
legacy `MainWindow` remains a separate compatibility milestone.
