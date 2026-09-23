# OpenRune Studio Semantic API Contract

> **Status:** architectural contract and implementation guide.
>
> `docs/ROADMAP.md` remains authoritative for execution order. This document defines the stable semantic boundary that the roadmap grows incrementally from Phase 0 correctness work.

## 1. Purpose

OpenRune Studio needs the same architectural property that makes RuneLite's plugin ecosystem useful: tools should consume stable game/content semantics instead of implementation-specific renderer, cache, or UI internals.

Studio must not copy RuneLite's API wholesale. RuneLite is a live-client API, while Studio is an authoring environment with undo/redo, preview, dirty state, unloaded regions, authored-versus-resolved identity, deterministic generation, and cache persistence.

The target is therefore a Studio-owned semantic API inspired by RuneLite where RuneLite has already established useful OSRS concepts.

The intended layering is:

    canonical authored world
            |
            v
    OSRS resolution / scene semantics
            |
            v
    Studio Semantic API
            |
            +---- first-party tools
            +---- inspectors / HUDs
            +---- parity tests
            +---- public plugins
            |
            v
    renderer-specific compilation and GPU internals

A renderer, cache backend, or UI toolkit must not become the public semantic contract.

---

## 2. Reference authority

Different external sources answer different questions. They must not be treated as interchangeable.

### 2.1 OSRS/RuneLite client behavior

For client scene semantics, use the vendored RuneLite source in this repository as the reproducible primary reference:

- `RuneLite-melxin/runescape-client` for decoded game-client behavior
- `RuneLite-melxin/runelite-mixins` for RuneLite's client-facing semantic hooks
- `RuneLite-melxin/runelite-api` for stable public concept names and relationships
- RuneLite GPU/client code when renderer submission behavior is specifically under investigation

Examples include:

- object transform selection
- tile paint and tile-model semantics
- scene layer ownership
- bridge/render-level behavior
- model selection by loc shape
- contouring and lighting order
- model bounds and scene placement
- draw ordering and visibility

The public RuneLite Javadocs are useful for navigation and API semantics, but implementation claims must be checked against the vendored source or another pinned source revision.

### 2.2 Cache decoding, encoding, and persistence

Use OpenRune as the primary backend/content-toolchain reference for:

- cache index/archive layout
- definition decoding
- definition encoding/builders
- writable cache behavior
- reference-table updates
- GameVals/symbolic references
- project/content integration

OpenRune-specific classes remain behind Studio-owned neutral adapters.

### 2.3 Real content truth

Use real OSRS cache/map fixtures for acceptance.

A code path matching RuneLite or OpenRune in isolation is not enough when a user-observed problem concerns a real scene. Curated fixtures must prove the authored placements, decoded definitions, resolved scene semantics, and rendered result agree for representative locations.

### 2.4 Secondary implementation references

TSPS and other open implementations are useful cross-checks for algorithms and renderer architecture, but they are secondary evidence.

Legacy RSPSi behavior is historical evidence only unless independently validated.

The OSRS Wiki and similar data sources are useful for IDs, names, locations, and human context. They are not authoritative for renderer math or client scene traversal.

---

## 3. Three separate public concepts

Studio must keep three concepts separate.

### 3.1 Authored World API

Answers:

> What is actually authored in the map/project?

Examples:

- world/region/tile address
- corner heights
- overlay/underlay IDs
- overlay shape/rotation
- tile flags
- placed loc ID/type/rotation
- source region ownership
- loaded/unloaded state
- dirty state

This layer describes source content before runtime/client interpretation.

### 3.2 Resolved Scene API

Answers:

> What does the OSRS client semantic model resolve this authored content into?

Examples:

- authored plane versus effective/render/cull plane
- simple tile paint versus shaped tile model
- final corner colors
- resolved texture
- visible/resolved object definition
- placed definition versus transformed definition
- object footprint and scene layer
- model-selection result
- bridge relation
- roof/visibility semantics
- stable scene object identity
- collision projection
- explicit unresolved diagnostics

This is the main RuneLite-inspired layer.

### 3.3 Render/Backend API

Answers:

> How is the resolved scene submitted by a specific renderer?

Examples:

- VBO/IBO offsets
- GPU buffer handles
- texture-array layers
- upload ranges
- native draw batches
- OpenGL state
- resident zones
- render diagnostics

