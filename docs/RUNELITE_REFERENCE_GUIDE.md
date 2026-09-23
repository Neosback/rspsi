# RuneLite / OSRS Client Reference Guide

> **Purpose:** fast navigation guide for OpenRune Studio correctness work.
>
> Use this before searching the vendored RuneLite tree from scratch. It maps common Studio problems to the most useful RuneLite/deob source, API concept, mixin/GPU implementation, and supporting cache reference.
>
> The vendored RuneLite tree is **read-only reference material**, not a build dependency.

## 1. The short rule

Use the sources for different questions:

| Question | Primary source |
|---|---|
| What content actually exists in this OSRS revision/region? | Real OSRS cache fixture |
| How does the real client interpret/build it? | `RuneLite-melxin/runescape-client` deobfuscated client |
| What is the established semantic concept/name? | `RuneLite-melxin/runelite-api` |
| How does RuneLite expose/intercept that client state? | `RuneLite-melxin/runelite-mixins` |
| How does RuneLite upload/draw it on the GPU? | `RuneLite-melxin/runelite-client/.../plugins/gpu` |
| How can we inspect/decode/cache-tool it cleanly? | OpenRune FileStore and, secondarily, RuneLite `cache` module |
| What proves Studio is correct? | Real fixture + semantic comparison + renderer comparison where needed |

Do **not** start renderer debugging in the GPU plugin if the problem may already exist in cache decode, object resolution, scene construction, plane handling, or tile semantics.

A useful mental model:

```
REAL CACHE
   |
   | what is authored?
   v
RUNESCAPE CLIENT DEOB
   |
   | how is it interpreted?
   v
RUNELITE API / MIXINS
   |
   | how is the semantic state named/exposed?
   v
RUNELITE GPU
   |
   | how is already-resolved scene data submitted?
   v
STUDIO SEMANTIC + RENDER PIPELINE
```

## 2. Vendored tree map

### Real client behavior

```
RuneLite-melxin/runescape-client/src/main/java/
```

This is the first stop for actual OSRS client semantics.

Important classes currently confirmed in the vendored tree:

- `ObjectComposition.java`
- `DynamicObject.java`
- `Scene.java`
- `Tile.java`
- `SceneTilePaint.java`
- `SceneTileModel.java`
- `ModelData.java`
- `Model.java`
- `Rasterizer3D.java`
- `TextureProvider.java`
- `Occluder.java`
- `Projection.java`
- `Tiles.java`
- `class470.java`

Many surrounding classes remain obfuscated. Prefer exported names, known fields/methods, and caller relationships over memorizing obfuscated class numbers.

### Semantic/API vocabulary

```
RuneLite-melxin/runelite-api/src/main/java/net/runelite/api/
```

Useful starting concepts:

- `Tile.java`
- `Scene.java`
- `SceneTilePaint.java`
- `SceneTileModel.java`
- `TileObject.java`
- `GameObject.java`
- `WallObject.java`
- `DecorativeObject.java`
- `ObjectComposition.java`
- `DynamicObject.java`
- `Model.java`
- `ModelData.java`
- `Perspective.java`
- `Projection.java`
- `coords/WorldPoint.java`
- `coords/LocalPoint.java`

Use this tree to understand established RuneLite terminology and relationships.

Do not treat the API interface alone as implementation proof.

### RuneLite mixins

```
RuneLite-melxin/runelite-mixins/src/main/java/net/runelite/mixins/
```

Known useful example:

- `RSModelMixin.java`

Use mixins when investigating how RuneLite exposes client internals, clickboxes, model behavior, injected fields, or semantic hooks.

### RuneLite GPU path

```
RuneLite-melxin/runelite-client/src/main/java/net/runelite/client/plugins/gpu/
```

Confirmed useful files:

- `GpuPlugin.java`
- `SceneUploader.java`
- `Zone.java`
- `TextureManager.java`

Supporting callback layer:

- `RuneLite-melxin/runelite-client/src/main/java/net/runelite/client/callback/RenderCallbackManager.java`

