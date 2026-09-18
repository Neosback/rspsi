# RSPSi Rendering System Audit — 2026-09-18

This is the current implementation audit for the renderer. It records what
RSPSi actually owns, what is verified, and what still prevents a faithful
static revision-240 scene from reaching a GPU backend.

## Executive result

The architecture is pointed in the right direction, but the renderer is not
complete yet. The neutral scene model, terrain topology, lighting baseline,
collision projections, bridge semantics, and revision-aware cache boundary
exist. The current implementation derives terrain packets, static model
packets, per-face model UVs, tile draw layers, bridge projections, and
explicit definition-backed occluder inputs. The remaining production path is
client-parity edge cases and upload of these packets to a real 3D backend.

The first implementation correction is now in place:

- terrain underlay blending uses the client `x - 4 .. x + 5` window;
- source HSL values remain in the 0..255 definition domain during blending;
- packed HSL uses the client saturation reduction and 6/3/7-bit layout;
- minimap and scene appearance derivation share the same named color utility.

Per-corner light is now applied while building `TerrainRenderPacket`; the
parallel `TerrainLight` map remains as diagnostics and source evidence.

## What the renderer is today

```text
FileStore / OSRS adapter
    -> revision-aware WorldDocument
    -> RenderSceneBuilder
        -> TerrainMeshBuilder       verified topology source
        -> TerrainAppearanceBuilder corrected source HSL inputs
        -> TerrainLighting           verified interior normal baseline
        -> collision and object projections
    -> RenderScene                  neutral scene snapshot
        -> RenderTextureResource   referenced texture metadata/pixels
    -> GpuUploadPlan                backend-neutral world-space upload plan
        -> SoftwareSceneRenderer    deterministic CPU pixel reference
        -> EmbeddedOpenGlViewport   embedded LWJGL/OpenGL 3.3 consumer
    -> CanonicalSceneViewport       JavaFX top-down compatibility preview
```

`CanonicalSceneViewport` is not a final 3D renderer. It is a compatibility
preview and should be renamed to `TopDownScenePreview` during the staged
naming migration. The neutral model must remain independent of JavaFX,
OpenGL, Dear ImGui, and cache-library types.

## Engine/API boundary now in use

The first strangler-migration boundary is implemented without adding Gradle
modules. `SceneResolver`/`OsrsSceneResolver` own the transition from the
canonical `WorldDocument` to the existing immutable `RenderScene`, while
`RenderPlanner`/`OsrsRenderPlanner` own the transition from that resolved scene
to a backend-neutral `GpuScenePacket`. `RenderScene` implements the
self-documenting `ResolvedScene` contract so existing callers can migrate
without creating a second scene representation.

Renderer settings now follow one typed path:

```text
RenderSettingKeys / SettingsRegistry
    -> SettingsSnapshot
    -> RenderConfigCompiler
    -> RenderConfig
    -> SceneVisibilityPolicy + category filtering
    -> GpuScenePacket
```

The default is `VANILLA_COMPATIBILITY`. Legacy JavaFX `Options` values cross
the boundary only through `LegacyRenderSettingsAdapter`; new renderer code
must not read them directly. During the migration, the adapter also observes
the existing visibility/diagnostic properties and updates a session-scoped
`SettingsStore`. The OpenGL viewport retains the unfiltered semantic packet
and reapplies the immutable `RenderConfig` projection on each change, so
turning a category off and back on does not lose geometry. The adapter remains
temporary and will be replaced when active settings panels bind directly to
the registry.

## Completed and verified

