# RuneLite rendering-contract audit

Date: 2026-09-18  
Scope: the RuneLite API surfaces that affect scene construction, model
selection, visibility, ordering, materials, and native OpenGL submission.

The machine-checked source of truth is
[`RENDERING_PARITY_MANIFEST.json`](RENDERING_PARITY_MANIFEST.json). Generate
the searchable Markdown/JSON report with `./gradlew renderingAuditReport`; use
[`RENDERING_PARITY_INDEX.md`](RENDERING_PARITY_INDEX.md) for the review order.
This document preserves the detailed findings and rationale behind the matrix.

This is an audit of the neutral editor pipeline, not a claim that the editor
is a RuneLite client. RuneLite is the semantic reference; cache decoding and
editing remain OpenRune Studio responsibilities.

## Executive result

The bridge path is now structurally correct for the current editor scope:

- bridge/effective-plane filtering is applied before GPU upload;
- bridge objects retain their authored-plane height instead of being promoted
  to the lower plane;
- roof filtering removes roof geometry without removing terrain or walls;
- wall orientation A/B is represented with the RuneLite cardinal and diagonal
  bitfields, and only cardinal wall planes become fixed-axis occluders;
- model face alpha, hidden render type 2, render type 3, texture faces,
  priorities, face bias, normals, and per-face UVs reach the upload plan;
- RuneLite render modes are now carried by `ModelRenderPacket` and preserved
  by `GpuUploadPlanBuilder`. Cache-backed static models explicitly use
  `DEFAULT` until a dynamic provider supplies another mode.

The native renderer is not yet full RuneLite parity. The remaining gaps are
named below rather than hidden behind a generic “rendered” status.

## Source contracts checked

The audit was checked against the official RuneLite API documentation:

- [DrawCallbacks](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/hooks/DrawCallbacks.html)
- [Scene](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Scene.html)
- [Tile](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Tile.html)
- [SceneTileModel](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/SceneTileModel.html)
- [SceneTilePaint](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/SceneTilePaint.html)
- [TileObject](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/TileObject.html)
- [GameObject](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/GameObject.html)
- [WallObject](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/WallObject.html)
- [GroundObject](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/GroundObject.html)
- [DecorativeObject](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/DecorativeObject.html)
- [Model](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Model.html)
- [Renderable](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Renderable.html)
- [Mesh](https://static.runelite.net/runelite-api/apidocs/net/runelite/api/Mesh.html)

## Contract matrix

Status values mean:

- **Covered**: the semantic information exists and is used by the native
  upload path.
- **Partial**: the important static-map case exists, but a RuneLite runtime
  or camera-dependent case is not represented yet.
- **Deferred**: intentionally outside the current static map-editor gate.

| RuneLite contract | OpenRune Studio mapping | Status | Finding |
|---|---|---:|---|
| `Scene.getTiles()` four planes and scene coordinates | `WorldDocument`, `SceneWindow`, `SceneTileSnapshot`, `WorldTileAddress` | Covered for region windows | The editor uses world-region windows rather than exposing the client’s 104×104 runtime array directly. |
| `Scene.getExtendedTiles()` border context | `SceneWindow.border`, window region set | Partial | Border data is available, but native traversal is not yet a camera-driven extended-scene traversal. |
| `Scene.getTileHeights()` shared corner heights | `TerrainRenderPacket` and terrain builders | Covered | Terrain is built from shared tile corner heights. |
| `Scene.getTileShapes()` and rotations | `TerrainRenderFace` material/shape/rotation data | Covered | Shape topology is emitted before GPU flattening. |
| `Scene.getMinLevel()` | render settings and active plane | Partial | The editor has explicit plane selection, but no client-style minimum visible plane derived from camera state. |
| `Scene.getRoofRemovalMode()` / `getRoofs()` | `RenderConfig`, `SceneVisibilityPolicy`, `roofRelated` | Partial | Roof filtering is explicit and preserves terrain/walls; client roof-removal modes and line-of-sight traversal are not fully reproduced. |
| `Scene.getInstanceTemplateChunks()` | instance model types and window context | Partial | The data model has instance support, but full rotated/template scene reconstruction is not yet a native parity fixture. |
| `Tile.getRenderLevel()` | `SceneTileSnapshot.effectivePlane` | Partial | Effective-plane bridge visibility is present. Authored plane and render level are not separate fields, which limits exact client traversal diagnostics. |
| `Tile.getBridge()` | `BridgeLink` and `SceneVisibilityPolicy` | Covered for current bridge policy | The upper tile can project the lower effective plane without deleting the upper authored data. |
| `Tile.getSceneTilePaint()` | `TerrainRenderPacket` | Covered | Paint-style terrain is normalized into terrain faces. |
| `Tile.getSceneTileModel()` | `TerrainRenderPacket` | Covered | Model-style terrain is normalized into the same terrain submission contract. |
| `Tile.getWallObject()` | `SceneLayer.Kind.WALL`, `WorldObject`, model packets | Covered for static geometry | Wall model expansion and orientation A/B are represented. |
| `Tile.getDecorativeObject()` | `SceneLayer.Kind.WALL_DECORATION` | Covered for static geometry | Wall-decoration displacement and diagonal variants are handled in `ModelPacketBuilder`. |
| `Tile.getGroundObject()` | `SceneLayer.Kind.GROUND_OBJECT` | Covered for static geometry | Ground-object model selection and placement are emitted. |
| `Tile.getGameObjects()` | `SceneLayer.Kind.GROUND_DECORATION` and model lists | Partial | Static map objects are represented; a RuneLite-style four-entry runtime array and duplicate-object identity are not preserved as a separate contract. |
| `Tile.getItemLayer()` / ground items | no neutral render layer | Deferred | Items/entities are not part of the static map-editor renderer gate. |
| `TileObject.getHash()` | `WorldObject` id/type/rotation/plane/x/y | Partial | Normal map identity is present, but packed hash/world-view identity is not retained. This matters for exact client/plugin interop and picking, not current static geometry. |
| `GameObject.getOrientation()` | `WorldObject.rotation()` | Covered for cache locations | Cache rotation is normalized into the neutral object. |
| `GameObject.getModelOrientation()` | model transform baked in `ModelPacketBuilder` | Partial | Static models are rotated before upload. The original Jagex-angle orientation is not retained as a separate runtime field. |
| `GameObject.getSceneMinLocation()` / max location | `RenderObject` footprint and anchor | Covered for static placement | Footprint dimensions swap on odd rotations and are used for placement/contour. A separately addressable min/max scene coordinate is not retained on the model packet. |
| `WallObject.getOrientationA/B()` | `WorldObject.wallOrientationA/B()` | Covered | Cardinal bits 1/2/4/8 and diagonal bits 16/32/64/128 match the RuneLite contract. Type 2 emits the two wall variants. |
| `Model.getFaceColors1/2/3()` | `ModelTriangle.colorA/B/C` | Covered | Flat-face sentinels are expanded at upload so the GPU receives ordinary vertex colors. |
| `Model.getUnlitFaceColors()` | `ModelTriangle.baseColor` plus relighting | Partial | Source base color is retained for relighting, but there is no separate public unlit-color packet for plugin/debug consumers. |
| `Model.getFaceRenderPriorities()` | `ModelTriangle.priority` → `GpuDrawCommand.priority` | Covered | Priority survives packet creation and draw-command grouping. |
| `Model.getFaceBias()` | `ModelTriangle.depthBias` → GPU command | Covered | Bias participates in upload and merge compatibility. |
| `Model.getTransparency()` | `ModelTriangle.alpha` | Covered | Model alpha follows RuneScape semantics: 0 opaque, 1–254 blended, 255 invisible. |
| `Model.getTextureFaces()` / texture indices | `ModelTriangle.textureId`, `TextureTriangle`, texture resources | Covered for decoded cache textures | Texture definition and pixel fallback diagnostics are explicit; animation/render-time texture offsets remain limited. |
| `Model.getVertexNormals*()` | `ModelVertex.normal*`, lighting and relight | Covered for static lighting | Normals are retained and used for OSRS-style lighting. Runtime normal-request flags are not separately exposed. |
| `Model.getUnskewedModel()` | contour/height application in `ModelPacketBuilder` | Partial | Static contouring is baked into packet vertices. Runtime HILLSKEW/height-map evaluation is not equivalent to a client model request. |
| `Model.getAABB()`, radius, diameter, bottom Y | packet min/max bounds and conservative visibility | Partial | Bounds exist, but there is no orientation-aware AABB/radius/diameter contract for exact RuneLite-style culling and picking. |
| `Model.getSceneId()` | `ModelRenderPacket.objectId` | Partial | Object definition identity exists; a unique scene-instance identity is not yet carried. |
| `Model.getBufferOffset()` / UV buffer offset | no native client buffer offsets | Deferred | These are runtime client GPU buffer details, not stable cache/editor semantics. |
| `Renderable.RENDERMODE_*` | `GpuDrawCommand.RenderMode` and `ModelRenderPacket.renderMode` | Covered as a transport contract | Static cache objects default to `DEFAULT`; sorted/no-depth dynamic providers are now preserved rather than silently discarded. |
| `Renderable.getAnimationHeightOffset()` | animation frame transforms, no height-offset field | Partial | Animated vertex transforms exist, but animation height offset is not a first-class packet value. |
| `DrawCallbacks.PASS_OPAQUE` / `PASS_ALPHA` | `GpuDrawCommand.SubmissionPass` | Covered | Opaque and alpha submissions are separate. |
| `DrawCallbacks.PRE_PASS_ALPHA` | no separate pre-alpha pass | Deferred/known gap | The current renderer intentionally omits RuneLite’s optional pre-alpha pass; transparent ordering must not be described as exact parity. |
| `DrawCallbacks.HILLSKEW` | baked contour mode | Partial | The editor can place contoured models, but does not expose the runtime callback flag or client height-map skew path. |
| `DrawCallbacks.NORMALS` | normals retained in every model vertex | Covered for static lighting | The data is available even though there is no callback-style request negotiation. |
| `DrawCallbacks.ZBUF` / zone frustum | OpenGL depth testing and native visibility | Partial | Depth state is stable and culling is disabled for baseline parity; RuneLite’s camera-zone frustum hooks are not a direct neutral contract. |
| `DrawCallbacks.NO_VERTEX_SNAPPING` | no vertex-snapping stage | Covered by omission | The native path does not introduce a snapping pass. |
| `DrawCallbacks.UNLIT_FACE_COLORS` | base color and lit colors | Partial | Base colors are retained, but no callback-controlled unlit submission mode exists. |

## Wall and bridge conclusions

### Walls

The wall issue was not a simple “rotate the model” problem. RuneLite exposes
two orientation bitfields, while cache locations use a four-value rotation.
The neutral path now keeps that distinction:

1. `WorldObject.wallOrientationA/B()` derives the RuneLite bitfield.
2. `ModelPacketBuilder` expands type 0–3 wall variants, including type 2’s
   second wall model.
3. `GpuScenePacketBuilder` only creates fixed-axis occluders for cardinal
   orientation bits. Diagonal bits do not become false rectangular planes.
4. `SceneVisibilityPolicy` removes roof geometry by metadata, not by deleting
   all geometry on the roof’s tile.

This is sufficient for the current wall/bridge baseline. Exact client wall
render order, camera clipping, and decoration interaction still belong to the
visibility/render-queue phase.

### Bridges

The current bridge model is now consistent with the intended editor behavior:
the bridge tile can select a lower effective plane for visibility, while the
object’s authored placement height remains on its own plane. Promoting the
object height to the lower plane caused bridges and elevated objects to look
physically inverted; that promotion has been removed.

## Highest-priority remaining work

1. Add a separate `authoredPlane`/`renderLevel` field to the tile packet and
   capture the distinction in bridge fixtures.
2. Add camera-dependent roof/min-level traversal instead of relying only on
   the selected plane.
3. Carry animation height offsets and a unique scene-instance identity for
   animated/pickable objects.
4. Implement a RuneLite/TSPS parity fixture for HILLSKEW, model bounds, and
   transparent ordering.
5. Add the optional pre-alpha submission phase only when a real source flag
   identifies geometry that requires it.
6. Add instance-template and extended-tile native acceptance fixtures.

## Explicit non-goals for this phase

The following are not needed to claim the solid static scene gate and should
not be invented as fake parity:

- RuneLite runtime buffer offsets;
- ground-item/entity rendering;
- plugin callback scheduling;
- exact client camera/frustum traversal;
- GPU height-map HILLSKEW;
- dynamic model scene IDs and animation height offsets;
- a full pre-alpha pass without source metadata.

## Verification

The parity transport change is covered by the GPU upload-plan test asserting
that `SORTED_NO_DEPTH` survives from `ModelRenderPacket` to
`GpuDrawCommand`. The existing alpha, texture, bridge, roof, wall-orientation,
and occluder tests remain required.

## Update 2026-09-18 (later same day) — wall/loc shape and ground-blend audit

Line-by-line comparison of `ModelPacketBuilder.java`, `TerrainAppearanceBuilder.java`,
`TerrainMeshBuilder.java`, `TerrainLighting.java`, and `GpuScenePacketBuilder.java`
against the deobfuscated client (`RSPSi-resources/RuneLite-melxin/runescape-client`,
primary oracle), `RSPSi-resources/OSRS-Environment-Exporter` (independent
second oracle), and `RSPSi-resources/TSPS` (checked; its renderer consumes
already-built RuneLite-style scene packets and does not independently derive
shape/displacement/blend math, so it is not a useful oracle for this audit).

**Confirmed correct, unchanged:** the shape 0-11 loc→model mapping and every
displacement table (`DECOR_DISPLACEMENT_X/Z`, `DIAGONAL_DISPLACEMENT_X/Z`)
against `Tiles.java`'s `field800/802/798/803/805` and the real shape switch in
`FriendSystem.java:446-682`/`class150.java:289-419`; the tile SHAPE_POINTS/
ELEMENTS triangulation tables against `SceneTileModel.java`; the chroma-
weighted-hue underlay average and the blend-radius-5 window bounds against
`class470.java`'s box-blur; the magenta `0xFF00FF` hidden-overlay sentinel;
the terrain slope-lighting normal/dot-product formula against
`class470.java:893-905`.

**Confirmed divergent, fixed this pass:**

1. **Ground color blended per corner instead of per tile** (highest visual
   impact of this audit). `TerrainAppearanceBuilder` computed four
   independent hue/saturation/lightness box-blurs, one per tile corner, each
   re-centered at a shifted position. The real client (`class470.java:1035-1073`)
   blends **once per tile**; the four corners then vary only in **lightness**
   via a separate per-vertex slope/AO term (`method2086`, hue/saturation bits
   untouched) — already correctly implemented on the RSPSi side via
   `TerrainLighting`'s bilinear corner light and
   `OsrsTerrainColorMath.adjustPackedHslLight`. Fixed by blending once and
   reusing that single value for all four corners
   ([`TerrainAppearanceBuilder.java`](TerrainAppearanceBuilder.java)) —
   `TerrainPacketBuilder`'s corner/midpoint selection becomes a no-op on
   equal inputs, so it needed no change. Previously this produced a visibly
   soft/wrong-hue "watercolor" blend at every underlay-type boundary (grass↔
   dirt, dirt↔sand, etc.) instead of the client's lightness-only gradient.
2. **Wall/loc mirror rule used OR gated to shape 2 instead of the client's
   XOR applied via the rotation parameter to every shape.** The real rule
   (`ObjectComposition.java:826-856`) is `isRotated XOR (rotationParam > 3)`,
   applied uniformly through the rotation value passed to `getModelData` —
   `rotationParam > 3` is true for shape 2's first wall piece (`rot+4`) *and*
   every diagonal wall-decoration variant (shapes 6/7/8/11, which also use a
   "+4" rotation). RSPSi's old `appearance.rotated() || (sourceType==2 &&
   rotation>3)` unconditionally mirrored shape-2's first piece regardless of
   `isRotated`, and never special-cased diagonal wall decorations at all —
   so with the common `isRotated=false` cache value, diagonal-wall torches,
   banners, and windows never mirrored when the client always does. Fixed in
   [`ModelPacketBuilder.java`](ModelPacketBuilder.java)'s `append()`.
3. **Same-tile shape-2 corner-wall normal merge (`mergeWallVariantNormals`)
   had no gate on the object definition's `mergeNormals` flag**, unlike the
   sibling cross-packet `mergeNormals()` method which correctly checks it.
   The client only reaches `Scene`'s shape-2 merge for objects whose
   definition set opcode 22 (`nonFlatShading`); RSPSi merged unconditionally,
   over-smoothing lighting seams on corner walls that never opted in. Fixed
   by gating the call on `appearance.mergeNormals()` in `ModelPacketBuilder`;
   regression test added (`mergeWallVariantNormalsIsSkippedWithoutTheMergeNormalsFlag`).
4. **Wall-decoration displacement fallback used the decoration's own cache
   `int2` instead of the client's hardcoded literal defaults** (16 for the
   straight case, 8 for diagonal) when no supporting wall is found on the
   tile — an editor-specific case (decoration placed before its wall).
   `FriendSystem.java:627,641,664`. Fixed by returning the literal `16`;
   `variantsFor`'s existing `/2` for the diagonal shapes reproduces the
   client's separate 8 default without a second literal.
5. **Translucent-face detection bug in `GpuScenePacketBuilder`** (found
   independent of the reference repos, from the method's own contradictory
   logic): `hasTransparentGeometry`'s `face.alpha() != 255` guard
   short-circuited `isTransparentFace()`'s own render-type-3/transparent-
   texture checks whenever a face's alpha happened to equal 255 — even
   though the client's (and this codebase's) opaque-alpha default is `0`,
   not `255`. Objects using render-type-3 faces or transparent-pixel
   textures with `alpha==255` (glass, spirit trees, some window/lattice
   decorations) were bucketed as opaque, causing depth-sort artifacts
   against genuinely translucent neighbors. Fixed by removing the redundant
   outer guard.

**Not fixed — unresolved, flagged for a future targeted check:**

- **`DIAGONAL_INTERACTABLE` (loc type 11) rotation.** RuneLite's
  deobfuscated `FriendSystem.java:684-691` treats types 10 and 11 identically
  (plain `rot`, no extra rotation). `OSRS-Environment-Exporter`'s
  `SceneRegionBuilder.kt:367-370` applies an extra 0x100 (45°-class)
  rotation for type 11 specifically. RSPSi's `ModelPacketBuilder` follows the
  exporter's version. The two reference repos disagree and this audit could
  not determine which is authoritative — needs a targeted decompile check
  before either side is trusted. Narrow scope (this loc type only).
- **Terrain AO/shadow source.** `TerrainLighting`'s slope-lighting formula
  matches the client's `class470.java:893-905` structurally, but the audit
  did not have budget to trace what populates `TerrainShadowMap`'s
  `cornerStrength` against the client's specific wall-adjacency `underlays2`
  mechanism (a byte array set to 50 wherever a `clipped=true` wall was
  placed, weighted `>>3,>>2,>>2,>>3,>>1` — `TerrainLighting` currently uses
  `>>2,>>3,>>2,>>3,>>1`). If `TerrainShadowMap` isn't driven the same way,
  this is a divergence in shadow-darkening magnitude near walls only (not
  hue or shape) — unverified either way.

All five fixes verified via `./gradlew :Client:test :Editor:test` and
`./gradlew foundationGate` (green); no visual/screenshot verification was
performed — that remains the user's own confirmation step.

## Update 2026-09-18 (third pass) — depth-bias semantics and the wall/decoration draw contract

Traced directly against the deobfuscated client
(`RSPSi-resources/RuneLite-melxin/runescape-client`), prompted by persisting
reports of odd walls and z-fighting on flush wall decorations.

### The load-bearing discovery: the scene renderer has no depth buffer

`Scene.java:1863-1960` draws each tile in a fixed sequence — tile
underlay/overlay, then `boundaryObject` (wall), then `wallDecoration`, then
`floorDecoration`, then `itemLayer`, then game objects. There is no depth
test in that path; **paint order alone** guarantees a decoration wins
against the wall it is mounted on. Every depth mechanism below exists to let
a z-buffered renderer reproduce that guarantee, and several of our bugs come
from having copied RuneLite's GPU *approximations* rather than the client's
own rule.

### Confirmed divergent, fixed this pass

1. **Face bias was distance-scaled; the client's is constant in world space.**
   `Model.java:1855` / `:2012`: `int var5 = faceBias[face] * 2;` then
   `method6970(field3037[v] - (float)var5)`. `field3037[v]` is the raw
   perspective divisor — view-space depth in world units
   (`Model.java:1300-1301`: `modelViewportXs = x + vx * zoom / var22;
   field3037[v] = var22`). So the client pulls a biased face **`faceBias * 2`
   world units** toward the camera, identically at every distance, and
   applies it **only to the depth value** — screen x/y still divide by the
   unbiased depth.

   Ours (both `GpuPriority.biasedDepth` and the GL vertex shader) instead did
   RuneLite's `screenPos.z += bias / 128.0` in clip space. Against this
   renderer's projection (`z_ndc = A + B/d`, `B ≈ 32`) that is equivalent to
   a world-space offset of `bias * d / 4097` — i.e. it *shrinks as the camera
   gets closer*. At d≈500 it is ~16x weaker than the client's, which is
   precisely the zoomed-in case where a flush decal most needs it. Fixed in
   both renderers to `depth - faceBias * 2`, with the GL path rewriting
   `z_clip = uDepthA*depth + uDepthB*(depth/biasedDepth)` so `w` stays at the
   true depth and screen position is untouched. Pinned by new
   `GpuPriorityTest` cases asserting the offset is distance-invariant.

2. **Shape 8 stacked two coplanar decoration models.** `FriendSystem.java:663-680`
   builds a shape-8 wall decoration as `newWallDecoration(..., renderable1 =
   getEntity(4, rot+4), renderable2 = getEntity(4, ((rot+2)&3)+4), 256, rot,
   xOffset, zOffset, ...)`. At draw time (`Scene.java:1918-1940`)
   `renderable1` is drawn **with** the displacement (`x*4096 + xOffset`,
   `y*64 + zOffset`) and `renderable2` **without any offset** (`x*4096`,
   `y*64`). We gave both pieces the same diagonal displacement, placing two
   near-coplanar models on top of each other — a guaranteed z-fight on every
   double-diagonal wall decoration. Fixed: the second variant now carries no
   displacement.

3. **Wall decorations get a minimum depth bias.** Because the client relies on
   paint order rather than depth, a shape-4 decoration (placed with *no*
   displacement at all) is exactly coplanar with its wall, and its cache face
   bias is frequently 0. Under a depth buffer the two surfaces then differ
   only by float rounding, which resolves per pixel as speckle. `GpuUploadPlanBuilder`
   now applies `Math.max(1, faceBias)` to `WALL_DECORATION` commands —
   stating explicitly what the client got implicitly, mirroring the existing
   `terrainDepthBias` overlay-over-underlay pattern in the same file.

### Confirmed divergent, NOT fixed — camera-dependent visibility

The client masks walls and wall decorations by which side of the tile the
camera is on. `Scene.java:1867-1881` computes a quadrant index from the
camera's tile vs the drawn tile:

```java
var21 = 0;
if (tileX == cameraXTile) ++var21; else if (cameraXTile < tileX) var21 += 2;
if (tileY == cameraYTile) var21 += 3; else if (cameraYTile > tileY) var21 += 6;
var12 = field2845[var21];            // Scene.java:277
```

with `field2845 = {19, 55, 38, 155, 255, 110, 137, 205, 76}` (and the
companion tables `field2912`, `field2847`, `field2878/2842/2932/2935` at
`:278-283`). A wall segment is drawn only if `(orientationA & var12) != 0`,
and likewise `orientationB`. Wall decorations use the same mask, and the
`orientation == 256` family (shapes 6, 7, 8) takes a separate branch that
picks **exactly one** renderable by a half-plane test on the camera offset:

```java
var18 = (orientation2 != 1 && orientation2 != 2) ?  dx : -dx;
var19 = (orientation2 != 2 && orientation2 != 3) ?  dz : -dz;
if (var19 < var18) draw(renderable1, x + xOffset, ...);
else if (renderable2 != null) draw(renderable2, x, ...);   // no offset
```

For shapes 6 and 7 `renderable2` is null, so those decorations are simply
**not drawn at all** from one side. We draw everything from every angle.

Two caveats before implementing this: (a) much of the wall mask is painter's-
algorithm *ordering* machinery for a renderer with no depth buffer, so it
should not be copied wholesale into a z-buffered path without deciding, per
case, whether it is hiding geometry or merely sequencing it; (b) the draw
calls use asymmetric units (`x * 4096` against `y * 64` for boundary objects
and wall decorations, versus `x * 64`/`y * 64` for floor decorations), so the
half-plane test's operands must be derived from the deob rather than assumed.

The natural home for this is `GpuCommandVisibility`, which already computes a
per-frame bitset keyed on `(plan, camera)`; it would need commands to carry
the decoration's orientation and which alternative they are.
