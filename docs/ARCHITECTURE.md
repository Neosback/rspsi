# RSPSi Architecture Contract

This is the implementation contract for the stabilization work. It narrows
the research in [`REFERENCE_ECOSYSTEM.md`](REFERENCE_ECOSYSTEM.md) into rules
that can be checked in code.

The product scope and controlled layout are locked in
[`PRODUCT_DESIGN.md`](PRODUCT_DESIGN.md).

The feature/plugin ownership model is recorded in
[`PLUGIN_ARCHITECTURE.md`](PLUGIN_ARCHITECTURE.md). In short, features own
their state, tools, commands, inspectors, overlays, and other contributions;
the Studio shell owns the hosts, placement, lifecycle, and frontend adapters.

## Ownership and dependency direction

```text
JavaFX / future ImGui frontend
            ↓
neutral tools, input, inspectors, renderer API
            ↓
EditorSession + WorldDocument + commands + selection
            ↓
neutral cache/definition services
            ↓
Displee legacy adapter | OpenRune OSRS adapter
```

The cache/provider selection is a separate startup layer from feature plugins.
The launcher selects the OSRS bundle first; the bundle opens the cache,
establishes revision identity, and initializes the cache/definition services.
Only after that succeeds does Studio create the canonical project session and
mount terrain, object, selection, collision, validation, and other feature
plugins. Dear ImGui or JavaFX consumes the resulting neutral session; neither
frontend is responsible for opening a cache or decoding archive formats.

```text
launcher selection
        ↓
OsrsBundle + cache identity
        ↓
WorldDocument / EditorSession / neutral assets
        ↓
first-party feature plugin host
        ↓
JavaFX or Dear ImGui adapter
```

This distinction preserves the existing OSRS selection workflow while
preventing feature plugins from reaching into legacy static loaders. The
current legacy `ClientPlugin`/OSRS loader remains a compatibility adapter for
the old renderer; the long-term project composition uses `OsrsBundle`,
`OsrsStudioProject`, and neutral cache services as the backend boundary.

The neutral editor packages own world data, editing, selection, tool contracts,
and renderer contracts. They may not import JavaFX, ImGui, OpenGL/LWJGL,
Displee, or OpenRune types. Frontends and cache adapters translate at the
boundary.

## Future source/build boundary

The long-term project model is source-first, but it must be layered onto the
existing canonical model rather than replacing it with a new editor format:

```text
read-only base OSRS cache
          +
versioned OpenRune project sources
          ↓
SourceProjectLoader → WorldDocument / EditorSession
          ↓
commands → validation → semantic diff
          ↓
WorldCompiler → disposable output cache
```

The base cache is never an implicit write target. The project source records
the base identity and semantic authored changes; the built cache is
reproducible output. While a project is open, `WorldDocument` remains the
working state so undo, redo, preview, and validation do not depend on parsing
files after every pointer event. Source serialization and cache packing are
explicit save/build operations.

This future boundary is described in [`STUDIO_DIRECTION.md`](STUDIO_DIRECTION.md).
It is not yet a second persistence implementation. Until the foundation gate
passes, `ProjectLayout`, autosave, `CacheStore`, `MapService`, and staged output
remain the supported paths.

Future source/build services must consume the existing neutral contracts:
`CacheStore`, `MapService`, `WorldDocument`, `EditorSession`, `EditorCommand`,
`WorldValidator`, and neutral asset repositories. They must not introduce a
second map model, cache facade, command history, or revision-specific branch
into editor code.

## Future workspace composition

OpenRune Studio may add specialized workspaces around the same shell and core:

- World: Map Editor first, then world-map and collision/navigation views;
- Assets: asset browser, definition inspector, model/texture previews;
- Content: interfaces, CS2, and cutscenes only after the world workflow is
  mature;
- Build: validation, semantic diff, packing, and revision audits.

Each workspace may be a plugin or frontend contribution, but it cannot own
project identity, world state, history, cache writes, or frontend-specific
types. The shell uses a stable application frame with feature workspaces; it
does not replace the whole JavaFX stage for each feature, and it does not add
unrestricted docking during foundation work.

## FileStore and plugin boundaries

OpenRune FileStore is the production cache/data layer. It is responsible for
filesystem access, raw map/location payloads, OSRS definition decoding,
models, sprites, XTEA, packing, and generic revision tooling. RSPSi's cache
adapter may use it, but the editable world model does not depend on its
archive layout or runtime types.

FileStore does not replace the feature plugin system. These are orthogonal
boundaries: FileStore is the selected OSRS cache backend, while editor plugins
own tools, commands, inspectors, overlays, panels, and other contributions.
`OsrsBundle` chooses and validates that backend before feature plugins are
started. A selected modern OSRS cache is opened through the OpenRune adapter;
a 317 cache remains on the quarantined Displee compatibility path. There is no
automatic modern-cache fallback to the 317 path and no `FileStorePlugin`.

