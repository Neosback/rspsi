# OpenRune Studio Semantic API Contract

> **Status:** architectural contract and implementation guide.
>
> `docs/ROADMAP.md` remains authoritative for execution order. This document defines the stable semantic boundary that the roadmap grows incrementally from Phase 0 correctness work.
>
> System-wide API layering, stability, lifecycle, permissions, and package boundaries are defined in `STUDIO_API_SYSTEM.md`. Public plugin/tool consumption of this semantic API is defined in `PLUGIN_EXTENSION_SDK.md`.

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

### 2.3 Server/source semantics

Connected OpenRune projects also have a source-semantic domain that is distinct from cache/client scene semantics.

The first source index uses the connected project's evaluated Gradle source roots and Kotlin PSI to emit Studio-owned neutral facts with exact provenance. PSI/compiler implementation classes must not escape the OpenRune adapter boundary.

Initial source facts include:

- class and function declarations
- call sites
- symbolic references
- `PluginScript` and `QuestScript` declarations
- OpenRune handler registrations such as `onOpLoc1`, `onOpContentLoc1`, and `onOpNpc1`
- quest-definition constructor metadata
- varbit/varp binding calls

Every source-derived fact must preserve:

- source file
- start/end offsets
- one-based line/column positions
- Gradle project path
- source-set identity
- package
- confidence/provenance category

Important epistemic rule:

**PSI structure is not the same thing as resolved K2 semantics.**

For example, seeing a call named `onOpContentLoc1("content.rock")` is strong structural evidence that the source registers that content handler, but overload/type resolution and cross-module symbol identity require a later K2/FIR/Analysis API enrichment. Studio must not silently upgrade a structural inference into a resolved semantic claim.

The public semantic/content graph should consume these neutral facts rather than exposing Kotlin PSI nodes directly. This keeps future parser/compiler upgrades from changing plugin/editor contracts.

### 2.4 Semantic Content Graph

The graph is the cross-domain relationship layer; it is not a replacement for the authored world API, source index, symbol service, or runtime.

The first graph contract has these invariants:

- **canonical identity:** aliases that represent the same neutral game concept share one node (for example OpenRune `obj.coal` and Studio `item.coal`);
- **typed relationships:** ownership, handler targets, ordinary references, state use, and state binding are different edge kinds rather than generic strings;
- **evidence on every promoted relationship:** a relationship must retain the PSI/source/declarative/mapping evidence that justified it;
- **partial knowledge is explicit:** unresolved symbolic nodes are valid graph members and may later gain a numeric mapping or additional evidence;
- **multiple sources may agree:** source PSI, declarative data, and RSCM/GameVals can all contribute evidence to one logical node;
- **conflicts are diagnostics:** competing numeric IDs or incompatible attributes must remain visible instead of silently choosing a new truth;
- **read-only first:** graph connectivity alone never grants mutation authority.

Initial node kinds:

    SYMBOL
    SCRIPT
    QUEST
    HANDLER
    RESOURCE

Initial directed relation kinds:

    DECLARED_IN
    OWNS
    TARGETS
    REFERENCES
    BINDS_STATE
    USES_STATE

Map/cache integration attaches server-side object semantics to the existing canonical symbol nodes rather than creating a second object-identity graph.

For OpenRune object content, the source-of-truth split is explicit:

- LIVE/client object definitions describe client-facing object appearance/interaction data;
- source-controlled `[[object]]` TOML overlays describe authored server additions such as `contentGroup` and server params;
- the generated SERVER cache contains the merged `ObjectServerType` result.

The graph therefore models an `OBJECT_DEFINITION` node keyed by resolved map/cache object ID when available, with typed relationships:

    IDENTIFIED_BY -> loc.*
    INHERITS      -> loc.*
    CONTENT_GROUP -> content.*
    HAS_PARAM     -> param.*
    PARAM_VALUE   -> symbolic value

This is sufficient for a selected `WorldObject.id` to reach the server content group and then the Kotlin handlers that target that group without guessing from display names.

Likewise, edit lenses must be an additional capability layered on graph provenance. A node is editable only when Studio can identify a supported writable origin, a mutation strategy, stale-source validation, and post-write verification. A read-only reference or generated mapping is never sufficient by itself. An authored-source flag is provenance, not permission to rewrite the file.

### 2.5 Real content truth

Use real OSRS cache/map fixtures for acceptance.

A code path matching RuneLite or OpenRune in isolation is not enough when a user-observed problem concerns a real scene. Curated fixtures must prove the authored placements, decoded definitions, resolved scene semantics, and rendered result agree for representative locations.

### 2.6 Secondary implementation references

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

RuneLite's `TileObject` is the useful conceptual base here: all scene-object layers should share one stable object identity and world-placement contract before layer-specific presentation is added.