| Area | Current evidence | Status |
|---|---|---|
| 64x64 regions and four planes | `WorldDocument`, region/window builders | verified |
| 13 terrain shapes × 4 rotations | `TerrainMeshBuilder` golden/topology tests | verified |
| bridge/effective-plane separation | scene and collision tests | verified |
| instance/chunk transform semantics | fixture/differential tests | verified |
| collision and route projections | canonical builder tests | verified |
| OSRS directional normal baseline | `TerrainLighting` and lighting tests | verified for interior tiles |
| radius-five underlay blend window | shared `OsrsTerrainColorMath` plus region/window tests and five-tile derived context border | corrected across loaded window; absent outer context remains explicit neutral data |
| packed OSRS HSL domains | shared utility plus scene/minimap tests | corrected |
| complete terrain render packets | `TerrainPacketBuilder` combines topology, per-corner HSL, client midpoint colors, light, texture metadata, hidden-overlay sentinels, and scene-shape semantics; textured overlays retain separate render and minimap/fallback HSL; region and window scenes publish packets | partial: padded/exact-shape fixture parity remains |
| complete model packets | `ModelPacketBuilder` selects typed model parts, expands wall corners, diagonal centrepieces, and all wall-decoration variants, applies client-order mirroring/45-degree/quarter-turn transforms, footprint-centred placement, explicit terrain elevation, scale/offset/recolor/retexture/contour, applies cycle-selected decoded legacy animation frames through vertex skin groups, computes client accumulated normals, preserves four OSRS texture mapping modes, emits per-face UVs, uses client light scalars for textured faces and HSL blending for non-textured faces, expands flat-face color sentinels at upload, preserves raw OpenRune `ModelType.faceZOffsets` as per-face depth bias, and emits lit model packets; window scenes derive models from the same padded world context as terrain and project anchors back to world coordinates | partial: provider-specific neighboring-tile merge traversal/occluded-face hiding, advanced model channels, and fixture coverage remain |
| scene-tile/GPU upload packets | `GpuScenePacketBuilder` assembles stable world-tile order, terrain/model packets, explicit terrain/object layer order, opaque/transparent model partitions, bridge/effective-plane visibility, raw tile flags, world/region/chunk identity, explicit definition-backed occluders, model-clipped wall occluder inputs, texture resources, and deterministic fingerprints; `GpuUploadPlanBuilder` now flattens those packets into world-space vertices, indices, material references, per-command face bias, deterministically merged contiguous occluder planes, and ordered draw commands consumed by both native and reference backends; `GpuPriority` applies the shared client-like priority-band and face-bias depth policy | partial: revision-specific plane-cull/force-visible interpretation, exact mask activation/traversal, and advanced material/shadow channels remain |
| camera occlusion | `SceneOcclusionResolver` implements client camera-relative projection slopes for type-1 X-wall, type-2 Z-wall, and type-4 horizontal occluder tests; `OcclusionPlanFilter` applies the same complete-triangle filter to native upload plans, while the software renderer evaluates the same rule before clipping | implemented as a conservative first pass; exact client visible-tile activation, region-wide mask merging, and partial-triangle raster occlusion remain |
| wall/object light occlusion | `TerrainShadowMap` and weighted wall contributions for types 0/1/3 plus client-style `XZRadius / 4` terrain-light occlusion for definition-backed clipped types 10/11, with the no-model fallback of 15; neutral appearance fields mapped from legacy/OpenRune definitions | partial: revision-specific type-2 occluder traversal |
| occluder derivation | explicit `ObjectAppearanceView.occludes()` objects become one camera-independent tile occluder input per object, with multipart model bounds aggregated; `modelClipped` wall shapes 0/2 also emit RuneLite-compatible type-1/type-2 wall-edge occluder inputs from terrain edge heights | partial: region-wide mask merging/traversal and broader roof occluder grouping remain |
| packet-backed semantic scene adapter | `CanonicalSceneViewport` remains available as a neutral scene/query and test adapter; it is no longer mounted as the Map Editor's visible renderer, and JavaFX Canvas is not used as a production scene fallback | packet-backed top-down reference remains available for diagnostics; production 3D is the embedded OpenGL path |
| texture resource handoff | `RenderTextureResource` retains referenced texture definitions, client-color pixels when the provider supplies them, and explicit unavailable/invalid diagnostics; `RenderScene`, `RenderWindowScene`, and `GpuScenePacket` publish the same session-scoped resources; the native GL 3.3 backend uploads available resources into one `GL_TEXTURE_2D_ARRAY` with per-layer scale, keeps texture RGB opaque, and discards the client RGB-zero transparent texel in GLSL, while model face transparency remains separate | verified for nearest sampling, client-cycle animation, texture-layer upload, and masked zero texels; filtering and mip policy remain explicit presentation work |
| deterministic software reference renderer | `SoftwareSceneRenderer` consumes only `GpuUploadPlan`, performs perspective projection, near/far clipping, depth testing, RuneLite-compatible screen-space packed-HSL smooth banding for vanilla presentation, nearest texture sampling, textured-face lightness, client-direction texture animation, priority-band depth bias, alpha compositing, and stable pixel fingerprints; it is independent of JavaFX/native graphics | verified as a static and animated-texture baseline; integer depth-bucket fixture parity, picking, shadows, and native GPU parity remain |
| front-face visibility | `BackfacePolicy` defines the accepted RuneScape packet winding; the software rasterizer rejects degenerate and reverse-facing projected triangles, while the embedded OpenGL backend enables back-face culling with the equivalent clockwise front-face rule | implemented in both backends; profile-specific two-sided debug presentation remains a later explicit setting |
| scene visibility projection | `SceneVisibilityPolicy` filters immutable packets by authored plane or effective bridge plane and independently handles bridge-upper/roof presentation; editor/all-planes remains the default | implemented as a shared packet projection; exact client roof traversal and occluder culling remain |
| typed render configuration | `SettingKey`, `SettingSpec`, `SettingsRegistry`, `SettingsSnapshot`, `SettingsStore`, `RenderConfigCompiler`, and `RenderConfig` provide stable renderer IDs, defaults, scopes, invalidation metadata, and one compiled configuration; `RenderConfig.apply` filters terrain and object categories before upload | implemented; registry-backed settings UI and persistence remain |
| presentation exposure | `RenderConfig.presentation()` produces frontend-only brightness/exposure, wireframe, vanilla smooth-banding, and optional scene-edge fog values; the software renderer and embedded OpenGL shader apply the same presentation math after scene color resolution, while the native OpenGL pass consumes wireframe and fog explicitly without changing authored packets or fingerprints | implemented and covered by presentation/config tests; post-process color grading remains deferred |
| resolver/planner API boundary | `SceneResolver`/`OsrsSceneResolver` and `RenderPlanner`/`OsrsRenderPlanner` wrap the existing scene and packet builders without duplicating world state | implemented as a migration seam; runtime state, draw-service, and picking APIs remain later slices |
| immutable frame/query boundary | `RenderFrame` captures the upload plan, compiled render configuration, camera, and client cycle; `SceneQuery`/`PacketSceneQuery` provide read-only world-addressed tile/model/flag inspection over the same packet; `GpuPlanPicker` ray-tests those same triangles and returns tile/object identity for the embedded surface | implemented as a correctness fallback; native ID-buffer picking and command-backed selection integration remain |
| production 3D backend | `EmbeddedOpenGlViewport` + `OpenGlSceneRenderer` consume the neutral upload plan for opened OSRS projects; the context requests OpenGL 3.3 core without requiring MSAA, binds a VAO before shader validation, uses GLSL 330 and a core texture array, consumes the host's explicit client cycle, applies raw face bias with one `/128` scale in the shader, and uses the shared fog presentation path | partial: native surface and live cycle refresh are wired, but full client parity, picking, and cross-platform runtime fixtures remain; context failure now reports an actionable error instead of mounting JavaFX Canvas |

