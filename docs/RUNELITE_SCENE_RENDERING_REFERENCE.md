# RuneLite OSRS Scene Rendering Reference

This document is the detailed semantic reference for the revision-240 scene
pipeline. It records behavior observed in the pinned RuneLite checkout and
the decisions RSPSi makes when converting that behavior into neutral editor
contracts.

RuneLite is a reference, not a runtime dependency. FileStore supplies cache
bytes and definitions, TSPS supplies independent revision-240 packet evidence,
and RSPSi owns the authored document, derived scene snapshot, and renderer
contracts.

For the details RuneLite leaves implicit, see the
[`scene rendering cross-reference`](SCENE_RENDERING_CROSS_REFERENCE.md). It
adjudicates RuneLite against TSPS and the OSRS Environment Exporter for
multi-region loading, border heights, streaming, GPU packet metadata,
independent lighting calculations, and export verification.

Reference checkout:

- repository: `RuneLite-melxin`
- local path: `../RSPSi-resources/RuneLite-melxin`
- commit: `1ad572d7dcdbc0fb67a4a00f0c2f959d5ab25abc`
- license observed in checkout: BSD-2-Clause

## The scene is a bounded layered projection

The client does not render a cache archive directly. It assembles a bounded
scene window, normally 104×104 tiles over four planes, with an extra border in
height arrays so shared vertices and neighbor gradients can be evaluated. The
window has a base coordinate and a set of source regions; it is not an
infinite world and it is not equivalent to one 64×64 map archive.

```text
cache archives and definitions
        ↓
revision-aware terrain/location decode
        ↓
source region window and shared-edge context
        ↓
four-plane scene materialization
        ↓
terrain topology/material/light derivation
        ↓
categorized object/model derivation
        ↓
bridge, roof, visibility, and occluder derivation
        ↓
immutable RenderSceneSnapshot
        ├── software/reference renderer
        ├── OpenGL/GPU renderer
        ├── minimap projection
        └── diagnostics and editor overlays
```

The authored `WorldDocument` remains the only editable truth. Paint, shaped
terrain, model packets, collision, minimap pixels, and overlays are derived
views and must be rebuildable.

## RuneLite source map

| Behavior | Primary source in the reference checkout | RSPSi decision |
|---|---|---|
| Terrain decode | `class264.loadTerrain` | Implement in the revision-aware OSRS decoder; never fall back to 317 |
| Scene construction | `class470.method9712` | Reproduce lighting, blending, tile presentation, bridges, and occluder inputs in neutral builders |
| Tile paint | `SceneTilePaint` | Preserve four corner colors, texture, flatness, and RGB/HSL-derived inputs |
| Shaped tiles | `SceneTileModel` and `Scene.tileShape2D` | Preserve all 13 shapes and four rotations as deterministic topology |
| Scene layers | `Scene.addTile`, `Tile`, `Scene.drawTile` | Preserve terrain, walls, decorations, ground objects, game objects, piles, and bridges separately |
| Occlusion | `Scene.Scene_addOccluder`, `Scene.occlude` | Store occluder inputs in the snapshot; frontend visibility is derived from them |
| Minimap | `Scene.drawTileMinimap` | Treat minimap as a separate projection, never as scene truth |
| Instance mapping | `Tiles.method2092` and scene-template fields | Preserve source-to-destination chunk transforms and orientation changes |
| GPU translation | `runelite-client/.../gpu/SceneUploader.java` | Use packet fields and upload order as parity evidence, not as authored state |
| Diagnostics | `runelite-client/.../devtools` | Implement as grouped, neutral diagnostics over immutable snapshots |

## Terrain decoding and shared heights

Terrain is decoded tile-by-tile with an opcode loop. The important semantic
cases are:

- opcode `0`: generate plane-zero height from the client noise function, or
  inherit the previous plane with the client's 240-unit offset;
- opcode `1`: read an explicit height byte, treating the value `1` as zero,
  then apply the plane-zero or upper-plane height rule;
- opcodes `2..49`: read overlay data, derive shape and rotation, and preserve
  the revision-specific overlay encoding;
- opcodes `50..81`: store tile-setting flags;
- opcode `82+`: store underlay identity.

The tile grid has four authored planes. Height arrays have a border because a
tile's north/east corner may belong to a neighboring tile or neighboring
region. A standalone region's final row and column are provisional until the
neighboring region is loaded. `WorldRegionWindow.stitchSharedEdges()` is the
neutral place to resolve this; the renderer must not invent independent seam
vertices.

For an instance, terrain is replayed into destination tiles using the source
opcode/provenance and the source-to-destination chunk rotation. Copying an
already materialized numeric height without preserving the source semantics is
not equivalent for generated heights or bridge planes.

## Four planes, bridges, and effective levels