These are internal or privileged diagnostics. They are not normal plugin/editor semantics.

RuneLite's `SceneTilePaint.getBufferOffset()`, `getUvBufferOffset()`, and `getBufferLen()` are examples of useful live-client renderer hooks that should not be copied into Studio's ordinary tile-paint API.

---

## 4. API design rules

### 4.1 Read semantics are immutable

Public scene/world views are read-only snapshots or stable query interfaces.

A plugin must not mutate a scene tile by calling setters analogous to RuneLite's `setSceneTilePaint` or `setTexture`.

### 4.2 Edits go through edit services

Mutation flows through:

    interaction
      -> Edit/Query services
      -> ChangePlan
      -> validate
      -> preview
      -> commit
      -> one undo history entry

This preserves canonical authored state and makes public plugins obey the same undo/preview rules as first-party tools.

### 4.3 Authored and resolved identity are both retained

A transformed/multiloc object must never lose the placed object's identity.

A semantic object view must be able to expose:

- placed object ID
- placed definition
- transform path
- resolved/display definition
- stable authored placement identity
- selected model IDs/types
- resolution status
- diagnostic reason when unresolved

### 4.4 World coordinates are first-class

Public semantic APIs use absolute world coordinates where the concept is world-level.

Local/scene coordinates are explicit projections, not the default identity.

### 4.5 Plane semantics are explicit

Never collapse the following into one ambiguous `plane` value where the distinction matters:

- authored plane
- effective plane
- render level
- plane/cull level
- picked surface plane

### 4.6 No silent unknown states

Missing definitions, blank client-default names, missing transforms, missing geometry, shape/model mismatch, and scene-submission loss must be explicit diagnostic states.

---

## 5. Initial semantic surface

Names may evolve before the public API is frozen, but the responsibilities should remain.

### 5.1 SceneView

Target responsibilities:

    world bounds
    loaded bounds
    tiles
    tile(WorldTile)
    objects
    object(SceneObjectIdentity)
    current scene/display policy
    query services

Do not expose renderer-owned packet arrays as the primary API.

### 5.2 SceneTileView

Target responsibilities:

    WorldTile worldTile()
    int authoredPlane()
    int effectivePlane()
    int renderLevel()
    int planeCullLevel()
    int flags()

    Optional<BridgeView> bridge()
    Optional<TileSurfaceView> surface()

    Optional<WallView> wall()
    List<WallDecorationView> wallDecorations()
    List<SceneObjectView> gameObjects()
    Optional<GroundDecorationView> groundDecoration()

    CollisionView collision()

RuneLite's `Tile` is the conceptual reference, but Studio additionally retains authored state and editor-specific plane semantics.

### 5.3 TileSurfaceView

The surface abstraction must distinguish simple paint from shaped geometry.

Target responsibilities:

    int underlayId()
    int overlayId()
    int shape()
    int rotation()

    int southWestHeight()
    int southEastHeight()
    int northWestHeight()
    int northEastHeight()

    Optional<TilePaintView> paint()
    Optional<TileModelView> model()

### 5.4 TilePaintView

Inspired by RuneLite `SceneTilePaint`, but renderer bookkeeping is excluded.

Target responsibilities:

    int southWestColor()
    int southEastColor()
    int northWestColor()
    int northEastColor()

    OptionalInt textureId()
    boolean flat()
    int minimapRgb()

The four corner colors are resolved scene colors, not merely the raw overlay/underlay definition RGB.

### 5.5 TileModelView

Inspired by RuneLite `SceneTileModel`.

Target responsibilities:

- shape
- rotation
- resolved underlay/overlay values
- vertices and triangle indices
- per-triangle colors
- per-triangle texture IDs
- flatness
- enough semantic geometry for parity inspection, overlays, and plugins

Do not expose GPU upload offsets through this normal API.

### 5.6 SceneObjectView

Target responsibilities:

    SceneObjectIdentity identity()
    WorldTile anchor()
    ObjectCategory category()
    OsrsLocShape shape()
    int rotation()

    ObjectDefinitionView placedDefinition()
    Optional<ObjectDefinitionView> displayDefinition()
    ObjectResolutionView resolution()

    int footprintWidth()
    int footprintLength()

    List<ModelReferenceView> selectedModels()
    ObjectAppearanceView appearance()
    ObjectCollisionView collision()

