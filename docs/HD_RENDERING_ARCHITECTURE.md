# HD Rendering Architecture

## Purpose

OpenRune Studio should be able to adopt 117HD/RLHD rendering behavior without turning the editor into a fork of RuneLite or creating a second scene system.

The target is one authoritative authored/resolved scene pipeline with multiple presentation profiles:

```text
WorldDocument / WorldRegionWindow
            |
            v
     OSRS semantic resolution
            |
            v
 renderer-neutral scene packets
            |
            v
 shading/material compilation
            |
            v
 canonical 8x8 render zones
            |
      +-----+-------------------+
      |                         |
      v                         v
 vanilla presentation      HD presentation
      |                         |
      +-----------+-------------+
                  |
                  v
          native OpenGL backend
```

Vanilla and HD modes must share:

- authored map state
- object/definition resolution
- scene plane and bridge semantics
- zone invalidation
- geometry residency
- picking and object identity
- animation state
- editor selection/tool behavior
- collision and authoring services

HD mode is a richer shading/presentation path, not a second source of scene truth.

---

## Current Studio baseline

The current renderer already has several foundations that line up well with modern RuneLite GPU and RLHD:

- canonical absolute 8x8 `WorldZoneCoordinate`
- immutable `GpuZoneUpload` / `GpuZonedUploadPlan`
- dirty-zone GPU residency in `ZoneVboManager`
- reusable native upload staging through `GpuUploadScratch`
- incremental GPU plan construction
- parallel immutable dirty-zone fragment compilation
- exact OSRS-style face priority/depth behavior
- separate opaque/alpha submission semantics
- retained model UVs
- retained model normals in neutral `GpuSceneVertex`
- retained unlit model face color
- retained object identity, client model bounds, contour metadata and placement metadata
- texture-array upload for cache textures
- CPU occlusion/visibility and DDA picking
- zone-resident incremental picking and client-AABB broad phase
- reverse-depth-style native depth configuration
- multisampled framebuffer presentation

This means the HD work should extend the existing contracts rather than replace them.

---

## Upstream references

Use current upstream RuneLite and 117HD/RLHD as implementation references while keeping Studio-owned neutral contracts.

### RuneLite GPU

Important current reference paths:

- `runelite-client/src/main/java/net/runelite/client/plugins/gpu/Zone.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gpu/SceneUploader.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gpu/ModelUploader.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java`

Relevant ideas:

- 8x8 zone-local geometry
- compact native vertex packing
- explicit opaque/alpha zone storage
- per-level and roof draw ranges
- reusable model scratch arrays
- separate temporary/dynamic model paths
- exact alpha/priority sorting behavior
- multi-zone model handling
- texture arrays and animated texture state
- multiple world-view/instance contexts

Do not copy live-client ownership concepts into editor APIs. Studio is an authoring application, not an injected client plugin.

### 117HD / RLHD

Important current reference paths include:

- `src/main/java/rs117/hd/renderer/zone/Zone.java`
- `src/main/java/rs117/hd/renderer/zone/ZoneRenderer.java`
- `src/main/java/rs117/hd/renderer/zone/SceneManager.java`
- `src/main/java/rs117/hd/renderer/zone/SceneUploader.java`
- `src/main/java/rs117/hd/renderer/zone/ZoneUploadJob.java`
- `src/main/java/rs117/hd/renderer/zone/ModelStreamingManager.java`
- `src/main/java/rs117/hd/renderer/zone/VertexWriteCache.java`
- `src/main/java/rs117/hd/scene/MaterialManager.java`
- `src/main/java/rs117/hd/scene/GroundMaterialManager.java`
- `src/main/java/rs117/hd/scene/TileOverrideManager.java`
- `src/main/java/rs117/hd/scene/ModelOverrideManager.java`
- `src/main/java/rs117/hd/scene/EnvironmentManager.java`
- `src/main/java/rs117/hd/scene/LightManager.java`
- `src/main/java/rs117/hd/scene/TextureManager.java`
- `src/main/java/rs117/hd/opengl/uniforms/UBOMaterials.java`
- `src/main/java/rs117/hd/opengl/uniforms/UBOWaterTypes.java`
- `src/main/java/rs117/hd/opengl/uniforms/UBOLights.java`
- `src/main/java/rs117/hd/opengl/shader/ShaderIncludes.java`
- `src/main/java/rs117/hd/utils/jobs/JobSystem.java`
- `src/main/java/rs117/hd/utils/buffer/GLBuffer.java`
- `src/main/resources/rs117/hd/scene_vert.glsl`
- `src/main/resources/rs117/hd/scene_frag.glsl`
- `src/main/resources/rs117/hd/tiled_lighting_frag.glsl`

