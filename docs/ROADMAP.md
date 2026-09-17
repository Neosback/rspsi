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

The current requirement-by-requirement foundation review is maintained in
[`FOUNDATION_AUDIT_2026-09-17.md`](FOUNDATION_AUDIT_2026-09-17.md).

The long-term source-project and multi-workspace direction is maintained in
[`STUDIO_DIRECTION.md`](STUDIO_DIRECTION.md). It records future ideas adopted
from the reviewed editor screenshots without changing the current OSRS map
foundation gate or importing the 742 editor/runtime.

The deferred feature parking lot is maintained in
[`IDEAS.md`](IDEAS.md). It currently records the capability-based server
adapter plan, including the OpenRune-first project/build/runtime integration
direction, without making server integration a foundation dependency.

The full `melxin/runelite` reference checkout is also recorded there. Its
plugin lifecycle, PF4J/classloader handling, API/mixin/injection layering, and
focused scene/client paths are inputs for later Studio runtime-bridge and
external-plugin work; they are not permission to add a second cache, client,
renderer, or world model to the product.

The focused scene findings are captured in
[`RUNELITE_SCENE_REFERENCE.md`](RUNELITE_SCENE_REFERENCE.md). It is the
source-backed reference for the four-plane tile grid, paint/model/object
layers, bridge/effective-plane relationships, renderer upload order, and the
scene projections future plugins may consume.

## Design update: FileStore boundary and foundation-first plugin sequencing

The latest FileStore review is adopted as an architectural refinement, not as
a request to turn FileStore into the editor. FileStore is already the right
production foundation for cache I/O, OSRS definitions, models, sprites,
RSCM/GameVal, XTEA, packing, and cache tooling. The missing Studio behavior is
semantic: editable map regions, world coordinates, scene construction,
collision, rendering rules, and commands. Those remain RSPSi-owned or belong
in small sibling layers behind neutral interfaces.

The near-term ownership boundary is:

| Layer | Owns | Does not own |
|---|---|---|
| OpenRune FileStore | Cache filesystem, raw map/location bytes, definitions, models, sprites, XTEA, packing, generic cache/revision tools | Editor world state, collision, scene graph, renderer, commands, UI plugins |
| RSPSi cache adapter | Revision profiles, map-index discovery, map/location codecs, project identity, output-cache coordination | OpenRune or Displee types in the editor model |
| RSPSi world/scene layers | `WorldDocument`, terrain semantics, coordinates, bridges, instances, collision, route previews, shaped-tile meshes, floor blending inputs, model runtime inputs | Cache archive layout and frontend state |
| RSPSi core | Sessions, commands, history, selection, dirty regions, renderer API, plugin contracts | JavaFX, ImGui, OpenGL, raw cache backends |
| First-party plugins | Terrain/object tools, asset browser, inspectors, validation, debug overlays, minimap/preview workflows | Direct mutation of world/cache state or alternate history systems |

The feature/plugin architecture is now documented in
[`PLUGIN_ARCHITECTURE.md`](PLUGIN_ARCHITECTURE.md): feature code is colocated
vertically, the shell owns contribution hosts and placement, and lifecycle
cleanup is owner-based. The current neutral API already covers tools,
commands, panels/workspaces, inspectors, validators, overlays, shortcuts,
typed lazy assets, immutable plugin scene snapshots, tool-context settings,
asset providers, status values, and command-referencing menu entries. The
JavaFX shell now mounts feature-owned tool-context settings, inspector
sections, asset providers, status values, plugin command menus, and a command
palette; equivalent Dear ImGui mounting is the next host seam. External
JAR hot reload,
permissions, scripting, and a Plugin Hub remain deferred.

The FileStore adoption contract is recorded in
[`OPENRUNE_FILESTORE_ADOPTION.md`](OPENRUNE_FILESTORE_ADOPTION.md). FileStore
is the selected OSRS cache/definition provider, not an `EditorPlugin`. Source
caches are read-only by default, staged output is the normal save path, and
direct output/build capabilities are explicit. The asset facade is
session-scoped; FileStore's global `CacheManager` is not Studio state. The
shared platform remains the prerequisite for map, interface, item, model, and
future workspaces.

The proposed FileStore `osrs-map` and unified asset APIs are candidate
upstream improvements, not new production dependencies to add by copying
code. RSPSi will first complete and verify its own neutral map and asset
seams. A later upstream contribution or sibling module is justified only when
it is generic, independently useful, and does not duplicate the canonical
RSPSi editor model.

The plugin rule is deliberately strict: a feature may be presented as a
first-party plugin, but it must use `EditorSession`, `EditorCommand`,
`SelectionModel`, `AssetRepository`, `SceneRenderer`, and neutral inspector or
workspace contracts. Asset browsing can therefore be a plugin without making
the cache, definitions, or world model a plugin. History, selection, project
identity, cache coordination, and the canonical world model remain core
services.

Foundation completion is now an explicit prerequisite. Before adding a large
set of tools or plugins, the project must demonstrate that it understands and
tests OSRS metadata and semantics: revision-driven map/location formats,
explicit versus generated heights, underlay/overlay blending, all 13 shaped
tile topologies and rotations, 64×64 regions, 8×8 chunks, world/region/local
coordinates, bridges and effective planes, location layers/types/orientations,
object footprints and configs, collision/route flags, model transforms, and
region/instance boundaries.

