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
  future save coordination.
- `EditorCommand` is the canonical mutation contract; `EditCommand` remains a
  temporary source-compatible alias.
- `WorldFragment` is the canonical portable terrain/location copy-paste
  payload; fragment pastes are grouped commands rather than direct scene
  mutations.
- `DirtyRegion` groups command invalidation by 8×8 chunk so future scene,
  collision, minimap, and cache writers can rebuild only affected derived data.
- `TerrainMeshBuilder` owns the 13 shaped-tile topologies and four rotations;
  it produces neutral mesh data for renderers and is covered independently of
  the legacy `ShapedTile` class.
- `CollisionMap`, `CollisionFlag`, and `CollisionDirection` own editor
  collision semantics; server routefinder types are reference inputs only.
- `SelectionModel` exposes one selection value for tiles, areas, vertices,
  objects, and fragments while retaining its legacy tile-set methods.
- `WorkspaceCatalog` and `StandardWorkspaceCatalog` define the fixed frontend
  presets; JavaFX renders them, but no frontend owns the layout model.
- `EditorTool`, `ToolContext`, `PointerEvent`, and `ToolInspector` are shared
  by all frontends.
- `SceneRenderer` consumes neutral scenes and changes and returns neutral pick
  results.
- Cache and definitions are accessed through RSPSi interfaces, never raw
  archive/index/file objects.
- `MapService` exposes semantic landscape and location payload access; its
  OSRS implementation owns the file-0/file-1 archive convention.
- `OsrsRegionDecoder` is the neutral format adapter for the current OSRS
  landscape/location payloads. It produces `WorldDocument` and
  `WorldObject` data without exposing OpenRune, Displee, archive IDs, or
  opcodes to editor packages.

## Frontends

JavaFX is the current frontend and keeps the existing workflow working. Its
event adapters translate to `PointerEvent`; JavaFX properties and controls do
not enter tool or document classes. Dear ImGui remains a future frontend
option, with GLFW/LWJGL integration deferred until the neutral contracts and
legacy behavior are stable.

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
and writes only underlay changes back to `MapRegion`. Object synchronization
and the replacement of the legacy tool remain separate milestones.

`PaintOverlayTool`, `ChangeHeightTool`, and `PaintFlagsTool` now use the same
neutral pointer/session/composite-command path. They are first-party core tools
and do not add editing behavior to `SceneGraph`.

`PlaceObjectCommand`, `DeleteObjectCommand`, `MoveObjectCommand`, and
`RotateObjectCommand` provide the corresponding canonical location mutations;
object tools can now be migrated without inventing a second history system.
The initial place/delete/rotate tools invoke these commands directly; picking
and drag-move remain UI migration work.

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