Relevant ideas:

- immutable/rebuildable zone state
- asynchronous zone upload jobs
- model streaming/cache layers
- pooled primitive staging
- persistent mapped-buffer support
- separate vertex and per-face shading metadata
- material tables and texture-array layers
- water-type tables
- environment profiles
- point-light definitions
- tiled light culling
- shader includes/defines and generated uniform blocks
- hot-reloadable data-driven materials, overrides, lights and environments

---

## Architecture rule: port behavior and data, not RuneLite plugin lifecycle

A future RLHD compatibility layer should translate RLHD concepts into Studio-owned types.

Avoid:

```text
Studio -> HdPlugin -> RuneLite Client API -> renderer
```

Prefer:

```text
RLHD data/schema adapter
          |
          v
Studio MaterialTable / TileOverrideRules / ModelOverrideRules
EnvironmentProfiles / SceneLights / WaterTypes
          |
          v
Studio renderer-neutral HD scene contracts
          |
          v
OpenGL backend
```

This keeps the editor usable without RuneLite runtime state and makes HD data available to previews, inspectors, plugins and offline rendering.

---

# 1. Preserve shading inputs before adding effects

## 1.1 Model normals

`GpuSceneVertex` already retains model normal X/Y/Z and magnitude, but `OpenGlSceneRenderer` currently packs a 12-float native vertex and discards those normals because vanilla lighting was resolved on the CPU.

HD rendering needs the normals on the GPU.

Do not remove the CPU-lit values used for vanilla parity. Add a native HD-capable vertex layout or a parallel normal attribute stream while preserving the current vanilla path.

### Acceptance

- vanilla screenshots remain unchanged
- model normals reach the shader losslessly enough for HD lighting
- flat-shaded faces retain their client semantics
- normal merging remains a CPU scene-semantic operation where required

## 1.2 Terrain normals

Terrain GPU vertices currently carry zero normal data.

Before normal maps, sun lighting or point lights are enabled, derive terrain normals from the same height/shape geometry used by the authoritative terrain compiler.

Terrain normal generation must respect:

- shaped tiles
- shared tile edges
- bridges/effective planes
- height edits
- cross-zone boundaries

Do not derive normals independently inside the GL backend.

---

# 2. Split geometry from face shading metadata

The current native vertex stream carries values that are really face/material state.

117HD's modern renderer is a useful model: stable geometry and separate face metadata make material changes cheaper and avoid inflating every vertex.

Introduce a renderer-neutral concept similar to:

```text
GpuFaceShading
    alpha
    priority
    depthBias
    material0
    material1
    material2
    terrainFlags
    waterType
    waterDepth0
    waterDepth1
    waterDepth2
    sourceHsl0
    sourceHsl1
    sourceHsl2
```

The exact packed native format can differ from the neutral model.

### Important

Do not repurpose `TerrainRenderFace.material` as an HD material ID. It currently has existing terrain semantics. Add an explicit material identity.

### Benefits

- material hot reload without rebuilding geometry
- HD texture-map changes without re-uploading positions
- clean water metadata
- future shader debugging/inspection
- easier RLHD data translation
- less duplication when the same geometry is rendered by multiple presentation profiles

---

# 3. Introduce stable material identities and a material table

Add a Studio-owned material abstraction before importing RLHD material files.

Conceptually:

```text
MaterialId
MaterialDefinition
MaterialTable
MaterialResolver
```

A material definition should be able to reference:

- base/color map
- normal map
- roughness map
- ambient-occlusion map
- displacement/height map
- flow map
- optional shadow-alpha map
- brightness/tint
- specular strength/gloss or future roughness/metalness representation
- UV scale
- UV scroll
- flow strength/speed
- flags such as upward normals, base-color override, vanilla-UV behavior and shadow receive/cast behavior

Vanilla OSRS cache textures become ordinary material-table entries through a vanilla adapter.

RLHD materials later become another source of material definitions.

The renderer should consume material IDs, not know where those materials came from.

---

# 4. Generalize texture arrays into texture-set resources

Studio already uploads vanilla cache textures into a GL texture array.

Extend this rather than introducing unrelated texture systems.

Target:

```text
TextureResource
TextureLayerHandle
TextureArraySet
    color
    normal
    roughness
    ao
    displacement
    flow
    shadowAlpha
```

Implementation may use one or several arrays depending on format/capability constraints.

Requirements:

- explicit sRGB versus linear data
- mip generation
- anisotropic-filtering capability
- per-layer dirty updates
- hot reload
- stable layer handles while unchanged
- diagnostics for missing/incompatible maps

Color-space handling should become explicit before HD lighting. Base-color textures are generally color data, while normal/roughness/AO/displacement maps are linear data.

---

# 5. Add a shader library instead of embedding larger shaders in the renderer

`OpenGlSceneRenderer` should not grow into a monolithic source-string owner.

Add a shader service with:

- file/resource loading
- `#include` support
- compile-time defines
- generated uniform-block declarations where useful
- dependency tracking
- hot reload in developer/editor mode
- source-file/line diagnostics
- capability variants

Suggested boundary:

```text
ShaderLibrary
ShaderSourceGraph
ShaderDefines
ShaderProgram
ShaderDiagnostic
```

Vanilla rendering should migrate onto the same infrastructure before HD shaders are introduced.

That proves the system without changing visual behavior.

---

# 6. Introduce structured uniform blocks

Current OpenGL state uses many individual uniform locations.

HD should move stable grouped data into explicit blocks, such as:

```text
FrameUniforms
    camera
    projection
    viewport
    time
    exposure
    fog

MaterialTableBuffer
WaterTypeBuffer
LightBuffer
WorldViewBuffer
```

Do not expose OpenGL UBO classes through Client/editor-neutral APIs. The neutral side should expose immutable data tables, while the OpenGL backend chooses UBO/SSBO/texture-buffer implementation based on capabilities.

---

# 7. Add a render-pass graph before shadows and tiled lights

The current renderer is primarily one scene pass plus framebuffer resolve.

Before HD effects, define explicit passes:

```text
visibility / scene preparation
        |
        +-> shadow depth pass
        |
        +-> light culling pass
        |
        +-> opaque scene pass
        |
        +-> alpha scene pass
        |
        +-> optional post processing
        |
        +-> editor overlays / presentation
```

Suggested concepts:

- `RenderPass`
- `RenderFrameContext`
- `RenderTargets`
- `RenderCapabilities`

Vanilla can initially use only opaque/alpha/presentation passes.

The point is to avoid hardcoding future shadow and lighting setup directly into `NativeSceneViewport`.

---

# 8. Keep static-zone residency separate from dynamic streaming

Static map geometry and dynamic/animated geometry have different lifetimes.

Target split:

```text
StaticZoneGeometry
    persistent by 8x8 world zone
    dirty only when authored/resolved scene changes

DynamicGeometryStream
    animated objects
    NPCs
    projectiles
    temporary previews/gizmos
    transient editor effects
```

For dynamic streams, investigate:

- persistent mapped buffers when supported
- ring-buffer/suballocation strategies
- geometric-growth staging fallback
- explicit capability fallback for older drivers

Do not replace the current static-zone residency with one giant streaming buffer.

---

# 9. Compact native zone packing is a later optimization

RuneLite and RLHD pack zone-local positions/UVs/normals more tightly than Studio's current all-float VBO.

Studio should keep renderer-neutral world-space geometry for correctness and tools, but the OpenGL backend may later pack zone-local native vertices.

Potential benefits:

- lower VRAM
- lower upload bandwidth
- better cache behavior
- stable precision at large world coordinates

The zone origin should remain backend metadata. Do not leak packed short coordinates into public editor APIs.

---

# 10. Add model reuse/streaming after material contracts stabilize

Studio currently produces placement-specific GPU geometry.

A future model cache can reuse immutable geometry when all render-affecting inputs match.

A safe mesh key must account for applicable inputs such as:

- resolved display definition/model IDs
- shape/type
- orientation
- recolor/retexture
- animation frame
- skeletal pose
- contour state
- merged-normal state
- material/model override state

Do not instance contoured or otherwise placement-mutated geometry merely because the source model ID matches.

Potential concepts:

```text
RenderableMeshKey
ResidentMesh
ModelGeometryCache
DynamicModelStream
InstancePlacement
```

This should follow, not precede, the material/face-metadata split.

---

# 11. Environment, lighting and editor spatial volumes

Define Studio-owned data types:

```text
EnvironmentProfile
EnvironmentVolume
SceneLight
LightAttachment
WaterType
```

Environment profiles can include:

- fog color/depth
- ambient color/strength
- directional light color/strength/direction
- sky color
- water color
- exposure/tone parameters
- lightning/weather presentation values

Scene lights should support:

- world-position lights
- object-attached lights
- NPC/projectile/graphics-attached lights when simulation is enabled
- radius/intensity/color
- fade behavior