## Required static rendering pipeline

```text
Cache bytes
  -> revision-aware definitions and map/location decoders
  -> authored WorldDocument
  -> padded world/window materialization
  -> terrain topology and raw surface appearance
  -> per-corner terrain lighting + object/wall occlusion
  -> object model selection and transforms
  -> bridge, roof, occluder, collision, and picking projections
  -> immutable SceneTileRenderSnapshot
  -> deterministic GpuUploadPlan
      -> software reference pixels and fingerprints
      -> native GPU buffers/shaders
```

The embedded LWJGL/OpenGL viewport and future Dear ImGui frontend consume the
same neutral scene and command state. The JavaFX canonical adapter may remain
available for headless/reference diagnostics, but it is not a production scene
renderer and must not be mounted as a fallback. Frontends must not
independently reconstruct terrain colors, object placement, lighting, or cache
definitions.

## Missing renderer work, in order

### 0. Reference rasterization baseline

`SoftwareSceneRenderer` is now the first executable consumer of the upload
plan. It establishes a deterministic static-scene oracle for camera
projection, camera-plane clipping, perspective-correct depth/material
interpolation, packed-HSL palette conversion, nearest texture sampling,
textured-face lightness, opaque depth writes, and sorted alpha composition.
This is not presented as a complete RuneLite rasterizer; it gives us pixel
regression coverage before platform-specific GPU code exists. A native backend
must consume the same plan and compare against this oracle on fixtures, with
documented exceptions for GPU precision and client-specific priority rules.

