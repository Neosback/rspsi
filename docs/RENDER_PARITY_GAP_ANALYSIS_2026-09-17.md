# Render parity gap analysis — 2026-09-17

This is the evidence-based audit of what the scene/render references require
versus what the current RSPSi code implements. Sources reviewed: the eight
reference documents (RuneLite scene/rendering/GPU/devtools, TSPS, scene
pipeline, Environment Exporter, cross-reference, README) and the pinned
checkouts (`RuneLite-melxin` @ `1ad572d7dc`, `TSPS` @ `83415f7`, `OSRS-
Environment-Exporter` @ `61d461d` — all verified present at their pinned
commits).

Verdict up front: **the neutral scene *data* layer is sound, the confirmed
terrain appearance bugs are corrected, and terrain/model/GPU-facing packets
are now populated for current neutral inputs.** A deterministic CPU reference
rasterizer now consumes the upload plan, so projection, clipping, depth,
palette, texture, lightness, and alpha behavior can be tested at pixel level.
The first embedded LWJGL/OpenGL consumer now consumes that same plan, so the
native backend is no longer merely planned. Client-parity edge cases and full
viewport interaction remain incomplete. The controlled JavaFX preview now
uses packet-derived terrain HSL through the client palette formula rather than
synthetic tile-ID colors, but it remains a top-down semantic preview while the
embedded 3D path is validated.

## Confirmed bugs (fix before any packet work)

### B1. `TerrainAppearanceBuilder.blendedUnderlay` uses the wrong hue/saturation/lightness domains

Client semantics (verified against melxin `MapRegion` lines ~620–800,
`FloorUnderlayDefinition`, TSPS `SceneBuilder.blendUnderlays` +
`ColorUtil.packHsl`, and our own verified-correct `MinimapBuilder`):

- the blend accumulates per-definition `weightedHue` (0..mult) and
  `hueMultiplier` (0..512) and produces `avgHue = sum(weighted) * 256 /
  sum(multiplier)` in the 0..256 domain;
- saturation and lightness are averaged in the 0..255 domain;
- the three are packed through `packHsl` (hue/4 << 10 | sat/32 << 7 | lum/2),
  including the >179/>192/>217/>243 saturation halving;
- the legacy source contains a commented-out randomized hue/light offset
  branch; RSPSi must not enable that branch without a revision fixture. The
  active path applies per-corner `adjustUnderlayLight`/`adjustOverlayLight`
  with tile light before palette lookup.

`TerrainAppearanceBuilder` previously computed `sum(weighted)/sum(multiplier)`
(a 0..1 fraction) clamped to 0..63 — so every blended underlay hue collapses
to ≈1/64 of the correct band — averages definition saturation (0..255) into a
0..7 clamp and lightness (0..255) into a 0..127 clamp (halving brightness),
skips `packHsl` saturation halving, the ±8/−16 offsets, and the tile-light
adjust entirely. The existing unit test passes only because it invents inputs
in the broken domain. The scene path now uses the proven source domains,
client packing, and asymmetric blend window through the shared
`OsrsTerrainColorMath` utility. Per-corner light adjustment remains a terrain
packet-builder responsibility. `MinimapBuilder.blendedOsrsUnderlay` continues
to implement the same algorithm and passed the external zero-pixel-difference
fixture.

### B2. Blend window asymmetry and region-boundary handling

The correct rolling window is asymmetric over the decode grid (`x−4 … x+5`
accumulation as TSPS removes column x−5 after adding x+5) and must run over a
scene grid that includes neighbor context, not a 64×64 document clamped at its
edges. `TerrainAppearanceBuilder` now uses the asymmetric x−4..x+5 loop.
`RenderWindowSceneBuilder` now materializes loaded regions into one derived
world document so appearance, lighting, and terrain packets share neighbor
context across loaded region boundaries. A five-tile external border is still
needed when the requested window ends at a loaded-world boundary; missing
regions must remain holes rather than fabricated cache data.

### B3. Per-corner light application is implemented; exact fixture parity remains

TSPS produces per-corner `SceneTileModel` colors by applying
`adjustUnderlayLight(blendHsl, tileLight[corner])` and
`adjustOverlayLight(overlayHsl, tileLight[corner])` — the tile light
(`TerrainLighting`) must be combined into the terrain packet per corner.
`TerrainPacketBuilder` now combines the neutral lighting map with terrain
topology and writes light-adjusted packed HSL values; the scene fingerprint
covers those packets. Wall-shadow contributions are now applied before this
stage through `TerrainShadowMap` for the verified client wall categories.
Remaining work is exact TSPS fixture parity for shaped intermediate vertices,
richer raw-HSL diagnostics, and the model/occluder paths. Window derivation now uses
an explicit five-tile neutral context border instead of repeating visible
edge samples.

