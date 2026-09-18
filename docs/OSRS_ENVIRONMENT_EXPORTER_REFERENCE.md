# OSRS Environment Exporter Reference

This document records the review of
[ConnorDY/OSRS-Environment-Exporter](https://github.com/ConnorDY/OSRS-Environment-Exporter)
for OpenRune Studio. It is a focused scene/render/export reference, not a
proposal to adopt the repository as a dependency or application base.

Its findings are compared with RuneLite and TSPS in
[`SCENE_RENDERING_CROSS_REFERENCE.md`](SCENE_RENDERING_CROSS_REFERENCE.md).

## Captured source

- Local checkout: `../RSPSi-resources/OSRS-Environment-Exporter`
- Pinned commit: `61d461d3bfd8217a470518924415d7de1b074b9c`
- Build: `./gradlew --no-daemon test --console=plain` — passed
- Project: Kotlin/JVM application, Java 19 target, version `2.4.2`
- Declared project license: GPL-3.0
- Important source notices: selected scene/renderer files retain RuneLite BSD
  notices; the repository also ships OpenRS and RuneLite license texts

The checkout is intentionally outside the RSPSi tree. The GPL-3.0 boundary
means we may study behavior and reimplement compatible concepts, but we must
not copy its source into RSPSi, add it as a runtime dependency, or bundle its
assets without a separate distribution review.

## What the source contributes

### Scene construction and lighting reference

`src/main/kotlin/models/scene/SceneRegionBuilder.kt` is the most valuable
piece. It is a concrete, RuneLite-derived example of the transition from a
64×64 cache region to renderable scene data:

- four planes and region-local coordinates are preserved explicitly;
- neighboring regions contribute to underlay HSL blending with a five-tile
  blend window;
- terrain height differences and tile settings produce directional tile
  brightness using the OSRS-style ambient/contrast/bias constants;
- overlay path and rotation select flat paint versus shaped tile model data;
- overlay textures and the `-1`/`-2` HSL sentinel cases are kept distinct;
- location orientation rotates object footprints before model placement;
- location types select wall, wall-decoration, ground, ground-decoration, or
  game-object categories; and
- contouring, transforms, animation, and normal merging are applied while
  resolving cache objects into render entities.

`SceneRegion.applyLighting` then demonstrates that normal merging is a
scene-context operation: neighboring tiles and object footprints contribute
to model lighting after placement. This supports keeping lighting inputs in
RSPSi's neutral `RenderScene` while leaving final shading to a renderer.

This does not replace the current RSPSi scene pipeline. It gives us another
focused comparison for material blending, directional brightness, model
anchors, footprints, contouring, and normal-merging behavior.

### Tile and renderer boundary

`src/main/kotlin/models/scene/SceneTile.kt` keeps the same useful semantic
separation found in RuneLite: tile paint, tile model, wall, wall decoration,
ground decoration, ground object, game-object collection, and source/cache
tile data are separate fields. `SceneRegion.kt` also makes lower-plane tile
creation and object-layer insertion explicit.

`src/main/kotlin/controllers/worldRenderer/SceneUploader.kt` is a concrete
renderer-consumer reference. Its upload path processes terrain and object
categories in a stable order, carries model colors/textures/transforms, and
packs face alpha and priority alongside vertex data. The order is useful for
parity fixtures and exporter output, but it is not permission to make upload
order the authored world model.

`PriorityRenderer.kt`, `AbstractPriorityRenderer.kt`,
`CPUNonPriorityRenderer.kt`, and `GLSLPriorityRenderer.kt` show a valuable
backend seam: scene upload and renderer strategy are separate. CPU and GLSL
paths can share scene/model inputs while differing in face ordering, buffer
management, and draw execution. This reinforces the RSPSi renderer contract:
the scene projection supplies geometry and material inputs; a JavaFX, Dear
ImGui, OpenGL, or future GPU backend owns presentation resources.

The README's configuration also identifies real diagnostic dimensions worth
testing later: alpha modes (`BLEND`, `CLIP`, `HASH`, ordered dither, hex dots,
ignore), MSAA, and CPU versus GLSL priority rendering. These should become
renderer characterization cases, not settings added to editor-core.

### Export and headless workflow

`src/main/kotlin/controllers/worldRenderer/SceneExporter.kt` and the neutral
`MeshFormatExporter` boundary demonstrate a future export plugin shape:

- consume a derived scene, not the mutable editor document;
- preserve model geometry, face colors, textures, alpha, priority, and UVs;
- emit a format-specific representation such as glTF; and
- keep texture/material handling behind the exporter boundary.

`GlTFExporter.kt` provides a concrete glTF/data-buffer/material/texture
workflow to use as a behavior and output-shape reference. RSPSi should first
define a versioned, deterministic neutral scene-export fixture and then write
an RSPSi-owned exporter. Do not copy the exporter implementation.

`CliExporter.kt` is also a useful product idea: a headless command can load a
cache, build a bounded scene, export it, and exit without starting the editor
shell. That would support CI parity checks, bug reports, model/scene capture,
and future Blender workflows while sharing the same cache adapter and scene
builder as Studio.

## What we are deliberately not adopting

- `com.displee:rs-cache-library` as a second OSRS production cache backend;
  OpenRune FileStore remains the production OSRS adapter.
- The Swing application shell or its UI architecture; JavaFX remains the
  current frontend and Dear ImGui remains a future frontend adapter.
- The repository's OpenGL/LWJGL renderer as RSPSi's renderer; only the
  backend split and measurable alpha/priority concerns are references.
- A mutable scene model parallel to `WorldDocument`, `EditorSession`, and
  `RenderScene`.
- GPL-covered source, generated outputs, cache dumps, or third-party assets in
  the RSPSi product without an explicit license/distribution decision.

## RSPSi actions and ideas

The review adds these bounded possibilities to the project backlog:

1. Add a renderer diagnostic mode for alpha, face priority, texture/UV,
   contouring, normal-merging, and object-layer ordering.
2. Define a deterministic scene-export snapshot and fingerprint format.
3. Build a first-party scene/model export plugin, with glTF as the first
   candidate format and OBJ/mesh comparison as a debugging fallback.
4. Add a headless `export-scene`/`verify-scene` command for CI and external
   renderer comparison.
5. Add lighting/material fixtures that compare terrain brightness, underlay
   blending, overlay texture handling, and model normal merging against
   independent references.
6. Keep export and renderer backends as plugins/adapters consuming immutable
   scene projections; they must not own editing, history, or cache semantics.

These are post-foundation work. The current foundation gate remains focused on
authored terrain/location semantics, neutral scene projections, collision,
plugin ownership, and frontend independence.

## Evidence limits

The repository's test suite passing verifies that its pinned Kotlin project
builds and tests successfully. It does not establish current-OSRS parity for
RSPSi. Scene lighting, model placement, alpha/priority behavior, glTF output,
and headless export still require RSPSi-owned fixtures and comparison reports.
The source is best treated as a focused donor/reference alongside RuneLite,
TSPS, and OpenRune evidence—not as an independent authority for every
behavior it inherits.
