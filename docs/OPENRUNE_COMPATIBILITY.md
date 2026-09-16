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

## Explicit limitations

The first OpenRune filesystem implementation is read-only. `write` throws
`UnsupportedOperationException` until writable packing and output-cache
semantics are validated. `flush` does not claim to persist edits. There is no
automatic fallback to Displee, because falling back could decode a cache with
the wrong format and silently produce incorrect data.

No real OSRS cache is checked into the repository yet. The adapter has a fake
byte-store test for boundary behavior, but real fixture loading, neutral
object/floor/texture conversion, map-index completion, and semantic parity are
still open acceptance work. The application continues to construct the legacy
Displee backend by default.

## Next spike gate

Provide a licensed representative OSRS cache fixture outside the repository,
load it through `CacheStoreFactory.openRune(Path)`, and compare its terrain,
objects, floors, flags, shapes, rotations, and region coordinates with the
existing representation. Only after that comparison passes should definition
adapters and writable packing be considered.
