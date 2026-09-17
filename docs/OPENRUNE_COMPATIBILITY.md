# OpenRune Compatibility Spike

## Target

The spike is pinned to OpenRune FileStore `2.4.19` and uses the published
Maven-compatible repository declared in the root Gradle build. The selected
modules are `filesystem`, `filestore`, `osrs-fs`, `osrs`, and `definition`.

## Current adapter

`OpenRuneCacheStore` adapts `dev.openrune.filesystem.Cache` to RSPSi's
byte-oriented `CacheStore`. The cache adapter currently uses:

- `Cache.Companion.load(Path)` to open a cache.
- `Cache.data(index, archive, file, null)` for byte reads.
- archive discovery and version-table access for neutral map indexes and cache
  identity metadata.
- `Cache.close()` for lifecycle cleanup.

The definition adapter additionally uses OpenRune's object, floor, texture,
and lazy model decoders, reducing their results to RSPSi-owned views before
they reach editor code.

The OpenRune type is confined to the cache adapter package. The public factory
accepts a `Path` and returns `CacheStore`; editor-facing code does not receive
OpenRune objects.

## Neutral OSRS map path

`MapIndexTable` discovers named `mX_Y` landscape and `lX_Y` location archives
through `CacheStore.archiveId`. For modern revision 237+ layouts, where
OpenRune's map packer uses numeric group IDs, the revision-aware overload uses
the neutral `CacheStore.archiveIds` seam and treats file 0 as terrain and file
1 as locations. The autodetecting overload remains available for compatibility
tools that do not know a revision. `OsrsMapService` then reads terrain and
location bytes by numeric archive ID without exposing OpenRune types. It also
distinguishes the two payload layouts at this boundary: named pre-packed
indexes use separate landscape/location groups with file 0 in each, while
modern packed groups share one numeric group with terrain in file 0 and
locations in file 1. `MapIndexTableTest` covers both layouts so a legacy
compatibility assumption cannot silently break modern loading. The
terrain codec is revision-aware at the same boundary: OSRS revisions before
209 use one-byte terrain opcodes and overlay values, while revision 209 onward
uses two-byte values, matching the captured TSPS/OpenRune-Editor scene decoder
behavior.
`OsrsRevisionProfile`, `OsrsMapService`, and `OsrsRegionSaveCoordinator` carry
that decision without exposing revision branches to the world model. The
legacy `MapIndexLoaderOSRS` compatibility facade is now backed by the same
neutral table and can export/import the existing six-byte-entry map-index
interchange format for tooling. That interchange export is not a claim that
OSRS caches use a legacy binary map-index file internally.

`OpenRuneDefinitionProvider` similarly decodes objects, underlays, overlays,
textures, map-scene sprites, and lazy model metadata with OpenRune codecs, then
exposes only RSPSi-owned definition views. Map-scene discovery first reads the
OSRS graphics-defaults group and falls back to the named `mapscene` archive in
the sprite index for DAT2-style caches. Full model geometry remains outside the
provider until a neutral mesh representation is validated.

`OpenRuneSymbolicNameProvider` adapts already-loaded RSCM/GameVal reverse
mappings into the neutral `SymbolicNameProvider`. It is optional, does not
load mapping files implicitly, and returns the exact backend key so the asset
browser can show symbolic provenance without making RSCM/GameVal part of the
editor model. `OpenRuneCacheStore.assetRepository(revision)` composes this
provider with the neutral definition adapter for asset-browser callers.

## Write-path evidence

The pinned FileStore source declares write methods on the neutral-looking
`Cache` interface, but its published file-backed `FileCache` inherits
`ReadOnlyCache`, whose write and index-creation methods throw
`UnsupportedOperationException`. RSPSi keeps the normal `OpenRuneCacheStore`
reader read-only and uses the neutral `LayeredCacheStore` for staged output;
the explicit `openWritable(Path)` path uses OpenRune's `CacheDelegate` for a
direct output cache.

The legacy Displee adapter now explicitly calls `CacheLibrary.update()` during
`flush()`; closing a Displee library alone does not repack dirty archives. An
opt-in integration test copies an explicitly supplied cache, edits a modern
region, flushes and reopens it through both Displee and the OpenRune reader,
and verifies the semantic terrain change. This validated the current output
path against live build 240 without mutating the source cache. It does not
make OpenRune's file-backed cache writable, but it does establish that staged
output is readable by the production OpenRune adapter.