## Contracts and remaining 3D packet milestones

- `TerrainRenderPacket`/`TerrainRenderVertex`/`TerrainRenderFace`:
  `TerrainPacketBuilder` now exists and is wired into definition-aware
  `RenderScene` and window snapshots. It now carries four blended underlay
  corner HSL values and client packed-HSL midpoint mixing for shaped vertices;
  textured overlays now retain separate render HSL (`-1`, light-only vertex
  values) and minimap/fallback HSL, while hidden overlays retain the client
  `-2` sentinel; overlay cache shape 0 is published as scene shape 1 rather
  than being mistaken for flat geometry. Remaining work is padded-window
  fixture coverage and final raw-HSL diagnostics,
  the 13-shape × 4-rotation vertex generation (already verified in
  `TerrainMeshBuilder` for topology — colors/UVs/hidden faces are the delta),
  UV basis, and hidden-face classification. Texture IDs now resolve into the
  scene-level `RenderTextureResource` handoff; nearest/clamp sampling and
  client-cycle animation offsets are consumed by both the deterministic
  software renderer and the embedded OpenGL backend.
- `ModelRenderPacket`/`ModelVertex`/`ModelTriangle`/`TextureTriangle`:
  `ModelPacketBuilder` now implements the static path for typed model
  selection, multi-part merge, wall-corner and wall-decoration variant
  expansion, mirroring, client-order 45-degree and quarter-turn orientation,
  recolor/retexture, scale/offset, decor displacement, ambient+64/
  contrast+768 lighting, normals, and full/partial/clamped/bridge contour
  modes. FileStore
  texture metadata now reaches the neutral view and the builder computes
  per-face UVs for simple/cylindrical/planar/spherical mappings with client
  seam correction. The `rotate(256)` wall-decoration path and conditional
  cross-object `mergeNormals` are implemented; byte/golden fixture coverage
  for those variants, model animation, and advanced model channels remain
  open. Models now use TSPS footprint-centred
  placement with an explicit sampled placement height, and normal merging is
  applied across matching world-space vertices when the adapter exposes the
  flag. Model normals now retain the client’s
  accumulated components and magnitudes rather than being normalized twice;
  textured faces now carry client lightness scalars (including the render-type
  1/2/3 sentinels) instead of applying non-textured HSL `blendLight` to the
  texture average; non-textured faces retain the HSL blend path;
  animation IDs remain metadata-only until frame/skeleton deformation is
  implemented. Window-scene model packets now use the same
  padded world context as terrain before being projected back to world anchors,
  preventing region-edge contour samples from diverging.
- `SceneTileSnapshot`/`GpuScenePacket`: `GpuScenePacketBuilder` now produces
  stable world-tile snapshots with terrain/model packets, explicit terrain →
  wall → wall-decor → ground → ground-decor layers, opaque/transparent model
  partitions with face-level triangle lists, bridge/effective plane visibility,
  raw authored tile flags, complete world/region/chunk identity, and
  deterministic explicit occluder inputs. It still needs revision-specific
  plane-cull and force-visible interpretation, interaction identity, and
  client type-2 occluder traversal.
- Texture resources: referenced terrain/model texture IDs now resolve through
  the session-scoped `DefinitionProvider` into immutable
  `RenderTextureResource` values. Metadata is preserved even when pixel
  archives are unavailable; non-square provider responses are marked invalid
  rather than reshaped. `RenderScene`, `RenderWindowScene`, and
  `GpuScenePacket` publish these resources so a backend never reopens the
  cache. Texture animation offsets, filtering, atlas allocation, and shader
  sampling remain backend work.
- `GpuUploadPlan`: `GpuUploadPlanBuilder` now expands terrain and model packets
  into world-space vertices, indices, texture/material references, and ordered
  opaque/alpha draw commands. It preserves terrain HSL, model normals and
  per-face UVs while skipping client-hidden render type 2 faces. It explicitly
  marks packed Jagex HSL versus textured-face lightness values so the GPU
  backend cannot double-light textured models, and carries model
  texture-triangle projection/animation metadata alongside the expanded
  faces. The plan remains backend-neutral, and the embedded OpenGL consumer
  uploads it to native buffers without introducing a second scene model.
