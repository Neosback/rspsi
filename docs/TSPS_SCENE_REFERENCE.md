# TSPS Scene Reference and Adjudication

This document records the pinned TSPS review for RSPSi's revision-240 scene
and renderer work. TSPS is a behavior donor and comparison source, not a
production dependency. Its TypeScript runtime, browser/WebGL shell, mutable
scene graph, cache loaders, and server are not imported into Studio.

The cross-source adjudication with RuneLite and the OSRS Environment Exporter
is maintained in [`SCENE_RENDERING_CROSS_REFERENCE.md`](SCENE_RENDERING_CROSS_REFERENCE.md).

Reference checkout:

- local path: `../RSPSi-resources/TSPS`
- commit: `83415f76589a360eacbd0e635fe0557d06a510f0`
- committed license: BSD-2-Clause
- important source paths: `client/rs/scene/SceneBuilder.ts`,
  `client/rs/scene/Scene.ts`, `client/rs/scene/SceneTileModel.ts`,
  `client/rs/config/loctype/LocModelLoader.ts`,
  `client/render/buffer/SceneBuffer.ts`, and
  `client/render/WebGLOsrsRenderer.ts`

The current working tree of the reference checkout deletes its `LICENSE` file.
The license statement above is based on the pinned commit, not on that dirty
working tree. Any future source adoption requires the committed notice to be
preserved and rechecked.

## What TSPS adds beyond the RuneLite review

RuneLite is the strongest client-semantic oracle. TSPS adds a complete,
inspectable implementation path from decoded map data to render buffers:

```text
revision-aware bytes
  -> SceneBuilder
  -> SceneTileModel / categorized SceneTile contents
  -> ModelData / Model lighting and transforms
  -> SceneBuffer vertex/index packets
  -> WebGL draw ranges and textures
```

The most valuable TSPS evidence is:

| Area | TSPS evidence | RSPSi decision |
|---|---|---|
| Final terrain appearance | `SceneTileModel` stores per-vertex HSL, minimap HSL, texture IDs, UVs, hidden faces, and face colors | Adopt the data requirements and algorithms behind a neutral `TerrainRenderPacket` |
| Underlay blending | `SceneBuilder.blendUnderlays` uses a radius-five hue/saturation/lightness window and hue multipliers | Adopt as the revision-240 floor-color derivation, with neighboring-region inputs explicit |
| Overlay semantics | Texture average HSL, sentinel overlay colors, secondary overlay colors, shape, and rotation are resolved before tile construction | Adopt the behavior; keep cache reads behind the FileStore adapter |
| Location model selection | `LocModelLoader.getLocModelData` selects `LocType.models` by `LocModelType`, handles mirrored models, and combines multi-part models | Adopt exact shape/type-aware selection and transformation in RSPSi neutral derivation |
| Model preparation | Recolor/retexture, resize, offsets, diagonal rotation, contouring, normal merging, ambient, and contrast are all explicit | Adopt the ordered transform pipeline; expose results as immutable render inputs |
| Scene layers | `SceneTile` separates tile model, wall, wall decoration, floor decoration, and multi-tile locs | Adopt the layer vocabulary, while extending RSPSi's neutral snapshot with explicit provenance/effective planes |
| Bridge behavior | `applyBridgeLinks` demotes tile columns, retains `linkedBelow`, creates non-rendering replicas, shifts collision, and preserves queryable original levels | Adopt the projected behavior and render order; do not mutate RSPSi authored data |
| GPU packetization | `SceneBuffer` separates terrain/models, opaque/alpha ranges, interaction ranges, priorities, HSL, alpha, textures, UVs, and model info | Use as the strongest packet-shape reference before choosing RSPSi's backend |
| Edit-mode workflow | `EditModePlugin` and `RegionPack` provide command-like terrain/location edits and revision-aware pack round trips | Use as export/fixture evidence; RSPSi commands/history remain authoritative |

## Canonical decisions when sources differ

### 1. Scene ownership: RSPSi wins

TSPS mutates a runtime `Scene` during bridge promotion, lighting, model
placement, dynamic overrides, and animation. That is appropriate for a client
frame, but not for an editor where authored data, undo, selection, save, and
recovery must remain stable.

RSPSi keeps:

- `WorldDocument` as authored truth;
- `EditorSession` as the command/history owner;
- immutable derived `RenderScene`/`EditorSceneSnapshot` projections; and
- explicit authored, physical, effective, and render-plane relationships.

TSPS's `linkedBelow`, bridge replicas, `skipRender`, and tile-level projection
rules are evidence for building that immutable projection. They are not a
reason to physically rewrite the editor's authored planes or object tuples.

### 2. Scene-build order: TSPS behavior is adopted

For revision 240, the derived build order is:

