# Viewport, Visibility, and Editor Settings Catalog

This is a design/settings list for OpenRune Studio. Some of these capabilities
already exist in partial form; this document records the complete target so we
can decide what belongs in the first polished Map Editor experience and what
stays advanced or debug-only.

This is not a request to expose every option at once. The normal toolbar stays
small, while category panels, presets, and the command palette reveal deeper
controls progressively.

## Core state model

Every major scene category should have four independent states:

| State | Meaning |
|---|---|
| Visible | Is it drawn? |
| Selectable | Can pointer selection target it? |
| Editable | Can an editing command change it? |
| Locked | Is editing explicitly prevented, including through tools? |

Example: walls may be visible, selectable, and editable but not locked; terrain
may be visible while not selectable/editable and locked during object work.
Visibility must never implicitly change selection behavior.

## Always-visible viewport controls

Keep the primary toolbar compact:

```text
Plane [0] [1] [2] [3]
👁 Terrain   Objects   Roofs   Bridges
   Grid      Collision Flags   Hidden
View [Vanilla]       Debug [None]
```

Each category opens a focused panel rather than adding dozens of toolbar
buttons. The most important controls are terrain, objects, roofs, bridges,
grid, collision, flags, hidden-data reveal, active plane, and view/debug
presets.

## Core visibility categories

| Category | Default | Notes |
|---|---:|---|
| Terrain | On | Master terrain mesh visibility |
| Underlays | On | Independent from overlays |
| Overlays | On | Independent from underlays |
| Walls/boundary objects | On | Loc layer/category |
| Wall decorations | On | Separate from walls |
| Game objects | On | Regular locs and multi-tile objects |
| Ground decorations | On | Separate from game objects |
| Bridge-linked tiles | On | Must remain inspectable |
| Roofs | On | Hide, fade, or hide over cursor/selection |
| Map-scene sprites/icons | On | Useful for map/minimap comparison |
| Animated objects/textures | On | Pause and speed controls apply |
| Runtime items/particles/spawned objects | Off | Simulator/future runtime content |
| Base-cache objects | On | Distinguish from authored/project changes later |

Object visibility needs convenient presets: All, Walls, Decorations, Game
Objects, Ground Decorations, Interactive, and Debug/Hidden.

## Plane, bridge, roof, and render-level settings

| Setting | Target behavior |
|---|---|
| Active plane | Select 0–3 as the editing context |
| Show active plane only | Hide all other planes |
| Show planes below/above | Independent plane inclusion |
| Fade inactive planes | Opacity slider rather than only hide/show |
| Ghost planes above/below | Contextual translucent reference |
| Select/edit active plane only | Prevent accidental cross-plane edits |
| Lock other planes | Stronger protection than fading |
| Show bridge tiles/linkage | Draw authored-to-underlying relationships |
| Show source plane | Where tile data is stored |
| Show render level | Effective visual level |
| Show collision plane | Effective movement/collision level |
| Highlight plane mismatches | Diagnose bridge/render/collision disagreement |
| Show roofs | Normal roof rendering |
| Hide/fade roofs | Interior editing without losing context |
| Hide roof over cursor/selection | Fast local inspection |
| Show bridge/under-roof/visible-below flags | Semantic flag debugging |

The inspector should distinguish source plane, render level, and collision
plane rather than presenting a single ambiguous “plane” value.

## Terrain rendering and debug modes

Candidate toggles:

- terrain mesh, underlay, overlay, overlay texture;
- raw tile color versus final lit color;
- texture, underlay, overlay, shape, and rotation IDs;
- height visualization, numeric heights, vertices, shared vertices, triangles,
  normals, and wireframe;
- ignore textures, ignore lighting, flat shading, and normal visualization.

Height display presets:

- Normal;
- Height heatmap;
- Slope heatmap;
- Contour lines;
- Vertex values;
- Wireframe.

These modes are especially important for validating shared-corner heights,
shape/rotation topology, blending, and terrain generators.

## Tile flags and hover inspection

