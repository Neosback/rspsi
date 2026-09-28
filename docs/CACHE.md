# Cache Model

## Authority

OpenRune FileStore is the primary modern OSRS cache backend.

Legacy Displee compatibility may remain only where current compatibility tests require it. Do not create a second modern cache implementation for the web bridge unless there is a demonstrated unsupported capability.

## Read versus write

Normal cache open is read-only.

Writable operations require an explicit output or staging cache. The publication path is:

```text
candidate data
  -> validate
  -> encode
  -> write staging/output
  -> flush reference tables
  -> close
  -> reopen
  -> semantic verification
  -> success
```

A failed reopen or verification is a failed publication.

## Map resources

The existing canonical responsibilities remain useful during the pivot:

- `OsrsRegionDecoder` decodes terrain/location data;
- `OsrsRegionEncoder` encodes terrain/location data;
- `MapService` / `OsrsMapService` resolve archive access;
- revision-profile logic remains explicit.

These classes may move packages/modules during Kotlin migration, but avoid creating parallel codecs.

## Browser boundary

The browser does not need a JVM `WorldDocument` clone merely to display a region.

The bridge should eventually return a stable neutral representation of the resource required by the web editor. Encoding/publication requests should carry a versioned neutral payload that can be validated without depending on UI types.

Binary representations are acceptable where they materially reduce transfer cost, but the format must be documented and versioned.

## Connected OpenRune caches

OpenRune `.data/cache/LIVE` and `.data/cache/SERVER` are generated outputs.

Do not treat them as normal write targets.

Use LIVE for client-facing/cache semantics and SERVER only for the server-oriented data for which it is authoritative. A source edit is published through the OpenRune build path and verified by reopening generated output.

## Revision handling

Cache format support and game revision are separate concepts.

Do not assume that successfully opening an index layout proves every revision-specific opcode or semantic rule is supported. Keep revision diagnostics explicit and fail unsupported writes instead of guessing.

## Verification

Preserve high-value codec and real-cache fixtures during migration. Rendering-specific parity fixtures can be retired when their only purpose is the deleted native renderer, but semantic cache fixtures should remain.