Use these only after scene semantics are understood.

### RuneLite cache utilities

```
RuneLite-melxin/cache/src/main/java/net/runelite/cache/
```

Known useful readable implementation:

- `MapImageDumper.java`
- `TextureManager.java`

This code can be easier to read than nearby deobfuscated client code for cache-oriented algorithms, but final live-client behavior should still be checked against the deob where relevant.

## 3. Problem-to-source lookup table

### Object missing, `null`, wrong model, wrong transform, wrong size

Start here:

1. `runescape-client/.../ObjectComposition.java`
2. `runescape-client/.../DynamicObject.java`
3. `runescape-client/.../Scene.java`
4. real cache placement + object definition

Specific methods/areas already identified:

#### `ObjectComposition.java`

- `transform()`
  - one client transform step
  - multiloc/varbit/varp resolution
- `getModelData(...)`
  - loc shape/model-type selection
  - model IDs
  - orientation
  - recolor/retexture
  - resize/offset preparation
- `getEntity(...)`
- `getModel(...)`
- `getModelDynamic(...)`
- model availability helpers such as `needsModelFiles()`

Important fields include:

- `modelIds`
- `models`
- `retextureFrom` / `retextureTo`
- model scale fields
- clipping/contouring state

#### `DynamicObject.java`

Start with:

- `getModel()`

The current pinned client behavior shows that the dynamic render path:

1. gets the placed object definition;
2. performs one `transform()` step where required;
3. uses the transformed/display definition to construct visible model appearance;
4. samples heights and dimensions for visible placement;
5. uses animation state carried by the dynamic object path.

This is the primary reference for Studio's placed-definition versus display-definition split.

#### `Scene.java`

Relevant scene insertion entry points include:

- `newFloorDecoration(...)`
- `newBoundaryObject(...)`
- `newWallDecoration(...)`
- game-object insertion methods near these methods

Use these to determine which scene layer a loc ultimately occupies and what coordinates/orientation/flags are retained.

If a real authored object exists but disappears in Studio, trace:

```
authored placement
 -> placed definition
 -> transform
 -> selected loc shape/model
 -> ModelData
 -> Model
 -> scene object layer
 -> Studio packet
 -> Studio submission
 -> visibility
```

Do not infer object count from screenshots. Decode the real region.

---

### Tile floor has wrong color, texture, shape, or interior appearance

Start here:

1. `runescape-client/.../class470.java`, method `method9712(WorldView)`
2. `runescape-client/.../Scene.java`, method `addTile(...)`
3. `runescape-client/.../SceneTilePaint.java`
4. `runescape-client/.../SceneTileModel.java`
5. `cache/.../MapImageDumper.java` as readable cross-check
6. `Rasterizer3D.java` / `TextureProvider.java` when texture/color behavior is involved

#### `class470.method9712(WorldView)`

This is a high-value starting point for terrain scene construction.

The currently pinned source shows it:

- computes/combines underlay/overlay color inputs;
- queries average texture color;
- calls `Scene.addTile(...)`;
- sets tile minimum planes;
- calls `Scene.setLinkBelow(...)` for bridge-linked tiles.

If an interior floor looks too dark, too bright, or simply wrong, inspect the semantic values produced here before touching Studio shader/color tuning.

#### `Scene.addTile(...)`

This selects between:

- `SceneTilePaint`
- `SceneTileModel`

and passes the resolved corner colors, heights, texture, shape, rotation, and minimap color data into the scene representation.

#### `SceneTilePaint.java`

Use for simple quad tile surfaces.

Important semantic state includes:

- four corner colors
- texture
- flatness
- RGB/minimap color

#### `SceneTileModel.java`

Use for shaped overlay/underlay geometry.

Important areas:

- shape-dependent vertex tables
- face construction
- rotation
- per-face color
- texture IDs

Do not debug a shaped-overlay issue using only the simple paint path.

---

### Bridge, floor plane, roof, render-level, or "wrong floor selected"

Start here:

