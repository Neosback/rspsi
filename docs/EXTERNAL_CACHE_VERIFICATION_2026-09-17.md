# External cache verification — 2026-09-17

This record captures read-only verification against an independently sourced
OSRS revision-237 cache. The cache remains outside the RSPSi repository and is
not copied, bundled, or modified by these checks.

## Cache identity

- Source role: OpenRune/Elvarg cache workspace
- Revision: 237
- Profile: numeric map groups, short terrain values
- Cache fingerprint: `bfc7a5105d1357b1002e78b232535c19faf522c60dd615808e3fde26fc840499`
- Backend mode: OpenRune `READ_ONLY`
- Neutral asset descriptors: 137,275
- Definitions exposed: 60,840 objects, 233 underlays, 627 overlays, 208
  textures, 60,113 models, 257 map-scene sprites, 13,742 sequences, and
  1,255 map elements

## Commands

The verifier was run from the repository root with the cache path supplied
through `RSPSI_OSRS_CACHE` and the region coordinates supplied explicitly:

```text
RSPSI_OSRS_CACHE=/path/to/osrs-237 \
RSPSI_OSRS_REGION_X=16 RSPSI_OSRS_REGION_Y=33 RSPSI_OSRS_REVISION=237 \
./gradlew --no-daemon verifyOsrsRevision

RSPSI_OSRS_CACHE=/path/to/osrs-237 \
RSPSI_OSRS_REGION_X=50 RSPSI_OSRS_REGION_Y=50 RSPSI_OSRS_REVISION=237 \
./gradlew --no-daemon verifyOsrsRevision
```

## Repeatable representative matrix

The repository also exposes `verifyOsrsRevisionMatrix`. It accepts an explicit
`RSPSI_OSRS_PARITY_MANIFEST` JSON file so one evidence run can cover multiple
regions without hardcoding a cache path or reference checkout into the build.
Each entry has this shape:

```json
[
  {
    "cache": "/path/to/cache",
    "fixture": "/path/to/fixture",
    "regionX": 50,
    "regionY": 50,
    "revision": 240,
    "requireParity": false
  }
]
```

The manifest and referenced artifacts remain external and must be
provenance-controlled by the operator.

## Pinned TSPS build-240 semantic probes

The pinned TSPS checkout and its local revision-240 cache were used as an
independent source for terrain semantics, location tuples, and authored scene
geometry. The exporter records `geometry.planeMode=AUTHORED`; the verifier
resolves authored source planes through the canonical bridge/effective-plane
rule before comparing scene geometry.

The water-dominant region `(16,33)` has overlay `487` on all 4,096 plane-0
tiles. It passes terrain, location, authored geometry, semantic round-trip,
neutral scene round-trip, and boundary checks with zero semantic differences.
The wall-heavy region `(29,72)` contains 9,712 scene objects and passes the
same checks with zero terrain, location, and geometry differences. Collision
fixture differences remain diagnostic because TSPS client `clipType` values are
not the canonical OpenRune route flags; minimap and full render fixtures were
not exported by this probe.

## Results

Both runs passed cache opening, map-index discovery, definition access,
terrain/location decode, 9-region window construction, semantic
decode → encode → decode equality, neutral scene construction, minimap
construction, and neutral scene round-trip equality with zero differences.
The definition audit also indexed both newly supported config categories and
successfully decoded sample ID `0` for each through the neutral provider.

| Region | Scene evidence |
|---|---|
| `(16,33)` | 147,456 world-addressed terrain tiles, 988 object projections, 0 bridge links, 0 non-empty collision tiles, 16,384 terrain meshes, 4-plane semantic and shaped minimaps |
| `(50,50)` | 147,456 world-addressed terrain tiles, 4,726 object projections, 120 bridge links, 3,983 non-empty collision tiles, 16,384 terrain meshes, 4-plane semantic and shaped minimaps |
| `(49,49)` | 147,456 world-addressed terrain tiles, 2,040 object projections, 0 bridge links, 1,038 non-empty collision tiles, 16,384 terrain meshes, 4-plane semantic and shaped minimaps; 0 validation warnings |

## Independent RuneLite parity probe

The pinned RuneLite cache module at commit `ced4c4aba7a3` was built with Java
21 and run outside this repository against the same read-only cache. Its
`RegionLoader` exported a temporary fixture for region `(50,50)` containing
all 16,384 terrain tiles and all 4,726 location tuples. RSPSi then consumed
that fixture through `RSPSI_OSRS_PARITY_FIXTURE`.

Results:

- `PASS terrain.parity`: 0 differing height, underlay, overlay, shape,
  rotation, or flag fields.
- `PASS location.parity`: 0 differing placements.
- RuneLite independently reported 40 bridge-marked tiles and the same
  terrain/location counts as the RSPSi read.
- RuneLite’s 256×256 `MapImageDumper` raster was intentionally not promoted
  to a passing minimap fixture: its renderer includes different palette,
  object, and scene composition semantics. The trial failed with 65,518
  pixel differences, so no renderer parity claim is made.
