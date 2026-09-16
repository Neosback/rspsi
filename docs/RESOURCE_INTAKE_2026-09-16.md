# RSPSi Resource Intake — 2026-09-16

This is the first evidence capture for the OpenRune resource plan. It records
what was inspected and what RSPSi is allowed to take from it. The checkouts
remain outside the RSPSi source tree and are not build dependencies.

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

## Current production dependency evidence

The OpenRune FileStore compatibility spike is pinned to `2.4.19` in
`Client/build.gradle`, using `filesystem`, `filestore`, `osrs-fs`, `osrs`, and
`definition`. Its RSPSi adapter remains read-only. Writable packing and real
cache parity are intentionally not marked verified.

## Next intake actions

1. Diff the Neosback server fork against its upstream OpenRune-Server parent
   before adapting collision or map algorithms.
2. Build the TSPS/RuneLite 52-case terrain reference matrix.
3. Add a licensed external OSRS cache fixture and run the neutral map service
   against it.
4. Record exact provenance beside every future adapted file and add a test or
   fixture demonstrating the behavior.