1. Decode authored terrain and locations.
2. Apply explicit editor overrides through the canonical session/command path.
3. Derive underlay blends and overlay/material inputs.
4. Build shaped terrain geometry and final terrain colors.
5. Derive floor collision before bridge promotion.
6. Resolve bridge/effective-plane projection and linked-below relationships.
7. Recompute plane/min-level visibility facts.
8. Resolve model transforms, normal merging, contouring, and lighting.
9. Publish immutable ordered scene/render packets.

This agrees with the existing RSPSi ordering and makes the important TSPS
rule explicit: floor collision is established before bridge demotion.

### 3. Terrain colors: TSPS is the implementation tie-breaker

RuneLite establishes the client vocabulary; TSPS exposes the complete
calculation in one place. RSPSi should implement the neutral equivalent of:

- radius-five underlay blending using hue multipliers;
- per-corner underlay HSL values;
- overlay primary/secondary HSL handling;
- texture average-HSL fallback for minimap/material derivation;
- sentinel/hidden overlay handling;
- directional tile light and tile-light occlusion; and
- shaped-tile vertex interpolation, face colors, UVs, and hidden faces.

The result belongs in `TerrainRenderPacket`, not only in `TerrainMaterial` or
`TerrainLight`. Minimap output may use a separate projection of the same
derived values.

### 4. Object/model transforms: TSPS is the implementation tie-breaker

`LocModelLoader` gives the clearest ordered behavior for static map objects:

1. Select model IDs by location shape/type.
2. Mirror when the definition/shape requires it.
3. Merge multi-part model data.
4. Apply special wall-decoration and diagonal rotations.
5. Apply orientation rotation.
6. Apply recolors and retextures.
7. Apply definition scale and offsets.
8. Apply normal merging or ambient/contrast lighting.
9. Apply contour-ground behavior against the scene height maps.
10. Resolve sequence/dynamic transforms only when the scene capability allows it.

RSPSi's neutral model packet must preserve enough information to reproduce
this sequence without exposing `Model`, `ModelData`, or `LocType` outside the
adapter.

### 5. Collision: RSPSi/OpenRune semantics win

TSPS's `CollisionMap` is valuable for checking client clip flags, wall
orientation, floor decoration, and object footprint placement. It is not the
same authority as OpenRune route semantics. RSPSi therefore keeps two named
layers:

- canonical editor/server collision: OpenRune-compatible route, reach, and
  movement semantics;
- client collision diagnostics: TSPS/RuneLite-style clip flags for explaining
  client behavior.

Neither layer may silently replace the other.

### 6. Rendering architecture: RSPSi contracts win; TSPS packet fields are adopted

TSPS's `SceneBuffer` is coupled to PicoGL, WebGL buffer layouts, draw-range
arrays, browser interaction IDs, and a mutable client frame. RSPSi should not
copy that architecture. It should adopt the demonstrated packet requirements:

- position and height;
- per-vertex HSL/color;
- alpha;
- texture index and UVs;
- face priority/render layer;
- scene/effective/plane-cull levels;
- contour-ground mode;
- interaction identity; and
- opaque versus transparent draw classification.

The neutral packet must remain backend-independent. JavaFX, Dear ImGui, an
OpenGL/WGPU backend, and a headless exporter may each translate it differently.

## TSPS-specific cautions

- `SceneBuilder` contains scattered game/revision conditionals such as
  `newTerrainFormat` and `centerLocHeightWithSize`; RSPSi must keep these in
  `OsrsRevisionFeatures`, not reproduce them as local branches.
- TSPS uses a mutable 104×104 runtime scene for instances. RSPSi must retain
  source-region provenance and destination materialization in its neutral
  instance contracts.
- TSPS's scene construction assumes square scene dimensions in at least one
  blend-bound check. RSPSi should use the correct independent X/Y bounds in
  general scene-window code.
- TSPS's bridge implementation mutates object/tile runtime levels for client
  lookup. RSPSi should derive render/effective levels without mutating authored
  location identity.
- TSPS's `EditModePlugin` is a useful workflow reference, but its edit log and
  region-pack writer are not RSPSi's undo/history or source-project format.
- Dynamic sequence, skeletal, particle, billboard, and animated-texture paths
  are useful capability references but should not block static revision-240 map
  rendering.

## Required RSPSi parity additions from TSPS

Before calling the 3D foundation complete, add fixtures for:

1. radius-five underlay blending and per-corner HSL;
2. textured and sentinel overlays, including hidden shaped faces;
3. every location model type used by the supported map corpus, including
   multi-part, diagonal, wall-decoration, roof, and floor-decoration cases;
4. model transforms, contour-ground, merged normals, alpha, priority, and UVs;
5. bridge promotion with linked-below terrain and original object identity;
6. opaque/transparent scene packet separation and plane-cull metadata; and
7. revision-aware region-pack decode/encode round trips using TSPS as an
   independent implementation check.

The authoritative acceptance artifact should be a deterministic neutral
render-packet fingerprint/export, not a byte-identical TSPS buffer or a
browser screenshot.
