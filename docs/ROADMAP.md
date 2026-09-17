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
| JavaFX editor and software renderer | implemented-unverified | Existing `Editor` and `Client` modules; retain during stabilization; bundled blank terrain and object fixtures are now characterized by executable compatibility tests |
| Manual JavaFX smoke coverage | implemented-unverified | `./gradlew :Editor:run` reaches the JavaFX application task, but interactive acceptance is pending because the desktop was locked during the 2026-09-16 attempt; rerun [`MANUAL_SMOKE_TEST.md`](MANUAL_SMOKE_TEST.md) on an unlocked desktop |
| Four-plane terrain and shaped tiles | implemented-unverified | Legacy `MapRegion`/`SceneGraph` behavior remains protected while the neutral `WorldDocument`, OSRS decoder/encoder, and 52-case terrain topology matrix cover four planes; live build-240 verification decodes 64x64x4 regions, while legacy characterization and external scene parity remain |
| Underlays, overlays, flags, bridges | implemented-unverified | Neutral tile snapshots, OSRS byte/short codecs, bridge links, and semantic round-trip tests cover the model; bridge-heavy live fixtures and legacy workflow smoke coverage remain |
| Object placement/deletion | implemented-unverified | Canonical place/delete commands and OSRS location decode/encode are covered; legacy `SceneGraph` characterization and broader live object fixtures remain |
| Selection and copy/import/export | implemented-unverified | Unified neutral selection, `WorldFragment` JSON interchange, clipboard/file workflows, and grouped paste tests exist; legacy workflow smoke coverage remains |
| Undo/redo | implemented-unverified | `EditorSession` owns command history, composite transactions, rollback, and exact history seeking; static `SceneGraph` history remains compatibility-only until full input migration |
| Autosave | implemented-unverified | Legacy `AutoSaveJob` remains as a compatibility path; neutral `SessionAutosaveStore` now writes atomic versioned project snapshots to the canonical `ProjectLayout.sessionAutosaveFile()` path and restores terrain, flags, and objects, while JavaFX scheduling/recovery UI remains |
| Legacy/317 cache loading | implemented-unverified | Existing Displee-backed `Cache`; protect before migration |
| OSRS cache support | implemented-unverified | `OSRSPlugin` discovers named and revision-237+ numeric map groups through `CacheStore`; external revision-6 named and live build-240 numeric terrain/location verification passes, the explicit Displee output adapter persists modern edits across reopen, and the opt-in OpenRune `CacheDelegate` writer now persists a modern terrain edit through the same neutral path; default source-cache reads remain read-only until broader output parity is complete |
| Neutral cache boundary | in-progress | `CacheStore` facade now covers regular resources, named sprite reads, the legacy byte accessor, and neutral `CacheIndexView`/`CacheArchiveView` loader initialization with archive/file enumeration; deprecated index access remains only for renderer compatibility, while remaining loader/renderer consumers migrate incrementally |
| Neutral map service | verified | `MapIndexTable` and `OsrsMapService` provide named and modern numeric-group OSRS map discovery, protection against partial name-hash matches, correct split-group file-0 and packed-group file-0/file-1 reads/writes, revision-aware pre-209 byte and 209+ short terrain codecs, canonical region and bounded multi-region window loading with explicit holes, and safe writes to existing regions; external revision-6 named and live build-240 numeric terrain/location verification and semantic round trips pass for the supported read/staged scope |
| OSRS region save coordination | verified | `OsrsRegionSaveCoordinator` encodes both region payloads before writing using the map service's revision-specific terrain format, rejects read-only sessions, supports a staged `LayeredCacheStore` output boundary, flushes through the neutral map service, and marks `EditorSession` saved only after success; failed writes/flushes preserve the dirty marker; `OsrsSessionLoader` returns a clean save-capable session, and modern live-build output persistence reopens successfully through both Displee and the OpenRune reader; native OpenRune writing remains a separate gated capability |
| Neutral definitions | implemented-unverified | Object/floor/texture/collision views plus lazy OpenRune model metadata, map-scene IDs, optional immutable `MapSceneSpriteView` assets, texture average-HSL metadata, and object appearance metadata (animation, contouring, transforms, recolor/retexture pairs) now cross the neutral boundary; `DefinitionAssetRepository` and `OpenRuneSymbolicNameProvider` expose optional neutral RSCM/GameVal keys and rich neutral asset property summaries; the verifier now reports map-scene sprite coverage explicitly (0 on the captured build-240 cache), while graphics-defaults sprite-group loading, mapping-file lifecycle, and broad real-cache definition parity remain |
| Command/session editing core | in-progress | Core model, command history, exact history navigation with replay rollback, session, neutral save handler, OSRS region session loader, centralized atomic grouped rollback, and migrated tools are covered; the JavaFX history panel now seeks the selected command, while legacy input and full UI migration remain |
| Session state notifications | implemented-unverified | Neutral edit/save-state and selection listeners now support synchronized frontend panels; thread/FX scheduling and full legacy binding remain |
| WorldFragment copy/paste | implemented-unverified | Canonical multi-plane fragment capture, versioned neutral JSON import/export, and atomic paste/undo command are covered; the canonical JavaFX tool rail now copies the active tile/area/object selection to the system clipboard, supports JSON file import/export, and pastes through the same undoable command path |
| Dirty-region invalidation | implemented-unverified | `EditorSession` merges affected tiles into plane-aware 8×8 `DirtyRegion` batches and invalidates cardinal neighboring chunks for edge edits that can affect shared geometry/blending; neutral `RenderChanges.fromDirtyRegions(...)` now rebuilds only the changed plane, while the explicit compatibility constructor still supports all-plane expansion and renderer/cache consumers remain |
| Unified selection service | implemented-unverified | Tile, arbitrary tile-set/lasso, area, vertex, single-object, multi-object, fragment, and attribute-query selections are available, including neutral object-category filters; viewport/tool migration remains |
| OSRS coordinate and tile inspector contract | implemented-unverified | `WorldWindow`, `WorldRegionWindow`, `WorldTileAddress`, and `TileInspectorSnapshot` provide validated world/region/chunk breakdowns, explicit loading-line holes, and raw flag semantics; the controlled inspector exposes neutral movement/projectile collision snapshots, the OSRS viewport sends hover coordinates without changing selection, and the persistent status bar now shows local/world/region/chunk hover context |
| DevTools-style debug overlay contract | implemented-unverified | `DebugOverlayBuilder` exposes frontend-neutral tile snapshots, bridge/effective-plane data, optional collision directions, and tile/chunk/region/world-window grid lines; `CanonicalSceneViewport` now renders the enabled flag, collision, bridge, and grid overlays from the neutral scene snapshot, while live hover wiring and toggle controls remain |
| First-party terrain tools | implemented-unverified | Command-backed overlay, vertex-aware raise/lower with radius/falloff, ramp, bilinear height sampling, tile-flag, flatten, and neighbour-aware smoothing brushes are tested; canonical JavaFX pointer routing and tool overlays are now available, while workspace tool activation controls and legacy migration remain |
| First-party object commands | implemented-unverified | Place/delete/move/rotate plus atomic multi-object moves, rotations, and definition replacement are tested through `EditorSession`; richer transform coverage remains |
| First-party object tools | implemented-unverified | Place/delete/rotate plus object-aware pick-on-drag move, single- and multi-object duplicate, box object selection, move-selection, rotate-selection, and replace-selection tools invoke canonical commands; pointer-level coverage now proves move, rotate, and replace selections are separate atomic history entries and undo restores positions, IDs, and rotations; the neutral viewport now exposes optional `objectAt` picking with legacy tile fallback, the canonical JavaFX surface translates pointer input into the tool controller, and the asset browser configures the canonical placement ID through a neutral descriptor callback with explicit rotation, while broader transform coverage and tool controls remain |
| Canonical terrain mesh topology | verified | RSPSi-owned `TerrainMeshBuilder` covers the flat topology plus encoded overlay shapes 0–11 mapped to 13 scene topologies × 4 rotations, corners, and integer midpoint heights; a 52-case deterministic golden matrix, all-pair north/south and east/west shared-edge invariant, encoder shared-edge rejection, and direct pinned-TSPS shape-point/face-table comparison lock topology safety; material, blending, bridge, and RuneLite scene parity remain separate gates |
| Neutral scene construction | implemented-unverified | `RenderSceneBuilder` derives a complete renderer-independent terrain/object snapshot from `WorldDocument`; definition-aware builds add neutral per-tile `TerrainMaterial` records for floor IDs, texture selection, and RGB inputs, neutral `RenderObject` records for category, shape, rotated footprint, model IDs, and collision inputs, while every complete scene carries per-corner `TerrainLight` values from the OSRS directional normal baseline and `RenderSceneFingerprint` includes all derived inputs; `RenderWindowSceneBuilder` now projects loaded `WorldRegionWindow` context into world-addressed tiles/objects/collision snapshots while preserving holes and stitching shared edges on a deep copy, leaving authored documents and session dirty state untouched; `SessionSceneController` now loads the initial scene and publishes session-owned dirty-chunk updates through the new scene-plus-changes publication path; mixed-plane terrain/object/bridge semantics now have a deterministic scene golden and production SHA-256 fingerprint, and the controlled workspace has a small canonical top-down JavaFX preview with neutral tile picking/selection, while faithful renderer integration and TSPS/RuneLite scene parity remain |
| Canonical object semantics | implemented-unverified | RSPSi-owned `OsrsLocShape` and `ObjectCategory` preserve OpenRune's shape IDs 0–22 and wall/wall-decor/ground/ground-decor layer mapping; neutral `ObjectInspectorSnapshot` and `RenderObject` flatten optional definitions/collision/appearance into immutable frontend/renderer inputs, including orientation-aware footprints, model IDs, animation, contouring, transforms, and replacement pairs, while placement-inspector wiring remains |
| Canonical bridge relationships | implemented-unverified | `OsrsTileFlags` names the raw bridge bit, `WorldDocument.effectivePlane(...)` owns the validated authored-to-effective plane rule, and `WorldDocument.bridgeLinks()`/`RenderScene` expose authored-plane relationships; focused bridge-column tests and live build-240 region `(50,50)` verification report 120 bridge links, while external RuneLite/TSPS parity and live overlay wiring remain |
| Instance/chunk scene materialization | implemented-unverified | `InstanceChunkTemplate`, `InstanceChunkTransform`, and `InstanceChunkGrid` preserve packed OSRS templates, holes, repeated source chunks, inverse lookup, and object orientation; `InstanceWorldBuilder` now materializes transformed 8×8 terrain/object chunks into a canonical `WorldDocument` with all four rotation cases covered, while live instance-template fixtures remain |
| Canonical collision map | implemented-unverified | RSPSi-owned flags/map plus `OsrsCollisionBuilder` consume canonical object categories for bridge-aware terrain masks, roof semantics, rotated footprint collision, wall-decor behavior, OpenRune route-blocker flags, adjacent non-bridge wall relinking across bridge boundaries, and decoded collision-inspector snapshots including route-blocked directions; focused bridge/wall tests plus deterministic differential vectors for every OpenRune `StepValidator` direction and actor sizes 1–4 now pass, and live build-240 region `(50,50)` verification produces 3,983 non-empty tiles from 4,726 object projections, while complete definition fixtures remain |
| Route and line-of-sight preview | implemented-unverified | Neutral bounded `RouteFinder`, `LineValidator`, `Reachability`, and `RoutePreviewService` provide collision-aware routes, OpenRune-compatible swept footprint checks for square actors, edge-aware rectangular line-of-sight and line-of-walk validation, actor/target footprint-aware LOS, destination location semantics, explicit optional route-blocker semantics, corner-cutting protection, projectile traces, object-footprint reach previews, and safe map-edge handling; the controlled JavaFX viewport now renders immutable route/LOS/reach results with start/target markers and exposes Route/LOS/Reach controls; broader OpenRune-Server route/reach parity remains |
| Neutral world validation | implemented-unverified | `WorldValidator` reports broken intra-document shared edges, unsupported OSRS map values, duplicate/invalid objects using canonical shape semantics, missing definitions, and definition-backed footprint bounds; region-context validation distinguishes an object extending into neighboring loaded context from invalid standalone data, `WorldRegionWindow` reports verifiable cross-region corner mismatches, and the controlled workspace exposes live diagnostics with a neutral definition adapter, while full parity rules remain |
| UI-neutral editor contracts | in-progress | Neutral pointer, tool, inspector, viewport, renderer, and controlled workspace seams introduced; command-backed tools and the JavaFX canonical viewport consume neutral state without exposing JavaFX types to editor-core; `UiNeutralImportTest` now fails the build if editor/project/map/definition contracts import JavaFX, ImGui, LWJGL/OpenGL, Displee, OpenRS2, or OpenRune-Server types, while the remaining legacy adapters and full input/UI migration remain |
| Live legacy document bridge | in-progress | MapRegion terrain import, scene-object anchor import, shared-corner height/floor/flag synchronization, and footprint-aware object replacement are implemented; UI smoke coverage remains |
| OpenRune backend | in-progress | 2.4.19 compatibility spike and neutral OSRS region decoder are isolated behind `CacheStore`; external revision-6 named and live build-240 numeric terrain/location verification passes, staged modern output persists through the explicit Displee adapter, and an opt-in OpenRune `CacheDelegate` writer now reopens a persisted terrain edit through OpenRune; the normal source reader remains read-only and legacy remains the default |
| Resource catalog and provenance | implemented-unverified | [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md) and [`RESOURCE_INTAKE_2026-09-16.md`](RESOURCE_INTAKE_2026-09-16.md) record roles, commits, license evidence, inspected paths, and current adoption tests |
| OSRS-only product scope | in-progress | Scope and migration policy are locked in [`PRODUCT_DESIGN.md`](PRODUCT_DESIGN.md); legacy paths remain quarantined during parity work |
| Project/cache identity metadata | implemented-unverified | Neutral `OsrsCacheMetadata`, `ProjectMetadata`, JSON persistence, explicit read-only mismatch assessment, optional backend-neutral `CacheStore.metadata(...)` capability, and `OsrsProjectSessionLoader` fail-closed session binding added; separate-output projects now assess identity against the stable source cache while reading/writing the output cache, so saved output revisions remain editable on reopen; `CacheStoreCapabilities.writeMode` distinguishes `READ_ONLY`, `DIRECT`, and `STAGED` output, and opened sessions expose that decision; mismatched sessions and matching projects over read-only backends reject edits at the core, while cache discovery and UI remain |
| Controlled workspace contracts | implemented-unverified | UI-neutral dock/panel/placement types, validated presets, reusable preset switching, visible focus styling, persistent status bar, and an opt-in `MainWindow` bridge that reuses the legacy renderer; the shared binder now accepts canonical sessions or `OsrsProjectSessionLoader.OpenedProject`, binds session-backed inspector/history/validation/assets, passes neutral definitions into the canonical viewport for footprint/material/collision-aware scenes, shows editable versus read-only state and compatibility issues, the object inspector displays optional animation/contouring/transform/replacement metadata, the history panel supports seeking, canonical OSRS viewport overlays plus non-selecting hover inspection are wired, and the adaptive tool rail exposes command-backed terrain/object tools plus multi-select debug overlay toggles while leaving the legacy rail intact; an additive `File > Open from > OSRS project…` workflow now selects project/cache/region and binds a read-only canonical session, while full UI smoke coverage remains |
| OSRS project composition | implemented-unverified | `OsrsStudioProject` composes the neutral OSRS map/session/definition/asset services, owns cache lifecycle, supports read-only OpenRune, staged OpenRune-read/Displee-output, and explicit OpenRune-output arrangements, exposes stitched bounded scene windows, and is covered with neutral lifecycle/session tests plus a live build-240 project-level command/save/reopen integration test; adapter fingerprints remain compatible across read-only `FileCache` and writable `CacheDelegate`; `ProjectLayout` establishes `project.json`, `autosave/`, and `edits/`, and metadata writes are atomic; the controlled JavaFX shell opens persisted project metadata and a selected region read-only and now shows a canonical top-down scene preview, while faithful viewport parity and native FileStore writing remain gated |
| RuneLite/TSPS parity harness | in-progress | `OsrsRevisionVerifier` reports auditable cache, capabilities, metadata/fingerprint, revision-profile, neutral object/underlay/overlay/texture/map-scene definition availability, terrain/location decode, neighboring region windows with explicit holes, provisional-border stitching and shared-edge results, bridge/plane semantics, selected-region scene construction plus a reproducible scene fingerprint, and full loaded-window scene construction; live build-240 verification now covers 9/9 regions, 147,456 world-addressed four-plane terrain tiles, 3,902 objects in region `(16,33)`, and 21,609 objects plus 120 bridge links and 3,983 non-empty collision tiles in bridge-heavy region `(50,50)`; decode→encode→decode now also compares the complete neutral derived scene and passes with zero differences on the bridge-heavy fixture; repeated verifier runs now produce the same neutral scene fingerprint, while `RenderSceneParity` and `MinimapParity` provide bounded tile/global and pixel-level comparison reports, and opt-in `RSPSI_OSRS_PARITY_FIXTURE` directories now make external scene fingerprints and PNG minimap comparisons executable with identity validation; `RSPSI_OSRS_REQUIRE_PARITY=true` now turns missing or warning external evidence into a release-gate failure; the shaped path now matches the TSPS boundary/orientation/render-flag/bridge/map-scene suppression/wall ordering and matches the independent build-240 fixture exactly on all four planes; direct pinned-TSPS topology-table comparison is recorded in [`TERRAIN_PARITY.md`](TERRAIN_PARITY.md), while licensed RuneLite golden comparisons and full 3D renderer parity remain |
| Lua, plugin permissions, Plugin Hub | deferred | Begin only after native command/plugin API is stable |
| Renderer/UI rewrite | deferred | Current JavaFX renderer remains the compatibility surface |

