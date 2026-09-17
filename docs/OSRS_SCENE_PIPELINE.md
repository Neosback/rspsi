# OSRS Scene Pipeline Contract

This document is the working map of how an OSRS scene is assembled and where
future RSPSi tools and plugins may attach. It is intentionally phrased in
neutral concepts; TSPS and RuneLite are evidence sources, not production
dependencies.

The concrete RuneLite scene vocabulary and source paths behind this contract
are recorded in [`RUNELITE_SCENE_REFERENCE.md`](RUNELITE_SCENE_REFERENCE.md).
That reference is especially important for the distinction between authored
tiles, derived paint/models, categorized objects, bridge/effective-plane
relationships, and renderer/minimap projections.

The pinned TSPS implementation review and source-adjudication decisions are
recorded in [`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md). TSPS is the
stronger concrete donor for final HSL/UV terrain packets, location model
selection/transforms, normal merging, bridge projection, and WebGL packet
fields. RuneLite remains the client-semantic oracle; RSPSi owns the neutral
contracts and authored/editor state.

## Scene assembly

```text
cache map archives + definitions
        |
        v
authored WorldDocument
  terrain opcodes, height provenance, flags, locations
        |
        +--> WorldRegionWindow / shared-edge context
        |
        +--> terrain topology + materials + lighting
        |
        +--> object categories + footprints + model inputs
        |          |
        |          +--> collision / route / reach projections
        |
        +--> bridge/effective-plane relationships
        |
        v
neutral RenderScene + derived minimap/validation views
        |
        v
JavaFX, Dear ImGui, software/GPU renderer, or plugin overlay
```

The authored document is the only editable truth. Every result below it is
rebuildable from the document, definitions, and explicit scene context. A
frontend must never edit a rendered tile model, minimap pixel, collision flag,
or plugin overlay as if it were authored map state.

## Reference phase order

| Phase | Meaning | RSPSi owner | Plugin/tool access |
|---|---|---|---|
| Archive read | Decode revision-specific terrain/location payloads and definitions | cache adapters | Definition views and diagnostics |
| Authored terrain | Preserve opcode values, generated/explicit height provenance, underlays, overlays, shapes, rotations, flags | `WorldDocument`, `OsrsRegionDecoder` | Read through session/document contracts |
| Region context | Load neighboring regions, represent holes, compare/stitch shared vertices | `WorldRegionWindow` | Window-aware inspectors and validators |
| Instance materialization | Replay source chunks into destination planes, including generated heights and object footprints | `InstanceChunkGrid`, `InstanceWorldBuilder` | Instance inspectors and future scene tools |
| Terrain derivation | Build 13 scene topologies × rotations, materials, and light inputs | `TerrainMeshBuilder`, material/lighting contracts | Read-only scene overlays |
| Object derivation | Resolve shape/category, orientation-aware footprint, model and appearance inputs | `RenderSceneBuilder`, definition views | Object inspectors and placement tools |
| Collision derivation | Project terrain, walls, decorations, footprints, bridges, and route-blocking semantics | `OsrsCollisionBuilder` | Validation, route, and collision overlays |
| Bridge resolution | Relate authored planes to effective/render planes without mutating authored data | `WorldDocument.effectivePlane`, render/window builders | Bridge diagnostics and plane inspectors |
| Presentation | Choose camera, clipping, batching, textures, pixels, and input focus | JavaFX/ImGui/render adapters | Renderer-specific only |
| Export projection | Serialize immutable scene/model/material data for diagnostics or external tools | Future RSPSi export adapters | Export plugins; never authored-state ownership |

The phase order is observable behavior. For example, instance terrain must
replay a source height opcode into the destination plane; copying a source
plane's already-materialized numeric height is incorrect. Likewise, bridge
relationships affect derived visibility/collision but must not rewrite the
authored plane or location tuple.

## Scene tile and location rules

An authored tile has two related but distinct identities:

1. Its cache-authored fields: height opcode result/provenance, underlay,
   overlay, shape, rotation, and raw flags.
2. Its derived scene presentation: effective plane, topology, blended material,
   light values, hidden/bridge visibility, and object projections.

Locations retain `id`, authored plane, local/world anchor, shape/type, and
rotation. Definition footprint is resolved at the boundary where definitions
are available. The footprint is rotated with the location orientation and is
used by instance transforms, collision, placement validation, and render
object projections. It is never inferred from a minimap sprite.

Map-scene sprites are presentation assets. They may decorate a minimap or
scene preview, but they do not define object category, collision, footprint,
selection identity, or save semantics. A missing map-scene sprite is an
explicit asset capability/diagnostic, not permission to invent a replacement
object or alter the authored scene.

## Plugin attachment points

Plugins should choose the narrowest contribution that fits their feature:

- `EditorToolRegistration` for command-backed interaction;
- `EditorShortcutRegistration` for deterministic key behavior after the
  frontend translates native events;
- `EditorSceneOverlay` for read-only scene annotations over an immutable
  `EditorSceneSnapshot` or its per-tile `EditorSceneTileProjection`;
- `EditorInspector` for immutable display fields and neutral selection/session
  context;
- `EditorValidator` for diagnostics expressed as `ValidationIssue`;
- panel/workspace registrations for frontend-rendered layout metadata;
- `AssetRepository` for typed lazy definitions and sprites without archive
  knowledge;
- `EditorCommandRegistration` for command-palette/menu capable command
  factories that still execute through the canonical session history.

No plugin should build a second scene graph, maintain a second undo stack,
write raw cache archives, or depend on JavaFX/Dear ImGui types. A plugin that
needs a derived fact should consume `EditorSceneAccess` or a neutral service;
if that service does not exist, add the service contract before adding a
frontend-specific workaround. `EditorSceneTileProjection` is the preferred
tile-level access path: it groups authored state, effective-plane/bridge
context, terrain outputs, collision, and renderer-ordered object layers.

Scene/export plugins follow the same rule. They may consume immutable scene
snapshots, model/material inputs, and renderer diagnostics to produce glTF,
mesh, image, or fingerprint output. They may not turn exported geometry into a
second editable world, bypass commands/history, or read cache-library types
directly. A future headless exporter should use the same cache adapter and
scene builder as the desktop shell.

`EditorPluginHost` owns the lifecycle boundary: discovery order is stable,
initialization is validated, shutdown runs in reverse order, and every
contribution registered during initialization is removed when the host closes.
The closed host also releases its plugin instances so an external plugin
classloader is not retained by the editor. This makes plugin reload/unmount a
frontend concern rather than a source of stale editor state.

## Dear ImGui consequences

Dear ImGui changes only the adapter and rendering layer. It does not require a
second world model, scene builder, plugin registry, coordinate system, or
history implementation. The ImGui adapter must:

- translate mouse, keyboard, drag, and viewport events into neutral events;
- render the same `WorkspaceCatalog`, tool registrations, inspectors, and
  validation fields as JavaFX;
- subscribe to the same session/dirty-region/render-change publication;
- own only ImGui draw lists, textures, docking state, and focus traversal;
- preserve visible keyboard focus and deterministic shortcut dispatch.

JavaFX and ImGui may differ in docking, styling, GPU resources, and input
capture. They must agree on command results, selection, scene coordinates,
history, dirty state, save behavior, and plugin contribution IDs.

The first shared frontend implementation seam is now executable: an
`EditorFrontendFrame` is captured from the existing `EditorPluginContext`, and
`DearImGuiFrontendAdapter` consumes it while routing keyboard/pointer input
through `EditorInputRouter`. This is deliberately a projection, not a second
scene graph or state store. The native Dear ImGui context and GPU resources
remain in the UI module and are not allowed into neutral contracts.

## Evidence and open seams

| Contract | Current evidence | Status |
|---|---|---|
| Authored terrain/location semantics | deterministic codec tests and revision-240 external parity | verified for covered revisions |
| Shaped terrain topology | 52-case topology matrix and pinned TSPS table comparison | verified |
| Instance chunk replay | external TSPS fixture: 676 templates, zero terrain/object differences | verified for fixture |
| Bridge/effective plane | bridge-column tests and external authored/effective geometry comparison | implemented, broader parity open |
| Collision | OpenRune differential vectors and live diagnostics | implemented, normalized external fixture open |
| Minimap/map-scene presentation | shaped TSPS export and image comparison | verified for the covered no-sprite and sprite-bearing revision-240 captures; broader revision coverage remains |
| 3D render packet inputs | TSPS `SceneTileModel`, `LocModelLoader`, `Model`, and `SceneBuffer` review | not-started: final terrain HSL/UVs, transformed model packets, layer ordering, alpha/priority, and backend-independent export remain |
| Static 3D scene backend | canonical JavaFX preview plus legacy compatibility renderer | not-started: no production renderer consumes the complete neutral 3D packet |
| Dear ImGui adapter | neutral contracts, shared `EditorFrontendFrame`, `DearImGuiFrontendAdapter`, and JavaFX reference adapter | native draw/input smoke open |
| Interactive workflow | manual smoke checklist | blocked until desktop is unlocked and exercised |

The next correct work on minimap parity is broader cache/revision coverage and
then a separate full-render comparison of sprite offsets, transparency,
materials, and object-category ordering. The covered sprite-bearing fixture
already verifies the map-scene offsets, transparency, and category ordering.
Do not make minimap pixels authoritative for map editing or plugin state.

## Current 3D rendering verdict

The neutral `RenderScene` is a valid semantic foundation, but it is not yet a
render-complete 3D scene. The current controlled JavaFX viewport is a top-down
semantic preview, and the legacy `SceneGraph`/software renderer is a
compatibility surface with older loader assumptions. Neither is allowed to
define the new renderer contract.

The RuneLite review identifies five missing seams that must be completed before
we call map-scene rendering faithful:

- final terrain corner/face colors, overlay blending, texture coordinates, and
  flatness rather than only source IDs, RGB values, heights, and a normal-based
  light baseline;
- exact shape/type-aware model selection plus transformed model geometry,
  including recolors, retextures, resize, offsets, contouring, ambient/
  contrast, and the declared policy for animation;
- model render attributes: face render type, alpha, priority, bias, texture
  face mapping, texture triangles, normals, and bounds;
- a complete ordered tile-layer snapshot with camera visibility, bridge/roof
  traversal, footprint/depth ordering, and occluder inputs; and
- neutral texture assets/material packets and a real 3D backend with a
  deterministic parity fixture.

The implementation order is deliberately packet-first. A JavaFX or Dear ImGui
frontend may own GPU resources and draw lists, but it must consume the same
immutable render packets and `EditorSceneSnapshot`. No frontend may grow a
second scene graph to compensate for a missing neutral contract.

## FileStore and lighting implementation gate

OpenRune FileStore supplies raw cache data, definition views, model geometry,
texture metadata, and the Jagex color/lighting primitives. RSPSi converts
those values into `TerrainAppearance`, `TerrainLight`, object, material, and
scene packets. The current `TerrainLighting` baseline is now parameterized by
an OSRS `LightingProfile`; it is not a frontend brightness setting.

The first faithful 3D gate requires final terrain HSL/material inputs,
radius-five underlay blending, overlay texture/sentinel handling, model
normal/ambient/contrast handling, and deterministic lighting fingerprints.
Frontend exposure is separate and must not affect authored data or packet
identity.