- `SceneOccluder`: explicit `ObjectAppearanceView.occludes()` objects now
  derive one camera-independent occluder per placed object, aggregating bounds
  across multipart model packets. `modelClipped` wall shapes 0/2 now also emit
  RuneLite type-1/type-2 wall-edge inputs with the client 240-unit height band.
  Region-wide mask merging/traversal and broader wall/roof grouping remain
  open; `obstructive` and `clipMask` remain separate gameplay/low-detail inputs.

## Latest reference-review additions

The additional engine review confirms that the next exactness gates are not
more UI behavior. They are preservation and submission details:

1. retain explicit-versus-generated height provenance in the authored terrain
   contract;
2. keep the 64x64 cache region, 104x104 scene window, and 105x105 shared
   height-sample domains distinct;
3. publish source/effective/collision/render plane state for bridges and roofs;
4. keep collision, projectile/LOS clipping, visual occlusion, and shadow
   contribution independent;
5. add runtime scene deltas without changing the static cache document;
6. carry complete instance metadata and texture resources into the GPU upload
   plan; and
7. complete client-compatible priority/alpha/depth submission, visibility,
   and native-vs-software comparison before calling the modern 3D backend
   exact.

The texture-resource handoff and the first CPU reference rasterization path are
now implemented and tested. The remaining items are deliberately still listed
as gaps rather than being implied by the top-down preview.

## Smaller divergences found during the audit

1. `LightingProfile.osrs()` documents the exporter's `(-50,-50,-10)` bias
   while the client/TSPS light direction is `(-50,-10,-50)` (Y is the height
   axis). `TerrainLighting` uses Y=-10 as height-normal dot, which matches
   TSPS; fix the comment/naming so the height axis is unambiguous, and keep
   the exporter's axis ordering only in the exporter comparison doc.
2. `TerrainLighting` clamps edge corners to ambient. TSPS leaves
   outer-ring corners at 0 within a single scene; with real 9-region windows
   the border tiles are interior — so the neutral builder must compute
   lighting on the window (padded), not the region document, and the
   ambient-clamp fallback applies only to explicit holes.
3. `ObjectAppearanceView` now carries `castsShadow`, `occludes`,
   `mergeNormals`, `nonFlatShading`, `contourGroundType/param`,
   `decorDisplacement`, ambient/contrast, clipping, and rotation. Legacy and
   OpenRune providers map the fields they expose. OpenRune does not expose a
   separate cast-shadow flag, so the adapter records the OSRS default policy;
   unsupported occlusion semantics remain explicit rather than fabricated.
4. Revision conditionals: TSPS gates `centerLocHeightWithSize` (OSRS or
   ≥465) and `newTerrainFormat` (≥209). `OsrsRevisionFeatures` covers the
   terrain format but not the center-height rule; add it before object
   packet work (multi-tile object center heights shift otherwise).
5. Wall light occlusion is now implemented for the verified client rules:
   wall types 0, 1, and 3 write strength 50 to orientation-dependent corner
   entries, and `TerrainLighting` applies the same weighted corner penalty.
   Model-radius terrain-light occlusion for clipped type-10/11 objects is now
   implemented as radius/4 capped at 30, with a conservative fallback when
   geometry is unavailable. Type-2 occluder traversal remains separate
   follow-up work.
6. `centerHeight` for multi-tile locs now samples the footprint-centred
   shared-corner surface (equivalent to the TSPS `(size>>1)` corner average
   for revision-240 placement), including bridge-plane promotion.
7. The devtools color palette and overlay families are implemented
   (`UserTileMarker`/`DiagnosticTileAnnotation`/`DebugColor`); nothing else
   missing for the diagnostic gate.

## What is already verified (do not redo)

- 13-shape × 4-rotation topology: 52-case golden matrix, shared-edge
  invariants, direct TSPS table comparison (`TerrainMeshBuilder`).
- Radius-5 blend, palette, adjust-light, shaped minimap masks/rotations:
  `MinimapBuilder` (externally verified at zero pixel difference on the
  sprite-bearing `(50,50)` fixture).
- Lighting constants and normal math: `TerrainLighting` matches TSPS
  `calculateTileLights` for interior tiles.
- Collision/route semantics: canonical OpenRune-compatible builder plus
  differential vectors.
- Instance replay semantics (opcode provenance, chunk transforms): verified
  against the 676-transform external fixture.
- Bridge authored/effective-plane separation and collision-before-promotion
  order: implemented and tested.

## Round-2 corrections (2026-09-18, verified against the pinned checkouts)