These volumes are a strong future use case for the editor-level spatial-query service discussed separately. DDA remains the primary mouse picker.

---

# 12. Tiled lighting

Once `SceneLight` and the light buffer are stable, add tiled light culling.

117HD currently uses a screen-space light-index texture organized into tiles/layers so the fragment shader evaluates only relevant point lights.

Studio should implement the same class of optimization behind its own contract.

Do not begin tiled lighting until:

- light definitions are stable
- render targets are abstracted
- uniform/storage buffers are available
- fallback non-tiled lighting exists for validation

The editor should expose diagnostics:

- visible light count
- lights assigned per screen tile
- overflow/clamping
- culling pass time
- lighting pass time

---

# 13. Shadows

Add shadows as an independent render pass.

Prepare zone state for separate visibility flags:

- visible to main scene
- visible to shadow pass

The shadow path needs:

- explicit caster/receiver material flags
- alpha-mask support where required
- configurable shadow draw expansion
- stable depth bias
- shadow-map diagnostics

Do not couple map-editor occlusion to shadow visibility.

---

# 14. Terrain materials, blending and tile overrides

117HD's ground/material system is more than texture replacement.

Studio needs a derived terrain-shading layer capable of expressing:

- underlay/overlay material resolution
- material blends across triangle vertices
- shaped overlays
- area-dependent overrides
- season/theme overrides
- water classification
- shoreline transitions
- optional texture replacement while preserving authored tile IDs

Suggested neutral services:

```text
GroundMaterialResolver
TileOverrideRules
TerrainShadingCompiler
```

Authored cache IDs remain unchanged unless an editor command explicitly edits them.

HD overrides are presentation data.

---

# 15. Water and underwater metadata

Water should not be inferred in the fragment shader from arbitrary texture IDs.

Introduce an explicit derived contract:

```text
WaterTypeId
water surface flag
per-vertex water depth
underwater flag
shore/edge metadata where required
```

This supports:

- water normals
- flow
- specular
- Fresnel
- foam
- depth tint
- underwater caustics
- underwater fog

The terrain compiler should own the semantic derivation. The renderer should consume the result.

---

# 16. Model override rules

Create a neutral model-override layer after material IDs exist.

Rules may match:

- object IDs
- NPC IDs
- graphics/projectile IDs
- area
- model identity
- orientation/shape
- var state or other supported deterministic context

Possible effects:

- material replacement
- UV behavior
- shadow flags
- normal behavior
- displacement
- object-specific presentation flags

Keep these separate from authored cache object definitions.

---

# 17. Data-driven hot reload

A map editor benefits even more than a game client from hot reload.

Eventually support watched external/project files for:

- materials
- ground-material sets
- tile overrides
- model overrides
- water types
- environments
- lights

Reload should produce a scoped invalidation result:

```text
material only
texture layer only
affected scene zones
lighting only
environment only
full shader rebuild
```

Avoid the default behavior of rebuilding the complete scene for every data-file change.

---

# 18. Job system evolution

PR #61 introduces bounded parallel work at the safe immutable GPU-fragment boundary.

The longer-term job system should add only what real workloads require:

- bounded worker count
- job groups
- cancellation
- priority
- dependency completion
- deterministic final assembly
- frame-safe publication
- per-job timing

Before parallelizing upstream terrain/model compilation, make concurrent definition access explicit.

Choose one of:

1. document that a specific immutable `DefinitionProvider` implementation supports concurrent reads, or
2. compile from immutable definition snapshots prepared before dispatch.

Do not assume every future cache backend is thread-safe.

---

# 19. Renderer capability profile

HD features should degrade deliberately.

Add a capability snapshot for features such as:

- OpenGL version
- texture-array limits
- UBO limits
- SSBO availability
- image load/store
- persistent mapped storage
- anisotropic filtering
- framebuffer formats
- maximum texture layers
- maximum light table size

Feature selection must be deterministic and diagnosable.

Avoid scattered extension checks throughout rendering code.

---

# 20. 117HD compatibility adapter

The eventual RLHD integration should be a translation layer, not the core renderer API.

Suggested module boundary:

```text
hd-core
    Studio-owned neutral material/light/environment/water contracts

hd-opengl
    native rendering implementation

rlhd-compat
    parser/translator for supported RLHD data files and behavior
```

The compatibility layer can map:

- RLHD materials -> `MaterialDefinition`
- ground materials -> `GroundMaterialResolver`
- tile overrides -> `TileOverrideRules`
- model overrides -> `ModelOverrideRules`
- environments -> `EnvironmentProfile/EnvironmentVolume`
- lights -> `SceneLight`
- water types -> `WaterType`