If the shell exposes this state, it is a cache-source/capability display, not a
plugin selector: `OpenRune FileStore (OSRS)` with `READ_ONLY`, `STAGED`, or
explicit `DIRECT` output capability. The UI must not imply that a 317 decoder
is an equivalent alternative for a modern cache.

RSPSi owns the semantic layers that FileStore should not absorb:

- map/location codec contracts as consumed by the editor;
- `WorldDocument`, `TerrainTile`, `WorldObject`, coordinates, regions, and
  chunks;
- bridges, effective/render planes, instances, collision, route previews,
  scene construction, shaped-tile meshes, and floor-blending inputs;
- sessions, commands, history, selection, dirty regions, and renderer APIs.

The current RSPSi neutral services are therefore the canonical implementation
while upstream FileStore improvements remain candidates. We must not add a
second production map model, collision engine, scene graph, or model runtime
just because a donor project contains one. A future FileStore contribution is
acceptable only if it is generic, provenance-reviewed, independently useful,
and consumed through the same RSPSi-owned interfaces.

`Plugins/OSRSPlugin` is a transitional renderer bridge, not the modern Studio
backend. It still installs the static loader adapters required by the legacy
software renderer. Retire it only after the compatibility renderer has been
migrated to neutral definitions/assets/scene services and the equivalent live
render and parity checks pass. New code must use `CacheStore`, neutral
definition views, `OsrsMapService`, and `RenderScene` instead of adding new
static-loader calls.

First-party plugins sit above this core. Terrain/object tools, selection,
asset browsing, definition inspection, validation, debug overlays, collision
previews, and minimap/scene-preview workflows may be packaged as vertical
feature plugins. A feature keeps its state, tools, commands, inspectors,
overlays, and settings together, while the shell decides where those
contributions appear. Plugins register through the neutral contribution
registry and execute core commands; they cannot own world state, history,
project identity, cache writes, or frontend types. The plugin boundary is a
packaging and capability boundary, not a second editor architecture.

## Canonical APIs

- `WorldDocument` is the mutable document model; `WorldModel` is a temporary
  compatibility name.
- `EditorSession` owns document state, selection, history, dirty state, and
  an optional neutral `SessionSaveHandler`. `OsrsSessionLoader` composes that
  handler with `OsrsRegionSaveCoordinator`, so callers save a loaded region
  through `session.save()` without passing cache or format types into editor
  code.
- `OsrsProjectSessionLoader` compares project/cache revision, subrevision, and
  fingerprint before attaching the save handler. Matching projects are
  save-capable; mismatched or unidentified caches remain inspectable but
  mutation-rejecting and read-only until ID migration exists.
- `OsrsRegionSaveCoordinator.saveAll(...)` is the multi-region save boundary:
  it pre-encodes every region, writes all terrain/location payloads, flushes
  once, and marks sessions saved only after the complete batch succeeds.
- `SessionStateListener` exposes edit/undo/redo/save-marker changes to
  frontend adapters, while `SelectionChangeListener` exposes the final
  unified selection value after each selection operation. Neither listener
  carries UI types or owns the state it observes.
- `EditorCommand` is the canonical mutation contract; `EditCommand` remains a
  source-compatible alias for external callers while all built-in commands use
  the canonical interface directly.
- The legacy “fix upper-plane heights” action now dispatches through
  `FixUpperPlaneHeightsCommand` whenever a controlled session is present; the
  bridge updates the existing renderer and legacy map arrays from the command,
  while the old direct loop remains only as the no-session fallback.
- `CommandHistory.moveTo(...)` and `EditorSession.jumpToHistory(...)` provide
  exact history navigation with one consolidated session update; failed
  forward replay rolls back commands already reapplied.
- `WorldFragment` is the canonical portable terrain/location copy-paste
  payload; fragment pastes are grouped commands rather than direct scene
  mutations.
- `DirtyRegion` groups command invalidation by plane and 8×8 chunk so scene,
  collision, minimap, and cache writers rebuild only affected derived data;
  an explicit compatibility form can still invalidate every plane.
- `TerrainMeshBuilder` owns the 13 shaped-tile topologies and four rotations;
  it produces neutral mesh data for renderers and is covered independently of
  the legacy `ShapedTile` class. The map-facing tile shape remains the encoded
  0..11 value; mesh topology 0 is the flat model and overlay values map to
  topology 1..12 at this boundary.
- `TerrainTile`/`TileSnapshot` use one-based cache-facing floor IDs with zero
  meaning absent for both underlays and overlays. Definition providers remain
  zero-based, so the map adapters subtract one only at lookup boundaries. The
  modern overlay high bit is masked in the decoder and is not allowed to leak
  into the editor model.