The visible appearance must come from the resolved display definition where the client transforms the object before model construction.

### 5.7 ObjectResolutionView

Initial Phase 0 statuses must distinguish at least:

- RESOLVED
- MISSING_PLACED_DEFINITION
- BLANK_OR_SENTINEL_NAME
- NO_DEFAULT_TRANSFORM
- MISSING_TRANSFORM_DEFINITION
- RESOLVED_NESTED_TRANSFORM_CHILD
- NO_MODEL_FOR_SHAPE
- MISSING_MODEL_GEOMETRY
- EMPTY_RENDERABLE_GEOMETRY
- PACKET_PRODUCED
- SCENE_SUBMITTED
- HIDDEN_BY_VISIBILITY_POLICY
- HIDDEN_BY_OCCLUSION
- PICK_IDENTITY_UNRESOLVED

Not every status needs to live in one enum. Definition resolution, model resolution, scene submission, and visibility may be separate diagnostic stages. A nested transform child should be reported, but the display resolver must not recursively apply it because RuneLite's DynamicObject performs one ObjectComposition.transform() step before model construction. The important requirement is that an authored loc can be traced end-to-end without a silent null.

### 5.8 SurfaceHit

Phase 0 picking should publish one canonical result used by hover HUD, click selection, inspectors, and tools.

Target responsibilities:

- world tile
- authored plane
- effective plane
- render plane
- hit world/local position
- sampled surface height at the hit point
- surface kind
- optional stable scene object identity
- triangle/face information when useful for diagnostics
- visibility/pick policy used

---

## 6. RuneLite concept mapping

Studio should deliberately reuse established OSRS vocabulary where it improves clarity, without inheriting live-client implementation details.

| RuneLite concept | Studio target | Notes |
| --- | --- | --- |
| `Scene` | `SceneView` | immutable semantic scene |
| `Tile` | `SceneTileView` | adds authored/resolved distinctions |
| `SceneTilePaint` | `TilePaintView` | no public GPU offsets |
| `SceneTileModel` | `TileModelView` | semantic geometry, no ordinary GPU bookkeeping |
| `GameObject` | `SceneObjectView` | retains placed + transformed definition identity |
| `WallObject` | `WallView` | semantic wall layer |
| `DecorativeObject` | `WallDecorationView` | semantic wall-decoration layer |
| `GroundObject` | `GroundDecorationView` | semantic ground-decoration layer |
| `WorldPoint` | existing `WorldTile` family | absolute world identity remains first-class |
| collision APIs | `CollisionView` | immutable semantic projection |
| draw callbacks | optional render-extension capability | separate from normal semantic API |

---

## 7. How existing RSPSi/OpenRune Studio types evolve

Do not rewrite working foundations.

### Already useful

- `WorldDocument`
- `WorldTile` / `WorldTileAddress`
- `TileSnapshot`
- `TerrainRenderPacket`
- `ModelRenderPacket`
- `SceneTileSnapshot`
- `SceneObjectIdentity`
- `EditorSceneAccess`
- `EditorSceneSnapshot`
- `DefinitionProvider`
- neutral definition/model/texture/animation views

### Direction

`EditorSceneSnapshot` should evolve into, adapt to, or back the public semantic scene contract instead of growing ad-hoc getters indefinitely.

`TerrainRenderPacket` and `ModelRenderPacket` remain excellent renderer-neutral compilation artifacts, but public plugins should normally consume higher-level semantic views.

Renderer packets may still be available to internal diagnostics and parity tests.

---

## 8. Plugin boundary

First-party and public plugins should consume the same neutral semantic services where practical.

A mature plugin context should eventually expose capabilities conceptually like:

    world()
    scene()
    queries()
    selection()
    edits()
    changePlans()
    assets()
    definitions()
    overlays()
    diagnostics()

Permission enforcement remains mandatory:

- WORLD_READ gates authored-world reads
- WORLD_EDIT gates edit-plan creation/commit
- ASSET_READ gates definitions/models/textures
- privileged renderer diagnostics require an explicit capability if exposed

