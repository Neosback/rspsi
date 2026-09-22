# OpenRune FileStore capability and utilization audit

**Audit date:** 2026-09-22  
**RSPSi pin:** `dev.or2:*:3.0.2`  
**Upstream:** `OpenRune/OpenRune-FileStore`  
**Policy:** OpenRune is the preferred OSRS cache/content backend when its behavior is correct for the
selected revision. Neutral editor contracts remain the product boundary. A capability is not considered
"integrated" merely because its dependency or adapter class exists: it must be initialized by the live
Studio composition root, consumed by a real tool/path, and covered by an observable test or diagnostic.

## Why this audit exists

RSPSi grew quickly and contains several cases where a subsystem existed without being exercised by the
actual Studio path. The same risk applies to FileStore: version 3.0.2 is a multi-module OSRS content suite,
not just a byte-store library. Upstream currently contains filesystem access, OSRS definition codecs,
GameVal/RSCM mapping support, cache packing/build tooling, incremental build state, map packing, interface
tooling, sprite/model rendering helpers, world-map rendering, CS2 tooling, sound/MIDI utilities, and more.

The goal is **not** to import every upstream class. The goal is to stop reimplementing relevant capability
without a reason, while retaining RSPSi-owned code where it is editor-specific or independently verified
to be more correct.

## Integration rule

For every important capability, verify this chain:

1. **Dependency present** - the module is on the runtime classpath.
2. **Adapter initialized** - the selected cache/project constructs it.
3. **Live consumer present** - Studio actually calls it.
4. **Observable evidence** - a test, verifier check, or runtime diagnostic proves the result.
5. **Semantic parity** - RuneLite/client/cache fixtures agree with the behavior for the supported revision.

Missing any step means the capability is partial, regardless of how complete the class hierarchy looks.

## Capability matrix

| Capability | Upstream FileStore support | RSPSi status | Decision / next action |
|---|---|---|---|
| Low-level cache filesystem reads | `filesystem` / `Cache` | **USED** | Canonical OSRS read backend through `OpenRuneCacheStore`. Keep. |
| Archive/file enumeration and named archives | `filesystem` | **USED** | Used by `CacheStore`, maps, definitions, fingerprints. Keep neutral boundary. |
| Cache revision detection | `readCacheRevision(cache)` via `version.dat` | **FIXED ON PR #6** | Live project opening now reads the same six-byte version record, with explicit property/env override and revision-240 fallback only when metadata is absent. |
| Writable output cache | `tools:CacheDelegate` | **USED, EXPLICIT ONLY** | `openRuneWritable` is opt-in and source/output paths stay separate. Keep this safety contract. |
| OSRS object definitions | `OsrsCacheProvider.ObjectDecoder` | **USED** | Reduced to neutral `ObjectDefinitionView` and appearance/collision views. |
| Underlay/overlay definitions | OSRS codecs | **USED** | Keep current neutral views and parity tests. |
| Model decode | FileStore `ModelDecoder` / model types | **USED** | Used lazily through `OpenRuneDefinitionProvider`; neutral geometry remains editor-owned. |
| Sequence/frame/skeleton data | OSRS/FileStore decoders | **PARTIAL / USED** | Sequence and legacy animation data are available to rendering. Continue auditing live animation consumers. |
| Map elements / map-scene sprites | FileStore definition/sprite decoders | **USED** | Asset/minimap paths use neutral projections. |
| Texture definitions | Upstream `TextureDecoder/TextureCodec` | **KEEP RSPSi ADAPTER FOR NOW** | Current upstream labels post-233 byte 5 as transparency, while RuneLite-melxin client `Texture` names/uses it as `isLowDetail`. RSPSi's boundary decoder matches the vendored client. Do not remove it until upstream/reference parity changes. |
| Texture pixels | FileStore `TextureType.load` | **USED** | Revision-240 0.6/128 decode is exposed through neutral texture resources. External `textures.json` parity fixture added on PR #6. |
| Embedded GameVals | `GameValHandler` | **FIXED/PARTIAL ON PR #6** | Live symbolic names now load object and sequence GameVals from the selected cache. Expand neutral asset categories before adding NPC/item/component groups. |
| RSCM / Sym mappings | `ConstantProvider`, `RSCMProvider`, `SymProvider` | **OPTIONAL FALLBACK** | Current symbolic provider still consumes mappings if a server/project integration loads them. Add explicit project mapping-source configuration instead of relying on global side effects. |
| GameVal assignment/writing | `GameValAssigner`, mapping providers | **NOT YET EXPOSED** | Strong candidate for Content Studio ID allocation/rename workflows. Do not build a competing allocator first. |
| Incremental cache build state | `IncrementalBuild`, `IncrementalSession`, `PackState`, `RecordingCache` | **NOT USED DIRECTLY** | High-value for Content Studio pack/export. Different concern from 8x8 scene rendering. Integrate when Studio owns pack inputs; OpenRune Server builds should continue using the server's Gradle tasks. |
| Map packing | `PackMaps`, incremental map units | **NOT USED DIRECTLY** | Evaluate as the canonical project/export pack path after editable map save semantics are stable. Existing direct region save remains appropriate for editor session writes. |
| Config packing / inheritance | `PackConfig` + incremental graph | **NOT USED** | Use for no/low-code content definitions rather than creating a second inheritance/packing engine. |
| Model/sprite packing | `PackModels`, `PackSprites` | **NOT USED** | Candidate for asset import/export workflows. |
| Interface packing/edit tooling | `PackIfType`, interface DSL/TOML exporter/loader | **NOT USED** | High-priority reference/backend for the future interface editor. Studio UI/editor model should remain neutral. |
| DB table packing | `PackDBTables` | **NOT USED** | Relevant when Content Studio exposes DB rows/tables. |
| CS2 packing/symbol tooling | `PackCs2`, symbol dump/sync | **NOT USED** | Future content workflow; dependency exclusions currently avoid the optional compiler. Revisit intentionally when CS2 editing is in scope. |
| Object sprite rendering | `ObjectSpriteFactory` | **DUPLICATED** | Studio has `ObjectPreviewRenderer`. Benchmark/reference OpenRune factory for asset thumbnails; keep neutral scene renderer for render-parity previews unless upstream proves preferable. |
| Item sprite rendering | `ItemSpriteFactory` | **NOT USED** | Use when item browser/editor lands; do not implement from scratch first. |
| NPC sprite rendering | `NpcSpriteFactory` | **NOT USED** | Use when NPC browser/editor lands; especially useful for thumbnail/chathead preview. |
| World-map rendering/packing | world-map providers, rasterizer, `WorldMapRenderer`, `WorldMapPacker` | **NOT USED** | Strong candidate for a World Map workspace/tool. It is not a replacement for the 3D map editor. |
| OpenRS2 cache acquisition | `tools:OpenRS2` | **NOT USED** | Optional future cache acquisition/update workflow; keep separate from normal project opening. |
| Sound/MIDI dump/pack | sound codecs/tools | **NOT USED** | Future asset tooling only. |
| AutoCert / build utilities | tools tasks | **NOT RELEVANT TO CURRENT MAP EDITOR** | Do not couple them into Studio merely because they exist. |
| Server cache build/fresh build | OpenRune Server/FileStore Gradle tasks | **USED INDIRECTLY** | `OpenRuneServerAdapter` discovers and invokes the server project's declared tasks. This is preferable to duplicating server build logic inside Studio. |