- `TerrainSharedEdgeInvariantTest` verifies every pair of the 13 topologies ×
  four rotations across east/west and north/south neighbors. Shared corner
  positions and heights must agree; this is a geometry invariant, not a
  renderer-specific visual assumption.
- `CollisionMap`, `CollisionFlag`, and `CollisionDirection` own editor
  collision semantics; server routefinder types are reference inputs only.
- The collision model preserves OpenRune's optional route-blocker layer. The
  neutral routefinder follows the donor's normal strategy by default and can
  explicitly enable route-blocker masks for diagnostics and parity tests.
- `OsrsCollisionBuilder` consumes `ObjectCategory`/`OsrsLocShape` rather than
  duplicating raw shape ranges. This preserves OpenRune's distinction between
  blocking walls, non-blocking wall decor, ground-layer locations, and ground
  decor in one auditable mapping.
- `RouteFinder` provides the first neutral collision-preview behavior: bounded
  eight-way routes with OpenRune-compatible swept footprint checks for square
  actors, diagonal corner protection, and straight projectile line-of-sight
  checks. It remains intentionally small and replaceable while broader
  OpenRune-Server route/reach parity is verified against fixtures.
- `CollisionMap.canTravel(..., size, ...)` is the focused port of the donor's
  `StepValidator` edge-sampling contract. The size-one overload remains
  source-compatible, while larger actor previews no longer silently test only
  their origin tile.
- `Reachability` uses the same default-off/explicit-on route-blocker choice as
  `RouteFinder`, so object reach previews cannot silently use a different
  collision layer.
- `RoutePreviewService` packages route, projectile line-of-sight, and
  object-footprint reach results as immutable neutral data. Frontends render
  `RoutePreview` without constructing or mutating collision state; the
  controlled JavaFX viewport is the first consumer and supports a selected
  object's resolved footprint for reach checks.
- `CollisionTileSnapshot` exposes ordinary movement blockers, the optional
  route-blocker layer, projectile blockers, and decoded floor/object/roof
  state so collision overlays can show routefinding semantics without
  importing server flags.
- `OsrsCollisionBuilder` converts canonical terrain flags into collision and
  resolves the plane-1 `LINK_BELOW` bridge relationship. Object collision is
  supplied by the optional neutral `ObjectCollisionView`; missing definitions
  are skipped for now and can be surfaced by validation rather than guessed.
- `SelectionModel` exposes one selection value for tiles, areas, vertices,
  objects, and fragments while retaining its legacy tile-set methods.
- `WorkspaceCatalog` and `StandardWorkspaceCatalog` define the fixed frontend
  presets; JavaFX renders them, but no frontend owns the layout model.
- `WorldWindow`, `WorldTileAddress`, and `TileInspectorSnapshot` provide the
  neutral coordinate/inspection payload used by future status bars and debug
  overlays. Local document coordinates remain separate from world-space
  region/chunk derivation.
- `WorldRegionWindow` composes bounded canonical 64×64 regions for scene and
  neighbor-context work. Missing regions remain explicit holes, so boundary
  blending and loading-line behavior cannot silently treat absent data as
  authored empty terrain. `stitchSharedEdges()` resolves the provisional
  final row/column of standalone decoded regions from loaded neighbor tile
  origins before scene construction; `boundaryMismatches()` remains available
  before and after stitching for diagnostics.
- `InstanceChunkTemplate`, `InstanceChunkTransform`, and `InstanceChunkGrid`
  keep current OSRS
  instance-template packing and 8×8 rotation semantics neutral. They map
  source world tiles to all repeated scene occurrences, preserve missing
  chunks as holes, invert the mapping for inspection, and adjust object
  orientation without exposing RuneLite or client classes.
- `InstanceWorldBuilder` materializes those transformed chunks into a normal
  `WorldDocument`, rotating terrain corner heights and overlay orientation and
  translating object anchors into destination-document coordinates. Source
  holes remain default/unloaded destination tiles rather than fabricated cache
  data.
- `WorldRegionWindow.boundaryMismatches()` checks shared corner heights across
  loaded east and north region edges; incomplete windows report only the
  boundaries that can actually be proven.
- `EditorTool`, `ToolContext`, `PointerEvent`, and `ToolInspector` are shared
  by all frontends. The canonical JavaFX viewport translates native
  press/drag/release events into `PointerEvent`, dispatches them through
  `EditorToolController`, and renders tool overlays through `OverlayDraw`;
  `CanonicalToolPanel` provides the first constrained OSRS tool selector,
  numeric brush settings, core terrain/object tools, collapsed advanced terrain
  and selection/multi-object tools, multi-select debug overlay toggles, and
  bounded route/LOS/reach preview controls, while
  `AdaptiveToolPanel` leaves the existing legacy rail intact for compatibility
  sessions. Legacy viewports remain compatible because object picking is
  optional. `AssetBrowserPanel` forwards neutral object selections into the
  canonical placement settings, so the browser can configure placement
  without exposing cache or definition types to a tool.