### 1. Terrain packet production — current implementation gate

`TerrainPacketBuilder` now combines terrain topology, four shared-corner
heights, four blended underlay HSL corner values, client packed-HSL midpoint
mixing for shaped vertices, raw overlay HSL inputs, four corner light values,
shape and rotation, texture IDs, texture-average inputs, hidden-overlay
classification, absent-underlay sentinels, material classification, and
deterministic UV coordinates.

It preserves raw HSL for diagnostics while writing light-adjusted packed
values consumed by a renderer. It uses the same 13-shape topology data already
verified by `TerrainMeshBuilder`. It is wired into definition-aware region and
window snapshots, and the scene fingerprint includes the packets. Remaining
work is padded external context, exact shaped-intermediate parity, and richer
raw-HSL diagnostics.

### 2. Window-level derivation

`RenderWindowSceneBuilder` now materializes loaded regions into one
world-addressed derived document and derives appearance, lighting, and terrain
packets from that shared context. It adds a five-tile derived context border,
shifts derived object coordinates with the border, and projects only requested
visible tiles into the world snapshot. Missing regions remain explicit neutral
holes rather than repeated edge samples.

### 3. Lighting occlusion

Neutral object appearance views now carry the source fields required by the
lighting/transform boundary (`castsShadow`, `occludes`, `mergeNormals`,
`nonFlatShading`, contour policy, ambient, contrast, displacement, clipping,
and rotation). `TerrainShadowMap` applies the verified client wall corner
strengths for wall types 0, 1, and 3 before terrain packet colors are
finalized. Model-radius terrain-light occlusion for definition-backed clipped
types 10 and 11 is also derived here, separately from renderer occluders and
collision. OpenRune FileStore does not currently expose a separate
`castsShadow` field, so the OSRS default shadow policy is recorded at the
adapter boundary. Revision-specific type-2 occluder semantics remain open.

### 4. Model packet production

`ModelPacketBuilder` now builds packets from `ModelGeometryView` and object
definitions with the source ordering for model selection, mirroring,
quarter-turn orientation, recolor/retexture, scale, offset, decor
displacement, full/partial/clamped/bridge contouring, normals, ambient/contrast, and per-corner model
lightness. Vertex normals retain the client’s accumulated components and
magnitudes instead of being normalized twice, so smooth multi-face models use
the same light denominator as RuneLite/TSPS. FileStore texture metadata is
retained at the neutral boundary and
the packet carries per-face UVs for simple, cylindrical, planar, and spherical
OSRS mappings, including seam correction. It is published by both region and
world-window scene snapshots. Packets now use footprint-centred x/z placement
and carry the sampled terrain placement height, allowing world-space vertex
matching and correct occluder elevation. Contour sampling uses the same
tile-owned four-corner height surface as terrain and no longer double-adds
the footprint centre. Cross-object `mergeNormals` is
implemented when the selected definition adapter exposes that flag; the
OpenRune definition type currently does not expose the TSPS opcode-22 field,
so that adapter remains explicitly conservative. Remaining parity work is
exact diagonal decoration variants and explicit particle or advanced
model-channel diagnostics. The provider now decodes index-0 legacy animation
frames and index-1 skeletons lazily. Scene builders accept an explicit client
cycle, select frames using client-duration and loop-back semantics, and apply
vertex transforms plus legacy type-5 face-alpha groups through neutral skin
channels. The embedded OpenGL frontend schedules cycle-driven derived-scene
refreshes; skeletal-animation IDs and particle/billboard channels remain
future work.