Target responsibilities:

    SceneObjectIdentity identity()
    WorldTile authoredAnchor()
    ObjectCategory category()
    OsrsLocShape shape()
    int rotation()

    ObjectDefinitionView placedDefinition()
    Optional<ObjectDefinitionView> displayDefinition()
    ObjectResolutionView resolution()

    SceneFootprint sceneFootprint()
    ModelFootprint modelFootprint()

    List<ModelReferenceView> selectedModels()
    ObjectAppearanceView displayAppearance()
    ObjectCollisionView placedCollision()

Do **not** collapse scene footprint and model footprint into one value. RuneLite's map loader establishes scene occupancy/collision from the placed definition, while `DynamicObject.getModel()` resolves the transform and uses the display definition's dimensions for model centering and height sampling.

Likewise, do not call a single ambiguous `appearance()` field authoritative for every runtime property. The current client path sources the DynamicObject animation id from the placed definition but constructs the visible model from the transformed/display definition's model appearance.

Layer-specific APIs such as wall orientation pairs or wall-decoration displacement should be added only when a concrete tool needs those semantics. The common `SceneObjectView` remains the normal plugin abstraction.

### 5.7 ObjectResolutionView

Initial Phase 0 statuses must distinguish at least:

- RESOLVED
- MISSING_PLACED_DEFINITION
- BLANK_OR_SENTINEL_NAME
- HIDDEN_IN_VAR_STATE (the var state selects -1; resolved with a fresh-account `ObjectVarState` by default)
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

**Decision (2026-09-23):** the Studio scene API uses RuneLite's names and getter shapes. It lives in
`com.rspsi.api` (interfaces, coordinates, `Perspective`), with the implementation in
`com.rspsi.api.scene` over the resolved `GpuScenePacket` plus authored tiles. The types are
Studio-owned: RuneLite is the vendored BSD reference, not a dependency. They add editor extras
(authored plane, stable placement id, `isRendered`) and omit GPU buffer bookkeeping.
RuneLite-style setters are allowed but must record undoable editor commands against the authored
world; they never mutate a snapshot.

| RuneLite concept | Studio type | Status / notes |
| --- | --- | --- |
| `WorldView` + `Scene` | `com.rspsi.api.WorldView`, `Scene`; impl `api.scene.SceneView` | **implemented** read side; bridge columns shift down exactly as `Scene.setLinkBelow` |
| `Tile` | `com.rspsi.api.Tile` | **implemented**: `getPlane`/`getRenderLevel`/`getPhysicalLevel` from `ScenePlaneSemantics`, `getBridge`, the four object layers; extras `getAuthoredPlane`, `getTileSettings` |
| `SceneTilePaint` | `com.rspsi.api.SceneTilePaint` | **implemented**: scene shape 0/1 per `runescape-client/Scene.addTile`, lit packed-HSL corners, client flatness; `getRBG` replaced by `getMinimapHsl` until a palette service exists |
| `SceneTileModel` | `com.rspsi.api.SceneTileModel` | **implemented**: scene-local vertices, faces, per-corner colours, textures; no buffer offsets |
| `TileObject` + `GameObject`/`WallObject`/`DecorativeObject`/`GroundObject` | same names in `com.rspsi.api` | **implemented**: built from authored placements, so invisible/unresolved locs remain objects (`isRendered() == false`), as in the client; `GameObject.getOrientation` per `RSGameObjectMixin`; stable 64-bit `getHash` from the placement identity (not the client tag) |
| `WorldPoint` / `LocalPoint` / `Point` | `com.rspsi.api.coords.*`, `com.rspsi.api.Point` | **implemented** |
| `Perspective.getTileHeight` | `com.rspsi.api.Perspective.getTileHeight` | **implemented** bit-for-bit, including bridge-level promotion |
| `WorldArea` | future world-space bounds/query value | Phase 3 should add a world-area type rather than misuse local `TileBounds` |
| `ObjectComposition` | `com.rspsi.api.ObjectComposition`; impl `api.cache.CacheObjectComposition` | **implemented** read side: positional actions (`DefinitionProvider.objectActions`), map scene/icon, impostor ids, `getImpostor()` through `ObjectDefinitionResolver` with the simulated var state, access mask, size, int/string params |
| `Client` (vars, lookups) | `com.rspsi.api.Client`; impl `api.runtime.SimulatedClient` | **implemented**: varbits/varps over `SimulationEngine`, `getObjectDefinition`, `loadModelData`, `loadModel` (+ recolour), `getMapElementConfig`, `getWorldMap`. Plugins reach it through `PluginApi.client()` once a cache is loaded |
| collision APIs / flags | existing `CollisionTileSnapshot` / `CollisionFlag` | reuse existing OSRS bit vocabulary |
| `JagexColor` | existing `OsrsTerrainColorMath` where terrain-specific | do not conflate generic packed-HSL helpers with source-domain terrain blending |
| `Mesh` / `ModelData` / `Model` / `AABB` | same names in `com.rspsi.api`; impl `api.model.DecodedModelData`, `LitModel` | **implemented**: fresh mutable copies per load; rotations, translate, `scale` (`resize`), recolour/retexture, shallow copy + clone*, and `light()` ported from deob `ModelData.toModel` (matches scene lighting face-for-face on real objects); bounds and `getAABB(orientation)` via `ClientModelBounds`. GPU buffer bookkeeping and draw calls are omitted |
| `Renderable` | `com.rspsi.api.Renderable` | `getModel`/`getModelHeight`; `WallObject.getModelA/B`, renderables and convex hulls wait for a per-placement model builder over `ObjectComposition` |
| `MapElementConfig` / `SpritePixels` | `com.rspsi.api.worldmap.MapElementConfig`, `com.rspsi.api.SpritePixels`; impl `api.cache.*` | **implemented**: icon sprite, category (opcode 19, now decoded), name, minimap/world-map visibility |
| `WorldMap` | `com.rspsi.api.worldmap.WorldMap`; impl `NavigatorWorldMap` | **implemented** over the camera navigator: position = camera tile (the minimap radar centre), zoom = radar pixels per tile, position target = a navigator jump. No separate world-map view exists yet |
| `Perspective` / clickbox helpers | internal projection + canonical `SurfaceHit` | plugins should consume semantic hit/geometry instead of rebuilding viewport projection |
| instance template/source mapping | existing instance model/builders; future scene provenance view | expose source/template coordinates only when instance authoring/debugging consumes them |
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