- `SceneRenderer` consumes neutral scenes and changes and returns neutral pick
  results.
- Cache and definitions are accessed through RSPSi interfaces, never raw
  archive/index/file objects.
- `Cache.readFile(...)`, `Cache.readNamedFile(...)`, and the deprecated
  byte-returning `Cache.getFile(CacheFileType, int)` are the byte-oriented seams
  for the legacy client/resource path. New consumers receive bytes through those
  methods; the deprecated `Cache.getFile(CacheFileType)` index accessor remains
  only for compatibility loaders that still require Displee indexes.
- Compatibility loader initialization now uses the neutral `CacheIndexView` /
  `CacheArchiveView` plus `CacheStore.fileIds(...)` contract. The legacy store
  and OpenRune store provide the same archive/file view, while old renderer
  classes that still need Displee archive objects remain explicitly quarantined.
- The legacy client no longer constructs named-sprite archives itself. Its
  compatibility `Cache` facade owns the remaining Displee archive operation
  through `readLegacySprites(...)`; the client/editor resource path uses the
  project-owned `CacheCompression` byte utility. This is a containment step,
  not a claim that the old renderer classes are migrated.
- The shared `Buffer` is byte-only. Legacy sprite/image/texture decoders may
  still receive Displee archives, but they extract bytes at that boundary
  instead of making the cache-library `File` type part of a reusable I/O API.
- `:Client:verifyCacheBackendBoundary` keeps the remaining raw cache imports
  limited to the legacy facade, old renderer/definition decoders, and explicit
  cache adapters. Any new product code that imports Displee or OpenRune must
  first establish an intentional adapter boundary.
- Displee dependencies are `implementation` dependencies of `Client`, not
  transitive editor API dependencies. The old compatibility classes can still
  compile and run, while `Editor` and future frontends consume RSPSi contracts.
- `CacheStoreFactory.legacy(Path)` is the path-based compatibility entrypoint;
  the raw `legacy(CacheLibrary)` overload is deprecated for integrations that
  have not migrated yet.
- The raw `Cache` escape hatches (`getIndexedFileSystem`, `writegetFile`, and
  `createArchive`) are explicitly deprecated. They remain only so the current
  compatibility client can be retired after OSRS parity, not as extension
  points for new editor features.
- `CacheStore` reads and writes use defensive byte-array ownership at each
  concrete backend. Decoders can therefore inspect or transform returned data
  without mutating a live cache buffer or a pending output write.
- Neutral replacement maps are sorted before publication so record snapshots
  and `RenderSceneFingerprint` values remain reproducible across separate cache
  openings.
  This is an incremental boundary, not a claim that all legacy loaders have
  already migrated.
- `CacheStore.metadata(revision)` is an optional neutral identity capability;
  project compatibility can compare revision, subrevision, and fingerprint.
  A project using a separate output cache assesses identity against its stable
  source cache while map reads/writes use the output layer, so saving does not
  make the same project falsely incompatible on its next open
  without depending on an OpenRune store class. Legacy stores may leave it
  unavailable.
- `LayeredCacheStore` keeps base-cache reads separate from staged output-layer
  writes. Save coordinators flush an explicit output layer; painting cannot
  mutate the source cache implicitly. `CacheStoreFactory.layered(...)` is the
  supported construction seam for that topology. For OSRS work,
  `CacheStoreFactory.openRuneWithDispleeOutput(...)` makes the arrangement
  explicit: OpenRune reads the base cache and a distinct Displee cache receives
  staged output. Equal base/output paths are rejected, and the layer preserves
  the base cache identity for project compatibility decisions.
- `MapService` exposes semantic landscape and location payload access; its
  OSRS implementation owns the file-0/file-1 archive convention.
- `MapService` write methods target existing indexed regions only and honor
  backend writability. A read-only OpenRune spike therefore fails safely
  instead of appearing to save an edited map.
- `OsrsRegionSaveCoordinator` is the editor-facing save boundary for a 64×64×4
  region. It encodes terrain and locations before writing either payload,
  flushes through `MapService`, and advances the session's saved marker only
  after the write batch succeeds. Writable OpenRune packing remains a later
  parity-gated adapter milestone.
- `OsrsRegionDecoder` is the neutral format adapter for the current OSRS
  landscape/location payloads. It produces `WorldDocument` and
  `WorldObject` data without exposing OpenRune, Displee, archive IDs, or
  opcodes to editor packages.
- `OsrsLocShape` and `ObjectCategory` are the canonical editor-side location
  semantics. They preserve OpenRune's shape IDs 0–22 and layer mapping for
  walls, wall decor, game objects, and ground decor without leaking donor
  enums into inspectors, selection queries, collision, or validation.
