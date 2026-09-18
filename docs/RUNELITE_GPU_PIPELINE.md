# RuneLite GPU Scene Pipeline

This document records how RuneLite moves an OSRS software-style scene into a
GPU renderer and how OpenRune Studio will adapt the idea without coupling
neutral scene semantics to OpenGL or LWJGL.

TSPS supplies the most explicit revision-240 WebGL packet and shader evidence;
the OSRS Environment Exporter supplies an independent OpenGL compute/CPU
fallback comparison. Their adjudicated additions are collected in
[`SCENE_RENDERING_CROSS_REFERENCE.md`](SCENE_RENDERING_CROSS_REFERENCE.md).

## The important clarification

The GPU does not receive a pre-rendered image of the whole map. The CPU still
decodes the cache, derives terrain and object geometry, and builds packets.
Static geometry and material data are uploaded to GPU buffers. Each frame the
GPU transforms, clips, rasterizes, textures, fogs, and composites those
packets using the current camera and presentation uniforms.

```text
FileStore bytes
  → RSPSi authored/derived scene
  → backend-neutral render packets
  → OpenGL buffer/texture upload
  → vertex transform and rasterization every frame
  → frontend composition and diagnostics
```

Camera movement should update uniforms and visibility ranges without rebuilding
unchanged static geometry. A terrain edit or object edit invalidates only the
affected packet zones and dependent derived data.

## RuneLite reference path

The primary source is:

`RuneLite-melxin/runelite-client/src/main/java/net/runelite/client/plugins/gpu/`

Relevant pieces include `GpuPlugin`, `SceneUploader`, `Zone`, texture-array
management, face-priority sorting, and the GLSL shader templates. RuneLite
installs this path as a client plugin implementing `DrawCallbacks`; it does not
change cache or scene semantics.

RSPSi follows the same separation:

- neutral scene contracts live under `com.rspsi.editor.render`;
- OpenGL/LWJGL code will live in a future backend/frontend module;
- the GPU adapter consumes packets and never opens FileStore;
- JavaFX, ImGui, and OpenGL share `RenderScene`/`EditorSceneSnapshot` data;
- a missing GPU backend cannot change decoding or scene fingerprints.

## Upload units and order

RuneLite subdivides the extended scene into 8×8 upload zones. Each zone keeps
opaque and alpha-capable ranges and records level/roof metadata. The uploader
processes a tile in semantic order:

1. flat terrain paint;
2. shaped terrain model;
3. wall renderables;
4. decorative renderables;
5. ground/floor decoration;
6. game objects once at their scene-minimum anchor; and
7. linked bridge content recursively.

RSPSi's future `GpuScenePacket` is deliberately zone-friendly but does not
force the editor to use 8×8 as its dirty-region size. The packet contains
stable tile/object identity so a backend can batch by zone, material, level, or
transparency without changing semantic order.

## RuneLite vertex payload

The observed GPU path packs a compact vertex payload containing:

- short world-relative position components;
- packed HSL/color plus alpha and face-bias information; and
- texture ID plus UV coordinates.

Terrain paint emits the four actual corner heights/colors as two triangles.
Shaped terrain emits generated vertices, triangle indices, per-face colors, and
texture IDs. Models are transformed from client model coordinates with the
orientation sine/cosine tables, translated into scene space, and emitted with
face color, transparency, priority, and texture mapping.

RSPSi's neutral packets retain full precision and explicit meaning. A future
OpenGL adapter may quantize to shorts or pack integers after it proves bounds
and parity. Quantization must never happen in the authored model or neutral
scene snapshot.

## Model texture mapping

RuneLite reconstructs model face UVs from texture-triangle geometry. Textured
faces reference the model's texture triangles; non-textured faces use a stable
fallback basis. The GPU packet must therefore distinguish:

- texture ID absent versus texture ID zero;
- textured versus untextured faces;
- texture triangle coordinates;
- per-face alpha and priority; and
- material/texture animation metadata.

Dropping texture triangles and assigning one UV layout to every triangle is
not acceptable for model parity.

## Shader responsibilities

The reference vertex shader:

- consumes position, packed color/HSL, and texture information;
- applies entity and world projection matrices;
- decodes alpha/bias and HSL;
- applies animated texture coordinates where enabled; and
- computes draw-distance/fog coordinates.

The fragment shader:

- samples a texture array for textured faces;
- applies texture-light mode and brightness/gamma policy;
- converts packed HSL when smooth-banding mode requires it;
- discards alpha-tested fragments at the configured mip level;
- applies optional colorblind transforms; and
- composites fog.

The faithful scene packet must be independent of editor exposure. Exposure,
brightness, colorblind preview, and fog preferences are frontend/presentation
uniforms, not authored colors.

## OpenGL target and boundary

The first concrete backend target is desktop OpenGL 3.3, matching RuneLite's
baseline shader capability. Optional OpenGL 4.5 features such as clip-control
may be enabled when available but cannot be required for correctness.

The neutral boundary is intentionally small:

```java
interface GpuSceneUploader {
    void upload(GpuScenePacket packet);
    void invalidate(Set<SceneTileKey> tiles);
    void release();
}
```

An OpenGL implementation owns VAOs, VBOs, texture arrays, shader programs,
framebuffers, and native handles. It must not leak those types into
`WorldDocument`, `EditorSession`, plugin APIs, or scene packets.

## Dynamic content and invalidation

Static terrain and static object geometry can remain resident until their
source tiles or definitions change. Dynamic objects, animated models,
particles, projectiles, and runtime entities require separate capability
packets and are deferred from the first static-map fidelity gate.

The neutral invalidation graph is:

```text
authored tile edit
  → affected terrain/object packet
  → neighboring lighting/blending packets when required
  → collision/selection/bridge projections
  → GPU zone invalidation
```

A camera move does not dirty authored packets. A presentation exposure change
does not change packet fingerprints. A definition/cache identity change
invalidates the session-scoped asset repository and requires a new scene
snapshot.

## GPU acceptance gates

Before implementing the OpenGL adapter, RSPSi must have deterministic tests
for:

- terrain packet vertices, colors, shapes, rotations, textures, and UVs;
- object transformed vertices, face indices, normals, alpha, and priorities;
- zone membership and stable layer ordering;
- opaque/alpha separation and texture identity;
- lighting/exposure separation; and
- identical packets from JavaFX and ImGui sessions.

The GPU backend is then a consumer of verified packets, not a second place
where OSRS scene interpretation is implemented.
