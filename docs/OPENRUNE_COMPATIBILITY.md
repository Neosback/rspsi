# OpenRune Compatibility Spike

## Target

The spike is pinned to OpenRune FileStore `2.4.19` and uses the published
Maven-compatible repository declared in the root Gradle build. The selected
modules are `filesystem`, `filestore`, `osrs-fs`, `osrs`, and `definition`.

## Current adapter

`OpenRuneCacheStore` adapts `dev.openrune.filesystem.Cache` to RSPSi's
byte-oriented `CacheStore`. The only OpenRune operations currently used are:

- `Cache.Companion.load(Path)` to open a cache.
- `Cache.data(index, archive, file, null)` for byte reads.
- `Cache.close()` for lifecycle cleanup.

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
location bytes by numeric archive ID without exposing OpenRune types. The
legacy `MapIndexLoaderOSRS` compatibility facade is now backed by the same
neutral table and can export/import the existing six-byte-entry map-index
interchange format for tooling. That interchange export is not a claim that
OSRS caches use a legacy binary map-index file internally.

`OpenRuneDefinitionProvider` similarly decodes objects, underlays, overlays,
textures, and lazy model metadata with OpenRune codecs, then exposes only
RSPSi-owned definition views. Full model geometry remains outside the provider
until a neutral mesh representation is validated.

`OpenRuneSymbolicNameProvider` adapts already-loaded RSCM/GameVal reverse
mappings into the neutral `SymbolicNameProvider`. It is optional, does not
load mapping files implicitly, and returns the exact backend key so the asset
browser can show symbolic provenance without making RSCM/GameVal part of the
editor model. `OpenRuneCacheStore.assetRepository(revision)` composes this
provider with the neutral definition adapter for asset-browser callers.

## Explicit limitations

The first OpenRune filesystem implementation is read-only. `write` throws
`UnsupportedOperationException` until writable packing and output-cache
semantics are validated. `flush` does not claim to persist edits. There is no
automatic fallback to Displee, because falling back could decode a cache with
the wrong format and silently produce incorrect data.

No real OSRS cache is checked into the repository yet. The adapter has fake
byte-store and named-map-index tests for boundary behavior, but real fixture
loading, neutral object/floor/texture conversion, writable packing, and
semantic parity are still open acceptance work. The application continues to
construct the legacy Displee backend by default.

## Next spike gate

Provide a licensed representative OSRS cache fixture outside the repository,
load it through `CacheStoreFactory.openRune(Path)`, and compare its terrain,
objects, floors, flags, shapes, rotations, and region coordinates with the
existing representation. Only after that comparison passes should definition
adapters and writable packing be considered.

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