## Phase gates

### Phase 0 — Safety net

- Add JUnit 5 tests and fixture-backed semantic map tests. The bundled blank
  terrain and object resources now have executable legacy characterization
  coverage; real interactive smoke acceptance remains separate.
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

- Add OpenRune only behind neutral interfaces. The normal source reader is
  read-only; writable support is an explicit output-cache capability proven by
  copied-cache integration evidence, not implied by a green compile.
- Complete OSRS map-index support.
- Discover named `mX_Y`/`lX_Y` archive IDs and revision-237+ numeric groups
  through the neutral cache boundary; prove both paths against a licensed
  representative cache.
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
inspector rails, a centered viewport, controlled bottom tabs, and a persistent
status row. `ControlledWorkspaceBridge` binds the same panels to the legacy
compatibility session and exposes an OSRS `OpenedProject` binding path. It does
not own document state, renderer state, or arbitrary docking; legacy sessions
retain the legacy renderer while OSRS sessions use the small canonical
semantic preview until faithful 3D scene parity is ready.

## Next implementation gate

External evidence now passes for revision-6 named maps and live build-240
numeric maps, including non-empty location payloads. Modern output-cache
reopening is also validated through a copied cache and the explicit Displee
writer. The OpenRune writer decision is now explicit: the pinned OpenRune
filesystem backend is `READ_ONLY`,
`CacheStoreFactory.openRuneWithDispleeOutput(...)` is the supported `STAGED`
arrangement, and `CacheStoreFactory.openRuneWritable(...)` is an explicit
`DIRECT` output capability.
Run
`./gradlew verifyOsrsRevision` with
`RSPSI_OSRS_CACHE=/path/to/cache`; for a selected region also provide
`RSPSI_OSRS_REGION_X`, `RSPSI_OSRS_REGION_Y`, and `RSPSI_OSRS_REVISION`.
Until the remaining output and scene/parity gates pass, the normal OpenRune
source reader remains read-only and the legacy backend remains available only
as a quarantined compatibility/output path. The explicit OSRS project-open
workflow and a small canonical top-down scene preview are now in place. The
next implementation step is broader output/cache parity and manual smoke
coverage; do not replace the legacy renderer or change product defaults before
those gates pass.