1. `runescape-client/.../Scene.java`
2. `runescape-client/.../class470.java`
3. `runescape-client/.../Tile.java`
4. `runelite-api/.../Tile.java`
5. `runelite-client/.../plugins/gpu/SceneUploader.java`

Key client methods:

- `Scene.setLinkBelow(...)`
- `Scene.setTileMinPlane(...)`
- scene plane/min-plane state
- tile bridge linkage

The GPU uploader is useful as a secondary confirmation. Its zone upload path explicitly checks bridge flags and traverses `Tile.getBridge()` / render-level semantics.

For Studio, keep distinct concepts where needed:

- authored plane
- effective/client plane
- render level
- cull/visibility plane

Do not collapse them into one ambiguous `plane` integer.

---

### Object contouring, scale, placement height, preview framing

Start here:

1. `ObjectComposition.getModelData(...)`
2. `ObjectComposition.getModel(...)`
3. `ObjectComposition.getModelDynamic(...)`
4. `ModelData.java`
5. `Model.java`
6. `DynamicObject.getModel()`

Look for:

- resize/model scale;
- orientation transforms;
- offsets/translations;
- contour-ground behavior;
- model local bounds;
- tile-height sampling;
- scene/world anchor conversion.

For GPU coordinate conventions, cross-check:

- `runelite-client/.../plugins/gpu/SceneUploader.java`
  - static model upload
  - object X/Y/Z placement
  - orientation
  - tile-local versus scene-local offsets

Do not let Object Studio invent a second transform interpretation. It should consume the same Studio object-resolution result used by Map Studio.

---

### Object or tile is semantically correct but not visible

First prove the object/tile reached the resolved scene.

Then inspect:

1. `runescape-client/.../Scene.java`
2. `runescape-client/.../Occluder.java`
3. `runelite-client/.../callback/RenderCallbackManager.java`
4. `runelite-client/.../plugins/gpu/SceneUploader.java`
5. `runelite-client/.../plugins/gpu/GpuPlugin.java`

Questions to answer in order:

1. Was it authored?
2. Was the definition resolved?
3. Was model/terrain geometry produced?
4. Was a scene object/tile created?
5. Was it submitted?
6. Was it filtered by render level/roof/bridge visibility?
7. Was it occluded/culled?
8. Did the GPU receive it?

This order prevents a renderer investigation from hiding an earlier semantic failure.

---

### GPU upload, zone residency, incremental rebuild, duplicate uploads

Start here:

- `runelite-client/.../plugins/gpu/SceneUploader.java`
- `runelite-client/.../plugins/gpu/GpuPlugin.java`
- `runelite-client/.../plugins/gpu/Zone.java`

`SceneUploader` currently exposes readable paths for:

- zone-level upload;
- bridge traversal;
- tile paint upload;
- tile model upload;
- wall/decorative/ground/game object upload;
- static model upload;
- texture/UV packing.

`GpuPlugin` is useful for:

- draw callbacks;
- scene draw setup;
- opaque/alpha passes;
- dynamic renderables;
- render levels;
- framebuffer/viewport behavior.

`Zone.java` is the reference for 8x8-zone residency/invalidation ideas already used by Studio.

Do not copy RuneLite's live-client GPU architecture wholesale. Use it to validate behavior and proven techniques.

---

### Picking, clickbox, hover, projection, world/local conversion

Start with semantic/API references:

- `runelite-api/.../Perspective.java`
- `runelite-api/.../Projection.java`
- `runelite-api/.../TileObject.java`
- `runelite-api/.../coords/WorldPoint.java`
- `runelite-api/.../coords/LocalPoint.java`
- `runelite-mixins/.../RSModelMixin.java`

For model silhouette/reference click geometry:

- `RSModelMixin.java`
- RuneLite API model helpers

Studio should normally expose the result as its own canonical `SurfaceHit` rather than forcing plugins to recreate RuneLite projection/clickbox calculations.

Use RuneLite to answer:

- coordinate convention;
- semantic object placement;
- model geometry relationship;
- clickbox/hull behavior.