### RuneLite API coverage review

The vendored RuneLite API contains many live-client concepts that Studio should **not** mirror simply because they exist. The map/content-editor relevant subset is now explicitly accounted for:

- **Scene/tile semantics:** tile paint, shaped tile model, heights, flags, bridge/render levels, roofs and scene visibility feed the planned tile/surface views.
- **Tile-object semantics:** common identity, world location, layer, actions, footprint, wall/decor presentation, and click geometry feed `SceneObjectView`, `SurfaceHit`, and existing scene metadata.
- **Definitions/multilocs:** name/actions, size, varbit/varp transforms, map scene and other definition metadata stay behind neutral definition views and resolution services.
- **Collision:** reuse existing collision snapshots/flags and route services.
- **Coordinates/areas:** reuse `WorldTile`, `LocalTile`, `WorldTileAddress`; add a world-space area/bounds type with the query engine instead of overloading local `TileBounds`.
- **Model geometry/bounds:** reuse `ModelRenderPacket`, `ClientModelBounds`, and semantic geometry adapters; do not surface raw GPU buffers.
- **Color/texture:** reuse client-backed terrain color math and texture resources; generic RuneLite color helpers are references, not automatic replacement APIs.
- **Instances:** reuse existing `InstanceChunkTemplate`, transforms, materializer, and instance parity fixtures. Public source/template provenance waits for an instance-aware tool.
- **Projection/canvas helpers:** remain frontend/internal. A plugin should normally ask for `SurfaceHit`, world geometry, or overlay primitives rather than calculate RuneLite-style canvas clickboxes itself.
- **Client vars** are in: varbits/varps drive multiloc appearance, so `Client` exposes them over the simulation engine (Player State panel).
- **Actors, players, NPCs, projectiles, widgets, menus, chat, social, inventory, game ticks and networking:** live-client domains are intentionally outside the static map-editor semantic API. Add them later only through the simulation/content-runtime roadmap when a Studio feature requires them.

Definition fields such as interaction access masks, map-area/map-icon IDs, sound metadata, parameters, and support-item flags already remain available through decoded/raw definition data even when they are not promoted into `ObjectDefinitionView`. Promote one into the stable semantic view only when a first-party or public capability consumes it.

### API liveness rule

A semantic API is not considered implemented merely because a type or method exists.

Before a new neutral/public member is promoted:

1. it must have at least one real first-party, verifier, or plugin-facing consumer;
2. its semantics must have a focused test or trusted fixture;
3. it must not duplicate an existing neutral service or renderer-neutral contract;
4. visibility must remain as narrow as practical until a second component needs it;
5. speculative convenience methods should be removed rather than preserved "for later";
6. compatibility shims are retained only when an existing caller or versioned public contract requires them.

Tests alone prove semantics, not product usefulness. Conversely, a production consumer without semantic tests is not enough for a stable plugin contract.

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

Studio mirrors RuneLite's scene API **shape** (section 6) so RuneLite plugin authors meet familiar names. It
does not promise binary/source compatibility: Studio types live in `com.rspsi.api`, not
`net.runelite.api`.

Live-client concepts (var state, NPCs, items, ticks) stay in scope for a future **simulation mode**. Design
new types so they can host that state later rather than designing it out. The first concrete case is
multiloc var state: the static resolver uses the definition's default transform, and multilocs without a
default render nothing. The client's fresh-state (`var == 0`) behaviour is documented in
PHASE0_LUMBRIDGE_ACCEPTANCE.md and should arrive as an explicit resolution/var-state context.

A later optional `studio-runelite-compat` module may implement `net.runelite.api` interfaces over the Studio
types, to port scene-oriented RuneLite code. That adapter is a convenience layer, not the Studio public API.

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