### 5. Scene tile and GPU packet production

`GpuScenePacketBuilder` now populates an immutable tile list in stable world
order, carries terrain and model packets, records explicit terrain/wall/decor/
ground draw layers, partitions model layers into opaque and transparent
submissions from retained face alpha/render type, and exposes face-level
triangle partitions to uploaders. It preserves bridge/effective-plane
visibility, carries raw authored tile flags plus complete world/region/chunk
identity, derives explicit definition-backed occluder inputs, carries
referenced texture resources, and emits a deterministic packet fingerprint.
`GpuUploadPlanBuilder` converts this into world-space vertex/index buffers and
ordered draw commands without introducing native GPU handles. Each uploaded
vertex explicitly identifies whether its encoded color is packed Jagex HSL or
the textured-face lightness scalar, preventing a backend from applying the
wrong color path. Model texture-triangle projection/animation metadata is
also preserved in the plan instead of being lost when faces are expanded.
`RsFaceOrderPlanner` now owns the client priority-group interleave for alpha
submissions, including the legacy 10/11 stream checkpoints and same-checkpoint
handoff. `OpenGlSceneRenderer` executes it without exposing OpenGL to the
neutral layer. The native backend uploads available textures as one
OpenGL 3.3 `GL_TEXTURE_2D_ARRAY` with per-resource layer/scale metadata.
Remaining work is integer depth-bucket fixture validation, plane-cull and
force-visible flags, exact client mask activation/traversal, native ID-buffer
selection, fog, and shadow passes. Raw per-face depth bias is now
retained from the OpenRune model definition through both reference and native
draw paths and is scaled once at the GLSL boundary; real-client image fixtures
are still required to validate the exact sign and interaction with every
revision-specific depth mode.

### 6. Occluders, headless verification, and backend

Complete client-compatible wall/decor grouping, mask activation, and type-2
occluder traversal,
add a headless scene fingerprint/export command, and extend the embedded
OpenGL backend against those parity fixtures. The OpenGL code is isolated to
the `Editor` frontend and supports the desktop packaging targets through LWJGL
natives. Live client-cycle scene refresh, texture animation, the neutral
priority planner, and raw face-bias propagation are now connected;
input/picking, shadow, cross-platform packaging, exact face-bias validation,
and native-vs-software image fixtures remain. The JavaFX top-down adapter is
reference/test infrastructure only; it is not a production fallback.

## Additional scene-engine requirements confirmed by the latest reference review

The latest RuneLite/OpenRS2/legacy-client review does not change the source
adjudication, but it makes several requirements explicit for the remaining
renderer work:

- Cache terrain remains 64x64x4 while a normal derived scene is 104x104 with
  105x105 shared height samples. The two representations must never be
  collapsed into one generic grid.
- Missing height opcodes have provenance. Plane-zero generated noise and
  higher-plane `underlying height - 240` are not equivalent to an explicit
  serialized height, even when their rendered values match.
- Terrain, locations, runtime scene deltas, collision, line-of-sight,
  visual occlusion, shadow contribution, and geometry are separate products.
  A renderer packet may reference each projection, but none may be inferred
  from a generic `blocked` boolean.
- Object placement is a typed 0..22 scene operation, not a slot index. Model
  selection, orientation-aware footprint, wall-decoration displacement,
  contouring, recolor/retexture, animation, and varbit/varp transforms must
  remain visible in the source/derived boundary.
- Dynamic instances are a 4x13x13 chunk template with coordinate-aware
  rotation. Repeated source chunks must retain distinct destination instance
  identities, and copying a chunk must transform object orientation,
  footprint, overlay rotation, wall decoration, and collision together.
- Static cache locations and runtime spawns/despawns are different layers.
  Runtime playback must be able to add a scene delta without mutating the
  authored cache document.
- The GPU does not receive a pre-rendered map image. CPU code still decodes,
  derives, sorts, and uploads scene packets; the GPU performs transformation,
  clipping, rasterization, depth/priority handling, texture sampling, fog,
  and presentation.
