# OSRS Scene Rendering Cross-Reference

This document records what the TSPS and OSRS Environment Exporter reviews add
to the RuneLite scene-rendering reference. It is an adjudication document for
RSPSi, not a proposal to combine the source repositories or copy their
runtime architectures.

The sources have different strengths:

| Source | Best evidence | RSPSi role |
|---|---|---|
| RuneLite-melxin | Client scene vocabulary, revision behavior, visibility, occlusion, GPU upload semantics, DevTools | Primary OSRS/client semantic oracle |
| TSPS | Explicit revision-240 scene construction, tile topology, bridge projection, packet layout, WebGL shaders, streaming, picking | Concrete revision-240 implementation comparison |
| OSRS Environment Exporter | Independent region builder, lighting/HSL calculations, object placement, CPU/GPU renderer split, glTF export | Independent cross-check and headless/export reference |
| OpenRune FileStore | Cache files, definitions, models, textures, sprites, and revision-aware source data | Production cache/asset backend |
| RSPSi | Authored world, history, neutral packets, workspaces, plugins, and frontend adapters | The only authoritative editor architecture |

The rule is simple: RuneLite tells us what the client means, TSPS makes
revision-240 packet requirements explicit, the Environment Exporter provides an
independent implementation check, and RSPSi owns the contracts that join those
facts together.

## Source and license boundary

The reviewed checkouts remain external research resources:

- `RuneLite-melxin`: pinned commit `1ad572d7dcdbc0fb67a4a00f0c2f959d5ab25abc`,
  BSD-2-Clause.
- `TSPS`: pinned commit `83415f76589a360eacbd0e635fe0557d06a510f0`, recorded as
  BSD-2-Clause in its committed history. Its current working tree removes the
  license file, so future source use must recheck the committed notice.
- `OSRS-Environment-Exporter`: pinned commit
  `61d461d3bfd8217a470518924415d7de1b074b9c`, GPL-3.0. Its source also carries
  RuneLite BSD and OpenRS notices.

