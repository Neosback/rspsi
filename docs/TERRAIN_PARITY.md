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

The neutral minimap builder also exposes an opt-in 4×4-per-tile raster using
the captured TSPS shaped-tile masks and rotation permutations. When floor
definitions provide HSL blend metadata, that path uses the OSRS radius-5
weighted hue/saturation/luminance blend and palette conversion. This locks
the neutral geometry/material baseline without claiming mapscene or live
RuneLite image parity; those remain explicit `NOT_RUN` verifier checks until
licensed reference fixtures are available.

No TSPS source or generated asset is copied into the product.
