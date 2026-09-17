# RSPSi Scene Semantics Contract

This document is the foundation contract for future tools, plugins, JavaFX,
Dear ImGui, and renderer implementations. It records which layer owns each
meaning and which values are authored versus derived.

The phase-by-phase scene assembly map, bridge/map-scene distinctions, and
future plugin attachment guidance live in [`OSRS_SCENE_PIPELINE.md`](OSRS_SCENE_PIPELINE.md).

## Ownership and data flow

```
OpenRune/FileStore
  -> RSPSi cache adapters and revision codecs
  -> authored WorldDocument
  -> derived scene, material, lighting, collision, minimap, validation
  -> neutral EditorSession, commands, selection, tools, plugin registry
  -> JavaFX or Dear ImGui adapters
  -> renderer-specific GPU/software implementation
```

Only `WorldDocument` is authoritative for editable map state.
`RenderScene`, `CollisionMap`, minimap images, validation reports, and
frontend state are derived views. A plugin may request or derive views, but it
must mutate the document only through `EditorCommand` and `EditorSession`.

## Coordinate and region semantics

| Concept | Contract |
|---|---|
| World tile | Absolute OSRS X/Y plus plane. |
| Region | 64×64 authored tiles per plane. |
| Chunk | 8×8 tile source/destination unit used by dirty regions and instances. |
| Local tile | Document-relative X/Y; never silently treated as world coordinates. |
| Scene window | A bounded collection of world regions with explicit holes and neighbor context. |
| Instance | Repeated 8×8 source chunks transformed into destination coordinates and rotation. |
| Authored plane | Plane encoded by the map/location data. |
| Effective/render plane | Plane used for bridge visibility and derived scene presentation. It must not overwrite authored data. |

The neutral `WorldTileAddress`, `WorldRegionWindow`,
`InstanceChunkGrid`, `InstanceChunkTransform`, and
`WorldDocument.effectivePlane(...)` APIs are the canonical implementations
of these rules.

Asset access follows the same rule: plugins consume neutral definition views
through `AssetRepository`, never archive IDs or cache-library objects. Its
typed lazy facade covers objects, floors, textures, models, map-scene sprites,
animation sequences, world-map/minimap elements, object appearance, and object
collision. A backend that cannot expose one category returns an explicit empty
capability; it must not fabricate definitions or leak its archive layout.

## Terrain semantics

Terrain decoding preserves, per tile and plane:

- explicit or generated corner heights;
- underlay and overlay IDs;
- overlay shape and rotation;
- raw render/terrain flags;
- shared-corner relationships between adjacent tiles.

The cache adapter also retains whether each tile height came from an explicit
height opcode or an opcode-0 generated height. This provenance is part of the
neutral model boundary, not a renderer detail: instance scene construction
replays the source opcode into the destination plane, matching OSRS upper-plane
inheritance and source-chunk noise coordinates.

Revision policy belongs to `OsrsRevisionFeatures`; the world model does not
branch on revision. Overlay shapes are encoded as 12 cache values that map to
13 scene topologies, each with four rotations. `TerrainMeshBuilder` owns the
neutral vertex/face topology. `TerrainLighting` owns the directional normal
baseline. `TerrainMaterial` carries definition-derived inputs without
embedding a renderer or cache archive.

Underlay blending, overlay color/texture selection, lighting, hidden faces,
bridge visibility, and minimap composition are derived scene concerns. They
must remain separately testable so a cache decode mismatch is not confused
with a renderer presentation mismatch.

## Location and object semantics

Location payloads preserve the canonical tuple:

```
object id, plane, world/local anchor, loc shape/type, rotation
```

`OsrsLocShape` and `ObjectCategory` own shape/layer meaning. Definition
views provide optional footprint, model IDs, animation, contouring, transform,
recolor, retexture, and collision inputs. `RenderObject` is the
renderer-neutral projection; it is not a replacement for the authored
`WorldObject`.

Object-derived collision is a separate projection through
`OsrsCollisionBuilder`. Missing definitions are diagnosable; they must not
be silently replaced with guessed footprints.

## Bridges, collision, and scene construction

Bridge flags create explicit authored-to-effective relationships. Collision,
route previews, line-of-sight, and reachability consume the canonical
`CollisionMap`/`CollisionTileSnapshot` contract and may expose their policy
layers independently.

Scene construction follows this order:

1. Decode authored terrain and locations.
2. Apply explicit edits/overrides to the authored document.
3. Build terrain topology and definition-aware materials.
4. Build object projections and object-derived collision.
5. Resolve bridge/effective-plane relationships.
6. Publish a complete neutral `RenderScene`.
7. Let the frontend/renderer decide camera, batching, GPU buffers, and pixels.

This ordering mirrors the pinned TSPS scene reference where floor collision is
established before bridge demotion and linked-below relationships are applied.
TSPS is also the tie-breaker for revision-240 render preparation details such
as radius-five underlay blending, final per-vertex HSL/UV terrain data,
shape/type-aware model selection, contouring, normal merging, and opaque/alpha
packet separation. It is a behavioral reference, not a production dependency;
RSPSi keeps authored state immutable and retains OpenRune route semantics as
the canonical editor collision layer.

## Evidence sources