Use Studio's own exact triangle picker for final Studio hit behavior.

---

### Texture or UV mismatch

Start here:

1. `runescape-client/.../TextureProvider.java`
2. `runescape-client/.../Rasterizer3D.java`
3. `runelite-client/.../plugins/gpu/TextureManager.java`
4. `runelite-client/.../plugins/gpu/SceneUploader.java`
5. `cache/.../TextureManager.java`

For tile texture IDs, also inspect:

- `SceneTilePaint`
- `SceneTileModel`
- `class470.method9712`

For object model textures, inspect:

- `ObjectComposition.getModelData`
- `ModelData`
- `Model`

Separate:

- definition texture ID;
- model face texture ID;
- terrain texture ID;
- texture material/image decode;
- UV generation;
- shader sampling.

Do not treat these as one layer.

---

### Terrain underlay/overlay color blend mismatch

Best starting pair:

- `runescape-client/.../class470.java`, `method9712`
- `cache/.../MapImageDumper.java`

`MapImageDumper` is especially useful because it presents an unobfuscated cache-oriented implementation of terrain color blending that is easier to follow.

The deob client remains the behavioral authority for the live scene path.

---

### Scene API / plugin API design question

Start in:

```
RuneLite-melxin/runelite-api/src/main/java/net/runelite/api/
```

High-value concepts:

- `Tile`
- `SceneTilePaint`
- `SceneTileModel`
- `TileObject`
- `GameObject`
- `WallObject`
- `DecorativeObject`
- `ObjectComposition`
- `Model`
- `Perspective`

Then read `docs/STUDIO_SEMANTIC_API.md`.

Rule:

> RuneLite API is a vocabulary/design reference. The deob is implementation evidence. Studio still owns its public API.

Do not expose RuneLite's GPU buffer offsets, live-client mutation model, ticks, actors, widgets, menus, or networking simply because those APIs exist.

---

### Plugin settings / configuration UI pattern

Useful design reference:

- `runelite-client/.../config/ConfigItem.java`
- RuneLite `ConfigManager`

Use this for declarative configuration ideas only.

Studio's UI surfaces and permission model remain defined by `UI_WORKSPACE_CONTRACT.md`.

---

### Model convex hull / silhouette

Confirmed references:

- `runelite-mixins/.../RSModelMixin.java`
- `runelite-api/.../model/Jarvis.java`

Studio already has related ported logic in:

- `Client/src/main/java/com/rspsi/editor/render/ConvexHull2D.java`

Do not independently port it again.

## 4. Near-term PR source map

### PR A - Object completeness diagnostics

Start with:

```
runescape-client/ObjectComposition.java
    transform()
    getModelData()
    getModel()
    getModelDynamic()

runescape-client/DynamicObject.java
    getModel()

runescape-client/Scene.java
    object insertion paths

runescape-client/Tile.java

runelite-api/ObjectComposition.java
runelite-api/TileObject.java
runelite-api/GameObject.java
```

Then validate against the real Lumbridge region fixture.

Questions PR A must answer:

- what loc was authored?
- what placed definition was decoded?
- did one transform occur?
- what display definition resulted?
- what loc shape/model type was requested?
- which model IDs were selected?
- did geometry decode?
- what scene layer received it?
- did Studio create a packet?
- did Studio submit it?
- was it visible?
- did stable placed identity survive?

### PR B - SurfaceHit and picking

Start with:

```
runelite-api/Perspective.java
runelite-api/Projection.java
runelite-api/Tile.java
runelite-api/TileObject.java
runelite-api/coords/WorldPoint.java
runelite-api/coords/LocalPoint.java
runelite-mixins/RSModelMixin.java
```

Then compare against Studio's existing exact-triangle picker.

Do not begin by copying RuneLite clickbox API signatures.

### PR C - Tile semantic views / interior parity

Start with:

```
runescape-client/class470.java
    method9712()

runescape-client/Scene.java
    addTile()
    setTileMinPlane()
    setLinkBelow()

runescape-client/SceneTilePaint.java
runescape-client/SceneTileModel.java
runescape-client/Rasterizer3D.java
runescape-client/TextureProvider.java

cache/MapImageDumper.java

runelite-client/plugins/gpu/SceneUploader.java
```

Semantic comparison comes before shader/pixel tweaking.

### PR D - Object preview correctness

Start with:

```
runescape-client/ObjectComposition.java
runescape-client/DynamicObject.java
runescape-client/ModelData.java
runescape-client/Model.java
runelite-client/plugins/gpu/SceneUploader.java
```

Reuse PR A resolution output.

Do not create an Object-Studio-only definition/model resolver.

## 5. How to navigate the deob efficiently

The deob contains many `classNNN` names that can move between revisions.

Prefer these navigation strategies:

### 5.1 Start from exported/named classes

Examples:

- `ObjectComposition`
- `DynamicObject`
- `Scene`
- `SceneTileModel`
- `SceneTilePaint`
- `ModelData`
- `Model`

### 5.2 Search for stable exported method names

Useful search terms include:

```
transform(
getModelDynamic(
getModelData(
addTile(
newFloorDecoration(
newBoundaryObject(
newWallDecoration(
setLinkBelow(
setTileMinPlane(
Scene_addOccluder
```

### 5.3 Follow call sites outward

When an obfuscated method is discovered, record:

- exact file;
- exact method;
- stable called method/export nearby;
- what inputs/outputs mean;
- which fixture proved the interpretation.

That makes the next investigation independent of memorizing the obfuscated class number.

### 5.4 Search semantic constants/flags

For scene issues, useful terms include:

- bridge tile flag;
- render level;
- min plane;
- occluder;
- tile settings/render flags;
- scene tile paint/model;
- object shape/model type.

### 5.5 Cross-check with readable RuneLite code

If a deob method is hard to understand, search:

- RuneLite cache module;
- RuneLite GPU uploader;
- RuneLite API;
- RuneLite mixins.

Use those to understand the operation, then return to the deob for the actual client behavior.

## 6. Evidence rule for OpenRune Studio PRs

Any PR claiming OSRS semantic parity should identify the evidence explicitly.

Good:

```
Reference:
RuneLite-melxin/runescape-client/src/main/java/ObjectComposition.java
ObjectComposition.transform()

Fixture:
OSRS rev 240, region 50,50, authored loc <id/coord>
```

Also good:

```
Reference:
RuneLite-melxin/runescape-client/src/main/java/class470.java
method9712(WorldView)

Cross-check:
RuneLite-melxin/cache/src/main/java/net/runelite/cache/MapImageDumper.java

Fixture:
pinned castle interior tile
```

Bad:

```
"RuneLite does this."
```

The exact file/method requirement exists so future work does not repeat the same research.

## 7. What not to waste time doing

Do not:

- browse public RuneLite first when the vendored snapshot already contains the behavior we are validating;
- use RuneLite API interfaces as proof of how the client constructs a scene;
- use GPU upload code as proof of cache/scene semantics;
- assume an object is absent because Studio did not display it;
- guess object counts from screenshots;
- patch colors in the shader before comparing tile semantic inputs;
- recursively resolve multilocs when the pinned client path performs one transform step in the relevant render path;
- copy live-client setters/mutation APIs into Studio;
- add RuneLite as a build dependency;
- create a second Studio object/tile resolver for a specialty workspace.

## 8. Source ownership summary

```
Real OSRS cache
    owns: authored truth

RuneScape client deob
    owns: client interpretation semantics

RuneLite API
    owns: useful established vocabulary

RuneLite mixins
    owns: RuneLite exposure/injection examples

RuneLite GPU
    owns: RuneLite rendering/submission implementation examples

OpenRune FileStore
    owns: Studio's primary cache decode/encode/build tooling direction

OpenRune Studio
    owns: editor semantics, project model, transactions,
          authored/resolved API, preview, undo/redo, persistence
```

This distinction should be preserved in code review and future documentation.