## Current baseline

| Area | Status | Evidence / next action |
|---|---|---|
| Gradle multi-module build | verified | `./gradlew test` compiles all modules; tests now run on JUnit Platform |
| JavaFX editor and software renderer | implemented-unverified | Existing `Editor` and `Client` modules; retain during stabilization; bundled blank terrain and object fixtures are now characterized by executable compatibility tests |
| Manual JavaFX smoke coverage | implemented-unverified | JavaFX 21 on the Java 21 toolchain now survives the bounded `:Editor:run` launch check on macOS 26.5.1 without the prior JavaFX 17 `NSTrackingRectTag` abort; full interactive load/edit/save/autosave acceptance remains pending and is documented in [`MANUAL_SMOKE_TEST.md`](MANUAL_SMOKE_TEST.md) |
| Four-plane terrain and shaped tiles | implemented-unverified | Legacy `MapRegion`/`SceneGraph` behavior remains protected while the neutral `WorldDocument`, OSRS decoder/encoder, and 52-case terrain topology matrix cover four planes; live build-240 verification decodes 64x64x4 regions, while legacy characterization and external scene parity remain |
| Underlays, overlays, flags, bridges | implemented-unverified | Neutral tile snapshots, OSRS byte/short codecs, bridge links, and semantic round-trip tests cover the model; bridge-heavy live fixtures and legacy workflow smoke coverage remain |
| Object placement/deletion | implemented-unverified | Canonical place/delete commands and OSRS location decode/encode are covered; legacy `SceneGraph` characterization and broader live object fixtures remain |
| Selection and copy/import/export | implemented-unverified | Unified neutral selection, `WorldFragment` JSON interchange, clipboard/file workflows, and grouped paste tests exist; legacy workflow smoke coverage remains |
| Undo/redo | implemented-unverified | `EditorSession` owns command history, composite transactions, rollback, and exact history seeking; static `SceneGraph` history remains compatibility-only until full input migration |
| Autosave | implemented-unverified | Legacy `AutoSaveJob` remains as a compatibility path; neutral `SessionAutosaveStore` writes atomic versioned project snapshots to the canonical `ProjectLayout.sessionAutosaveFile()` path and restores terrain, flags, and objects; `SessionAutosaveCoordinator` binds edit/undo/redo/save-marker transitions to that store and `OsrsStudioProject.attachAutosave(...)` verifies project identity; the JavaFX editable-output workflow now schedules snapshots and offers identity/dimension-checked one-command recovery, while interactive smoke acceptance remains |
| Legacy/317 cache loading | implemented-unverified | Existing Displee-backed `Cache`; protect before migration |
| OSRS cache support | implemented-unverified | `OSRSPlugin` discovers named and revision-237+ numeric map groups through `CacheStore`; external revision-6 named and live build-240 numeric terrain/location verification passes, the explicit Displee output adapter persists modern edits across reopen, and the opt-in OpenRune `CacheDelegate` writer now persists underlay, overlay shape/rotation, tile flags, shared-corner heights, and object rotation through the same neutral project/session path; default source-cache reads remain read-only until broader output parity is complete |
| Neutral cache boundary | implemented-unverified | `CacheStore` and `OpenRuneCacheStore` now make OpenRune FileStore the explicit modern OSRS backend; `CacheStoreFactory.openOsrs(Path)` is the production entry point, the compatibility `Cache` facade selects OpenRune for modern OSRS and Displee only for 317, explicit DAT2 index identities prevent the 317 animation/skeleton reversal, and `verifyCacheBackendBoundary` prevents new raw backend imports from escaping approved adapters. FileStore is deliberately not an `EditorPlugin`; `OsrsBundle` selects it before feature plugins and the shell may expose only its read/staged/direct capability state. The remaining work is migrating old renderer consumers away from static loader adapters, not adding another cache backend |
| Neutral map service | verified | `MapIndexTable` and `OsrsMapService` provide named and modern numeric-group OSRS map discovery, protection against partial name-hash matches, correct split-group file-0 and packed-group file-0/file-1 reads/writes, revision-aware pre-209 byte and 209+ short terrain codecs, canonical region and bounded multi-region window loading with explicit holes, and safe writes to existing regions; external revision-6 named and live build-240 numeric terrain/location verification and semantic round trips pass for the supported read/staged scope |
| FileStore and sibling-layer boundary | verified | The cross-resource review selects OpenRune FileStore for production cache I/O/definitions/models/sprites while RSPSi retains map semantics, world/session/history, collision policy, scene construction, rendering contracts, and feature contributions; no FileStore plugin or second production map/world/renderer is approved. Future upgrades or upstream contributions require the same adapter and parity gates |
| Revision feature registry and conformance audit | implemented-unverified | `OsrsRevisionFeatures` is now the single neutral policy object for the audited map-group and terrain-value transitions; `OsrsRevisionProfile` delegates to it and `RevisionAudit` reports the selected policy. Fixture-driven revision audits and real-cache evidence remain required before adding another supported revision |
| OSRS region save coordination | verified | `OsrsRegionSaveCoordinator` pre-encodes terrain/location payloads using the map service's revision-specific format, supports single- and multi-region batches, rejects read-only/duplicate sessions, writes and flushes through the neutral map service, and marks sessions saved only after the complete batch succeeds; failed writes/flushes preserve dirty markers; `OsrsSessionLoader` returns clean save-capable sessions, and modern live-build output persistence reopens successfully through both Displee and the OpenRune reader; native OpenRune writing remains a separate gated capability |
| Neutral definitions and unified asset facade | implemented-unverified | Object/floor/texture/collision views plus lazy OpenRune model metadata, model-index enumeration, sampled neutral model geometry, map-scene IDs, immutable `MapSceneSpriteView` assets, texture average-HSL metadata, object appearance metadata (animation, contouring, transforms, recolor/retexture pairs), and neutral sequence/map-element view seams now cross the boundary; the OpenRune adapter supports graphics-defaults and named `mapscene` sprite-group discovery plus lazy raw-config sequence/map-element decoding, and the real revision-237 verifier exposes 60,840 objects, 233 underlays, 627 overlays, 208 textures, 60,113 models, 257 map-scene sprites, 13,742 sequences, and 1,255 map elements, with sample ID 0 decoding in both new categories; `DefinitionAssetRepository` and `OpenRuneSymbolicNameProvider` expose optional neutral RSCM/GameVal keys and rich neutral asset property summaries, while model descriptors stay lightweight until a model is selected; the controlled browser now filters models and sprites; the repository caches its immutable descriptor index for responsive repeated asset searches; `AssetRepository` now exposes the same typed definitions lazily to tools and plugins without archive knowledge; mapping-file lifecycle and broader real-cache definition parity remain |
| Command/session editing core | in-progress | Core model, canonical `EditorCommand` history, exact history navigation with replay rollback, session, neutral save handler, OSRS region session loader, centralized atomic grouped rollback, and migrated tools are covered; built-in tile/composite commands no longer depend on the deprecated `EditCommand` alias, explicit underlay, overlay, height, tile-flag, and upper-plane-height commands now back canonical edits, the legacy fix-heights action dispatches through the neutral command whenever a controlled session is present, OSRS workspace menu and keyboard Undo/Redo/Save actions target the bound canonical session even when its history is empty, and Delete uses a grouped canonical object command; remaining legacy input and full UI migration remain |
| Session state notifications | implemented-unverified | Neutral edit/save-state and selection listeners now support synchronized frontend panels; thread/FX scheduling and full legacy binding remain |
| WorldFragment copy/paste | implemented-unverified | Canonical multi-plane fragment capture, versioned neutral JSON import/export, and atomic paste/undo command are covered; the canonical JavaFX tool rail now copies the active tile/area/object selection to the system clipboard, supports JSON file import/export, and pastes through the same undoable command path |
| Dirty-region invalidation | implemented-unverified | `EditorSession` merges affected tiles into plane-aware 8×8 `DirtyRegion` batches and invalidates cardinal neighboring chunks for edge edits that can affect shared geometry/blending; neutral `RenderChanges.fromDirtyRegions(...)` now rebuilds only the changed plane, while the explicit compatibility constructor still supports all-plane expansion and renderer/cache consumers remain |
| Unified selection service | implemented-unverified | Tile, arbitrary tile-set/lasso, area, vertex, single-object, multi-object, fragment, and attribute-query selections are available, including neutral object-category filters; viewport/tool migration remains |
| OSRS coordinate and tile inspector contract | implemented-unverified | `WorldWindow`, `WorldRegionWindow`, `WorldTileAddress`, and `TileInspectorSnapshot` provide validated world/region/chunk breakdowns, explicit loading-line holes, and raw flag semantics; the controlled inspector exposes neutral movement/projectile collision snapshots, the OSRS viewport sends hover coordinates without changing selection, and the persistent status bar now shows local/world/region/chunk hover context |
| DevTools-style debug overlay contract | implemented-unverified | `DebugOverlayBuilder` exposes frontend-neutral tile snapshots, bridge/effective-plane data, optional collision directions, and tile/chunk/region/world-window grid lines; `CanonicalSceneViewport` now renders the enabled flag, collision, bridge, and grid overlays from the neutral scene snapshot, while live hover wiring and toggle controls remain |
| First-party terrain tools | implemented-unverified | Command-backed overlay, vertex-aware raise/lower with radius/falloff, ramp, bilinear height sampling, tile-flag, flatten, and neighbour-aware smoothing brushes are tested; canonical JavaFX pointer routing and tool overlays are now available, and the terrain plugin owns the shared typed settings used by the JavaFX context host while exposing core terrain tools and collapsed ramp/flag tools; broader interactive smoke coverage and legacy migration remain |
| First-party object commands | implemented-unverified | Place/delete/move/rotate plus atomic multi-object moves, rotations, and definition replacement are tested through `EditorSession`; richer transform coverage remains |
| First-party object tools | implemented-unverified | Place/delete/rotate plus object-aware pick-on-drag move, single- and multi-object duplicate, box object selection, move-selection, rotate-selection, and replace-selection tools invoke canonical commands; pointer-level coverage now proves move, rotate, and replace selections are separate atomic history entries and undo restores positions, IDs, and rotations; the neutral viewport now exposes optional `objectAt` picking with legacy tile fallback, the canonical JavaFX surface translates pointer input into the tool controller, the asset browser configures the canonical placement ID through a neutral descriptor callback with explicit rotation, and the OSRS rail exposes configurable single-object and selection quarter-turns plus a shared bounded snap-grid setting for single- and multi-object move/duplicate tools; broader transform coverage remains |
| Canonical terrain mesh topology | verified | RSPSi-owned `TerrainMeshBuilder` covers the flat topology plus encoded overlay shapes 0–11 mapped to 13 scene topologies × 4 rotations, corners, and integer midpoint heights; a 52-case deterministic golden matrix, all-pair north/south and east/west shared-edge invariant, encoder shared-edge rejection, and direct pinned-TSPS shape-point/face-table comparison lock topology safety; material, blending, bridge, and RuneLite scene parity remain separate gates |
| Neutral scene construction | implemented-unverified | `RenderSceneBuilder` derives a complete renderer-independent terrain/object snapshot from `WorldDocument`; definition-aware builds add neutral per-tile `TerrainMaterial` records for floor IDs, texture selection, and RGB inputs, neutral `RenderObject` records for category, shape, rotated footprint, model IDs, and collision inputs, while every complete scene carries per-corner `TerrainLight` values from the OSRS directional normal baseline and `RenderSceneFingerprint` includes all derived inputs; `RenderWindowSceneBuilder` now projects loaded `WorldRegionWindow` context into world-addressed tiles/objects/collision snapshots while preserving holes and stitching shared edges on a deep copy, leaving authored documents and session dirty state untouched; window collision is now aggregated across loaded regions so wall reciprocal flags spill across 64×64 boundaries; `SessionSceneController` now loads the initial scene and publishes session-owned dirty-chunk updates through the new scene-plus-changes publication path; mixed-plane terrain/object/bridge semantics now have a deterministic scene golden and production SHA-256 fingerprint, and the controlled workspace has a small canonical top-down JavaFX preview with neutral tile picking/selection, while faithful renderer integration and TSPS/RuneLite scene parity remain |
| RuneLite/TSPS-backed 3D render packets | not-started | RuneLite confirms client scene vocabulary and traversal; TSPS adds the concrete revision-240 behavior for radius-five underlay blending, final terrain HSL/UV/hidden-face packets, location model-type selection, transforms, contouring, normal merging, bridge projection, alpha/priority separation, and WebGL packet fields. RSPSi still needs neutral equivalents, ordered scene layers, camera visibility/occlusion inputs, and a real backend; the canonical JavaFX viewport is currently a top-down preview and the legacy `SceneGraph` is compatibility-only |
| Canonical object semantics | implemented-unverified | RSPSi-owned `OsrsLocShape` and `ObjectCategory` preserve OpenRune's shape IDs 0–22 and wall/wall-decor/ground/ground-decor layer mapping; neutral `ObjectInspectorSnapshot` and `RenderObject` flatten optional definitions/collision/appearance into immutable frontend/renderer inputs, including orientation-aware footprints, model IDs, animation, contouring, transforms, and replacement pairs, while placement-inspector wiring remains |
| Canonical bridge relationships | implemented-unverified | `OsrsTileFlags` names the raw bridge bit, `WorldDocument.effectivePlane(...)` owns the validated authored-to-effective plane rule, and `WorldDocument.bridgeLinks()`/`RenderScene` expose authored-plane relationships; focused bridge-column tests and live build-240 region `(50,50)` verification report 120 bridge links, while external RuneLite/TSPS parity and live overlay wiring remain |
| Instance/chunk scene materialization | implemented-unverified | `InstanceChunkTemplate`, `InstanceChunkTransform`, and `InstanceChunkGrid` preserve packed OSRS templates, holes, repeated source chunks, inverse lookup, and object orientation; `InstanceWorldBuilder` replays explicit/generated height provenance into destination planes, applies source-region world coordinates, OSRS footprint-aware anchors, scene-edge bounds, and all four rotations; a pinned TSPS revision-240 4-plane 13×13-chunk external fixture now passes template, terrain, and object parity with zero differences through `verifyOsrsInstance`; broader multi-region/empty-chunk fixtures and interactive instance editing remain |
| Minimap composition parity | verified for covered fixtures | Neutral semantic/shaped minimap contracts and TSPS-shaped PNG export exist; the exporter records actual sprite availability, and the revision-240 sprite-bearing region `(50,50)` now passes all four shaped 256×256 planes with zero pixel differences after matching scene-layer order and vertical map-scene centering; broader cache/revision coverage and full 3D renderer parity remain |
| Canonical collision map | implemented-unverified | RSPSi-owned flags/map plus `OsrsCollisionBuilder` consume canonical object categories for bridge-aware terrain masks, roof semantics, rotated footprint collision, wall-decor behavior, OpenRune route-blocker flags, adjacent non-bridge wall relinking across bridge boundaries, and decoded collision-inspector snapshots including route-blocked directions; focused bridge/wall tests plus deterministic differential vectors for every OpenRune `StepValidator` direction and actor sizes 1–4 now pass, and live build-240 region `(50,50)` verification produces 3,983 non-empty tiles from 4,726 object projections, while complete definition fixtures remain |
| Route and line-of-sight preview | implemented-unverified | Neutral bounded `RouteFinder`, `LineValidator`, `Reachability`, and `RoutePreviewService` provide collision-aware routes, OpenRune-compatible swept footprint checks for square actors, edge-aware rectangular line-of-sight and line-of-walk validation, actor/target footprint-aware LOS, destination location semantics, explicit optional route-blocker semantics, corner-cutting protection, projectile traces, object-footprint reach previews, and safe map-edge handling; the controlled JavaFX viewport now renders immutable route/LOS/reach results with start/target markers and exposes Route/LOS/Reach controls; broader OpenRune-Server route/reach parity remains |
| Neutral world validation | implemented-unverified | `WorldValidator` reports broken intra-document shared edges, unsupported OSRS map values, duplicate/invalid objects using canonical shape semantics, missing definitions, and definition-backed footprint bounds; region-context validation distinguishes an object extending into neighboring loaded context from invalid standalone data, `WorldRegionWindow` reports verifiable cross-region corner mismatches, and the controlled workspace exposes live diagnostics with a neutral definition adapter, while full parity rules remain |
| UI-neutral editor contracts | implemented-unverified | Neutral pointer, keyboard, tool, inspector, viewport, renderer, controlled workspace, and immutable `EditorFrontendFrame` projections are present; `EditorInputRouter` and `DearImGuiFrontendAdapter` share shortcut/input/session/scene state with JavaFX; import gates pass, while native ImGui draw integration and full input/UI migration remain |
| Optional OpenRune-Server adapter | implemented-unverified | Capability-only `ServerAdapter`/`ServerBuildProvider` contracts and `OpenRuneServerAdapter` detect project layout, LIVE/SERVER cache paths, pack roots, and `or-cache` build actions without a server runtime dependency; runtime bridge and structured process execution remain deferred |
| OSRS scene pipeline and plugin attachment map | implemented-unverified | [`OSRS_SCENE_PIPELINE.md`](OSRS_SCENE_PIPELINE.md) records archive-to-authored-to-derived-to-frontend phase order, bridge/effective-plane rules, location/map-scene distinctions, export projections, Dear ImGui responsibilities, and the narrow plugin attachment points; [`RUNELITE_SCENE_REFERENCE.md`](RUNELITE_SCENE_REFERENCE.md) grounds the tile-layer vocabulary and renderer order in the pinned full-fork source; [`OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md`](OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md) adds a GPL-bounded scene lighting, renderer diagnostics, glTF, and headless export reference; sprite-bearing minimap composition is now verified for the pinned revision-240 fixture, while full renderer parity and interactive frontend evidence remain open |
| First-party plugin boundary | implemented-unverified | Neutral `EditorPluginLoader`, dependency-aware `EditorPluginHost`/`EditorPluginDescriptor`, `EditorPluginRegistry`, `EditorPluginContext`, typed lazy `AssetRepository`, immutable `EditorSceneSnapshot`, and immutable per-tile `EditorSceneTileProjection` now discover first-party plugins, validate dependencies, expose derived scene snapshots without a mutable document back door, and register tools, commands, panel/workspace metadata, scene overlays, inspectors, validators, keyboard shortcuts, typed tool-context settings, asset providers, status values, and command-referencing menus against one session without frontend/backend types; terrain, object, and selection registrations are separate built-in vertical plugins with stable contribution IDs, labels, categories, and neutral factories, and the controlled JavaFX shell mounts built-in/discovered contributions plus generic plugin tool contexts, inspector sections, asset providers, status values, plugin command menus, and a searchable command palette; plugin lifecycle is shell-owned with reverse-order shutdown, host-owned LIFO resource cleanup, closed-registry protection, and contribution cleanup; discovery order is dependency-aware with stable load-order/ID tie-breaking; the Dear ImGui adapter remains next; existing legacy loaders remain compatibility-only; ownership and Dear ImGui implications are recorded in [`PLUGIN_ARCHITECTURE.md`](PLUGIN_ARCHITECTURE.md) and [`SCENE_SEMANTICS.md`](SCENE_SEMANTICS.md) |
| Live legacy document bridge | in-progress | MapRegion terrain import, scene-object anchor import, shared-corner height/floor/flag synchronization, and footprint-aware object replacement are implemented; UI smoke coverage remains |
| OpenRune backend | in-progress | 2.4.19 compatibility spike and neutral OSRS region decoder are isolated behind `CacheStore`; `CacheStoreFactory.openOsrs(Path)` and the compatibility facade now route modern OSRS reads through OpenRune, while 317 remains explicitly legacy; external revision-6 named and live build-240 numeric terrain/location verification passes, staged modern output persists through the explicit Displee adapter, and an opt-in OpenRune `CacheDelegate` writer reopens persisted edits through OpenRune; the normal source reader remains read-only |
| Backend/plugin startup boundary | implemented-unverified | `OsrsBundle` is the selectable OSRS composition root: it opens modern FileStore, validates cache identity, creates revision-aware neutral definitions/assets and the project/session, then starts feature plugins. FileStore is a backend/provider, not an `EditorPlugin`; the legacy `OSRSPlugin` remains only as a renderer compatibility bridge until static loader consumers are migrated. Broader project-open lifecycle smoke evidence remains |
| Legacy loader retirement | in-progress | Modern reads no longer default to the old 317-oriented cache path, but `Plugins/OSRSPlugin` still installs static loaders required by the compatibility/software renderer. Exit requires neutral definition/model/texture/map-scene/animation consumption in the renderer, no new static-loader call sites, live render/parity coverage, and safe removal of the compatibility bridge |
| Resource catalog and provenance | implemented-unverified | [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md), [`RESOURCE_INTAKE_2026-09-16.md`](RESOURCE_INTAKE_2026-09-16.md), and [`RESOURCE_REVIEW_2026-09-17.md`](RESOURCE_REVIEW_2026-09-17.md) record roles, commits, license evidence, inspected paths, adoption tests, and the single-spine rule; freshness and future dependency upgrades remain deliberate review events |
| Foundation resource review | verified | The 2026-09-17 review, including the attached client-base proposal and pasted architecture proposals, confirms OpenRune FileStore as the only production ecosystem dependency, RSPSi as the owner of canonical world/editor semantics, RuneLite as the client-semantic oracle, TSPS as the concrete scene/render-packet donor and edit-mode comparison source, OpenRune-Editor as workflow donor, Domw71 as a RuneLite-derived revision-240 case study, and OSRS Environment Exporter as a GPL-bounded scene/render/export reference; conflicts are adjudicated in [`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md), and no second cache/world/history system, FileStore feature plugin, or current TypeScript/WebGPU base is approved |
| OSRS-only product scope | in-progress | Scope and migration policy are locked in [`PRODUCT_DESIGN.md`](PRODUCT_DESIGN.md); legacy paths remain quarantined during parity work |
| Project/cache identity metadata | implemented-unverified | Neutral `OsrsCacheMetadata`, `ProjectMetadata`, JSON persistence, explicit read-only mismatch assessment, optional backend-neutral `CacheStore.metadata(...)` capability, and `OsrsProjectSessionLoader` fail-closed session binding added; separate-output projects now assess identity against the stable source cache while reading/writing the output cache, so saved output revisions remain editable on reopen; `CacheStoreCapabilities.writeMode` distinguishes `READ_ONLY`, `DIRECT`, and `STAGED` output, and opened sessions expose that decision; mismatched sessions and matching projects over read-only backends reject edits at the core, while cache discovery and UI remain |
| Controlled workspace contracts | implemented-unverified | UI-neutral dock/panel/placement types, validated presets, reusable preset switching, visible focus styling, persistent status bar, and an opt-in `MainWindow` bridge that reuses the legacy renderer; the shared binder now accepts canonical sessions or `OsrsProjectSessionLoader.OpenedProject`, binds session-backed inspector/history/validation/assets, passes neutral definitions into the canonical viewport for footprint/material/collision-aware scenes, shows editable versus read-only state and compatibility issues, the object inspector displays optional animation/contouring/transform/replacement metadata, the history panel supports seeking, canonical OSRS viewport overlays plus non-selecting hover inspection are wired, and the constrained tool rail exposes command-backed terrain/object tools, collapsed advanced terrain and selection/multi-object tools, and multi-select debug overlay toggles while leaving the legacy rail intact; the asset browser now adapts its filter controls between narrow vertical side-rail and wide horizontal layouts, filters objects/floors/textures/models/sprites, and shows a bounded neutral model-geometry preview with visible focus styling; the shell exposes a constrained preset selector for Map, Terrain, Objects, Collision, and Validation/Debug; `File > Open from > OSRS project…` now offers explicit read-only or separate prepared-output-cache modes, attaches project autosave for editable sessions, offers identity/dimension-checked one-command recovery, and routes window Undo/Redo/Save actions to the same canonical OSRS session; full UI smoke coverage remains |
| OSRS project composition | implemented-unverified | `OsrsStudioProject` composes the neutral OSRS map/session/definition/asset services, owns cache lifecycle, supports read-only OpenRune, staged OpenRune-read/Displee-output, and explicit OpenRune-output arrangements, exposes stitched bounded scene windows, and is covered with neutral lifecycle/session tests plus a live build-240 project-level command/save/reopen integration test covering terrain semantics and object rotation; adapter fingerprints remain compatible across read-only `FileCache` and writable `CacheDelegate`, and a saved output project remains editable on reopen; `ProjectLayout` establishes `project.json`, `autosave/`, and `edits/`, and metadata writes are atomic; `SessionAutosaveCoordinator` and the JavaFX workflow now bind editable project sessions to recovery snapshots without mutating the source cache; faithful viewport parity and native FileStore writing remain gated |
| RuneLite/TSPS parity harness | in-progress | `OsrsRevisionVerifier` reports auditable cache, capabilities, metadata/fingerprint, revision-profile, neutral object/underlay/overlay/texture/model/map-scene definition availability, terrain/location decode, neighboring region windows with explicit holes, provisional-border stitching and shared-edge results, bridge/plane semantics, selected-region scene construction plus a reproducible scene fingerprint, and full loaded-window scene construction; read-only revision-237 verification passes on representative plain and bridge-heavy regions, including 9/9 neighboring regions, 147,456 world-addressed four-plane terrain tiles, 3,902/21,610 world objects, 120 bridge links, 3,983 non-empty collision tiles, minimap construction, semantic round trips, and zero-difference neutral scene round trips; pinned TSPS revision-240 probes now pass terrain, locations, authored geometry, round trips, and boundary stitching for water-dominant `(16,33)`, generated-height `(49,49)`, bridge-heavy `(50,50)`, and wall-heavy `(29,72)` regions, with 4,441 populated authored geometry tiles in the bridge probe and 9,712 objects in the wall probe; repeated verifier runs now produce the same neutral scene fingerprint, while `RenderSceneParity` and `MinimapParity` provide bounded tile/global and pixel-level comparison reports, and opt-in `RSPSI_OSRS_PARITY_FIXTURE` directories now make external scene fingerprints, TSPS-exported terrain semantic snapshots, TSPS location tuples, authored TSPS scene-geometry exports, and PNG minimap comparisons executable with identity validation; `verifyOsrsRevisionMatrix` now makes the representative fixture run repeatable from an explicit external manifest; all 4,726 RuneLite location tuples match for revision 237; the TSPS exporter now declares `minimap.mapScenes` from actual sprite availability, so no-sprite captures use the terrain/wall baseline while sprite-bearing captures opt into full asset composition and the pinned revision-240 `(50,50)` sprite-bearing fixture passes all four shaped planes with 0 differing pixels; `RSPSI_OSRS_REQUIRE_PARITY=true` now turns missing or warning external evidence into a release-gate failure, accepting either independent full scene fingerprint or geometry evidence as the scene-evidence branch; direct pinned-TSPS topology-table comparison is recorded in [`TERRAIN_PARITY.md`](TERRAIN_PARITY.md), while full lighting/material, licensed RuneLite golden comparisons, additional multi-region/instance captures, and full 3D renderer parity remain |
| Source-first project format | not-started | Design is recorded in [`STUDIO_DIRECTION.md`](STUDIO_DIRECTION.md): immutable base cache + Git-controlled semantic sources + disposable built cache. Current `project.json`, autosave, and staged output remain transitional compatibility paths; first implementation requires a versioned manifest, base identity, deterministic source loader/writer, and round-trip fixtures |
| Semantic build, diff, and revision reports | not-started | Future `WorldCompiler`, `BuildReport`, `SemanticDiff`, and revision-audit workflows must reuse `WorldDocument`, validators, asset repositories, and command transactions; do not compare opaque cache files or start incremental packing before the source format is stable |
| OpenRune Studio shell and workspace expansion | not-started | Future shared shell may host Map, Assets, World/Collision, and later Content/Build workspaces; Map Editor remains the only product workspace being completed now, with controlled JavaFX layout and UI-neutral contracts preserved for a later frontend |
| Lua, plugin permissions, Plugin Hub | deferred | Begin only after native command/plugin API is stable |
| Renderer/UI rewrite | deferred | Current JavaFX renderer remains the compatibility surface |

## Phase gates

Parity note: the TSPS `collision.json` export is retained as diagnostic
evidence, not a strict release requirement, because TSPS uses client
`clipType`/scene-edge rules while the canonical editor collision layer follows
OpenRune-Server `solid`/`blockWalk` and routefinder semantics. Focused bridge,
wall, object, and OpenRune `StepValidator` vector tests are the current
authoritative collision gate; a normalized RuneLite/OpenRune fixture can be
promoted later. The first pinned OpenRune-Server exporter probe did not produce
such a fixture: its object decoder expects config archive 55, while the pinned
TSPS revision-240 cache exposes archives 1–54 and 70+, leaving the server-side
object table empty. This is recorded as an adapter compatibility task, not
silently converted into parity evidence.

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

### Phase 4 — Foundation completion

Before expanding the workflow surface beyond the initial core tool plugin,
close the semantic foundation. This phase is complete only when the following are
covered by executable fixtures or explicit parity evidence:

- `OsrsRevisionFeatures` (or the final equivalent) drives map/location and
  definition decisions without scattered revision branches;
- explicit and generated heights, underlay/overlay blending, all 13 shaped
  tile topologies × four rotations, terrain materials, and shared edges;
- 64×64 regions, 8×8 chunks, world/region/local coordinates, loading-line
  holes, neighboring-region context, and instance transforms;
- bridges, authored versus effective/render planes, floor/roof/render flags,
  wall/wall-decor/ground/ground-decor categories, loc types, orientations,
  footprints, configs, and object-derived collision;
- model transforms, contouring inputs, recolor/retexture metadata, minimap
  inputs, and the distinction between decoded cache data and derived scene
  data;
- unified lazy asset access for objects, floors, overlays, underlays, models,
  textures, sprites, sequences, and map elements without archive knowledge in
  Studio plugins;
- revision audit reports for every supported cache fixture and semantic
  decode → encode → decode evidence for terrain and locations.

The exit gate is a green `verifyOsrsRevision` run over representative plain,
water/swamp, bridge/multi-plane, wall-heavy, region-boundary, and instance
fixtures, with the remaining warnings classified. No new large plugin or
renderer subsystem starts while a foundation item is unknown or represented
by a second competing model.

#### Phase 4 gate result — complete for the current supported scope

The semantic Phase 4 gate is met for the pinned revision-240 profile. The
operator-supplied `verifyOsrsRevisionMatrix` passes the representative
plain/water, generated-height, wall-heavy, bridge-heavy, sprite-bearing, and
boundary fixtures; `verifyOsrsInstance` passes 676 packed transforms with zero
terrain or object differences; and the repository `foundationGate` is green.
The remaining `CLIENT_CLIP_TYPE` collision output is explicitly diagnostic,
not silently treated as OpenRune route parity. Manual desktop acceptance,
full 3D renderer comparison, and the Dear ImGui host are follow-on acceptance
work and do not require a new foundation model.

### Phase 5 — Editing improvements

Prioritize terrain sculpting, object transforms, richer selection, collision tools, then region/asset workflows. Every operation must use the command/history path.

Once the foundation gate passes, package the workflow as first-party plugins in
this order: terrain tools, object tools, selection, collision/route previews,
asset browser/definition inspection, validation/debug overlays, minimap or
scene preview, and finally scene/model export. Each plugin registers neutral
tools/panels and delegates all mutation to core commands. Export plugins are
read-only consumers of immutable scene projections. Plugin permissions,
external plugin discovery, Lua/CS2 scripting, and a public Plugin Hub remain
later work.

The first controlled JavaFX workspace shell is now available as a frontend
adapter. It materializes the neutral workspace presets into fixed tool and
inspector rails, a centered viewport, controlled bottom tabs, and a persistent
status row. `ControlledWorkspaceBridge` binds the same panels to the legacy
compatibility session and exposes an OSRS `OpenedProject` binding path. It does
not own document state, renderer state, or arbitrary docking; legacy sessions
retain the legacy renderer while OSRS sessions use the small canonical
semantic preview until faithful 3D scene parity is ready.

### Phase 6 — Source project and build pipeline

This phase begins only after the foundation and core map workflow are accepted.
It turns the cache-aware editor into a reproducible OpenRune project without
creating a second world model.

1. Freeze a versioned project manifest containing game, format, base cache
   identity, and supported revision profile.
2. Define and fixture-test semantic map sources for base-region patches and
   full-region authoring; use 8×8 chunks as an invalidation/build unit while
   keeping the on-disk format changeable until merge behavior is proven.
3. Implement source load/save against `WorldDocument` and `EditorSession`, with
   deterministic ordering and explicit symbolic/numeric asset references.
4. Add a build coordinator that resolves base cache + sources into a separate
   output cache and reports dirty regions, validation issues, and output
   identity.
5. Add semantic before/after/ghost/diff views and meaningful Changes,
   Validation, Build, and Debug records.
6. Add reproducible command-line/CI validation and cache builds.
7. Only later consider chunk-aware merge assistance, construction layers,
   committed asset palettes, and visual history checkpoints.

The output cache remains disposable. A brush stroke or fragment paste remains
one command, one history entry, and one source transaction; the builder runs
after the transaction, not on every pointer event.

### Future Studio expansion (deferred)

The product can grow beyond map editing after Phase 6 has a stable source/build
contract:

- World: world map and collision/navigation diagnostics;
- Assets: object/floor/model/texture browsing and definition inspection;
- Build: validation, semantic diff, pack, and revision audit workspaces;
- Content: interfaces, CS2, and cutscenes, only after a separate scope and
  correctness review.

These are shared-shell workspaces or first-party plugins, not new foundations.
742-era cache/runtime support, non-OSRS profiles, unrestricted docking, server
gameplay, public plugin distribution, and renderer/frontend replacement remain
out of scope.

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
independent TSPS terrain-semantic export path is implemented and passes for
build 240; the remaining parity evidence is an independent scene fingerprint
or equivalent 3D render export, plus broader output/cache and manual smoke
coverage. A read-only live build-240 run at region `(50,50)` now confirms the
modern FileStore path, numeric/short profile, 2,937 map groups, 141,363
neutral assets, 9-region context, bridge links, scene meshes, object
projections, and exact neutral round trips. The immediate renderer priority is
now the packet-first 3D scene gate: final terrain colors/materials, exact
shape-aware model geometry and transforms, ordered tile layers, camera
visibility/occlusion, textures/UVs, alpha/priorities, and an independent
render-packet or 3D export fixture. Manual JavaFX workflow, canonical OpenRune
route semantics, and native ImGui host checks remain separate acceptance gates.
Do not replace the legacy renderer or broaden revision support until the
neutral 3D packet contract is frozen and the compatibility path is no longer
the only renderer consuming modern assets.

The source-first project and broader Studio ideas are now recorded, but they do
not change that immediate gate. Finish and verify the map/editor foundation
first; then implement Phase 6 as one deliberate source/build milestone.