- `OsrsRegionEncoder` writes dirty-region terrain and location payloads from
  the canonical model. It uses explicit heights during the first migration
  so decode/encode/decode tests verify semantic equality without preserving
  source-specific generated-height choices, and rejects inconsistent shared
  corner heights or unsupported location shape IDs before bytes are emitted.
- `WorldValidator` is the deterministic pre-save/parity diagnostic layer;
  renderers and UI panels consume its issues rather than reimplementing
  world invariants.
- `EditorSession` records dirty work at plane plus 8×8 chunk granularity. An
  edit on a chunk edge also invalidates the adjacent cardinal chunk so shared
  terrain edges, floor blending, and picking can be rebuilt without rebuilding
  unrelated planes or a complete region.
- `CommandTransaction` is the single rollback helper for grouped edits. It
  applies composite, paste, and multi-object delegates in order and undoes
  already-applied delegates if a later one fails, keeping failed commands out
  of session history.
- `ObjectInspectorSnapshot` resolves optional neutral definition and collision
  providers plus optional appearance data into immutable frontend data. It
  keeps missing definitions explicit and exposes canonical category/shape
  names, animation, transforms, contouring, and replacement metadata without
  coupling an inspector to Displee, OpenRune, JavaFX, or ImGui.
- `MapSceneSpriteView` is the optional neutral ARGB asset boundary for
  object/map-scene sprites. Minimap composition can center and clip these
  sprites without knowing a cache index, graphics-defaults group, or sprite
  decoder. A backend that cannot expose the selected sprite leaves it absent
  rather than fabricating a visual asset.
- `ModelDefinitionView` carries cheap model metadata for search and
  inspection; `ModelGeometryView` is the separate lazy geometry boundary for
  previews and future renderers. Geometry uses packed XYZ vertices and ABC
  triangle indices with defensive ownership and optional face channels, so a
  model browser never needs to expose an OpenRune `ModelType` or eagerly decode
  the entire model index.
- `SessionInspectorPanel` caches the neutral collision map for its bound
  session and rebuilds it only after document/provider changes; hover
  inspection therefore reads collision snapshots without rebuilding the
  entire map for every pointer event.
- `ObjectAppearanceView` is optional neutral object model metadata: animation,
  ground contouring, scale/translation, recolor, and retexture pairs. Legacy
  and OpenRune adapters may provide it independently of collision data; an
  absent value never invents renderer behavior.
- `RenderObject` is the renderer-facing projection of a canonical
  `WorldObject`. It resolves optional neutral definitions and collision views
  into category, shape, orientation-aware footprint, model IDs, and movement/
  projectile blocking inputs. The raw object remains present for editing and
  identity; renderer code does not need to rediscover cache semantics.
- `DefinitionAssetRepository` turns neutral definition IDs into searchable
  `AssetDescriptor` values for the first asset-browser categories (objects,
  underlays, overlays, and textures). `SymbolicNameProvider` is the narrow
  adapter seam for RSCM/GameVal names: descriptors retain the display name,
  numeric ID, and optional symbolic key, and search includes that key without
  importing a naming-library type into the editor.
- `RenderSceneBuilder` derives renderer-independent terrain meshes and
  canonical object placements plus `RenderObject` projections from
  `WorldDocument`. It supports both complete
  snapshots and `RenderChanges`-scoped terrain rebuilds, preserving untouched
  mesh instances while refreshing canonical object placements. Neighbor tiles
  can be included by the caller for blended floors and shared edges; scene
  construction remains outside `SceneGraph`. Definition-aware scenes also
  carry neutral terrain materials, and every complete scene carries per-corner
  `TerrainLight` values from the OSRS directional normal calculation.
  Complete scenes also carry local `CollisionTileSnapshot` values derived from
  the same neutral terrain/object collision builder used by window scenes, so
  route and overlay consumers do not reconstruct collision independently.
- `RenderWindowSceneBuilder` projects a `WorldRegionWindow` into world-addressed
  neutral tiles and objects. It prepares a deep copy, stitches loaded
  neighboring edges there, and preserves absent regions as holes, so
  loading-line context and region boundaries remain observable without
  mutating authored documents or bypassing session dirty tracking. It also
  publishes world-addressed `CollisionTileSnapshot` values built from terrain
  flags and, when available, neutral object definitions; collision overlays,
  route previews, and inspectors therefore consume the same semantics as the
  neutral collision services.
- `SceneRenderer.update(RenderScene, RenderChanges)` is the preferred
  incremental publication path. Its default delegates to the original
  `update(RenderChanges)` method so existing renderers remain source
  compatible, while new renderers receive the derived scene they must draw.
