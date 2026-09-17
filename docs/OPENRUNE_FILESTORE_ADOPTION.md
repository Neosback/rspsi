# OpenRune FileStore Adoption Contract

OpenRune FileStore is Studio's primary OSRS cache and definition ecosystem. It
is infrastructure selected by the OSRS bundle, not an editor feature plugin.

## Ownership

| FileStore supplies | Studio supplies |
|---|---|
| DAT2 opening, indices, archives, files, names, CRCs, XTEA | project/session lifecycle |
| revision-aware OSRS definition codecs | authored `WorldDocument` |
| models, sprites, textures, sequences, map elements | map/location semantics and scene truth |
| GameVals/RSCM, DB tables, interfaces, CS2 building blocks | collision policy, history, selection, validation |
| packing/build tasks and progress hooks | neutral assets, render packets, workspaces, plugins |

FileStore/OpenRune types stop at `CacheStore`, `DefinitionProvider`, and
`AssetRepository` adapters. They never enter editor, workspace, renderer, or
plugin contracts.

## Cache modes

The source cache is read-only by default. Studio supports these explicit
capabilities:

| Mode | Meaning | Default |
|---|---|---:|
| `READ_ONLY` | FileStore reads the selected source cache | Yes |
| `STAGED` | edits are buffered and committed only to a separate output cache | Yes for saving |
| `DIRECT` | an explicitly selected output cache is writable | Advanced |
| `BUILD_ONLY` | a provider can pack/build but is not an editor write target | Optional |

`CacheDelegate`, Displee, and FileStore packers remain behind output/build
adapters. The source and output paths must differ, and every output must be
reopened through the normal FileStore reader before it is considered valid.

## Session-scoped assets

`CacheManager` is not used as Studio state because it accumulates definitions
globally across cache loads. Each opened OSRS project owns its own lazy
`AssetRepository`. The repository reports supported categories and exposes
immutable RSPSi views for objects, floors, textures, models, sprites, map
scenes, sequences, and map elements. Interfaces, items, NPCs, CS2, GameVals,
and DB tables are reserved extension categories and must use the same facade
when they are added.

## Scene and lighting boundary

FileStore supplies bytes, definitions, raw model fields, texture metadata, and
Jagex color/lighting primitives. RSPSi derives the authored scene, terrain
appearance, object transforms, bridges, collision, and immutable render
packets. The default lighting profile follows the OSRS directional baseline
`(-50, -10, -50)`, ambient `96`, and intensity factor `768`. Frontend exposure
is presentation-only and cannot alter authored data or scene fingerprints.

## Source checkout risks

The inspected FileStore source checkout currently defaults its publishing
repository to a hard-coded Windows path and its README advertises an older
published version than its build script. Studio therefore pins published
artifacts behind compatibility tests. Any upstream fix is tracked separately;
Studio does not silently fork the cache semantics.

## Adoption gates

The FileStore adoption is accepted only when revision-240 cache opening,
archive identity, session isolation, source immutability, staged output
reopen, terrain/location parity, model/material packets, and lighting
fingerprints pass their independent tests.