Tile flags should have a dedicated debug mode with independent highlights for
bridge, under-roof, visible-below, blocked floor, roof-related, and unknown/raw
bits. A raw hexadecimal value must remain available alongside decoded names.

The hover inspector should be configurable and show, as applicable:

```text
World X/Y       Region ID       Region-local X/Y
Chunk           Plane           Source/render/collision plane
Height          Underlay        Overlay
Shape           Rotation        Raw/decoded flags
```

Object hover adds name, ID/symbol, type, orientation, size, animation,
collision, actions, model/mapscene IDs, and transform information.

## Object rendering and overlays

Independent object information overlays should include:

- object ID and name;
- loc type and orientation;
- footprint and origin tile;
- collision footprint and directional blockers;
- model bounds, convex hull, model IDs, animation ID;
- map icon/mapscene IDs;
- transform chain and varbit/varp state;
- actions and definition/config flags.

Loc-type filters should support all supported type IDs with presets for walls,
wall decorations, game objects, ground decorations, and all categories.

## Collision and pathfinding visualization

Collision belongs on the main toolbar because it is a core editing/debugging
workflow. Modes should include Movement, Projectile/LOS, Both, and Off.

Detailed toggles:

- blocked tiles;
- directional north/east/south/west clipping;
- diagonal clipping and corner protection;
- object, floor, and floor-decoration blocking;
- projectile clipping;
- raw collision masks and hexadecimal values;
- route-blocker masks and derived reachability.

Directional arrows/edges are preferred over painting every blocked tile solid
red. Pathfinding debug should support reachable/unreachable tiles, BFS/A*
expansion, shortest path, LOS, and actor sizes 1×1 through 4×4/custom.

## Grids and coordinate overlays

| Grid | Visual emphasis |
|---|---|
| Tile | Thin |
| 8×8 chunk | Medium |
| 64×64 region | Thick |
| Loaded/extended scene boundary | Strong boundary |

Optional labels include world, region-local, scene, chunk, and region
coordinates, tile centers, vertices, chunk IDs, and instance source/destination
coordinates. Grid opacity and line thickness should be adjustable.

## Selection, filters, and locks

Selection display options:

- outline, fill, selected vertices, object footprints, pivot, transform gizmo;
- affected area, brush radius, falloff, clipboard preview, ghost paste;
- generator preview and proposed diff;
- distinct Added, Modified, Deleted, and Selected visuals.

Selectable categories should be independent:

- terrain, objects, walls, decorations, game objects, ground decorations;
- height vertices, overlay, underlay, collision;
- current plane only, visible items only, and unlocked layers only.

Editing locks should cover terrain, heights, overlays, underlays, flags,
objects, collision, planes, regions, selections, and outside-selection areas.
Protection options should include Protect Objects, Protect Terrain, Protect
Selection, and Protect Outside Selection.

## Snapping and placement assistance

Candidate snapping targets:

- tile center, edge, corner, half/quarter tile, and fine coordinate;
- object, wall, selection, height, plane, chunk, and region;
- smart loc orientation and smart wall orientation.

Placement assistance should preview object ghost, footprint, collision,
orientation arrow, loc type, origin, map icon, transformed variant, animation,
and contour-to-ground behavior. Options include auto-orient, auto-determine
loc type, prevent invalid overlap, warn instead of prevent, and an explicit
unsafe-placement override.

## Terrain and floor editing settings

Tool settings belong in the context panel, not as global hidden state:

- radius, strength, falloff, spacing, pressure, and brush shape;
- shared-vertex editing, edge locks, neighbour preservation, and region-edge
  preservation;
- relative versus absolute height;
- raise, lower, flatten, smooth, noise, terrace, and slope modes;
- paint overlay, underlay, shape, and rotation independently;
- preserve overlay, underlay, shape, rotation, height, and flags independently.

The default behavior should make “change only this field” easy and safe.

## Lighting, animation, and camera

Lighting modes:

- Vanilla Preview: OSRS lighting, ambient, contrast, shading, texture
  animation, face priorities, and transparency;
- Editor Neutral: clear neutral lighting for authoring;
- Unlit, normals, wireframe, and face-priority debug modes.