- `RenderSceneGoldenTest` locks a mixed two-plane scene containing shaped
  terrain, canonical objects, and a bridge link. It is a local semantic
  characterization fixture; TSPS/RuneLite parity fixtures remain independent
  acceptance evidence.
- `RenderSceneFingerprint` also produces a deterministic SHA-256 identity for
  the neutral scene snapshot. The cache verifier reports it as construction
  evidence; it is intentionally not a rendered-image parity hash.
- `RenderSceneParity` compares two neutral scene snapshots and returns bounded
  tile/global differences across terrain, materials, lighting, objects, and
  bridges. It is the fixture seam for TSPS/RuneLite comparisons; it does not
  import or execute either oracle.
- `MinimapParity` compares neutral ARGB rasters with exact mismatch counts and
  bounded pixel samples. It is the corresponding fixture seam for visual
  minimap comparisons and does not assume a particular frontend image type.
- `OsrsParityFixture` is the external fixture adapter for the verifier. It
  validates optional region/revision/cache identity properties, loads PNG
  minimap references by plane, and leaves the fixture directory outside the
  repository. `RSPSI_OSRS_PARITY_FIXTURE` enables those comparisons; without it
  the verifier reports parity as `NOT_RUN` rather than inventing an oracle.
- `MinimapBuilder` produces a deterministic ARGB raster from canonical tiles
  and neutral floor definitions. It includes an explicit blocked-tile color,
  optional cardinal underlay blending, and deterministic missing-definition
  fallbacks. It is a construction baseline only; mapscene icons and live
  RuneLite/TSPS image parity remain separate.
- `WorldDocument.bridgeLinks()` turns the OSRS bridge flag into explicit
  authored-plane/effective-plane links. The raw flag remains part of the
  canonical tile snapshot for lossless encoding, while scene and collision
  consumers can use the same derived relationship instead of duplicating bit
  interpretation.

## Debug overlay data

`com.rspsi.editor.debug` contains the frontend-neutral debug view contract.
`DebugOverlayBuilder` turns a `WorldDocument`, its world origin, and an
optional `CollisionMap` into a `DebugOverlaySnapshot`. The snapshot exposes
tile inspector payloads, bridge/effective-plane information, collision
directions, and semantic tile/chunk/region/world-window grid lines. A JavaFX
viewport may render these values today, while a future OpenGL or Dear ImGui
frontend can consume the same data. Overlay colors, labels, and drawing
technology remain frontend concerns.

## Frontends

JavaFX is the current frontend and keeps the existing workflow working. Its
event adapters translate to `PointerEvent`; JavaFX properties and controls do
not enter tool or document classes. Dear ImGui remains a future frontend
option, with GLFW/LWJGL integration deferred until the neutral contracts and
legacy behavior are stable.

`Editor/src/main/java/com/rspsi/ui/workspace/ControlledWorkspaceShell.java`
is the first concrete JavaFX adapter for the neutral workspace contracts. It
renders fixed side rails, a permanent center viewport, and controlled bottom
tabs from `WorkspaceCatalog` data. It may host legacy panels while migration
continues, but it must not become the owner of workspace, document, or
renderer state. The existing `MainWindow` remains the compatibility entry
point until the shell has equivalent launch/load/edit/save coverage.

`ControlledWorkspaceBridge` is an opt-in adapter selected by the
`controlledWorkspace` setting. It reparents the existing `main_test4.fxml`
tool rail, renderer viewport, asset pane, and menu bar into the shell while
leaving the default legacy layout unchanged. The bridge uses session-backed
inspector/history/validation panels, plus a placeholder console panel, while
the inspector resolves object details through the neutral `DefinitionProvider`
contract (the compatibility bridge currently supplies the legacy adapter).
Tile inspection also derives a neutral `CollisionTileSnapshot` from the
canonical document, making movement/projectile blockers, floor/object
blocking, and roof semantics visible without placing collision logic in the
JavaFX panel.
When an OSRS-backed `AssetRepository` is supplied, the asset pane uses
`AssetBrowserPanel` to search display names, numeric IDs, and optional
RSCM/GameVal keys; descriptors also carry neutral property summaries for
object size/models/actions/collision and floor/texture values. A null
repository preserves the legacy inspector fallback.
the controlled workflow and its manual coverage continue to be migrated.

`WorkspaceStatusBar` is a persistent JavaFX state row below the controlled
bottom tabs. It reports the active OSRS region/project context, cache revision,
editable versus read-only state, compatibility issues, and saved/dirty state.
It observes only `EditorSession` state and is intentionally not a cache or
renderer status channel. Its coordinate readout accepts neutral hover data and
shows local, world, region, and chunk coordinates without changing selection.
Preset changes detach and remount the row safely, so
the fixed workspace can change without losing session state.