A plugin should never need Dear ImGui, GLFW, OpenGL, OpenRune backend classes, or `GpuScenePacket` merely to understand a tile or object.

---

## 9. RuneLite compatibility policy

Do not promise binary/source compatibility with RuneLite plugins.

Most RuneLite plugins depend on a live game client, ticks, actors, widgets, varbits, events, menus, or network/client state that does not exist in a map editor.

A later optional compatibility/adaptation module may make scene-oriented RuneLite algorithms easier to port:

    studio-runelite-compat

Potential targets:

- tile/world coordinate adapters
- tile paint/model read adapters
- scene-object adapters
- polygon/model-bounds helpers
- overlay geometry helpers

This is an adapter/convenience layer, not the Studio public API itself.

---

## 10. Source and licensing discipline

Prefer behavioral reimplementation behind Studio-owned interfaces.

When source code is intentionally copied or substantially derived from RuneLite, preserve the applicable license/copyright notices and attribution requirements.

Keep the vendored RuneLite revision identifiable so parity investigations can state which source revision was used.

Do not copy renderer-specific RuneLite API fields merely because they exist.

---

## 11. Testing contract

The semantic API becomes a high-value parity boundary.

### 11.1 Tile paint parity

For representative simple tiles compare:

- four corner colors
- texture ID
- flatness
- minimap RGB
- authored IDs
- effective/render plane

### 11.2 Tile model parity

For shaped/textured tiles compare:

- shape and rotation
- vertex positions/heights
- face topology
- triangle colors
- texture IDs
- underlay/overlay semantic values

### 11.3 Object parity

For every authored object in curated fixtures record:

- placed definition status
- transform path
- resolved/display definition
- loc shape/model-type match
- selected model IDs
- geometry decode status
- packet status
- scene-submission status
- stable identity
- final visibility/pick status

### 11.4 Pick parity

A canonical SurfaceHit fixture should validate:

- world position
- sampled height
- tile identity
- authored/effective/render planes
- stable object identity
- active-plane restrictions
- bridge behavior

### 11.5 Golden scenes

Prioritize real-cache scenes that combine multiple semantics:

- Lumbridge Castle entrance/bush placements
- stone/castle interior
- wooden interior
- shaped overlays
- textured floors
- walls and wall decorations
- ground decorations
- roofs
- bridges/elevated surfaces
- multilocs
- large footprints

---

## 12. Implementation sequence

The semantic API is grown from correctness work rather than implemented as a speculative framework rewrite.

### Step A - Object resolution foundation

- central object transform resolver
- safe object labels
- placed-versus-display definition identity
- resolved appearance uses the display definition
- explicit resolution diagnostics
- real multiloc fixture
- stable scene identity retained

### Step B - Canonical surface hit

- one SurfaceHit contract
- actual hit position
- sampled hit height
- authored/effective/render plane
- stable object identity
- hover and selection separated
- HUD/inspector/pickers consume the same result

### Step C - Tile semantic views

- SceneTileView
- TileSurfaceView
- TilePaintView
- TileModelView
- adapt existing SceneTileSnapshot/TerrainRenderPacket rather than duplicating compilation
- real interior parity fixtures

### Step D - Scene object semantic view

- SceneObjectView
- ObjectResolutionView
- selected-model diagnostics
- collision/appearance/footprint
- scene-submission/visibility diagnostics

### Step E - First-party adoption

Move first-party inspectors, HUDs, overlays, and tools to semantic APIs where practical.

Do not freeze the public ABI until the Phase 0 callers prove the contracts are sufficient.

### Step F - Public plugin capability

Expose the proven semantic API through the versioned public plugin boundary with permissions and compatibility policy.

---

## 13. Definition of success

The semantic boundary is successful when:

1. a user can inspect any authored tile/object and understand what was authored, how OSRS semantics resolved it, and what Studio submitted;
2. renderer implementation details are unnecessary for ordinary tools/plugins;
3. first-party tools and community plugins can query the same stable concepts;
4. parity tests can fail at the semantic boundary before debugging pixels;
5. a renderer/backend rewrite does not require rewriting ordinary plugins;
6. no object or surface silently degrades to `null`, an ambiguous plane, or an unexplained invisible state;
7. edits remain previewable, undoable, world-aware, and persistence-safe.