## Immediate findings from PR #6

### 1. Revision detection was not real detection

Before this audit, `OpenRuneCacheStore.detectRevision` validated OSRS config/map indices and returned a
configured/default revision. It now follows OpenRune's `version.dat` layout when no explicit override is
provided. The project metadata therefore reflects cache evidence instead of an implicit 240 assumption.

### 2. Symbolic names existed but the live cache path did not populate them

`OpenRuneSymbolicNameProvider` read `ConstantProvider.mappings`, but opening a normal cache did not load
RSCM/GameVal mappings into that global provider. On PR #6, cache-backed providers now read embedded object
and sequence GameVals through FileStore's `GameValHandler` and retain external RSCM/Sym as a fallback.

### 3. Texture decoding is a justified exception to "prefer upstream"

The current post-233 FileStore `TextureCodec` and the vendored RuneLite client disagree on the semantic
name/use of the fifth byte. RuneLite-melxin's `Texture` constructor reads it as `isLowDetail`; RSPSi's
boundary decoder matches that client behavior. The custom decoder remains until an external
revision-240 fixture and/or upstream change proves a different interpretation.

### 4. Object preview is a deliberate duplicate worth benchmarking

`ObjectPreviewRenderer` builds a neutral RSPSi model packet and renders it with
`SoftwareSceneRenderer`. OpenRune now has `ObjectSpriteFactory`, plus item/NPC factories. Do not replace
the preview blindly: the neutral path doubles as renderer parity infrastructure. Instead:

- use OpenRune factories as reference/golden thumbnail producers;
- consider them for asset-browser thumbnails;
- keep the neutral renderer for editor scene/parity previews where identical scene contracts matter.

### 5. Incremental FileStore packing and incremental scene rendering are separate systems

OpenRune incremental build tracks source hashes, mapping dependencies, cache reads/writes, output
ownership, removals, and revision changes. RSPSi's 8x8 renderer work tracks dirty scene zones and GPU
residency. Both are valuable, but one must not be presented as implementing the other.

For Content Studio builds, prefer OpenRune's incremental packing engine. For interactive map painting,
continue the zone-based renderer work.

## Next integration slices

### Slice A - finish this Map Studio foundation PR

- finish P0 renderer/cache validation;
- keep the FileStore fixes above;
- add runtime/CI evidence where a capability is claimed as active;
- do not add broad Content Studio packing APIs to this already-large PR.

### Slice B - FileStore-backed Content Studio services

Create neutral services backed by OpenRune for:

1. definition catalog expansion: items, NPCs, varbits/varps, enums, structs, DB rows/tables;
2. GameVal/RSCM browse, reverse lookup, assignment, and rename;
3. object/item/NPC thumbnail providers;
4. pack/build status and incremental-build diagnostics;
5. model/sprite/config/map import/export.

Each service must expose neutral RSPSi contracts so a future backend can be substituted and UI plugins do
not import `dev.openrune.*`.

### Slice C - specialized editors

Use upstream tooling as the backend/reference for:

- interface editor and TOML/DSL interchange;
- World Map workspace;
- DB table editor;
- CS2/symbol workflows;
- sound/MIDI asset tools.

## Upgrade policy

The root property `openruneFileStoreVersion` stays pinned. A FileStore change is accepted only after:

1. `foundationGate`;
2. external `verifyOsrsRevisionMatrix` against the selected revision/cache fixtures;
3. texture-definition/pixel fixture verification;
4. writable output round-trip tests;
5. GameVal/symbolic-name checks;
6. revision detection check;
7. this capability matrix is reviewed for changed APIs/semantics.

The upstream repository currently still declares build number **3.0.2**, so the priority is better
utilization and semantic auditing, not a speculative dependency replacement.