`ControlledWorkspaceBridge.bindSession(...)` is the shared frontend binding
path for legacy compatibility sessions and future canonical OSRS sessions.
`bindProject(...)` additionally supplies an `OsrsProjectSessionLoader`
`OpenedProject` to the same history, inspector, validation, status, and asset
panels. Legacy sessions continue to use the legacy renderer; OSRS project
sessions use the canonical semantic preview described below until faithful
3D scene parity is ready.

`ControlledViewportPanel` keeps the existing legacy viewport available and
can switch the controlled workspace to `CanonicalSceneViewport` for a loaded
OSRS project. The canonical viewport is a deliberately small top-down
semantic preview backed by `SessionSceneController`; it supports neutral tile
picking and selection and is not a claim of final 3D/render-parity support.

`OsrsStudioProject` is the Client-side OSRS composition root. It owns the
neutral map service, project/session loader, definition provider, asset
repository, and cache lifecycle. It supports an OpenRune read-only source and
the currently validated OpenRune-read/Displee-output arrangement while
keeping both backend types behind the cache boundary. Frontends receive
`OpenedProject`, `DefinitionProvider`, and `AssetRepository`, never archive,
filesystem, or cache-library objects.

`ProjectLayout` owns the small project directory contract: `project.json`,
`autosave/`, and `edits/`. `ProjectMetadataStore.write(...)` replaces metadata
through a temporary file and atomic move when the filesystem supports it, so a
crash cannot leave a partially written project identity. `initializeProject(...)`
captures the selected OpenRune cache fingerprint without copying or modifying
the cache; opening a different cache later still goes through the existing
read-only compatibility decision. `OsrsProjectSessionLoader` also applies the
selected backend capabilities: a matching project opened over a read-only
OpenRune store is inspectable only, while the explicit staged output store can
produce an editable session.

The JavaFX project opener exposes that boundary explicitly: read-only source
inspection or a separately prepared output cache. Editable sessions attach
`SessionAutosaveCoordinator`, schedule recovery snapshots outside the core,
and can recover a matching snapshot as one `PasteFragmentCommand`; the source
cache remains untouched until the normal session save path is invoked.

The composition root also exposes `openWindow(...)` and
`openWindowAround(...)` for scene consumers. These load a bounded OSRS context,
preserve missing-region holes, and stitch shared terrain borders before the
window is consumed. A window is scene context, not a second editor model;
editable state remains owned by the `EditorSession` returned by `openRegion`.

`SessionSceneController` is the neutral session-to-renderer binding. It loads
the initial `RenderScene`, consumes session-owned dirty chunks as bounded
renderer updates, and removes its listener on close. JavaFX and future ImGui
frontends can host this binding without placing UI types in the editor core.

The OSRS project opener also binds the window-level Undo, Redo, and Save menu
actions to the same canonical `EditorSession` used by the controlled panels.
Legacy sessions retain their compatibility fallback, but an active OSRS
project can no longer send a menu action to the stale legacy session or
`SceneGraph` history, including when the canonical history is empty.

The JavaFX keyboard adapter follows the same bridge for Ctrl+Z/Ctrl+Y. It does
not select a history implementation itself; the active window dispatches to
the canonical session when an OSRS project is open and retains the legacy
fallback for the compatibility editor.

Delete follows the same rule: an OSRS object selection becomes one grouped
canonical command, while an active OSRS project with no object selection
consumes the key instead of allowing the hidden legacy `SceneGraph` to mutate.

The canonical JavaFX tool rail exposes the existing fragment contracts as a
small clipboard workflow: selection is captured with `WorldFragment.capture`,
encoded by `WorldFragmentCodec`, and pasted through `PasteFragmentCommand`.
The same adapter offers explicit JSON file import/export. Clipboard, file
dialogs, and error presentation remain frontend concerns; the fragment data
and undo semantics remain neutral.

`SessionAutosaveStore` writes a versioned snapshot of the canonical document,
project identity, and history position to `ProjectLayout.sessionAutosaveFile()`
(`autosave/session.json`) using the same atomic-replacement rule as project
metadata. Its layout-aware overloads read the project metadata and canonical
path together. Recovery restores a standalone `WorldDocument`; it never opens
or rewrites the source cache. `SessionAutosaveCoordinator` binds that store to
an `EditorSession`: every edit, undo, redo, and save-marker transition updates
the recovery snapshot without changing the session's saved marker. It has no
scheduler, so JavaFX or a future frontend supplies timer policy. The
`OsrsStudioProject.attachAutosave(...)` seam verifies project identity before
attaching the coordinator.

When a legacy map reaches its existing ready state, the client emits a small
map-ready lifecycle callback. In controlled mode `MainWindow` imports terrain
and object anchors into a fresh `EditorSession`, attaches
`LegacyMapDocumentBridge`, and binds the inspector/history/validation panels.
This keeps the default legacy path untouched while making the controlled
panels reflect the loaded map rather than a synthetic document. The bridge
uses scene object keys, shared-corner height writes, and footprint-aware
removal only at the compatibility boundary; new editor behavior remains
command/session-owned.