- Scene geometry, collision, and full render fingerprint still require
  independent exports before the release gate can be marked complete. A later
  TSPS revision-240 sprite-bearing fixture verifies the neutral map-scene-aware
  minimap path; that result is recorded below.

The fixture and RuneLite cache remain outside the product repository. These
results promote independent terrain/location parity from “not run” to proven
for the selected revision-237 bridge-heavy region while keeping renderer
parity explicitly open.

## Independent TSPS scene probe — revision 240

A separate pinned TSPS checkout was run against its local revision-240 cache
and emitted temporary terrain semantics and scene-geometry fixtures for region
`(50,50)`. The fixtures remain outside this repository and were consumed by
the same read-only RSPSi verifier.

- Terrain semantics: pass, with all 16,384 height, underlay, overlay, shape,
  rotation, and render-flag fields matching.
- Scene geometry: pass, with all 4,441 populated geometry tiles matching.
  The verifier resolves each effective render plane back to its authored
  source when a bridge column has been relinked, so the comparison is against
  the same post-build scene state as TSPS.
- Location, collision, and minimap exports were not included in this probe.

The bridge-aware effective-plane resolution closes the geometry seam for this
fixture. Full lighting/material, collision, and renderer parity still need
their independent exports before those checks can become release gates. The
neutral minimap path was subsequently verified with a separate sprite-bearing
fixture described below.

The same exporter/verifier path was then run against revision-240 regions
`(16,33)` and `(49,49)`. Terrain semantics, location semantics, and authored
scene geometry passed for both. Region `(49,49)` is the generated-height
coverage case; it exposed and then verified the fixed OSRS world-noise offsets
and cosine-table interpolation in `OsrsRegionDecoder`. TSPS collision exports
declare `CLIENT_CLIP_TYPE` and remain diagnostic only because that client layer
is not the same as RSPSi's OpenRune route/collision policy. A future
independent fixture must declare `OPENRUNE_ROUTE` before it can become an
authoritative parity gate.

## OpenRune route-fixture probe — blocked by cache layout

On 2026-09-17, the pinned OpenRune-Server checkout was compiled and exercised
with its own `GameMapDecoder` and routefinder `CollisionFlagMap` against the
pinned TSPS revision-240 cache. Initialization reached the server definitions,
but the OpenRune object decoder expects config archive `55`; the TSPS cache's
config index exposes archives `1–54` and `70+`. As a result, the server object
table remained empty and the exporter could not produce a trustworthy
`OPENRUNE_ROUTE` fixture. This is a compatibility finding for the future
server-adapter layer, not a route-parity result. The existing TSPS collision
fixture therefore remains explicitly diagnostic with `CLIENT_CLIP_TYPE`.

## Independent TSPS instance probe — revision 240

The reference-only `tools/tsps/export-instance-fixture.ts` exporter generated
a deterministic 4-plane, 13×13-chunk instance using source region `(16,33)`.
The fixture remains outside the repository and was verified with
`verifyOsrsInstance` against the same read-only revision-240 cache.

- Packed template transforms: pass, 676 entries.
- Instance terrain: pass, zero differing height, underlay, overlay, shape,
  rotation, or flag fields.
- Instance objects: pass, zero differing transformed placements across 2,474
  exported objects.

This probe also closed two foundation bugs: region-local object anchors must be
converted back to world coordinates before chunk transforms, and generated
terrain opcodes must be replayed for the destination plane rather than copied
as already-materialized source heights.

The shaped minimap exporter now records whether the reference cache actually
provided map-scene sprites instead of assuming that the archive exists. The
revision-240 `(50,50)` capture has no reference map-scene sprite payload, so
the verifier correctly compares the terrain/wall path and reports zero pixel
differences across all four shaped planes. The same comparison now passes all
four planes with zero differing pixels for the representative revision-240
captures `(16,33)` (water/plain), `(29,72)` (wall-heavy), `(49,49)`
(generated-height), and `(50,50)` (bridge-heavy). The wall-heavy capture also
verifies that the neutral object-definition boundary preserves OSRS's explicit
interactivity field for red versus white wall markers. Sprite-bearing captures
remain a separate follow-up before declaring universal map-scene parity.

### Sprite-bearing map-scene follow-up

The TSPS revision-240 cache does contain a named `mapscene` sprite archive with
272 sprite entries, but its OSRS graphics-defaults record leaves that group
unset. The reference-only exporter now resolves the named archive as a
provenance-preserving fallback and records `minimap.mapScenes=true`.

Running the new fixture through RSPSi reaches the real sprite path: 127 placed
map-scene objects resolve to neutral sprite pixels and the verifier reports
terrain, location, and authored geometry parity. The shaped minimap comparison
now matches all four 256×256 planes with 0 differing pixels. The final fixes
aligned object draw ordering with TSPS's wall → game-object →
ground-decoration passes and applied TSPS's vertical map-scene centering term;
the earlier 947- then 878-pixel differences are eliminated. This verifies the
current neutral sprite-bearing minimap composition path, while full 3D renderer
parity remains a separate gate.