Candidate controls include ambient/directional intensity, light direction,
vanilla colors, shadows, texture animation, transparency, flat shading, and
normal visualization.

Animation settings include enabled/paused, speed, reset, random start state,
forced animation ID, animated textures, and texture-animation speed.

Camera modes include perspective, orthographic, top-down, OSRS camera, and
free camera, with movement/fast/slow speed, mouse/zoom sensitivity, invert Y,
smoothing, frame selection, focus hovered tile/region/chunk, remembered camera
per map, and cardinal/top/perspective views.

## Render distance, culling, and performance

Candidate render controls:

- near/far clip;
- terrain/object/render distance;
- LOD distance;
- frustum, backface, occlusion, roof, plane, and distance culling;
- show culled objects, occluders, and bounds.

Performance settings may eventually include target FPS, VSync, MSAA, resolution
scale, texture filtering, anisotropic filtering, shadow quality, GPU terrain
batching, object instancing, chunk mesh caching, model/texture cache sizes,
and background region/model loading.

Performance diagnostics should expose FPS, frame time, GPU time, triangle count,
draw calls, visible objects, loaded chunks, and texture/model memory.

## Minimap, regions, chunks, and hidden data

Minimap modes:

- Vanilla minimap;
- Collision minimap;
- Height minimap;
- Material minimap.

Optional layers include terrain colors, mapscene sprites, icons, walls,
objects, collision, chunk boundaries, and region boundaries, with a live-update
toggle.

Region/chunk controls include loaded/neighbouring regions, auto-load adjacent
regions, context radius, chunk IDs, instance source/destination, chunk rotation,
empty template cells, duplicate sources, and rotation arrows.

“Show invisible data” should be a deliberate Reveal Hidden Data mode with
sub-options for hidden tiles, invisible/no-model objects, collision-only
objects, roof/bridge flags, unused overlay information, empty flagged tiles,
and runtime-only objects.

## Cache, revision, save, and history settings

These belong in project/build settings rather than the viewport toolbar:

- cache path, revision/subrevision, map/location/model/texture formats;
- XTEA source and keys, read-only/working-copy mode, target cache, export
  revision;
- strict versus best-effort compatibility;
- validate before save/pack, backup before overwrite, atomic write;
- warn on lossy conversion, revision mismatch, missing XTEA, and unsupported
  opcodes;
- encode → decode round-trip validation after packing;
- autosave interval/generations, undo-history limit, persistent history, and
  crash recovery;
- record a generator as one undoable operation regardless of change count.

## Renderer compatibility

The renderer settings must distinguish target semantics from editor presentation:

- Vanilla Compatibility;
- Editor Enhanced;
- Debug.

Later backend options may include OpenGL/WebGPU or fallback modes, but the
renderer remains behind `SceneRenderer` and cannot become another OSRS decoder.
Compatibility toggles may cover depth buffer, legacy face sorting, priorities,
transparency, OSRS lighting, filtering, and anti-aliasing.

## Workspace settings and presets

Settings should be remembered globally or per project, with explicit scope:

- panel layout;
- renderer settings;
- visibility/selection/lock settings;
- camera;
- active plane;
- tool parameters.

Useful presets:

| Preset | Typical state |
|---|---|
| Mapping | Normal terrain/objects, light grid, minimal labels |
| Terrain | Objects faded/off, grid and height vertices on |
| Objects | Terrain on, footprints and IDs on, collision optional |
| Collision | Terrain faded, directional collision and grid on |
| Roofs | Roof/bridge/plane flags and transparency emphasized |
| Debug | Labels, raw IDs, flags, bounds, hidden data as requested |
| Vanilla Preview | OSRS lighting, normal visibility, minimal overlays |
| Performance | Diagnostics and culling information visible |

## Delivery rule

For the first polished Map Editor, choose roughly 20–25 high-value controls:
terrain/objects/roofs/bridges, active-plane controls, grid, collision, flags,
selection filters, locks, snapping, height/floor tool settings, camera presets,
and a few viewport presets. The rest should remain discoverable advanced or
debug settings rather than overwhelming the default workspace.
