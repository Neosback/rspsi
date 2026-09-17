# RSPSi Resource Intake — 2026-09-16

This is the first evidence capture for the OpenRune resource plan. It records
what was inspected and what RSPSi is allowed to take from it. The checkouts
remain outside the RSPSi source tree and are not build dependencies.

## Upstream OpenRune-Server comparison

- Upstream checkout: [OpenRune/OpenRune-Server](https://github.com/OpenRune/OpenRune-Server)
- Captured commit: `72e8e1a1a05c54208f64c163cae4637301397d90`
- Local checkout: `../RSPSi-resources/OpenRune-Server`
- Intended role: baseline donor/oracle for comparing the Neosback fork's
  cache-backed engine and world semantics.
- License evidence: checked-out `LICENSE.md` grants BSD-2-Clause rights and
  identifies the `RS Mod` 2025 copyright. Any inherited file still requires
  file-level provenance review before adaptation.

### Focused comparison evidence

- `engine/map` contains 22 Kotlin source files in both checkouts; no focused
  source additions were found in the fork.
- `engine/routefinder` contains 20 Kotlin source files in both checkouts; the
  fork changes `CollisionFlagMap.remove` so removing a mask materializes the
  tile through the map accessor instead of returning when its zone is absent.
- `or-cache` contains 187 upstream Kotlin/Java source files and 186 in the
  fork. The map decoder, location decoder, terrain encoder, and map packer
  paths inspected for RSPSi are byte-for-byte unchanged. The fork's other
  differences are primarily generated server-cache tables/tooling and are not
  eligible for direct editor adoption.

RSPSi therefore keeps the upstream checkout as a comparison baseline and
continues to use only small, Java-owned collision/map semantics. No server
runtime, generated table, or fork-specific cache implementation is imported.

## OpenRune-Server Neosback fork

- Upstream checkout: [Neosback/OpenRune-Server](https://github.com/Neosback/OpenRune-Server)
- Captured commit: `bde85d0b0a5f7f87c1b8e9430fa81677bf443c9a`
- Local checkout: `../RSPSi-resources/OpenRune-Server-Neosback`
- Intended role: donor for OSRS cache-backed world semantics, coordinates,
  collision, routefinding, and map utilities.
- License evidence: checked-out `LICENSE.md` is BSD 2-Clause text and carries
  the `RS Mod` 2025 copyright notice. Any adapted file needs an additional
  provenance check for inherited upstream notices.

### Inspected modules and files

- `engine/map/src/main/kotlin/org/rsmod/map/CoordGrid.kt`
- `engine/map/src/main/kotlin/org/rsmod/map/square/MapSquareGrid.kt`
- `engine/map/src/main/kotlin/org/rsmod/map/square/MapSquareKey.kt`
- `engine/map/src/main/kotlin/org/rsmod/map/zone/ZoneGrid.kt`
- `engine/map/src/main/kotlin/org/rsmod/map/zone/ZoneKey.kt`
- `engine/map/src/main/kotlin/org/rsmod/map/util/Bounds.kt`
- `engine/map/src/main/kotlin/org/rsmod/map/util/Translation.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/StepValidator.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/LineValidator.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/collision/CollisionFlagMap.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/flag/CollisionFlag.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/loc/LocShapeConstants.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/loc/LocLayerConstants.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/util/Rotations.kt`
- `engine/routefinder/src/main/kotlin/org/rsmod/routefinder/flag/CollisionFlag.kt`
- `or-cache/src/main/kotlin/org/rsmod/game/map/collision/CollisionFlagMapExtensions.kt`
- `or-cache/src/main/kotlin/dev/openrune/map/GameMapDecoder.kt`
- `or-cache/src/main/kotlin/dev/openrune/map/tile/MapTileDecoder.kt`
- `or-cache/src/main/kotlin/dev/openrune/map/tile/MapTileByteEncoder.kt`
- `or-cache/src/main/kotlin/dev/openrune/map/loc/MapLocListDecoder.kt`
- `or-cache/src/main/kotlin/dev/openrune/map/packing/MapPackers.kt`
- `or-cache/src/main/kotlin/dev/openrune/ServerCacheManager.kt`

### RSPSi decision

Use these modules as focused semantic donors. Do not import Kotlin server
runtime, content, networking, player, combat, or tick systems. The first RSPSi
replacement seams are the Java-owned `MapIndexTable`/`MapService` APIs in
`com.rspsi.cache.map`; future collision and route APIs will be equally neutral.

Evidence currently proving adoption:

- `MapIndexTableTest` verifies named OSRS map archive discovery through
  `CacheStore`, without an OpenRune type in the test-facing API.
- `MapIndexLoaderOSRSTest` verifies the compatibility facade and stable map
  index export/import behavior.
- `OsrsLocShapeTest` verifies all 23 location-shape IDs and the exact
  wall/wall-decor/ground/ground-decor mapping from the inspected
  `LocShapeConstants.kt` and `LocLayerConstants.kt`.
- `ObjectInspectorSnapshotTest` verifies that definitions and collision are
  flattened into RSPSi-owned inspector data; `SelectionQueryTest` verifies
  category filtering without importing donor enums.
- `WorldRegionWindowTest` verifies bounded multi-region loading semantics and
  preserves absent regions as explicit loading-line holes.
- `OsrsCollisionBuilderTest`, `CollisionMapTest`, `ReachabilityTest`, and
  `RouteFinderTest` verify the neutral route-blocker masks, OpenRune's
  default-off versus explicit-on strategy, and swept movement for larger
  actors. `OpenRuneCollisionSemanticsTest` locks the composite masks against
  the inspected `StepValidator.kt`/`CollisionFlag.kt` source.

No OpenRune-Server source, cache dump, model, or generated asset was copied or
bundled in this intake.

## OpenRune-Editor Neosback fork

- Upstream checkout: [Neosback/OpenRune-Editor](https://github.com/Neosback/OpenRune-Editor)
- Captured commit: `1e5b41055da267ca94a615a0ec9853e21296b239`
- Local checkout: `../RSPSi-resources/OpenRune-Editor-Neosback`
- Intended role: primary donor for map-editor workflows, brush tools, history,
  region stamps, scene/picking behavior, and debug-oriented UX.
- License evidence: checked-out `LICENSE` is BSD 2-Clause with the 2022–2023
  `dennisdev` copyright notice. The repository README describes a map viewer,
  but the captured source contains a substantial map editor; source structure,
  not README wording, is the basis for this classification.

### Inspected modules and files

- `src/mapeditor/MapEditor.ts`
- `src/mapeditor/MapEditorRenderer.ts`
- `src/mapeditor/MapEditorWorkbench*`
- `src/mapeditor/editor-tool-input.ts`
- `src/mapeditor/map-editor-history*.ts`
- `src/mapeditor/map-editor-object-history.ts`
- `src/mapeditor/plugins/editor-plugin-host.ts`
- `src/mapeditor/plugins/builtins/height*`
- `src/mapeditor/plugins/builtins/smooth.plugin.tsx`
- `src/mapeditor/plugins/builtins/underlay.plugin.tsx`
- `src/mapeditor/plugins/builtins/overlay.plugin.tsx`
- `src/mapeditor/plugins/builtins/tile-flags*`
- `src/mapeditor/plugins/builtins/object-*`
- `src/mapeditor/plugins/builtins/region-stamp-*`
- `src/mapeditor/webgl/loader/*`
- `src/mapeditor/webgl/sceneLocPicker.ts`
- `src/mapeditor/webgl/scene-loc-height-sync.ts`
- `src/rs/scene/SceneBuilder.ts`
- `src/rs/scene/SceneTileModel.ts`
- `src/rs/scene/CollisionMap.ts`
- `src/rs/scene/Loc.ts`
- `src/rs/scene/Wall.ts`

### RSPSi decision

Adapt behavior into the existing Java-owned `EditorSession`,
`EditorCommand`, `EditorTool`, `WorldDocument`, `SceneRenderer`, and future
`WorldFragment` APIs. Do not copy the React, Tauri, WebGL, or unrestricted
docking architecture into RSPSi. No editor source, generated map tile, model,
or font asset was copied or bundled in this intake.

## Terrain and coordinate evidence

The captured TSPS checkout (`83415f76589a360eacbd0e635fe0557d06a510f0`)
was inspected at:

- `client/rs/scene/SceneTileModel.ts`
- `client/rs/scene/SceneBuilder.ts`
- `client/rs/scene/CollisionMap.ts`
- `client/common/instance/InstanceTypes.ts`

Its scene-tile model contains the 13 shaped-tile vertex-index rows and face
topology used by the RSPSi-owned `TerrainMeshBuilder`. The arrays match the
existing 52-case topology matrix; TSPS remains the donor/oracle and its code
is not copied into the product.

The reference-only helper [`tools/tsps/export-terrain-semantics.ts`](../tools/tsps/export-terrain-semantics.ts)
can export a pinned TSPS `SceneBuilder` region into the external
`terrain-semantics.json` fixture format. Build-240 region `(50,50)` was
compared through that fixture: all 16,384 tile height/underlay/overlay/
shape/rotation/flag fields matched RSPSi. The JSON export remains outside the
repository; only the generator and verifier contract are committed. The same
helper decodes TSPS location bytes into `locations.json`; build-240 region
`(50,50)` matched all 4,726 canonical ID/type/rotation/plane/coordinate tuples.
The same helper exports `scene-geometry.json` from TSPS's authored terrain
models; 4,481 populated tiles matched RSPSi vertex coordinates and face
topology. This is geometry evidence only; full lighting/material render
parity remains a separate gate.

The captured RuneLite checkout
(`ced4c4aba7a3cb7cace42e1f0c25a5f79b7faef`) was inspected at:

- `runelite-api/src/main/java/net/runelite/api/SceneTileModel.java`
- `runelite-api/src/main/java/net/runelite/api/coords/WorldPoint.java`
- `runelite-api/src/main/java/net/runelite/api/Scene.java`
- `runelite-api/src/main/java/net/runelite/api/Tile.java`
- `runelite-client/src/main/java/net/runelite/client/plugins/devtools/DevToolsOverlay.java`

The evidence confirms that tile shape/rotation, scene planes, bridge-aware
tile inspection, and instance template transforms are separate semantics. The
RuneLite chunk packing and four rotation cases are covered by
`InstanceChunkTemplateTest`, `InstanceChunkTransformTest`, and
`InstanceChunkGridTest` in RSPSi.

The TSPS `CollisionMap` was also exercised through the reference-only helper,
which now writes an optional external `collision.json` snapshot. That snapshot
is useful for inspecting client collision flags, but it is not treated as a
strict product parity oracle: TSPS gates location collision with `clipType`
and intentionally skips locations on its scene loading line, whereas
OpenRune-Server maps OSRS definitions through `solid`/`blockWalk` and adds its
routefinder layer. RSPSi preserves `clipType` in the neutral
`ObjectCollisionView` for client-scene diagnostics, while the canonical editor
collision builder follows OpenRune's `solid`/`blockWalk` mapping and keeps the
routefinder layer authoritative.

Provenance and evidence:

- Reference path: TSPS `client/rs/scene/CollisionMap.ts` and
  `client/rs/scene/Scene.ts` at commit `83415f76589a360eacbd0e635fe0557d06a510f0`.
- Behavior adopted: client collision bit vocabulary, floor/decor/location
  layers, wall direction families, and bridge-plane relinking diagnostics.
- RSPSi replacement API: `CollisionMap`, `CollisionFlag`,
  `OsrsCollisionBuilder`, and neutral `ObjectCollisionView`.
- Tests: `OsrsCollisionBuilderTest`, `OpenRuneStepValidatorParityTest`, and
  `OpenRuneCollisionSemanticsTest`; the live verifier reports the TSPS
  collision snapshot as diagnostic until a normalized RuneLite/OpenRune
  fixture is captured.

## Current production dependency evidence

The OpenRune FileStore compatibility spike is pinned to `2.4.19` in
`Client/build.gradle`, using `filesystem`, `filestore`, `osrs-fs`, `osrs`, and
`definition`. Its RSPSi adapter remains read-only. Read-only parity passed for
OpenRS2 cache 391/revision 6 and live build 240/cache 2710; modern output
reopening passed through a copied cache and the explicit Displee adapter. A
safe OpenRune-native writer is still not marked verified.

### Neutral cache-view adoption

The first loader-facing portion of the cache boundary is now implemented. The
inspected OpenRune FileStore behavior is archive/file enumeration plus byte
reads, represented in RSPSi by:

- `com.rspsi.cache.store.CacheStore`
- `com.rspsi.cache.store.CacheIndexView`
- `com.rspsi.cache.store.CacheArchiveView`

The RSPSi views intentionally expose only IDs, byte reads, and presence checks.
Displee `Index`, `Archive`, and `File` objects remain inside the legacy and
OpenRune adapter implementations. Client and OSRS compatibility loaders now
initialize through these neutral views; the deprecated raw index accessor is
retained only for the remaining legacy renderer compatibility path.

Provenance and evidence:

- Upstream references: OpenRune FileStore `filesystem/Cache.kt` and
  `tools/CacheDelegate.kt` at commit `236e3920aa077a5990f2915e74f1c7d7729db47e`.
- Behavior adopted: enumerate archive/file IDs, read archive bytes, preserve
  missing-file semantics, and layer pending/output data over a base cache.
- RSPSi replacement API: `CacheStore.fileIds`, `CacheIndexView`, and
  `CacheArchiveView`; no upstream cache type appears in the loader API.
- Tests: `CacheIndexViewTest`, the full Gradle test/check suite, and the live
  `verifyOsrsRevision` runs against OpenRS2 cache 391 and live build 240.

The revision audit now also records the neutral definition surface without
depending on backend types. Live build 240 region `(50,50)` reports 62,522
object IDs, 251 underlay IDs, 643 overlay IDs, 214 texture IDs, 62,040 model
IDs, and 265 map-scene sprite IDs; a sampled model-geometry decode also
passes. Focused partial-provider tests intentionally report missing families
as warnings. The
published OpenRune `tools` module is now an explicit cache-write dependency
only for `CacheDelegate`, with the unrelated CS2 compiler transitively
excluded; an external copied build-240 cache successfully persisted a modern
terrain edit through `openRuneWritable` and reopened through the read-only
OpenRune adapter.

This is a verified seam, not completion of the entire cache migration. The
legacy renderer and native FileStore writer remain explicitly quarantined or
deferred until their own parity gates pass; the separate published
`CacheDelegate` output path is covered by the focused live-cache integration
evidence.

### Neutral model geometry adoption

The pinned OpenRune FileStore `ModelType` fields were inspected at commit
`236e3920aa077a5990f2915e74f1c7d7729db47e`. RSPSi adopts only the geometry
needed by previews and future renderers: packed vertex XYZ positions, triangle
ABC indices, optional face colors, alpha values, and texture IDs. The
replacement boundary is `ModelGeometryView`; `OpenRuneDefinitionProvider`
decodes it lazily and caches only selected model results. No `ModelType` or
OpenRune mesh object crosses into editor code. `NeutralDefinitionContractsTest`
verifies defensive ownership and index validation, while the strict build-240
verifier reports `revision.definitions.modelGeometry: model 0 geometry
decoded`.

## Additional research checkouts captured

The remaining first-pass references are also checked out outside the RSPSi
tree as shallow research repositories. Their revisions are pinned here so a
future intake can reproduce the inspected source set:

| Resource | Commit | Role |
|---|---|---|
| [OpenRune FileStore](https://github.com/OpenRune/OpenRune-FileStore) | `236e3920aa077a5990f2915e74f1c7d7729db47e` | Production-candidate API and OSRS cache/definition reference |
| [OpenRune/OpenRune-Server](https://github.com/OpenRune/OpenRune-Server) | `72e8e1a1a05c54208f64c163cae4637301397d90` | Upstream baseline for focused Neosback server-fork comparison |
| [RSPSApp/TSPS](https://github.com/RSPSApp/TSPS) | `83415f76589a360eacbd0e635fe0557d06a510f0` | Terrain, scene, model, bridge, and instance donor/oracle |
| [RuneLite](https://github.com/runelite/runelite) | `ced4c4aba7a3cb7cace42e1f0c25a5f79b7faef` | Independent current-OSRS semantics and DevTools oracle |
| [RuneLite cache-code updater](https://github.com/runelite/runelite-cache-code-updater) | `a200d75bf779cdc76cae53e7e2cd6f23172e3535` | Revision-drift strategy reference |
| [Domw71 revision-240 editor](https://github.com/Domw71/OSRS-Map-Editor-Loading-240-rev) | `ddc360daaf3e60414e1822c5784531e3696096af` | Revision-240 forensic reference; no runtime dependency |
| [Explv OSRS map tiles](https://github.com/Explv/osrs_map_tiles) | `1e3d20bfccca800c7bdec6611655670323ae10f6` | Visual world-map reference only; generated assets remain outside the product |

The Explv checkout contains the repository commit and is not used as a
canonical data source. Its generated tile working tree was not fully
materialized because those assets are not currently licensed for distribution.

## Next intake actions

1. Build the TSPS/RuneLite 52-case terrain reference matrix.
2. Select a licensed external OSRS cache region with a non-empty location
   payload and run the neutral map service against it.
3. Record exact provenance beside every future adapted file and add a test or
   fixture demonstrating the behavior.

## Real-cache verification evidence

On 2026-09-16, OpenRS2 cache ids `391` and `2710` were downloaded outside the
repository and extracted to temporary paths. Their metadata reports OSRS
revision `6` and live build `240`, respectively. Running
`verifyOsrsRevision` against revision-6 region `30,74` passed named-map
opening, byte terrain decoding, a non-empty location payload, 26,469 neutral
asset descriptors, collision, scene construction, and semantic round trip.
Running it against live build-240 region `16,33` passed modern numeric-group
discovery, short terrain decoding, a 2,040-byte location payload containing
988 objects, 63,630 neutral asset descriptors, collision, scene construction,
and semantic round trip. A region-edge object is reported as a warning when
the verifier is operating on a single-region context. Neither cache was
copied into RSPSi and no generated assets were bundled.

The run also captured the revision boundary used by TSPS and
OpenRune-Editor: OSRS terrain opcodes and overlay values are byte-width before
revision 209 and short-width from revision 209 onward. RSPSi now records that
choice in `OsrsRevisionProfile` and carries it through map loading and save
encoding.
