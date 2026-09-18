# RSPSi Deobfuscation & Renaming Audit

This document provides a comprehensive, categorized inventory of obfuscated, decompiled, and auto-generated identifiers (`method\d+`, `anInt\d+`, `aBoolean\d+`, `Class\d+`, etc.) remaining in **RSPSi-master**. Every item includes its current location, suggested clean name, type/signature, semantic rationale, and evidence from the reference oracles located in `/Users/tylercovalt/Documents/ChatGPT/RSPSi-resources` (primarily **RuneLite-melxin**, **TSPS**, **OpenRune-Editor-Neosback**, and **OpenRune-FileStore**).

---

## Table of Contents

1. [Architectural Overview & Reference Oracles](#1-architectural-overview--reference-oracles)
2. [Class-Level Renaming & Decompiler Heritage](#2-class-level-renaming--decompiler-heritage)
3. [Scene Graph & Occlusion Pipeline](#3-scene-graph--occlusion-pipeline)
   - [SceneCluster.java](#sceneclusterjava)
   - [SceneGraph.java](#scenegraphjava)
   - [SceneTile.java](#scenetilejava)
   - [ShapedTile.java](#shapedtilejava)
   - [Wall.java & GameObject.java](#walljava--gameobjectjava)
   - [RenderableObject.java](#renderableobjectjava)
4. [Model & 3D Rasterization Pipeline](#4-model--3d-rasterization-pipeline)
   - [Mesh.java](#meshjava)
   - [GameRasterizer.java](#gamerasterizerjava)
   - [PreviewModel.java & ObjectDefinition.java](#previewmodeljava--objectdefinitionjava)
5. [Cache Definitions & World Map Elements](#5-cache-definitions--world-map-elements)
   - [TextureDef.java](#texturedefjava)
   - [RSArea.java & OsrsAreaLoader.java](#rsareajava--osrsarealoaderjava)
6. [Terrain Synthesis & Chunk Loading](#6-terrain-synthesis--chunk-loading)
   - [MapRegion.java](#mapregionjava)
   - [Chunk.java & BasicChunk.java](#chunkjava--basicchunkjava)
   - [MapTile.java](#maptilejava)
7. [Client Runtime & Camera Navigation](#7-client-runtime--camera-navigation)
   - [Client.java](#clientjava)
8. [I/O & Utility Cleanup](#8-io--utility-cleanup)
   - [Sprite.java](#spritejava)
   - [Buffer.java](#bufferjava)
9. [Refactoring Priority & Action Plan](#9-refactoring-priority--action-plan)

---

## 1. Architectural Overview & Reference Oracles

The core 3D scene, terrain, model rasterization, and cache pipeline in RSPSi originates from a legacy Jagex 317/rev-revision client base that was decompiled with Jad/Jode and underwent partial community refactoring. Many fundamental math functions, traversal loops, visibility matrices, and occluder fields retained raw deob names.

To establish authentic names and correct semantics, this audit cross-references:

| Resource | Path | Role / Oracle Function |
|---|---|---|
| **RuneLite-melxin** | `RSPSi-resources/RuneLite-melxin` | Primary oracle for scene traversal (`Scene.java`), occluders (`Occluder.java`), tile structures (`Tile.java`, `SceneTileModel.java`), model rendering (`Model.java`), and client exports (`net.runelite.rs.api`). |
| **TSPS** | `RSPSi-resources/TSPS` | Modern terrain, height calculation, scene raycasting, and bridge/instance semantics. |
| **OpenRune-Editor-Neosback** | `RSPSi-resources/OpenRune-Editor-Neosback` | Modern TypeScript/WebGL scene builder (`SceneBuilder.ts`), tile models (`SceneTileModel.ts`), and terrain lighting. |
| **OpenRune-FileStore** | `RSPSi-resources/OpenRune-FileStore` | Production cache decoders for textures (`TextureCodec.kt`), world map areas, and definition loaders. |
| **OSRS-Map-Editor-Loading-240-rev** | `RSPSi-resources/OSRS-Map-Editor-Loading-240-rev` | Cache and config format baseline. |

---

## 2. Class-Level Renaming & Decompiler Heritage

### 2.1 Misnamed / Obfuscated Classes

| Current Class Name | File Location | Suggested Name | Semantic Role & Oracle Citation |
|---|---|---|---|
| `SceneCluster` | [`SceneCluster.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/SceneCluster.java) | `Occluder` *(or `CullingCluster`)* | **Explicit FIXME in source**: `// FIXME crap name`. Represents a 3D bounding occlusion box/plane used to cull invisible geometry behind walls or terrain. Exactly matches RuneLite's `Occluder.java` and `RSOccluder.java`. |
| `RSArea` | [`RSArea.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/def/RSArea.java) | `WorldMapElement` *(or `WorldMapElementDefinition`)* | Misleadingly named `RSArea`. Decodes OSRS config archive containing world map elements, icons, map label texts, and category IDs. Matches RuneLite's `WorldMapElement.java` and `net.runelite.cache.definitions.WorldMapElementDefinition`. |
| `TextureDef` | [`TextureDef.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/def/TextureDef.java) | `TextureDefinition` *(or `LegacyTextureConfig`)* | Incomplete, deob-named config loader for `textures.dat`. Should be renamed to `TextureDefinition` to align with modern clean nomenclature and `TextureDefinitionView`. |

### 2.2 Legacy Jad Decompilation Heritage Markers

Several core classes contain Jad decompiler header comments left over from raw obfuscated class names (`Class1`, `Class30`, etc.):

| File | Legacy Comment | Actual Semantic Class | Suggested Action |
|---|---|---|---|
| [`HashTable.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/link/HashTable.java#L8) | `// Class1` | Hash table with separate chaining | Remove artifact comment |
| [`BZip2Decompressor.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/BZip2Decompressor.java#L8) | `// Class13` | BZip2 Huffman stream decompressor | Remove artifact comment |
| [`Linkable.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/link/Linkable.java#L5) | `// Class30` | Doubly-linked list node (`Node` in RuneLite) | Remove artifact comment |
| [`Renderable.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/Renderable.java#L12) | `// Class30_Sub2_Sub4` | Base 3D renderable entity | Remove artifact comment |
| [`Mesh.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/model/Mesh.java#L25) | `// Class30_Sub2_Sub4_Sub6` | 3D Model mesh | Remove artifact comment |
| [`BZip2DecompressionState.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/BZip2DecompressionState.java#L11) | `// Class32` | BZip2 internal decode state (`DState`) | Remove artifact comment |
| [`VertexNormal.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/model/VertexNormal.java#L9) | `// Class33` | 3D vertex normal accumulator | Remove artifact comment |
| [`Vector3.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/model/Vector3.java#L11) | `// Class33` | 3D spatial coordinate vector | Remove artifact comment |

---

## 3. Scene Graph & Occlusion Pipeline

### `SceneCluster.java`
**Location**: [`Client/src/main/java/com/jagex/map/SceneCluster.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/SceneCluster.java)  
Every single member of this class is currently an obfuscated `anInt\d+` field. This class directly corresponds to **RuneLite `Occluder.java`**.

| Current Name | Proposed Name | Type | Description & Purpose | RuneLite Oracle |
|---|---|---|---|---|
| `anInt787` | `minTileX` | `int` | Minimum scene tile coordinate X (tile grid space: `worldX / 128`). | `Occluder.minTileX` |
| `anInt788` | `maxTileX` | `int` | Maximum scene tile coordinate X (tile grid space: `worldX / 128`). | `Occluder.maxTileX` |
| `anInt789` | `minTileZ` *(or `minTileY`)* | `int` | Minimum scene tile coordinate Z (grid Y/Z: `worldZ / 128`). | `Occluder.minTileY` |
| `anInt790` | `maxTileZ` *(or `maxTileY`)* | `int` | Maximum scene tile coordinate Z (grid Y/Z: `worldZ / 128`). | `Occluder.maxTileY` |
| `anInt791` | `type` | `int` | Occluder orientation plane type: `1` = East/West (X plane), `2` = North/South (Z plane), `4` = Ground/Ceiling (Y/height plane). | `Occluder.type` |
| `anInt792` | `minX` | `int` | Minimum world X coordinate in 3D scene units. | `Occluder.minX` |
| `anInt793` | `maxX` | `int` | Maximum world X coordinate in 3D scene units. | `Occluder.maxX` |
| `anInt794` | `minZ` | `int` | Minimum world Z coordinate in 3D scene units. | `Occluder.minZ` |
| `anInt795` | `maxZ` | `int` | Maximum world Z coordinate in 3D scene units. | `Occluder.maxZ` |
| `anInt796` | `minY` *(or `minHeight`)* | `int` | Minimum height/Y coordinate in 3D scene units. | `Occluder.minY` |
| `anInt797` | `maxY` *(or `maxHeight`)* | `int` | Maximum height/Y coordinate in 3D scene units. | `Occluder.maxY` |
| `anInt798` | `cullDirection` *(or `mode`)* | `int` | Camera facing direction relative to occluder: `1`=East, `2`=West, `3`=North, `4`=South, `5`=Top. | `Occluder.field2984` |
| `anInt799` | `minNormalX` | `int` | Projected normal slope for minimum X: `(minX - camX << 8) / delta`. | `Occluder.field2976` |
| `anInt800` | `maxNormalX` | `int` | Projected normal slope for maximum X: `(maxX - camX << 8) / delta`. | `Occluder.field2986` |
| `anInt801` | `minNormalZ` | `int` | Projected normal slope for minimum Z: `(minZ - camZ << 8) / delta`. | `Occluder.field2991` |
| `anInt802` | `maxNormalZ` | `int` | Projected normal slope for maximum Z: `(maxZ - camZ << 8) / delta`. | `Occluder.field2988` |
| `anInt803` | `minNormalY` | `int` | Projected normal slope for minimum Y (height): `(minY - camY << 8) / delta`. | `Occluder.field2989` |
| `anInt804` | `maxNormalY` | `int` | Projected normal slope for maximum Y (height): `(maxY - camY << 8) / delta`. | `Occluder.field2990` |

---

### `SceneGraph.java`
**Location**: [`Client/src/main/java/com/jagex/map/SceneGraph.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/SceneGraph.java)  
Corresponds to **RuneLite `Scene.java`** and 317 `WorldController`. RSPSi has 62 deob identifiers in this critical class.

#### Methods in `SceneGraph.java`

| Current Method | Proposed Method | Signature | Purpose & Explanation | RuneLite Oracle |
|---|---|---|---|---|
| `method276` | `setLinkBelow` *(or `initBridge`)* | `(int x, int y)` | Lowers planes `0..2` when a bridge tile is present on plane 1, linking the lower tile via `tileBelow` (`linkedBelowTile`). | `Scene.setLinkBelow` (`@Export("setLinkBelow")`) |
| `method277` | `addOccluder` | `(int plane, int minX, int maxX, int minZ, int maxZ, int minY, int maxY, int type)` | Instantiates and registers an `Occluder` into the plane's occluder array. | `Scene.Scene_addOccluder` (`@Export("Scene_addOccluder")`) |
| `method306` | `mergeGroundDecorationNormals` | `(Mesh model, int x, int y, int z)` | Welds and blends vertex normals between ground decorations on adjacent tiles to eliminate lighting seams. | `SceneBuilder` lighting pass |
| `method307` | `mergeAdjacentNormals` | `(int plane, int sizeX, int sizeY, int startX, int startY, Mesh model)` | Blends vertex normals between a world object and surrounding walls, ground decorations, and adjacent objects. | `SceneBuilder` lighting pass |
| `method310` | `buildVisibilityMap` | `(int minDepth, int maxDepth, int viewWidth, int viewHeight, int[] pitchOffsets)` | Precalculates 4D tile frustum visibility lookups (`aBooleanArrayArrayArrayArray491`) across all camera pitch/yaw permutations. | 317 `initCulling` / `setupViewport` |
| `method311` | `isPointInViewport` | `(int worldY, int worldX, int worldZ)` | Projects a 3D world coordinate against camera rotation angles and tests bounds against the viewport rectangle. | 317 `isPointVisible` |
| `method319` | `occlude` *(or `updateActiveOccluders`)* | `()` | Iterates active plane occluders, culls those outside the field of view, calculates slopes, and builds the active occluder list. | `Scene.occlude` (`@Export("occlude")`) |
| `method320` | `isTileOccluded` | `(int x, int y, int z)` | Tests if all 4 corners of a terrain tile are fully obscured by active occluders. | `Scene.method5801` |
| `method321` | `isWallOccluded` | `(int x, int y, int z, int orientation)` | Tests whether a wall segment at the specified tile and orientation is completely occluded from the camera. | `Scene.method5624` |
| `method322` | `isDecorationOccluded` | `(int plane, int x, int y, int height)` | Tests whether a wall or ground decoration is completely behind active occluders. | `Scene.method5625` |
| `method323` | `isObjectOccluded` | `(int plane, int minX, int maxX, int minY, int maxY, int height)` | Tests if the bounding footprint of a multi-tile game object is occluded. | `Scene.method5626` |
| `method324` | `isPointOccluded` | `(int worldX, int worldY, int height)` | Tests whether an individual 3D point is hidden behind any active occluder polygon. | `Scene.method5627` |

#### Fields & Static Lookup Arrays in `SceneGraph.java`

| Current Name | Proposed Name | Type | Purpose & Reference |
|---|---|---|---|
| `anInt475` | `activeOccluderCount` | `int` | Count of occluders active for current camera viewpoint. Matches RuneLite `Scene_currentOccludersCount`. |
| `aClass47Array476` | `activeOccluders` | `SceneCluster[]` | Array of currently active occluders for this frame. Matches RuneLite `Scene_currentOccluders`. |
| `anInt446` | `visibleTileCount` | `int` | Count of tiles passing the frustum/visibility test per render frame. Reset to `0` before each draw loop; incremented per visible tile (SceneGraph line 2717). **Not** a frame cycle counter — confirmed by code. |
| `aBoolean1323` | `drawSecondary` *(or `tileVisible`)* | `boolean` | Tile secondary visibility flag. Matches RuneLite `Tile.drawSecondary`. |
| `anInt1325` | `drawGameObjectEdges` *(or `wallCullDirection`)* | `int` | Bitmask for wall edge culling direction. Matches RuneLite `Tile.drawGameObjectEdges`. |
| `anInt1326` | `wallUncullDirection` | `int` | Directional mask indicating which walls remain visible. Matches RuneLite `Tile.field2782`. |
| `anInt1327` | `wallCullOppositeDirection` | `int` | Opposite wall edge culling mask. Matches RuneLite `Tile.field2767`. |
| `anInt1328` | `cameraAngleMask` | `int` | Perspective bitmask determined by camera yaw/position. Matches RuneLite `Tile.field2758`. |
| `anInt276` | `wallOrientationMask` | `int` | Wall orientation config bits (1, 2, 4, 8, 16, 32, 64, 128). Matches RuneLite `BoundaryObject.flags`. |
| `anInt277` | `wallDrawnCycle` | `int` | Cycle timestamp when the wall was last rendered. Prevents duplicate draw calls. |
| `anInt527` | `tileSortDistance` *(or `sortDistance`)* | `int` | Depth sorting metric used by painter's algorithm. Matches RuneLite `GameObject.field3203`. |
| `anInt1654` | `modelHeight` | `int` | Base model vertical height used for tile roof / item clearance calculations. |
| `anInt488` | `mergeCycle` *(or `mergeTag`)* | `int` | Monotonically increasing tag to prevent duplicate vertex normal merging. |
| `anIntArray486` | `mergeVertexTagsA` | `int[]` | Vertex allocation tracking array for primary model during normal merging. |
| `anIntArray487` | `mergeVertexTagsB` | `int[]` | Vertex allocation tracking array for secondary model during normal merging. |
| `anInt493` | `viewportCenterX` | `int` | Viewport center horizontal offset (`viewportWidth / 2`). |
| `anInt494` | `viewportCenterY` | `int` | Viewport center vertical offset (`viewportHeight / 2`). |
| `anInt495` | `viewportMinX` | `int` | Viewport left boundary (default `0`). |
| `anInt496` | `viewportMinY` | `int` | Viewport top boundary (default `0`). |
| `anInt497` | `viewportMaxX` | `int` | Viewport right boundary (default `viewportWidth`). |
| `anInt498` | `viewportMaxY` | `int` | Viewport bottom boundary (default `viewportHeight`). |
| `anIntArray463` | `WALL_DECORATION_INSET_X` | `static final int[]` | `{53, -53, -53, 53}`: Horizontal offset for type 6 corner wall decorations. |
| `anIntArray464` | `WALL_DECORATION_INSET_Y` | `static final int[]` | `{-53, -53, 53, 53}`: Depth offset for type 6 corner wall decorations. |
| `anIntArray465` | `WALL_DECORATION_OUTSET_X` | `static final int[]` | `{-45, 45, 45, -45}`: Horizontal offset for type 7 corner wall decorations. |
| `anIntArray466` | `WALL_DECORATION_OUTSET_Y` | `static final int[]` | `{45, 45, -45, -45}`: Depth offset for type 7 corner wall decorations. |
| `anIntArray478` | `WALL_DRAW_FLAGS` | `static final int[]` | `{19, 55, 38, ...}`: Wall render visibility flags indexed by 3x3 camera angle grid. Matches RuneLite `Scene.field2845`. |
| `anIntArray479` | `WALL_CULL_FLAGS` | `static final int[]` | `{160, 192, 80, ...}`: Wall edge cull bitmask indexed by camera angle. Matches RuneLite `Scene.field2912`. |
| `anIntArray480` | `CAMERA_ANGLE_MASKS` | `static final int[]` | `{76, 8, 137, ...}`: Perspective angle bitmasks stored into `tile.anInt1328`. Matches RuneLite `Scene.field2847`. |
| `anIntArray481` | `DIAGONAL_WALL_MASKS_1` | `static final int[]` | `{0, 0, 2, ...}`: Traversal flags for diagonal wall type 16 (NW). Matches RuneLite `Scene.field2878`. |
| `anIntArray482` | `DIAGONAL_WALL_MASKS_2` | `static final int[]` | `{2, 0, 0, ...}`: Traversal flags for diagonal wall type 32 (NE). Matches RuneLite `Scene.field2842`. |
| `anIntArray483` | `DIAGONAL_WALL_MASKS_3` | `static final int[]` | `{0, 4, 4, ...}`: Traversal flags for diagonal wall type 64 (SE). Matches RuneLite `Scene.field2932`. |
| `anIntArray484` | `DIAGONAL_WALL_MASKS_4` | `static final int[]` | `{1, 1, 0, ...}`: Traversal flags for diagonal wall type 128 (SW). Matches RuneLite `Scene.field2935`. |
| `anIntArrayArrayArray445` | `tileHeights` | `int[][][]` | `[planes][width + 1][length + 1]`: Exact duplicate of `MapRegion.tileHeights`. |
| `aBooleanArrayArray492` | `visibleTiles` | `boolean[][]` | Active camera visibility grid for current plane. |
| `aBooleanArrayArrayArrayArray491` | `visibilityMap` | `boolean[][][][]` | 4D precalculated frustum visibility matrix `[pitch][yaw][x][y]`. |

---

### `SceneTile.java`
**Location**: [`Client/src/main/java/com/jagex/map/tile/SceneTile.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/tile/SceneTile.java)  
Directly corresponds to **RuneLite `Tile.java`** (`RSTile.java`) and 317 `Ground`.

| Current Name | Proposed Name | Type | Semantic Explanation & Oracle Citation |
|---|---|---|---|
| `needsRendering` | `drawPrimary` | `boolean` | Primary flag: tile terrain must be drawn this frame. Matches RuneLite `Tile.drawPrimary`. *(Note: `needsRendering` is already a readable community name — renaming is optional but `drawPrimary` is the canonical RuneLite export.)* |
| `aBoolean1323` | `drawSecondary` *(or `visible`)* | `boolean` | Secondary flag indicating if tile underlay/paint is visible. Matches RuneLite `Tile.drawSecondary`. |
| `hasObjects` | `drawGameObjects` | `boolean` | True if tile objects should be rendered this cycle. Matches RuneLite `Tile.drawGameObjects`. |
| `tileBelow` | `linkedBelowTile` *(or `bridge`)* | `SceneTile` | Reference to the ground tile beneath a bridge. Matches RuneLite `Tile.linkedBelowTile`. |
| `anInt1310` | `minPlane` *(or `physicalLevel`)* | `int` | Minimum physical plane for traversal/collision. Matches RuneLite `Tile.minPlane`. |
| `anInt1325` | `drawGameObjectEdges` *(or `wallCullDirection`)* | `int` | Bitmask for wall edge culling. Matches RuneLite `Tile.drawGameObjectEdges`. |
| `anInt1326` | `wallUncullDirection` | `int` | Unculled wall edge direction bits. Matches RuneLite `Tile.field2782`. |
| `anInt1327` | `wallCullOppositeDirection` | `int` | Opposite wall edge cull mask. Matches RuneLite `Tile.field2767`. |
| `anInt1328` | `cameraAngleMask` | `int` | Perspective bitmask from `CAMERA_ANGLE_MASKS`. Matches RuneLite `Tile.field2758`. |

---

### `ShapedTile.java`
**Location**: [`Client/src/main/java/com/jagex/map/tile/ShapedTile.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/tile/ShapedTile.java)  
Corresponds to **RuneLite `SceneTileModel.java`** and 317 `Class40`.

| Current Name | Proposed Name | Type | Explanation |
|---|---|---|---|
| `anIntArray693 = { 1, 0 }` | `QUAD_VERTEX_INDICES_A` | `static int[]` | Deob constant for 2-triangle quad corner indexing. **Confirmed dead code** — no references outside `ShapedTile.java` itself; grep finds zero cross-file usages. Safe to remove. |
| `anIntArray694 = { 2, 1 }` | `QUAD_VERTEX_INDICES_B` | `static int[]` | Deob constant for second vertex corner indexing. **Confirmed dead code** — same as above. Safe to remove. |
| `anIntArray695 = { 3, 3 }` | `QUAD_VERTEX_INDICES_C` | `static int[]` | Deob constant for third vertex corner indexing. **Confirmed dead code** — same as above. Safe to remove. |

---

### `Wall.java` & `GameObject.java`
**Locations**:
- [`Client/src/main/java/com/jagex/map/object/Wall.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/object/Wall.java)
- [`Client/src/main/java/com/jagex/map/object/GameObject.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/object/GameObject.java)

| Class & Field | Proposed Name | Type | Purpose & Reference |
|---|---|---|---|
| `Wall.anInt276` | `orientationA` *(or `primaryOrientationMask`)* | `int` | Orientation bitmask for the **primary** wall renderable. Set from `anIntArray152[orientation]` or `anIntArray140[orientation]`. Bitwise-ANDed with camera angle mask to decide if the primary mesh is drawn. Matches RuneLite `BoundaryObject.orientationA`. |
| `Wall.anInt277` | `orientationB` *(or `secondaryOrientationMask`)* | `int` | Orientation bitmask for the **secondary** wall renderable. Passed as `anIntArray152[oppositeOrientation]` from `MapRegion`; tested with bitwise AND against camera angle masks, and passed to `method321()` just like `anInt276`. Matches RuneLite `BoundaryObject.orientationB`. **NOT a render cycle.** |
| `GameObject.anInt527` | `sortDistance` *(or `distanceFromStartX`)* | `int` | Distance key for depth sorting overlapping interactive objects. Matches RuneLite `GameObject.field3203`. |

---

### `RenderableObject.java`
**Location**: [`Client/src/main/java/com/jagex/entity/object/RenderableObject.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/object/RenderableObject.java)  
Corresponds to **RuneLite `DynamicObject.java`**. It holds the 4 corner tile heights used to incline/contour animated and morphing object models.

| Current Name | Proposed Name | Type | Purpose & Explanation |
|---|---|---|---|
| `anInt1603` | `heightSW` *(or `swTileHeight`)* | `int` | Southwest tile corner height passed to `ObjectDefinition.modelAt`. |
| `centre` | `heightSE` *(or `seTileHeight`)* | `int` | Southeast tile corner height (misnamed `centre` in legacy source). |
| `anInt1605` | `heightNE` *(or `neTileHeight`)* | `int` | Northeast tile corner height passed to `ObjectDefinition.modelAt`. |
| `anInt1606` | `heightNW` *(or `nwTileHeight`)* | `int` | Northwest tile corner height passed to `ObjectDefinition.modelAt`. |

---

## 4. Model & 3D Rasterization Pipeline

### `Mesh.java`
**Location**: [`Client/src/main/java/com/jagex/entity/model/Mesh.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/model/Mesh.java)  
Corresponds to **RuneLite `Model.java`** and 317 `Model`.

| Current Name | Proposed Name | Type / Signature | Explanation & Oracle Reference |
|---|---|---|---|
| `aBoolean1684` | `pickingEnabled` *(or `checkHover`)* | `static boolean` | Enables mouse intersection / hover picking when rendering faces. Matches 317 `Model.takingInput` / RuneLite picking. |
| `anInt1654` | `modelHeight` *(or `unscaledHeight`)* | `int` | Model height value assigned via `model.anInt1654 = model.getModelHeight()`. Used for roof clipping. |
| `anIntArray1622` | `sharedVertexX` | `static int[]` | Temporary scratch buffer for sharing vertex X coordinates in `method464`. |
| `anIntArray1623` | `sharedVertexY` | `static int[]` | Temporary scratch buffer for sharing vertex Y coordinates in `method464`. |
| `anIntArray1624` | `sharedVertexZ` | `static int[]` | Temporary scratch buffer for sharing vertex Z coordinates in `method464`. |
| `anIntArray1625` | `sharedFaceAlpha` | `static int[]` | Temporary scratch buffer for sharing face alpha values in `method464`. |
| `anIntArray1673` | `facePriorityCounts` | `int[12]` | Number of faces queued in each priority bucket (`0..11`) during depth sort. Matches RuneLite `bucketCounts`. |
| `anIntArrayArray1674` | `facePriorityBuckets` | `int[12][2000]` | 2D bucket array storing face indices sorted by priority. Matches RuneLite `bucketFaces`. |
| `anIntArray1675` | `priorityNearDistances` | `int[2000]` | Minimum distance limits per priority group during depth sorting. |
| `anIntArray1676` | `priorityFarDistances` | `int[2000]` | Maximum distance limits per priority group during depth sorting. |
| `anIntArray1677` | `priorityTotalDistances` | `int[12]` | Sum of face depths in bucket, used to calculate bucket average depth. |
| `anIntArray1678` | `clippedScreenX` | `int[10]` | Screen X coordinate output buffer for clipped polygon vertices. |
| `anIntArray1679` | `clippedScreenY` | `int[10]` | Screen Y coordinate output buffer for clipped polygon vertices. |
| `anIntArray1680` | `clippedVertexShades` | `int[10]` | Lighting/color intensity output buffer for clipped polygon vertices. |
| `method3027` | `rotateX` | `(int x, int z, int sin, int cos)` | Helper performing 2D axis rotation: `x * cos + sin * z >> 16`. |
| `method3028` | `rotateZ` | `(int x, int z, int sin, int cos)` | Helper performing 2D axis rotation: `cos * z - sin * x >> 16`. |
| `method464` | `shareVertices` *(or `copyMesh`)* | `(Mesh model, boolean shareAlphas)` | Clones a mesh while sharing static vertex and alpha arrays to save allocations. |
| `method485` | `drawClippedFace` *(or `clipAndDrawTriangle`)* | `(GameRasterizer rasterizer, int faceIndex)` | Performs near-plane Z-clipping on a face and passes resulting clipped triangles to rasterizer. |

---

### `GameRasterizer.java`
**Location**: [`Client/src/main/java/com/jagex/draw/raster/GameRasterizer.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/draw/raster/GameRasterizer.java)  
Corresponds to 317 `Rasterizer` / `Rasterizer3D` and RuneLite `RSRasterizer3D`.

| Current Name | Proposed Name | Type / Signature | Explanation |
|---|---|---|---|
| `method377` | `fillScanline` *(or `drawHorizontalLine`)* | `(int[] pixels, int offset, int color, int dummy, int startX, int endX)` | Fills a single horizontal pixel scanline with solid color `color`. Directly matches 317 `Rasterizer2D.drawHorizontalLine`. |
| `aBooleanArray1663` | `faceCullFlags` | `boolean[6500]` | Unused dead deob array (only declared, never read or written). Safe to delete. |
| `anInt1477` | `currentTextureIndex` | `int` | Unused dead deob field. Safe to delete. |
| `anInt1481` | `rasterizerFlags` | `int` | Unused dead deob field. Safe to delete. |
| `anIntArray1480` | `depthBuffer` | `int[50]` | Dead deob array (only initialized and nulled in `dispose()`). Safe to delete. |
| `anIntArray1673`..`1680` | *(same as Mesh)* | `int[]` / `int[][]` | Shared rasterizer face priority and polygon clipping buffers. |

---

### `PreviewModel.java` & `ObjectDefinition.java`
**Locations**:
- [`Client/src/main/java/com/jagex/entity/model/PreviewModel.java#L16`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/entity/model/PreviewModel.java#L16)
- [`Client/src/main/java/com/jagex/cache/def/ObjectDefinition.java#L239`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/def/ObjectDefinition.java#L239)

| Class & Identifier | Proposed Name | Purpose |
|---|---|---|
| `PreviewModel.anInt1654` | `modelHeight` | Copied from `model.anInt1654`. |
| `ObjectDefinition: model.anInt1654` | `model.modelHeight` | Set via `model.anInt1654 = model.getModelHeight()`. |

---

## 5. Cache Definitions & World Map Elements

### `TextureDef.java`
**Location**: [`Client/src/main/java/com/jagex/cache/def/TextureDef.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/def/TextureDef.java)  
This class decodes legacy 508+ `textures.dat`. It contains 19 obfuscated fields:

| Current Name | Proposed Name | Type | Purpose & Format |
|---|---|---|---|
| `aBoolean1223` | `isTransparent` | `boolean` | Transparency flag (read byte == 1). |
| `aBoolean1204` | `isLoaded` | `boolean` | Texture readiness flag. |
| `aBoolean1205` | `isBrightnessAdjusted` | `boolean` | Flag indicating gamma/brightness adjustment. |
| `aByte1217` | `textureType` *(or `intensity`)* | `byte` | Texture shader/material type. |
| `aByte1225` | `blendType` *(or `effectType`)* | `byte` | Alpha/color blending mode. |
| `aByte1214` | `blendParam1` | `byte` | First blend parameter. |
| `aByte1213` | `blendParam2` | `byte` | Second blend parameter. |
| `aShort1221` | `averageHsl` *(or `averageColor`)* | `short` | 16-bit average texture HSL color. Matches `TextureType.averageRgb` in OpenRune-FileStore. |
| `aByte1211` | `animationSpeed` | `byte` | Texture scroll speed. Matches `TextureType.animationSpeed`. |
| `aByte1203` | `animationDirection` | `byte` | Texture scroll direction (0=none, 1=down, 2=left, 3=up, 4=right). |
| `aBoolean1222` | `clampS` *(or `repeatS`)* | `boolean` | Horizontal texture coordinate wrapping/clamping. |
| `aBoolean1216` | `clampT` *(or `repeatT`)* | `boolean` | Vertical texture coordinate wrapping/clamping. |
| `aByte1207` | `mipmapping` | `byte` | Mipmap generation policy byte. |
| `aBoolean1212` | `useAlpha` | `boolean` | Whether texture has an alpha channel. |
| `aBoolean1210` | `isAlphaMask` | `boolean` | Alpha masking flag. |
| `aBoolean1215` | `isHd` | `boolean` | HD texture flag. |
| `anInt1202` | `spriteCount` | `int` | Number of sub-sprites comprising this texture. |
| `anInt1206` | `materialId` *(or `textureColor`)* | `int` | Combined 32-bit material color/ID. |
| `anInt1226` | `combineMode` | `int` | Texture combine operation mode. |
| `nullLoader()` | `clearCache()` | `static void` | Nulls the static `textures` array. |

---

### `RSArea.java` & `OsrsAreaLoader.java`
**Locations**:
- [`Client/src/main/java/com/jagex/cache/def/RSArea.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/def/RSArea.java)
- [`Client/src/main/java/com/rspsi/compat/osrs/OsrsAreaLoader.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/rspsi/compat/osrs/OsrsAreaLoader.java)

Corresponds to **RuneLite `WorldMapElement.java`** and `WorldMapElementDefinition`.

| Current Name | Proposed Name | Type | Opcode | Explanation & Oracle Reference |
|---|---|---|---|---|
| `anInt1967` | `sprite2Id` *(or `secondarySpriteId`)* | `int` | 2 | Secondary map icon sprite ID. Matches RuneLite `WorldMapElement.sprite2`. |
| `name` | `name` | `String` | 3 | Name of the world map element. Matches RuneLite `WorldMapElement.name`. |
| `anInt1959` | `fontColor` *(or `textColor`)* | `int` | 4 | Text display RGB color (read as 24-bit medium int). Matches RuneLite `WorldMapElement.field1993`. |
| `anInt1968` | `textSize` | `int` | 6 | Map label text font size. Matches RuneLite `WorldMapElement.textSize`. |
| `aStringArray1969` | `menuActions` | `String[5]` | 10..14 | Right-click context menu options. Matches RuneLite `WorldMapElement.menuActions`. |
| `anIntArray1982` | `coordinateOffsets` | `int[]` | 15 | Relative coordinate pairs for area boundaries. Matches RuneLite `WorldMapElement.field1999`. |
| `anIntArray1981` | `compositeElementIds` | `int[]` | 15 | Sub-element / linked element IDs. Matches RuneLite `WorldMapElement.field1995`. |
| `aByteArray1979` | `planeBytes` *(or `levels`)* | `byte[]` | 15 | Array of plane/height levels for the area elements. Matches RuneLite `WorldMapElement.field1998`. |
| `aString1970` | `menuTargetName` | `String` | 17 | Hover target label shown in right-click menus. Matches RuneLite `WorldMapElement.menuTargetName`. |
| `anInt1980` | `category` | `int` | 19 | Element category ID (read unsigned short). Matches RuneLite `WorldMapElement.category`. |

---

## 6. Terrain Synthesis & Chunk Loading

### `MapRegion.java`
**Location**: [`Client/src/main/java/com/jagex/map/MapRegion.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/MapRegion.java)  
Corresponds to 317 `ObjectManager` / `Region` and OpenRune-Editor `SceneBuilder.ts`.

| Current Name | Proposed Name | Type / Signature | Explanation & Oracle Reference |
|---|---|---|---|
| `method171` | `buildTerrain` *(or `addTileModels`)* | `(SceneGraph scene)` | Calculates directional tile lighting, smooths underlay HSL blending across 5x5 windows, and instantiates shaped/simple tiles into `SceneGraph`. Matches OpenRune-Editor `SceneBuilder.addTileModels`. |
| `method174` | `smoothBorderHeights` *(or `stitchSeamHeights`)* | `(int startX, int startY, int xLen, int yLen)` | Sets default shading to 127 and welds tile heights across region boundary edges. |
| `anIntArray140` | `DIAGONAL_WALL_MASKS` | `static final int[]` | `{16, 32, 64, 128}`: Bitmasks for diagonal wall placement (NW, NE, SE, SW). |
| `anIntArray152` | `STRAIGHT_WALL_MASKS` | `static final int[]` | `{1, 2, 4, 8}`: Bitmasks for cardinal wall placement (W, N, E, S). Explicit comment in source: `// orientation -> ??`. |
| `anIntArray128` | `underlaySampleCounts` | `int[length]` | Sliding sample count of underlays present in the radius-5 blending window. Matches RuneLite radius-5 window accumulator. |
| `anIntArrayArrayArray135` | `tileRenderFlags` | `int[4][w+1][l+1]` | Bitmask storing tile rendering attributes (e.g. `|= 0x924` for flat coplanar tiles with identical corner heights). |
| `anIntArray124` | `blendedHue` | `int` (local var) | Local variable in `method171` holding calculated tile hue. |
| `blended_anIntArray124` | `blendedHueSum` | `int` (local var) | Running sum of weighted hues across the 5x5 sliding window. |
| `blended_anIntArray124_divisor` | `blendedHueWeightSum` | `int` (local var) | Running sum of hue weight multipliers for normalization. |
| `blended_anIntArray125` | `blendedSaturationSum`| `int` (local var) | Running sum of saturation values across the sliding window. |
| `blended_anIntArray126` | `blendedLuminanceSum` | `int` (local var) | Running sum of lightness/luminance values across the sliding window. |
| `blend_direction_tracker` | `blendedSampleCount` | `int` (local var) | Effective sample count used as divisor for saturation and luminance averaging. |

---

### `Chunk.java` & `BasicChunk.java`
**Locations**:
- [`Client/src/main/java/com/jagex/chunk/Chunk.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/chunk/Chunk.java)
- [`Client/src/main/java/com/jagex/chunk/BasicChunk.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/chunk/BasicChunk.java)

| File & Method | Proposed Name | Signature | Purpose |
|---|---|---|---|
| `Chunk.method50` | `drawMinimapFeature` *(or `drawMinimapWall`)* | `(int x, int y, int z, int nullColor, int defaultColor)` | Draws wall lines and door symbols onto the chunk minimap raster based on object definitions and attributes. |
| `Chunk.method63` | `applyPermanentSpawns` | `()` | Processes object spawns where `longevity == -1` (permanent editor/server spawned objects). |
| `Chunk.method115` | `tickTemporarySpawns` | `()` | Ticks down longevity and delay timers on temporary spawned objects and unlinks expired ones. |
| `BasicChunk.method171` | `buildTerrain` | `(SceneGraph scene)` | Delegates to `mapRegion.method171(scene)`. |

---

### `MapTile.java`
**Location**: [`Editor/src/main/java/com/rspsi/game/map/MapTile.java#L124`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Editor/src/main/java/com/rspsi/game/map/MapTile.java#L124)

| Current Call | Proposed Call | Purpose |
|---|---|---|
| `mapRegion.method171(sceneGraph);` | `mapRegion.buildTerrain(sceneGraph);` | Rebuilds terrain lighting and tile models when a map tile is modified. |

---

## 7. Client Runtime & Camera Navigation

### `Client.java`
**Location**: [`Client/src/main/java/com/jagex/Client.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/Client.java)  
Primary game loop, input handler, and camera controller.

| Current Name | Proposed Name | Type / Signature | Explanation & Context |
|---|---|---|---|
| `method120()` | `getMaxVisiblePlane()` | `static int ()` | Returns `3` if all heights are visible, otherwise `Options.currentHeight.get()`. |
| `method121()` | `getVisiblePlane()` | `static int ()` | Duplicate of `method120()`. Can be merged or aliased. |
| `method51()` | `resumeRegionWorker()` | `void ()` | Sets `aBoolean831 = true` to signal the background map loading thread to resume. |
| `method54()` | `checkChunksLoaded()` | `boolean ()` | Iterates all chunks; if ready, transitions to `LoadState.ACTIVE` and fires `mapReadyListeners`. |
| `method118()` | `stopRegionWorker()` | `void ()` | Sets `aBoolean831 = false` and blocks in 50ms sleep loop until `aBoolean962` is false. |
| `method144` | `updateCameraAngle` | `(int distance, int pitch, int yaw)` | Calculates 3D camera offsets and curves based on spherical distance, pitch, and yaw. |
| `aBoolean831` | `regionWorkerRunning` | `volatile boolean` | Flag controlling whether the background region loading worker loop continues running. |
| `aBoolean962` | `regionWorkerActive` | `volatile boolean` | State flag indicating the background region loader is actively executing a task. |
| `aLong1220` | `lastClickTimestamp` | `long` | Timestamp of previous mouse click used to calculate click time delta: `(lastMouseClick - aLong1220) / 50`. |
| `anInt1014` | `cameraFollowX` | `int` | Interpolated camera X coordinate smoothly easing towards `anInt1278` (`targetCameraX`). |
| `anInt1015` | `cameraFollowY` | `int` | Interpolated camera Y coordinate smoothly easing towards `anInt1131` (`targetCameraY`). |
| `anInt1278` | `targetCameraX` | `int` | Target anchor camera X coordinate. |
| `anInt1131` | `targetCameraY` | `int` | Target anchor camera Y coordinate. |
| `anInt896` | `cameraYawOffset` | `int` | Additional rotation offset added to `cameraYaw`. |
| `anInt916` | `mouseCrossTimer` *(or `mouseCrossPulse`)* | `int` | Animation counter for mouse click cross (`+= 20; if (>= 400) anInt917 = 0`). |
| `anInt917` | `mouseCrossType` | `int` | Type of mouse cross (0=none, 1=red/interaction, 2=yellow/walk). |
| `anInt984` | `cameraHeightInterpolated` | `int` | Smoothed camera elevation easing between heights. |
| `anInt985` | `lastCameraPlane` | `int` | Previous camera plane used to detect plane transitions. |
| `anIntArray1203` | `planeCameraHeights` | `int[]` | Minimum camera height limits per plane. |
| `aLongArray7` | `frameTimestamps` | `long[10]` | Ring buffer of frame timestamps used for FPS smoothing. Each slot holds `System.currentTimeMillis()` for that frame index. |

---

## 8. I/O & Utility Cleanup

### `Sprite.java`
**Location**: [`Client/src/main/java/com/jagex/cache/graphics/Sprite.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/graphics/Sprite.java)

| Current Method | Proposed Method | Signature | Explanation |
|---|---|---|---|
| `method346(int x, int y)` | `drawSprite` *(or `drawClipped`)* | `(int x, int y)` | Draws sprite with viewport clipping to `GameRasterizer.getInstance()`. |
| `method346(GameRasterizer r, ...)` | `drawSprite` | `(GameRasterizer r, int x, int y)` | Overload accepting explicit rasterizer target. |
| `method347` | `blitTransparentBlock` | `(int destIndex, int width, ...)` | Private static copy loop transferring non-zero pixels from source raster into destination. |
| `method352` | `drawRotatedMasked` | `(int height, int theta, int[] mask, ...)` | Renders sprite rotated at angle `theta` within line mask bounds (used for minimap compass). |
| `method353` | `drawRotatedScaled` | `(int x, int y, int w, int h, double theta, ...)` | Renders sprite with arbitrary rotation angle and scaling factor `j1`. |

---

### `Buffer.java`
**Location**: [`Client/src/main/java/com/jagex/io/Buffer.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/io/Buffer.java)  
Contains legacy duplicate and obfuscated byte-reading methods. The "A", "S", "LE" variants originate from 317-era deob naming conventions where `A` = XOR with `128`, `S` = subtract from `128`, `LE` = little-endian:

| Current Method | Proposed Action / Name | Explanation |
|---|---|---|
| `readIMEInt()` | `readInverseMiddleEndianInt()` *(or `readIntV2()`)* | Reads 32-bit int in inverse middle-endian format (`B2, B1, B4, B3`). Marked `// V2`. |
| `readMEInt()` | `readMiddleEndianInt()` *(or `readIntV1()`)* | Reads 32-bit int in standard middle-endian format (`B3, B4, B1, B2`). Marked `// V1`. |
| `readShort2()` | `readSignedShort()` *(or consolidate with `readShort()`)* | Reads signed 16-bit short; nearly identical to `readShort()`. Used by `OsrsAnimationFrameLoader`. |
| `readByteS()` | `readNegatedByte()` | Returns `(byte)(128 - payload[pos++])` — 317-era "S" (subtracted) variant. |
| `readUByteA()` | `readUByteXored()` | Returns `(payload[pos++] - 128) & 0xFF` — 317-era "A" (XOR-128) variant. |
| `readUByteS()` | `readUByteNegated()` | Returns `(128 - payload[pos++]) & 0xFF` — 317-era "S" (subtracted) variant. |
| `readUShortA()` | `readUShortXored()` | Reads unsigned 16-bit big-endian short with low byte XOR'd by 128 — 317-era "A" variant. |
| `readLEShortA()` | `readLEShortXored()` | Little-endian signed short with XOR-128 low byte — 317-era "A" variant. |
| `readLEUShortA()` | `readLEUShortXored()` | Little-endian unsigned short with XOR-128 low byte — 317-era "A" variant. |
| `readNegByte()` | `readNegatedByte()` *(or unify with `readByteS()`)* | Returns `-payload[pos++]` as signed byte — effectively same pattern as `readByteS`. Consider merging. |
| `readNegUByte()` | `readNegatedUByte()` | Returns `-payload[pos++] & 0xFF` — negated unsigned byte. |
| `readUByte()` vs `readUnsignedByte()` | Unify to `readUnsignedByte()` | Two identical methods performing `payload[position++] & 0xFF`. |
| `readUShort()` vs `readUnsignedShort()` | Unify to `readUnsignedShort()` | Two identical methods performing unsigned short decoding. |
| `readString()` vs `readOSRSString()` vs `readStringAlternative()` | Clarify format names | `readNullTerminatedString()`, `readOsrsString()`, `readByteTerminatedString()`. |
| `getULEShort()` | `readUnsignedLittleEndianShort()` *(or `readULEShort()`)* | Uses `get` prefix inconsistently with the `read` convention used elsewhere. |

---

## 9. Additional Findings (New Sweep)

### `SpawnedObject.java` — Spelling Typo
**Location**: [`Client/src/main/java/com/jagex/map/object/SpawnedObject.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/object/SpawnedObject.java)

| Current Name | Proposed Name | Type | Explanation |
|---|---|---|---|
| `longetivity` | `longevity` | `int` | **Spelling error** — misspelled throughout the field, getter (`getLongetivity()`), and setter (`setLongetivity()`). Also misused in `Chunk.java`. Fix both field and generated Lombok accessors. |

---

### `RSArea.java` — Deob Getters via Lombok `@Getter`
**Location**: [`Client/src/main/java/com/jagex/cache/def/RSArea.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/cache/def/RSArea.java)  
The class uses `@Getter` / `@Setter`, so every `anInt1967`, `anIntArray1982` etc. field generates a deob getter name like `getAnInt1967()`. These need renaming at the field level (Lombok will then generate clean accessors).

| Current Field | Proposed Field | Proposed Getter | Used In |
|---|---|---|---|
| `anInt1967` | `sprite2Id` | `getSprite2Id()` | `OsrsAreaLoader.java` |
| `anInt1959` | `fontColor` | `getFontColor()` | `OsrsAreaLoader.java` |
| `anInt1968` | `textSize` | `getTextSize()` | `OsrsAreaLoader.java` |
| `anIntArray1982` | `coordinateOffsets` | `getCoordinateOffsets()` | `OsrsAreaLoader.java` |
| `anIntArray1981` | `compositeElementIds` | `getCompositeElementIds()` | `OsrsAreaLoader.java` |
| `aByteArray1979` | `planeBytes` | `getPlaneBytes()` | `OsrsAreaLoader.java` |
| `aString1970` | `menuTargetName` | `getMenuTargetName()` | `OsrsAreaLoader.java` |
| `aStringArray1969` | `menuActions` | `getMenuActions()` | `OsrsAreaLoader.java` |
| `anInt1980` | `category` | `getCategory()` | `OsrsAreaLoader.java` |

> **Note**: `OsrsAreaLoader.java` lines 101–103 also use raw deob local variable names (`anIntArray1982`, `anIntArray1981`, `aByteArray1979`) which should be updated when the field names are fixed.

---

### `MapRegion.java` — Additional Naming Issues
**Location**: [`Client/src/main/java/com/jagex/map/MapRegion.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/map/MapRegion.java)

| Current Name | Proposed Name | Type | Explanation |
|---|---|---|---|
| `SINE_VERTICIES` | `SINE_VERTICES` | `static final int[]` | **Spelling error** — `VERTICIES` → `VERTICES`. |
| `underlay_floor_map_color` | `underlayFloorMapColor` | `int` | Inconsistent `snake_case` in Java field — should be `lowerCamelCase`. |
| `underlay_floor_texture` | `underlayFloorTexture` | `int` | Same `snake_case` convention error. |
| `save_terrain_tile` | `saveTerrainTile` | `void method` | `snake_case` method name in Java — should be `lowerCamelCase`. |
| `anIntArray128` | `underlaySampleCounts` | `int[]` | Already listed; confirmed as radius-5 underlay accumulator. |
| `anIntArrayArrayArray135` | `tileRenderFlags` | `int[][][]` | Already listed. |

---

### `ObjectState.java` — Dead Field
**Location**: [`Client/src/main/java/com/rspsi/game/save/object/state/ObjectState.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/rspsi/game/save/object/state/ObjectState.java)

| Current Name | Action | Explanation |
|---|---|---|
| `private byte shading = -1;` | **Delete** | Self-annotated `// XXX UNUSED`. The `preserve()` method does set it from `mapRegion.shading[z][x][y]`, but the field is never read back after being set. Safe to remove. |

---

### `Chunk.java` — Renamed-but-Uncleaned Methods
**Location**: [`Client/src/main/java/com/jagex/chunk/Chunk.java`](file:///Users/tylercovalt/Documents/ChatGPT/RSPSi-master/Client/src/main/java/com/jagex/chunk/Chunk.java)

The deob method names here are NOT listed in the audit because `method50`, `method63`, `method115` are defined directly in this class. The audit lists them under `Chunk.java` in section 6 but the class still uses raw names internally. Confirmed needed:

| Current Name | Proposed Name | Explanation |
|---|---|---|
| `method50(int x, int y, int z, int nullColour, int defaultColour)` | `drawMinimapWalls` | Draws wall lines and door symbols onto the minimap raster. |
| `method63()` | `applyPermanentSpawns` | Processes permanent spawns (`longevity == -1`). |
| `method115()` | `tickSpawns` | Ticks down spawn longevity/delay timers; removes expired spawns. |

---

## 10. Refactoring Priority & Action Plan

To ensure refactoring is safe and does not break rendering, the changes should be executed in three phased stages:

```
┌─────────────────────────────────────────────────────────────┐
│ Phase 1: High-Impact Occlusion & Scene Traversal (Zero Risk) │
│ - Rename SceneCluster -> Occluder and anInt787..804          │
│ - Rename SceneTile deob flags (drawPrimary, drawSecondary)  │
│ - Rename Wall (orientationA, orientationB) & GameObject     │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ Phase 2: Scene Building, Normal Blending & Model Pipeline    │
│ - Rename SceneGraph methods (setLinkBelow, addOccluder, etc)│
│ - Rename MapRegion method171 (buildTerrain) & blend counters │
│ - Fix MapRegion typo SINE_VERTICIES, snake_case fields       │
│ - Rename Mesh vertex sharing, clipping, and rotation helpers │
│ - Clean up dead arrays in GameRasterizer and ShapedTile      │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ Phase 3: Cache Definitions, Client Runtime & I/O Hygiene    │
│ - Rename RSArea fields -> WorldMapElement clean names        │
│ - Fix RSArea Lombok deob getters (setAnIntArray1982 etc.)    │
│ - Rename TextureDef -> TextureDefinition and field members   │
│ - Fix SpawnedObject longetivity -> longevity typo            │
│ - Delete ObjectState.shading dead field (XXX UNUSED)        │
│ - Rename Client.java camera easing & worker control methods  │
│ - Expand Buffer.java: rename A/S/LE variant methods          │
│ - Clean up Buffer.java duplicate methods & Jad comments      │
│ - Remove Jad artifact Class## comments from all classes     │
└──────────────────────────────┬──────────────────────────────┘
```

### Safety & Verification Notes
1. **No Behavior Changes**: Renaming these identifiers is purely cosmetic / structural refactoring. Bitwise operations, mathematical constants, and data formats remain identical.
2. **Compilation**: After each phase, `gradle compileJava` and `gradle test` should be executed to verify complete symbol replacement across both `Client` and `Editor` subprojects.
3. **Dead Code Elimination**: Arrays like `anIntArray1480` in `GameRasterizer` and `anIntArray693..695` in `ShapedTile` are completely unread and can be removed cleanly.