The explicit native `CacheDelegate` output path is also covered by the copied-
cache integration suite. Against the captured live build-240 cache at region
`(50,50)`, the test opened `openRuneWritable(output)`, changed terrain through
`OsrsMapService`, flushed, closed, and reopened the output with the normal
OpenRune reader. The edited underlay survived the reopen. The project
composition integration additionally persisted overlay shape/rotation, tile
flags, shared-corner heights, and object rotation through
`OsrsStudioProject.openWithOpenRuneOutput(...)`. This proves the direct
delegate arrangement for the currently supported map payloads; it does not
authorize source-cache mutation or make the native writer the default backend.

## Explicit limitations

The normal OpenRune filesystem implementation is read-only. `open(Path)` and
`write` therefore remain read-only by default. Project identity fingerprints
are computed from non-empty index IDs and reference-table CRCs, which are
canonical across OpenRune's read-only `FileCache` and writable `CacheDelegate`.
An explicit
`openWritable(Path)` adapter is now available through OpenRune's
`CacheDelegate`; it uses the FileStore `Cache` contract while writing an
explicitly selected output cache through OpenRune's published writable
delegate. There is still no automatic fallback to a different backend because
falling back could decode a cache with the wrong format and silently produce
incorrect data.

`CacheStoreFactory.openRuneWithDispleeOutput(base, output)` packages this
topology for callers: OpenRune remains the read/definition source, writes are
staged, and the separately prepared output cache is the only writable target.
The factory rejects identical paths.

The staged output advertises `mapPacking=true` because its Displee adapter
repacks dirty archive indexes during `flush()`. Its neutral capability is
`writeMode=STAGED`; direct Displee and explicit writable OpenRune adapters
report `writeMode=DIRECT`, while the normal OpenRune reader reports
`writeMode=READ_ONLY`. These distinctions are exposed only
through the neutral boundary; the editor does not receive Displee index
objects.

No real OSRS cache is checked into the repository. External OpenRS2 fixtures
have now passed the read-only verification path: cache id 391 (revision 6)
passed named-map terrain and location decoding, while cache id 2710 (live
build 240, captured 2026-09-16) passed modern numeric-group discovery, modern
terrain decoding, 63,630 neutral asset descriptors, collision and neutral
scene construction, and semantic decode -> encode -> decode. The selected
modern region contained a 2,040-byte location payload and 988 objects. A second
live region check at `(50,50)` decoded an 11,157-byte location payload, built
4,726 object projections, produced 3,983 non-empty collision tiles, and
matched an independently generated TSPS shaped-minimap fixture with zero
pixel differences on all four planes. The
same external fixture now also contains `terrain-semantics.json`, exported
from TSPS `SceneBuilder`; RSPSi compares all 16,384 tiles and reports zero
differing height/underlay/overlay/shape/rotation/flag fields for build 240.
The companion `locations.json` export decodes the same TSPS location payload
semantics without using scene-container capacity rules; all 4,726 placements
match the canonical RSPSi objects for the same region.
The companion `scene-geometry.json` export compares authored terrain mesh
vertices and face topology on 4,481 populated tiles with zero differences.
This is geometry evidence, not a claim of full lighting/material/render parity;
the independent scene fingerprint remains a stronger optional check.
The normal OpenRune source backend remains read-only. The application continues
to construct the legacy Displee backend by default for the compatibility launch
path, while OSRS projects may explicitly select either staged Displee output or
native OpenRune `CacheDelegate` output after the source/output paths are kept
separate.

## Next spike gate

Provide a licensed representative OSRS cache fixture outside the repository,
load it through `CacheStoreFactory.openRune(Path)`, and compare terrain,
objects, floors, flags, shapes, rotations, and region coordinates with the
existing representation. Revision-6 named maps and live build-240 numeric
maps now pass this read-only comparison. Writable output-cache reopening is
validated through both the explicit Displee adapter and the native OpenRune
delegate, with the OpenRune reader used for the reopen check. The current
supported arrangements are therefore OpenRune-read/Displee-output staging and
explicit OpenRune-read/OpenRune-output composition; both keep the source cache
read-only and separate from the output target. The native delegate remains
opt-in and is not the default compatibility backend.
Definition adapters are available, but are not yet the default product
backend. The verifier also
compares the complete neutral derived scene after round-trip encoding; the
bridge-heavy build-240 fixture currently passes that check with zero
differences.

