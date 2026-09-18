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