The current research checkouts are pinned outside the product repository:

- TSPS `83415f7`: scene construction, shaped terrain topology, lighting inputs,
  bridge ordering, instances, and edit-mode region packs.
- RuneLite `ced4c4a`: `WorldPoint`, scene tile model, cache location semantics,
  and independent OSRS inspection behavior.
- OpenRune-Server `72e8e1a`: location categories, collision flags, step
  validation, route/reach semantics, and object footprint behavior.
- OpenRune-Editor `1e5b410`: workflow and plugin ideas only.

The executable RSPSi evidence is in terrain golden/topology tests,
world-region/instance tests, object-shape tests, collision vector tests,
scene fingerprints, minimap parity tests, and the external-cache verifier.

## Dear ImGui impact

Dear ImGui is a frontend adapter, not a foundation change. It may implement:

- `SceneRenderer`;
- `Viewport` and pointer-event translation;
- `OverlayDraw`;
- workspace/panel rendering from `WorkspaceCatalog`;
- tool inspectors and asset/validation/history panels.

It must not introduce JavaFX, ImGui, OpenGL, cache-backend, or renderer types
into `com.rspsi.editor`, `com.rspsi.project`, `com.rspsi.cache.map`,
neutral definition contracts, commands, or plugin APIs. Both frontends must
consume the same `EditorSession`, renderer-owned `RenderScene`, immutable
plugin-facing `EditorSceneSnapshot`, `RenderChanges`,
`SelectionModel`, `AssetRepository`, and workspace metadata.

| Concern | Foundation owner | JavaFX / Dear ImGui responsibility |
|---|---|---|
| Authored map state | `WorldDocument` and `EditorSession` | Display current state; never keep a second editable copy |
| User input | Neutral `PointerEvent`, tool lifecycle, and commands | Translate mouse/tablet/keyboard events into neutral input |
| Scene meaning | `RenderSceneBuilder`, terrain/object/collision contracts | Choose camera, visibility, batching, and draw submission |
| GPU/software resources | Renderer adapter boundary | Own JavaFX canvas, OpenGL/WebGPU/ImGui textures, buffers, and cleanup |
| Tool/panel catalog | `EditorPluginRegistry` and workspace metadata | Render labels, controls, inspectors, and docking/preset layout |
| Plugin lifetime | `EditorPluginHost` and session-scoped context | Mount/unmount frontend views; do not pass toolkit objects to plugins |
| Threading | Session/command publication and explicit change notifications | Marshal events and rendering to the frontend thread |

Dear ImGui therefore affects the adapter surface and renderer implementation,
not coordinate semantics, cache decoding, scene construction, command history,
or plugin identity. The first ImGui implementation should be a second consumer
of these contracts and should share the same plugin/tool catalog with the
controlled JavaFX workspace.

## Plugin structure

Plugins are capability-scoped contributors, not alternate applications. The
neutral `EditorPluginLoader`, `EditorPluginHost`, and
`EditorPluginRegistry` allow plugins to be discovered and register tools,
commands, panel metadata, workspace definitions, scene overlays, inspectors,
validators, and shortcuts. They receive an
`EditorPluginContext` containing the current session, neutral assets, and
registry, and optional host-owned `EditorSceneAccess` backed by immutable
`EditorSceneSnapshot`. The existing
JavaFX/client plugin loaders remain compatibility adapters until individual
workflows migrate.

The first-party terrain, object, and selection plugins are the initial
reference implementation of this boundary. The `CoreToolsPlugin` name now
serves only as their built-in factory. They register the tool catalog using
stable contribution IDs, labels, categories, and neutral factories. The
JavaFX tool rail mounts those registrations and generic feature-owned context
settings; tool behavior and command/history ownership remain neutral. A future
Dear ImGui rail must consume the same registrations rather than recreate the
catalog.
Overlay callbacks receive only `EditorSceneSnapshot` and neutral `OverlayDraw`;
inspector callbacks emit immutable display-neutral fields; validator callbacks
emit `ValidationIssue` values. Frontends decide how to draw, edit, filter, or
dock those contributions. Keyboard input follows the same rule: JavaFX and
Dear ImGui translate native events into `EditorKeyEvent`, while plugins may
register deterministic `EditorShortcut` contributions. Focus remains a
frontend concern; command execution and session ownership remain neutral.

Plugins must:

- use canonical commands and selection;
- consume neutral world/scene/asset contracts;
- declare stable IDs;
- provide deterministic registration;
- remain frontend-agnostic.

Plugins must not own the world document, history, cache writes, project
identity, collision truth, or a second renderer. JavaFX and Dear ImGui may
provide separate panel renderers for the same neutral contribution.

## Foundation exit evidence

Before declaring the foundation complete, `foundationGate` must pass and the
external verifier must cover plain, water/swamp, bridge, wall-heavy,
region-boundary, and instance fixtures. Current live instance terrain/object
parity is zero-difference, and the revision-240 no-sprite shaped minimap
capture is zero-difference across all four planes. Sprite-bearing minimap
captures, full render fingerprints, and the manual smoke checklist must still
confirm both legacy compatibility and the controlled OSRS workspace.
Missing external fixtures or interactive evidence remain open roadmap items,
even when deterministic unit tests pass.