Do not expose RLHD enums/classes to ordinary Studio plugins.

---

# 21. Licensing and asset boundary

As of the current upstream RLHD repository, the code is BSD 2-Clause.

If Studio copies or modifies RLHD source:

- retain the required copyright notice
- retain the BSD conditions/disclaimer in source distributions
- reproduce required notices for binary redistribution

RLHD's repository license explicitly notes that third-party assets are not all covered by the BSD license. Its texture license manifest includes multiple external licenses, permission-based assets and Jagex-derived fan-content assets.

Therefore:

- do not bulk-copy RLHD texture assets into Studio core
- keep `THIRD_PARTY_NOTICES` and per-asset provenance if assets are ever distributed
- prefer a separately auditable HD asset/data pack
- code-port approval and asset-distribution approval are separate checks
- preserve upstream file-level license headers when porting code

This separation also makes it easier for Studio to support alternative HD material packs later.

---

# 22. Ordered implementation plan

Keep one focused PR at a time.

## H0 - Architecture contract

This document and roadmap linkage.

No renderer behavior change.

## H1 - Preserve GPU normals

- upload retained model normals
- add authoritative terrain normals
- native validation/debug-normal view
- no vanilla visual change

## H2 - Face shading metadata stream

- create renderer-neutral face shading payload
- zone-resident native metadata buffer
- keep existing vanilla texture/color behavior through an adapter
- prove geometry can be reused when only shading metadata changes

## H3 - Material table

- stable `MaterialId`
- vanilla material adapter
- immutable `MaterialTable`
- material-table GPU buffer
- no RLHD dependency yet

## H4 - Shader infrastructure

- file/resource shader library
- includes
- defines
- uniform-block declarations
- hot reload/diagnostics
- migrate vanilla shader onto it first

## H5 - Texture-set manager

- color/normal/roughness/AO/displacement/flow map layers
- explicit color spaces
- mipmaps/aniso capability
- per-layer dirty reload

## H6 - Render-pass graph

- explicit opaque/alpha/presentation passes
- render-target abstraction
- capability profile
- no HD-only effects required yet

## H7 - Shadow pass

- shadow depth target
- zone shadow visibility
- material cast/receive flags
- alpha masking

## H8 - Scene lights and tiled lighting

- neutral `SceneLight`
- light GPU table
- non-tiled correctness path
- tiled culling optimization
- editor light gizmos/diagnostics

## H9 - Terrain HD metadata

- ground-material resolver
- material blend metadata
- tile overrides
- terrain normal validation
- scoped hot reload

## H10 - Water

- water types
- derived depth metadata
- water/underwater shading
- shoreline validation

## H11 - Environments

- area/volume profiles
- interpolation
- sun/fog/ambient/exposure controls
- editor preview/inspection

## H12 - Model overrides and streaming

- neutral model override rules
- mesh-key cache
- safe static instancing where inputs match
- dynamic model stream
- persistent mapped-buffer capability where useful

## H13 - RLHD compatibility layer

- translate supported current RLHD JSON/data schemas
- port/adapt selected BSD-licensed renderer logic where it remains useful
- preserve attribution
- keep third-party asset handling separate
- comparison fixtures against known RLHD scenes

---

# 23. Acceptance strategy

Every HD stage needs two kinds of validation.

## Semantic validation

Before pixel comparison, verify:

- selected material IDs
- selected override rule
- world/environment volume
- light set
- water type/depth
- normal vectors
- UVs
- object identity
- effective/render plane
- shadow flags

## Image validation

Then compare representative fixtures:

- Lumbridge exterior
- Lumbridge Castle interior
- stone/wood interiors
- shaped overlays
- roofs
- wall decorations
- multi-tile objects
- bridges
- water/shoreline
- transparent textures
- animated textures
- point-light scene
- environment boundary
- shadow boundary
- cross-zone model

Keep vanilla parity fixtures active while HD work proceeds. HD features must not regress the vanilla authoring renderer.

---

# 24. Immediate priorities after current performance work

After the picker and dirty-zone CPU work, the highest-value HD-preparation sequence is:

1. preserve model normals in the native GPU layout and add terrain normals
2. split per-face shading/material metadata from geometry
3. add Studio-owned material IDs/table
4. introduce shader include/uniform infrastructure
5. generalize texture arrays into material texture sets
6. introduce render-pass/target abstraction

Only after those foundations should we start directly porting substantial RLHD shading, lighting, water or environment behavior.

This order minimizes throwaway work and keeps every intermediate renderer usable.