## Explicit verification

The verifier never searches for or mutates an implicit user cache. Run the
fixture-only gate with:

```text
./gradlew verifyOsrsRevision
```

For an explicitly selected OpenRune-compatible cache, set
`RSPSI_OSRS_CACHE=/path/to/cache`. This inspects the OSRS map index using the
known revision layout when a region revision is supplied, or safe autodetection
otherwise. To verify
one region, also set `RSPSI_OSRS_REGION_X`, `RSPSI_OSRS_REGION_Y`, and
`RSPSI_OSRS_REVISION`. The verifier reports cache metadata, the selected
revision profile, map/location payloads, neutral definition loading,
validation, collision construction, and semantic decode/encode/decode equality
as explicit PASS/FAIL/NOT_RUN checks. It does not write the supplied cache.

An external parity directory can be supplied with
`RSPSI_OSRS_PARITY_FIXTURE=/path/to/fixture` alongside the selected-region
arguments. Its optional `fixture.properties` may identify `region.x`,
`region.y`, `revision`, `cache.fingerprint`, and `scene.fingerprint`. An
optional `minimap.mapScenes=true` property declares that the PNG captures
include cache-backed map-scene sprites. Without it, shaped PNG comparison uses
the terrain/wall baseline while normal product minimap construction still
composes available sprites. This keeps older independent captures from being
mistaken for full asset-render parity. An optional `terrain-semantics.json`
file is a TSPS-exported, independent
64x64x4 semantic snapshot containing flattened heights, underlays, overlays,
shapes, rotations, and flags. The repository includes the reference-only
export helper at
`tools/tsps/export-terrain-semantics.ts`; it requires `TSPS_CLIENT_ROOT` and
must be run against an explicitly pinned TSPS checkout/cache. PNGs named
`minimap-plane-N.png` and `minimap-shaped-plane-N.png` are compared through the
neutral minimap parity service. Terrain snapshots are compared through the
neutral terrain parity service. An optional `locations.json` contains the
independent delta-packed location decode and is compared against canonical
object ID/type/rotation/plane/coordinate tuples. Identity mismatches fail the gate; missing
fixture data remains visible as `WARN`/`NOT_RUN`. An optional
`scene-geometry.json` compares authored terrain vertices and topology as a
renderer-neutral 3D geometry export. This keeps RuneLite/TSPS
captures and generated images outside the repository while making their
provenance-controlled acceptance path executable.

The optional `collision.json` export is intentionally diagnostic rather than
a strict gate. TSPS exposes client-scene flags based on `clipType` and omits
locations at its scene loading line, while RSPSi's canonical collision map
follows OpenRune-Server's `solid`/`blockWalk` and routefinder semantics. The
product-side collision gate is therefore the deterministic OpenRune
`StepValidator` vector suite and focused bridge/wall/object tests. A future
normalized RuneLite/OpenRune collision fixture can be promoted to the strict
parity gate without changing the editor model.

For visual debugging, set `RSPSI_OSRS_PARITY_OUTPUT=/explicit/output/path`
with the selected-region arguments. The verifier then writes the derived
`minimap-plane-N.png` and `minimap-shaped-plane-N.png` rasters to that path.
This output is opt-in and derived from the supplied cache; it is not a checked-
in fixture or a replacement for the independent oracle images.

For release/CI acceptance, set `RSPSI_OSRS_REQUIRE_PARITY=true` as well. The
verifier will then fail unless an independent scene evidence check passes
(`render.parity` or `scene.geometry.parity`) and `terrain.parity`,
`location.parity`, and `minimap.parity` are `PASS`; the default remains
non-strict so fixture-free local cache checks keep reporting missing external
evidence as `NOT_RUN` or `WARN`. A terrain snapshot is deliberately separate from the render
fingerprint: it proves cache/scene semantics without making a renderer’s
internal representation part of the cross-project contract.