RSPSi may use these repositories as behavioral evidence and may independently
reimplement compatible algorithms. The GPL project must not be copied into
RSPSi, added as a runtime dependency, or used as an unreviewed source of
bundled assets. The source-specific references and notices are retained in
[`OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md`](OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md)
and [`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md).

## What RuneLite leaves implicit and the other sources make explicit

### 1. A scene window needs more source data than the visible tiles

RuneLite exposes the client scene as a bounded four-plane scene, normally
104×104 tiles, but the final terrain values depend on a surrounding border and
on every 64×64 source map square intersecting the requested window.

TSPS makes this operational. Its scene builder computes the inclusive range of
map squares intersecting a requested base/window, loads them with offsets, and
keeps a height border for shared vertices, gradients, and model contouring. It
also has explicit missing-square handling and incremental rebuild paths.

The Environment Exporter provides an independent version of the same idea: a
64×64 region is rendered with a five-tile neighborhood queried from adjacent
regions so underlay blending does not stop at an archive boundary. Its GUI can
load a grid of regions around a center region asynchronously.

RSPSi decision:

- `SceneWindow` must identify the destination window, source regions, border,
  revision, and cache identity.
- The map editor may display a 64×64 region, but scene derivation must load all
  intersecting source squares plus the required neighbor context.
- Missing neighbors are explicit holes/diagnostics; the renderer must not
  invent a second, independent seam height.
- Region loading, cache decoding, and scene materialization remain separate so
  the same builder can serve JavaFX, ImGui, GPU, and headless verification.

### 2. Underlay blending is a neighborhood calculation

Both TSPS and the Environment Exporter confirm that floor color is not a local
tile-only property. The revision-240 implementation uses a radius-five window
of neighboring underlays, rolling hue/saturation/lightness sums, and hue
multipliers. The neighborhood may cross region boundaries.

This is important for the editor because changing one underlay can affect a
larger derived area than the edited tile. The authored edit is local, but the
invalidated material and lighting packets are not necessarily local.

RSPSi decision:

- Keep underlay identity and raw authored data in `WorldDocument`.
- Derive blended HSL and final corner values in the scene/material builder.
- Invalidate the five-tile blend neighborhood, plus lighting/model dependents,
  after an underlay edit.
- Store enough provenance to distinguish an absent underlay, a hidden overlay,
  a textured overlay, and a valid HSL value.

### 3. Tile topology is a real geometry contract

TSPS exposes the complete tile-shape tables that RuneLite names but does not
present as a backend-neutral packet specification. It records the 13 shape
families, their four rotations, corner/half/quarter vertices, face indices,
per-vertex HSL, UVs, texture IDs, and hidden faces.

The Environment Exporter independently consumes the same shaped-tile concepts
when uploading tile models. Together they confirm that `shape` and `rotation`
are not merely minimap metadata: they determine the actual terrain triangles,
diagonal orientation, material assignment, and lighting interpolation.

RSPSi decision:

- Preserve all 13 topology families and rotations in deterministic neutral
  tables.
- Preserve shared corner heights and the generated intermediate vertices.
- Preserve per-face colors, texture IDs, UVs, and hidden-face state.
- Do not flatten shaped tiles into a heightfield or triangulate them differently
  in each frontend.
- Keep the source tables in RSPSi-owned contracts; the donor table is evidence,
  not a runtime dependency.

### 4. Bridge projection has more state than a simple linked tile

RuneLite establishes bridge/effective-plane semantics. TSPS reveals additional
runtime details: bridge replicas, a `linkedBelow` relationship, plane demotion
for the bridge flag, copied wall flags at bridge boundaries, visibility/minimum
level recomputation, and separate treatment of force-visible/roof flags.

The TSPS build order is also significant: floor collision is derived before
bridge links are applied, then tile minimum levels are recomputed. This avoids
using a post-demotion render projection as if it were authored collision.

RSPSi decision:

- Authored plane, physical plane, effective/render plane, bridge target, and
  linked-below relation are separate fields.
- Bridge projection is immutable derived state; it never rewrites authored
  tiles or object identities.
- Collision semantics are derived before render-plane promotion and remain
  available for diagnostics after promotion.
- Force-visible, roof, occluder, and plane-cull inputs are retained in the
  snapshot rather than hidden inside a frontend.

### 5. Object rendering needs instance metadata, not only mesh vertices

TSPS's `SceneBuffer` adds data that is easy to lose if we only compare final
triangles: scene position, height offset, authored/effective level, plane-cull
level, contour-ground mode, priority, interaction type, and interaction ID.
Its separate opaque, alpha, LOD, and interaction ranges make those fields
available to the GPU without rebuilding mesh data.

The Environment Exporter independently confirms the ordered object pipeline:
shape/type model selection, orientation-aware footprint, wall and decoration
placement, center-height calculation, diagonal/wall-decoration transforms,
contouring, ambient/contrast, normal merging, and object layer insertion.

RSPSi decision:

- `ModelRenderPacket` contains complete geometry/material data.
- A separate instance record contains scene anchor, effective level, cull
  level, contour policy, interaction identity, alpha/priority classification,
  and source provenance.
- Model preparation happens in a fixed order before packet publication.
- A model may be uploaded once and instanced many times, but instance identity
  and selection data must remain stable.

### 6. GPU rendering still starts with CPU scene preparation

Both sources clarify an important misconception: the GPU does not receive a
pre-rendered map image. CPU-side code still decodes the cache, derives the
scene, resolves object transforms/material inputs, and builds upload packets.
The GPU then transforms, clips, rasterizes, samples textures, applies fog and
presentation uniforms, and composites each frame.

TSPS makes the WebGL path explicit:

- interleaved vertex/index buffers;
- per-draw model metadata texture;
- a multi-plane height map for contouring;
- texture/material arrays and animation metadata;
- separate opaque, alpha, LOD, and interaction ranges;
- priority and plane-cull epsilon handling in shaders; and
- CPU-side terrain ray intersection/picking using the same height data.

The Environment Exporter provides an independent OpenGL comparison with a
`PriorityRenderer` seam, static and dynamic buffers, compute-shader dispatch,
memory barriers, and a CPU fallback. Its macOS configuration is a useful
reminder that compute shaders are optional, not a correctness requirement.

RSPSi decision:

- The neutral scene packet is backend-independent and complete before upload.
- A GPU backend owns buffers, textures, shaders, barriers, and native handles.
- OpenGL 3.3/software compatibility is a correctness baseline; compute,
  WebGPU, and other acceleration paths are optimizations.
- CPU and GPU backends consume the same packet fingerprint and must agree on
  selection coordinates, layer order, and scene identity.
- Height-map contouring must eventually distinguish exact terrain triangles
  from the TSPS safety approximation that takes the maximum of both possible
  tile diagonals.

### 7. Streaming and stale scene generations matter

RuneLite describes the scene lifecycle, but TSPS makes asynchronous streaming
failure modes visible. It tracks active map generations, carries forward
overlapping map data, rejects incompatible ordinary-map data during instance
scenes, and avoids a misleading fade when a teleport has no overlapping
window.

RSPSi decision:

- Every derived snapshot and asynchronous load has a scene/window generation.
- A late decode cannot publish into a newer session or cache identity.
- Overlapping source data may be reused only when revision, cache identity,
  scene mapping, and generation match.
- Instance and ordinary-scene loaders are separate capabilities.
- Cancellation, stale-result rejection, and partial-window diagnostics are
  foundation behavior, not renderer polish.

### 8. Water and advanced materials are a later but real packet concern

TSPS includes a water path that RuneLite's basic scene description does not
fully expose: water masks, packed bed color/depth, shoreline detection from
neighbors, normal maps, foam, caustics, specular/Fresnel, and animated material
metadata.

RSPSi will not block the static revision-240 map foundation on a complete water
renderer. It will, however, reserve explicit material capability fields and
diagnostics so water is not forced into an opaque flat-texture approximation
that changes authored semantics later.

### 9. Headless export is a valuable verification surface

The Environment Exporter demonstrates a practical headless/export workflow and
glTF/mesh output. That is useful because a deterministic scene can be inspected
without the editor shell, compared in Blender or another viewer, and attached
to regression reports.

RSPSi decision:

- Add a versioned neutral scene/render-packet export format before adding a
  production glTF exporter.
- Build a headless `verify-scene`/`export-scene` path over the same FileStore,
  OSRS bundle, scene builder, and packet fingerprint used by Studio.
- Treat glTF as an output adapter, never as the authored project format.

## Lighting and color adjudication

The Environment Exporter independently confirms the useful OSRS-style lighting
constants: contrast `768`, ambient `96`, directional bias `(-50, -50, -10)`,
and a `256` normal/brightness basis. Neighboring render flags contribute to
tile brightness, and final HSL lightness is clamped to the client range.

It also provides two useful comparisons that must not be conflated:

1. `SceneRegionBuilder` lighting/HSL behavior is evidence for authored/render
   semantics and should be compared with RuneLite and TSPS.
2. `ColorPalette` applies a gamma-like display adjustment while generating
   colors. That is an exporter/presentation choice, not permission to rewrite
   cache HSL or scene fingerprints.

RSPSi's default remains the faithful OSRS-style lighting profile. Brightness,
exposure, colorblind preview, and display gamma are frontend uniforms. They may
change what a user sees, but must not mutate authored colors or deterministic
scene packets.

## Adjudicated architecture

```text
OpenRune FileStore / OSRS revision adapter
        │  bytes, definitions, models, textures
        ▼
RSPSi authored WorldDocument + EditorSession
        │  revision-aware derivation
        ▼
RenderSceneSnapshot
        ├── terrain topology/material/light packets
        ├── object/model packets + instance metadata
        ├── bridge/roof/occluder/collision projections
        ├── selection/picking projections
        └── deterministic fingerprint/export
             ├── JavaFX compatibility renderer
             ├── ImGui frontend
             ├── OpenGL/GPU adapter
             └── headless exporter/verification
```

No source repository becomes the application base. No frontend opens the cache.
No renderer reconstructs terrain topology or lighting independently. No
diagnostic overlay mutates scene truth.

## Adopt, adapt, defer

| Evidence | Decision |
|---|---|
| RuneLite scene vocabulary, bridge/occluder/client behavior | Adopt semantically through RSPSi contracts |
| TSPS 13-shape topology, HSL/UV packets, bridge order, instance metadata, GPU height map, streaming generations | Adopt behavior and packet requirements; reimplement in RSPSi |
| Environment Exporter independent lighting, object placement, renderer seam, CPU/GPU fallback, glTF workflow | Adopt as comparison fixtures and future adapter design |
| RuneLite/TSPS/Displee/OpenRS cache loaders | Do not add as production readers; FileStore remains the single cache boundary |
| TSPS browser/WebGL scene graph and mutable edit runtime | Do not copy; use only as packet/shader evidence |
| Environment Exporter GPL source and runtime dependencies | Do not copy or bundle; reimplement only after license review |
| Water/particles/animated materials | Reserve capability fields; defer full rendering until static parity passes |
| GPU compute shaders | Optional acceleration after CPU/GPU packet parity |

## New and revised acceptance gates

The rendering foundation is not complete until fixtures cover:

1. windows crossing multiple 64×64 regions with border heights;
2. radius-five underlay blending across region boundaries;
3. all 13 terrain shapes and four rotations, including hidden faces;
4. explicit/generated height provenance and shared-edge stitching;
5. bridge replicas, linked-below tiles, force-visible flags, and plane-cull data;
6. object category/shape/orientation/footprint/model-transform ordering;
7. alpha, priority, texture, UV, interaction, and contour metadata;
8. CPU and GPU packet fingerprints that remain identical under exposure changes;
9. stale asynchronous load rejection and instance/ordinary-scene separation;
10. RuneLite/TSPS/Environment Exporter lighting comparisons with documented
    differences; and
11. deterministic headless scene export for every parity fixture.

These gates strengthen the existing RuneLite and TSPS documents; they do not
replace the revision-240 foundation gate or make any external research repo a
runtime dependency.

## Related references

- [`RUNELITE_SCENE_REFERENCE.md`](RUNELITE_SCENE_REFERENCE.md)
- [`RUNELITE_SCENE_RENDERING_REFERENCE.md`](RUNELITE_SCENE_RENDERING_REFERENCE.md)
- [`RUNELITE_GPU_PIPELINE.md`](RUNELITE_GPU_PIPELINE.md)
- [`RUNELITE_DEVTOOLS_OVERLAYS.md`](RUNELITE_DEVTOOLS_OVERLAYS.md)
- [`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md)
- [`OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md`](OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md)
- [`OSRS_SCENE_PIPELINE.md`](OSRS_SCENE_PIPELINE.md)
