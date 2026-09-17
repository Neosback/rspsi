# Terrain topology parity

This document records the first direct comparison between the RSPSi-owned
terrain mesh builder and the pinned TSPS scene-tile reference.

## Captured reference

- Repository: [RSPSApp/TSPS](https://github.com/RSPSApp/TSPS)
- Commit: `83415f76589a360eacbd0e635fe0557d06a510f0`
- File: `client/rs/scene/SceneTileModel.ts`
- Local research checkout: `../RSPSi-resources/TSPS`
- Compared RSPSi file: `Client/src/main/java/com/rspsi/editor/terrain/TerrainMeshBuilder.java`

## Result

At the captured commit, a source-level comparison found:

- 13 of 13 shape-point rows equal;
- 13 of 13 face-topology rows equal;
- 52 topology/rotation combinations represented by both implementations.

The existing `TerrainMeshGoldenTest` and
`TerrainSharedEdgeInvariantTest` protect the RSPSi implementation in CI
without requiring the external checkout to be present.

This is intentionally a topology result, not a claim of complete scene
parity. The following remain separate work: per-vertex HSL/light calculation,
underlay blending, overlay/texture material selection, hidden-face behavior,
bridge/render-level handling, region neighbors, collision, minimap output,
and final renderer comparison against TSPS and RuneLite.

The renderer-neutral scene path now carries a definition-aware
`TerrainMaterial` per tile (underlay/overlay IDs, texture ID, and RGB inputs)
when a neutral definition provider is available. This is the material input
boundary for future scene parity; it does not yet claim lighting, texture
animation, hidden-face, or rendered-image equivalence.

The neutral scene now also carries per-corner `TerrainLight` values using
TSPS's directional constants (`-50, -10, -50`, ambient `96`) and height-normal
calculation. Object light occlusion and rendered-light image comparison are
not yet included in that baseline.

The neutral minimap builder also exposes an opt-in 4×4-per-tile raster using
the captured TSPS shaped-tile masks and rotation permutations. When floor
definitions provide HSL blend metadata, that path uses the OSRS radius-5
weighted hue/saturation/luminance blend and palette conversion. This locks
the neutral geometry/material baseline. It also follows TSPS's scene boundary,
vertical image orientation, render-flag visibility, bridge demotion, and
wall-marker ordering. An independently generated TSPS build-240 fixture for
region `(50,50)` now compares exactly on planes 2 and 3; planes 0 and 1 still
have bounded differences because the OpenRune adapter does not yet expose
graphics-defaults sprite groups and the full location decoration asset path is
not implemented yet. The captured build-240 cache also has no
graphics-defaults map-scene group, and the OpenRune provider therefore
reports zero real map-scene sprites. The neutral `MapSceneSpriteView` boundary
and synthetic composition tests are in place so that work can proceed without
changing editor APIs. This is useful,
executable progress, not a claim of complete map/minimap or live RuneLite
image parity.

The current verifier evidence is:

- 256×256 shaped minimap rasters compared for all four planes;
- 0 differing pixels on planes 2 and 3;
- 9,616 differences on plane 0 and 1,488 on plane 1, concentrated in the
  remaining map-scene/location decoration path;
- cache identity, revision, terrain/location decoding, bridge links, scene
  construction, and decode→encode→decode semantic equality all pass for the
  fixture's OpenRune-backed build-240 cache.

The fixture is generated and retained outside the product repository under
the resource-intake workflow. No TSPS source, cache dump, or generated asset
is copied into the product.

No TSPS source or generated asset is copied into the product.