The authored plane, physical plane, render/effective plane, and bridge target
are related but different facts:

- authored plane identifies where the cache record lives;
- physical/effective plane determines how the scene presents it;
- bridge flags can lower collision or visibility evaluation;
- a linked-below tile supplies another surface for traversal and rendering;
- roof/visibility flags influence whether a higher plane is exposed.

RSPSi must keep these relationships explicit. A renderer or plugin may inspect
an effective plane, but it may not repair a bridge by changing authored tile
data. `EditorSceneTileProjection` is the plugin-facing view; the new
`SceneTileSnapshot` contract carries the same relationships for renderer and
GPU consumers.

## Underlay blending and terrain lighting

RuneLite's scene builder computes terrain light from neighboring heights and
then subtracts a local underlay shadow/occlusion term. The directional
baseline is equivalent to a light vector near `(-50, -10, -50)` with an
ambient baseline near `96`.

Underlay color construction uses a rolling radius-five window. The window
accumulates hue, saturation, lightness, hue multiplier, and sample count from
underlay definitions. Hue is normalized through the hue multiplier; saturation
and lightness are averaged by count. The result is randomized by the bounded
client hue/lightness offsets and converted through the client HSL palette.

The final tile light is applied at the four tile corners. A flat tile can use
`SceneTilePaint`; a shaped tile carries per-face/per-corner colors through
`SceneTileModel`. This is why a single tile RGB value is insufficient for
faithful rendering.

Overlay derivation is separate:

- overlay shape and rotation choose the topology and color placement;
- textured overlays use texture-average color/HSL inputs;
- the hidden overlay sentinel must remain a semantic state;
- secondary overlay color is distinct from primary color;
- overlay and underlay colors are combined according to the selected shape,
  not flattened before topology construction.

RSPSi's `LightingProfile` represents authored/client-style lighting. The
frontend-only `LightingExposure` value may improve readability, but it must
not change scene fingerprints, authored colors, cache values, or saved data.

## Tile shapes and rotations

RuneLite defines 13 shaped-tile families. Their vertex points include corners,
edge midpoints, and quarter/three-quarter points. Each shape has explicit
triangle topology and can be rotated through four permutations. Rotation is
not merely a texture rotation: it changes which generated vertex and color
occupy each logical corner.

The neutral packet therefore preserves:

- shape and rotation;
- generated vertex positions and heights;
- triangle indices;
- underlay/overlay material identity per face;
- final packed color per vertex/face;
- texture ID and UV basis;
- flatness and hidden-overlay state.

The minimap uses related 2D shape masks and rotations but is a separate
projection. Matching minimap pixels does not prove 3D topology parity.

## Object categories and model inputs

A scene tile can contain, in semantic categories:

1. terrain paint;
2. shaped terrain model;
3. wall object;
4. decorative/wall-decoration object;
5. ground/floor decoration;
6. game objects;
7. item piles and other runtime-only layers; and
8. a linked bridge tile.

Object shape/category and orientation determine footprint, model selection,
placement transform, collision, and scene ordering. Odd rotations swap the
definition width and length. Instance chunk rotations transform both anchor
coordinates and object orientation.

The model projection must preserve vertices, indices, face colors, alpha,
render type, face priority, texture IDs, texture triangles, UVs, normals,
recolors, retextures, resize/offset data, contouring policy, bounds, and any
unsupported advanced capability as an explicit diagnostic. Missing model data
must not cause the decoder to reinterpret the object as another category.

## Scene traversal, occlusion, and minimap

`Scene.draw` and `drawTile` perform visibility and ordering traversal; they are
not equivalent to drawing a flat list of tiles. Occluders have type and
world/tile bounds and are activated based on camera direction and visible
tiles. Bridges and roof visibility can recursively add or suppress layers.

The GPU uploader's stable semantic upload sequence is:

```text
paint → shaped terrain → wall → decoration → ground object
      → game objects at their anchor → linked bridge tile
```

This sequence guides neutral layer ordering, but depth testing and transparent
face sorting remain renderer responsibilities.

The minimap uses shape masks, rotations, terrain colors, textures, and
map-scene sprites in a 2D projection. Debug overlays use projected tile
polygons and world/scene/region coordinates. Neither output may become a
second editable scene.

## Fidelity gates

The scene foundation is complete only when deterministic fixtures cover:

- terrain opcodes, height provenance, four planes, and region borders;
- all 13 shapes and four rotations;
- radius-five underlay blending and overlay texture/sentinel behavior;
- object category, orientation, footprint, and model transforms;
- bridge/effective-plane and instance replay;
- model materials, alpha, priority, normals, and texture mapping;
- occluder inputs and ordered scene layers; and
- scene fingerprints independent of JavaFX, ImGui, OpenGL, and cache-library
  implementation details.