- OpenRune hosting currently publishes `dev.or2:filestore:3.0.2` and
  `dev.or2:tools:3.0.2`; RSPSi pins that release, uses FileStore through the
  neutral cache adapter, and uses the tools artifact for the explicit writable
  `CacheDelegate` output path. Incremental pack tasks remain a source-first
  build-phase adapter, not a second editor state system.

The implementation consequence is that a future `GpuUploadPacket` must grow
from a tile list into an explicit upload plan: shared terrain/model buffers,
instance metadata, texture resources, opaque/alpha/priority ranges, occluder
inputs, picking data, and optional height-map/contouring data. The packet must
remain immutable and fingerprinted before any frontend-specific GPU handles
are allocated.

## Naming cleanup

Use these canonical names in new code and migrate old names with deprecated
compatibility wrappers. Do not mass-rename before the packet boundary is
implemented; that would create mechanical churn while the pipeline is still
changing.

| Current | Canonical | Reason |
|---|---|---|
| `RenderScene` | `SceneSnapshot` | immutable derived state |
| `RenderSceneBuilder` | `SceneSnapshotBuilder` | identifies the output |
| `RenderWindowScene` | `WorldSceneSnapshot` | world-window state |
| `RenderWindowSceneBuilder` | `WorldSceneSnapshotBuilder` | matching builder |
| `SceneWindow` | `SceneRenderContext` | context, not a UI window |
| `RenderSceneFingerprint` | `SceneSnapshotFingerprint` | explicit fingerprint target |
| `RenderObject` | `SceneObjectProjection` | derived, not authored state |
| `WorldRenderObject` | `WorldSceneObject` | world-addressed object |
| `TerrainMesh` | `TerrainTopology` | geometry shape, not backend mesh |
| `TerrainMeshBuilder` | `TerrainTopologyBuilder` | explicit topology source |
| `TerrainAppearance` | `TerrainSurfaceAppearance` | surface meaning |
| `TerrainMaterial` | `TerrainSurfaceMaterial` | avoids generic material ambiguity |
| `TerrainLight` | `TerrainCornerLighting` | four-corner meaning |
| `TerrainLighting` | `TerrainLightingBuilder` | current type derives lighting |
| `SceneTileSnapshot` | `SceneTileRenderSnapshot` | derived tile snapshot |
| `GpuScenePacket` | `GpuUploadPacket` | backend upload boundary |
| `GpuSceneUploader` | `GpuUploadTarget` | target abstraction |
| `SceneRenderer` | `SceneRenderBackend` | rendering implementation boundary |
| `CanonicalSceneViewport` | `TopDownScenePreview` | current JavaFX behavior |
| `RenderChanges` | `SceneInvalidation` | invalidated derived state |
| `PickResult` | `ScenePickResult` | scene-specific pick result |
| `RenderTextureResource` | `SceneTextureResource` | explicit scene resource name; migrate only with the broader packet rename |

## Source adjudication

- RuneLite-melxin is the client-semantic reference for scene construction,
  tile layers, lighting, object placement, and GPU-facing separation.
- TSPS is the independent revision-240 scene-packet and transform reference,
  especially for shaped terrain, underlay blending, model placement, and
  renderer seams.
- OSRS-Environment-Exporter is an independent lighting/export and CPU/GPU
  comparison source. Its display palette and axis conventions are evidence,
  not runtime dependencies.
- OpenRune FileStore remains the cache/definition source. It does not own the
  neutral scene graph or frontend renderer.

When sources disagree, preserve client/revision semantics in neutral packets,
document the disagreement as a fixture, and expose brightness/exposure only
at the frontend.

## Acceptance gate

The renderer foundation is complete only when a revision-240 fixture can be
opened read-only, converted into a deterministic scene snapshot, and produce
the same terrain packets, object packets, bridge/occluder projections,
collision projections, and fingerprint across JavaFX, headless verification,
and the OpenGL backend. A visually plausible top-down preview is not enough.

Dynamic entities, particles, projectiles, advanced water, cutscene playback,
and broad multi-revision support remain deferred until this static gate passes.