Two families of client-exactness bugs were found by re-diffing the live
decode/render path against melxin `Model.contourGround`,
`Model.calculateBoundsCylinder`, `ObjectComposition.getModel*`, and TSPS
`ModelData.contourGround` — both families are now fixed and pinned by tests.

### R1. Animation transforms resolved skeleton labels as raw vertex indices

`Model.transform` resolves each skeleton label through the model's per-vertex
skin map (`vertexSkins[v] == labelId` defines membership); the old code
iterated label ids as if they were vertex indices, silently working only for
dense humanoid skins. The rewrite builds the two-level indirection (labels →
vertex/triangle lists, then transforms), applies the type-5 face alpha via the
same face-label indirection, and uses the client rotation order Z → X → Y with
the natural angle-source mapping (melxin `Model.animate`, confirmed by TSPS
`Model.animate`). Pinned by `ModelAnimationTest`.

### R2. Contour ground used TSPS's generalized types instead of the client path

The live OSRS client only ever calls contourGround with
`param = clipType * 65536`: `-1` skips, `0` fully warps every vertex, and
positive values warp only the span where `(-y << 16) / max(-y) < param` —
note the ratio is **positive top-to-bottom**; deriving the denominator as
`-max(-y)` (TSPS `this.height` re-signed) inverts which vertices conform.
The rewrite implements the client path exactly: radius-box bounds and
flat-footprint skip from `calculateBoundsCylinder` (xzRadius =
`max(sqrt(x²+z²)) + 0.99`), `>> 7` bilinear sampling so negative scene
heights floor identically, reference height = the footprint central-block
corner mean, light baked pre-contour (client order), and TSPS types 3–5 kept
as generalized fallbacks. The OpenRune provider now maps clipType through the
client's `clipType * 65536 >= 0` gate (including the int-overflow skip), and
the builder gate moved off the legacy `contouredGround()` boolean.
Pinned by `ModelPacketBuilderTest` contour cases and
`OpenRuneDefinitionProviderContourGroundTest`.

### Depth-bias sign note (comment-only fix)

`GpuPriority.biasedDepth` carries a stale "native path is conventional-Z"
comment; the embedded renderer actually runs reversed-Z (`glClearDepth(0.0)`
+ `GL_GEQUAL`, matching RuneLite's vert.glsl bias direction). The code was
already correct for both backends; only the comment lied.

## Prioritized plan

1. **P0 — Complete B1/B2/B3**: padded/window-level appearance derivation and
   per-corner light application are implemented; the remaining P0 work is
   exact shaped-vertex fixture parity and raw-HSL diagnostics.
2. **P1 — Complete light occlusion**: resolve type-2 occluder semantics from
   the selected revision/provider. Wall corner shadow rules for types 0, 1,
   and 3 plus clipped type-10/11 model-radius terrain-light occlusion are now
   implemented and tested; geometry-backed objects use `XZRadius / 4` while
   undecoded entities retain the client fallback of 15.
3. **P2 — Complete model packet parity**: finish byte/golden fixture coverage
   for the ordered TSPS transform pipeline, diagonal wall-decoration variants,
   and cross-object normal merging, then add animation policy and advanced
   model channels. The static packet builder and four-mode UV projection now
   exist.
4. **P2 — Scene tile snapshot + GPU packet producer**: the opaque/alpha split,
   layer order, bridge/effective-plane state, roof flags, occluder inputs,
   texture resources, and deterministic upload plan are implemented. Remaining
   work is zone membership, force-visible/roof policy, and plane-cull levels;
   make `EditorSceneSnapshot` consume those visibility decisions so JavaFX and
   ImGui see one projection.
5. **P3 — Occluder parity**: complete client-compatible wall/decor grouping
   and type-2 traversal; visibility math stays renderer-side.
6. **P3 — Window-level scene build**: add generation tracking and stale-result
   rejection (cross-reference §7), so a 104×104 client-size window can be
   fingerprinted end-to-end. The shared padded derivation and visible-tile
   projection are now in place.
7. **P4 — Headless `verify-scene`/`export-scene`** over the same builders,
   producing the deterministic packet fingerprint/export the references
   require as the acceptance artifact; the embedded OpenGL 3.3 adapter now
   consumes packets only. Remaining work is native-vs-software parity
   evidence, not a second scene-construction path.

Steps 1–3 unblock the "faithful terrain colors" gate; 4–5 are the core 3D
packet milestone; 6–8 complete the acceptance ladder the cross-reference
defines. Each step keeps FileStore behind adapters, external checkouts as
evidence only, and fingerprints deterministic.