## Correctness workflow

RuneLite DevTools is the live OSRS truth viewer. TSPS and RuneLite cache/client
behavior provide independent scene and map references. OpenRune provides the
planned production OSRS cache backend. Explv map tiles are visual QA only;
Domw71's rev-240 editor is forensic revision evidence; the runelite cache
updater informs future revision-audit reports; model exporters isolate geometry
decode/transform/rendering problems.

## Migration rules

1. Preserve current JavaFX and software-renderer behavior behind adapters.
2. Add new editing behavior through `EditorSession` and `EditorCommand`.
3. Do not add new editing logic to `SceneGraph`, global `Options`, or static
   history.
4. Migrate one input/tool path at a time and retain compatibility constructors
   until characterization tests cover the replacement.
5. Do not extract a new Gradle core module until package rules and seams are
   proven; package-first enforcement is the current deliberate choice.

The first migrated tool is `PaintUnderlayTool`: it receives neutral pointer
events, resolves tiles through `Viewport`, and commits a grouped
`CompositeEditCommand` through `EditorSession`. Its JavaFX input bridge is
optional so the existing SceneGraph behavior remains the default until a
document/viewport bridge is connected to the loaded map.

`LegacyMapDocumentBridge` is the compatibility adapter for that connection. It
imports terrain into `WorldDocument`, listens to affected-tile notifications,
and writes canonical terrain fields and scene-object changes back to
`MapRegion`/`SceneGraph`. Cache persistence remains a separate save boundary.

`PaintOverlayTool`, `ChangeHeightTool`, and `PaintFlagsTool` now use the same
neutral pointer/session/composite-command path. Their atomic mutations are
represented by the explicit `PaintOverlayCommand`, `ChangeHeightCommand`, and
`ChangeTileFlagsCommand` types; `PaintUnderlayTool` uses the corresponding
`PaintUnderlayCommand`. They are first-party core tools and do not add editing
behavior to `SceneGraph`.

`FlattenTerrainTool` and `SmoothTerrainTool` extend that same path. Flatten
writes a uniform four-corner height, while smooth samples corresponding shared
corners from neighbouring tiles; both produce one history entry per pointer
stroke. `ChangeHeightTool` now accumulates changes by shared terrain vertex,
supports a bounded radius with none/linear/smooth falloff, and emits tile
snapshots that preserve shared-edge heights.
`RampTerrainTool` interpolates directly on shared vertex coordinates, and
`TerrainHeightSampler` provides neutral bilinear samples for tools and
inspectors. `TerrainMeshGoldenTest` locks all 52 shape/rotation combinations
to deterministic RSPSi-owned topology signatures pending external parity.

`PlaceObjectCommand`, `DeleteObjectCommand`, `MoveObjectCommand`, and
`RotateObjectCommand` provide the corresponding canonical location mutations;
object tools can now be migrated without inventing a second history system.
The initial place/delete/rotate tools invoke these commands directly, while
`MoveObjectTool` and `DuplicateObjectTool` add neutral pick-and-release
workflows. Both transform tools share a bounded nearest-grid snap setting.
`BoxSelectTool` can select a tile area or all objects in that area through the
unified selection model; multi-object transforms and replace remain later tool
features. `MoveObjectsCommand` and `MoveSelectionTool` now provide one atomic
multi-object translation path, including overlapping source/target tiles.
`RotateObjectsCommand` and `RotateSelectionTool` provide the corresponding
atomic orientation path and refresh the selection to the transformed values.
`ReplaceObjectsCommand` and `ReplaceSelectionTool` provide atomic definition
replacement while preserving placement attributes.

The initial OSRS map codec now decodes all four planes, explicit/inherited
heights, underlays, overlays, shapes, rotations, flags, and delta-packed
locations. Its fixture suite is a format characterization layer, not yet a
claim of parity against a licensed external cache.

## Deferred systems

No new renderer, public Plugin Hub, Lua/CS2 IDE, server runtime, live network
connection, collaboration, cloud cache, or procedural-generation system is a
prerequisite for this architecture.

## Product scope and migration

RSPSi production support targets OSRS caches supported by OpenRune. Non-OSRS
formats are quarantine-only during migration: existing behavior is protected
by characterization tests, no new features are added, and the legacy product
paths are removed after the OpenRune OSRS gates pass. The last compatible
legacy state is preserved in repository history or an archive rather than
maintained as a second product family.

Project metadata records the OSRS cache revision, optional subrevision, and a
cache fingerprint. A mismatch is reported and opens read-only until a future
migration workflow is explicitly implemented.
