# RSPSi Architecture Contract

This is the implementation contract for the stabilization work. It narrows
the research in [`REFERENCE_ECOSYSTEM.md`](REFERENCE_ECOSYSTEM.md) into rules
that can be checked in code.

The product scope and controlled layout are locked in
[`PRODUCT_DESIGN.md`](PRODUCT_DESIGN.md).

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

The neutral editor packages own world data, editing, selection, tool contracts,
and renderer contracts. They may not import JavaFX, ImGui, OpenGL/LWJGL,
Displee, or OpenRune types. Frontends and cache adapters translate at the
boundary.

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
- `SessionStateListener` exposes edit/undo/redo/save-marker changes to
  frontend adapters, while `SelectionChangeListener` exposes the final
  unified selection value after each selection operation. Neither listener
  carries UI types or owns the state it observes.
- `EditorCommand` is the canonical mutation contract; `EditCommand` remains a
  temporary source-compatible alias.
- `CommandHistory.moveTo(...)` and `EditorSession.jumpToHistory(...)` provide
  exact history navigation with one consolidated session update; failed
  forward replay rolls back commands already reapplied.
- `WorldFragment` is the canonical portable terrain/location copy-paste
  payload; fragment pastes are grouped commands rather than direct scene
  mutations.
- `DirtyRegion` groups command invalidation by 8×8 chunk so future scene,
  collision, minimap, and cache writers can rebuild only affected derived data.
- `TerrainMeshBuilder` owns the 13 shaped-tile topologies and four rotations;
  it produces neutral mesh data for renderers and is covered independently of
  the legacy `ShapedTile` class. The map-facing tile shape remains the encoded
  0..11 value; mesh topology 0 is the flat model and overlay values map to
  topology 1..12 at this boundary.
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
  eight-way routes with diagonal corner protection and straight projectile
  line-of-sight checks. It is intentionally small and replaceable while
  OpenRune-Server route/reach semantics are verified against fixtures.
- `Reachability` uses the same default-off/explicit-on route-blocker choice as
  `RouteFinder`, so object reach previews cannot silently use a different
  collision layer.
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
- `WorldRegionWindow.boundaryMismatches()` checks shared corner heights across
  loaded east and north region edges; incomplete windows report only the
  boundaries that can actually be proven.
- `EditorTool`, `ToolContext`, `PointerEvent`, and `ToolInspector` are shared
  by all frontends.
- `SceneRenderer` consumes neutral scenes and changes and returns neutral pick
  results.
- Cache and definitions are accessed through RSPSi interfaces, never raw
  archive/index/file objects.
- `CacheStore.metadata(revision)` is an optional neutral identity capability;
  project compatibility can compare revision, subrevision, and fingerprint
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
- `EditorSession` records dirty work at 8×8 chunk granularity. An edit on a
  chunk edge also invalidates the adjacent cardinal chunk so shared terrain
  edges, floor blending, and picking can be rebuilt without rebuilding a
  complete region.
- `CommandTransaction` is the single rollback helper for grouped edits. It
  applies composite, paste, and multi-object delegates in order and undoes
  already-applied delegates if a later one fails, keeping failed commands out
  of session history.
- `ObjectInspectorSnapshot` resolves optional neutral definition and collision
  providers into immutable frontend data. It keeps missing definitions
  explicit and exposes canonical category/shape names without coupling an
  inspector to Displee, OpenRune, JavaFX, or ImGui.
- `DefinitionAssetRepository` turns neutral definition IDs into searchable
  `AssetDescriptor` values for the first asset-browser categories (objects,
  underlays, overlays, and textures). `SymbolicNameProvider` is the narrow
  adapter seam for RSCM/GameVal names: descriptors retain the display name,
  numeric ID, and optional symbolic key, and search includes that key without
  importing a naming-library type into the editor.
- `RenderSceneBuilder` derives renderer-independent terrain meshes and
  canonical object placements from `WorldDocument`. It supports both complete
  snapshots and `RenderChanges`-scoped terrain rebuilds, preserving untouched
  mesh instances while refreshing canonical object placements. Neighbor tiles
  can be included by the caller for blended floors and shared edges; scene
  construction remains outside `SceneGraph`.
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
RSCM/GameVal keys; a null repository preserves the legacy inspector fallback.
the controlled workflow and its manual coverage continue to be migrated.

`WorkspaceStatusBar` is a persistent JavaFX state row below the controlled
bottom tabs. It reports the active OSRS region/project context, cache revision,
editable versus read-only state, compatibility issues, and saved/dirty state.
It observes only `EditorSession` state and is intentionally not a cache or
renderer status channel. Preset changes detach and remount the row safely, so
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

The composition root also exposes `openWindow(...)` and
`openWindowAround(...)` for scene consumers. These load a bounded OSRS context,
preserve missing-region holes, and stitch shared terrain borders before the
window is consumed. A window is scene context, not a second editor model;
editable state remains owned by the `EditorSession` returned by `openRegion`.

`SessionSceneController` is the neutral session-to-renderer binding. It loads
the initial `RenderScene`, consumes session-owned dirty chunks as bounded
renderer updates, and removes its listener on close. JavaFX and future ImGui
frontends can host this binding without placing UI types in the editor core.

`SessionAutosaveStore` writes a versioned snapshot of the canonical document,
project identity, and history position to `ProjectLayout.sessionAutosaveFile()`
(`autosave/session.json`) using the same atomic-replacement rule as project
metadata. Its layout-aware overloads read the project metadata and canonical
path together. Recovery restores a standalone `WorldDocument`; it never opens
or rewrites the source cache.

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
neutral pointer/session/composite-command path. They are first-party core tools
and do not add editing behavior to `SceneGraph`.

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
