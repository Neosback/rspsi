# OpenRune Hosting Maven Catalog and Studio Adoption Matrix

> **Purpose:** inventory the OpenRune Maven repository and record which published capabilities OpenRune Studio currently uses, which arrive transitively, which are candidates for future use, and which are intentionally out of scope.
>
> Repository: `https://raw.githubusercontent.com/OpenRune/hosting/main`
>
> This is a capability catalog, not permission to add dependencies casually. `docs/ROADMAP.md` remains authoritative for implementation order.

## 1. Why this document exists

OpenRune's Maven host contains substantially more than the small set of FileStore modules currently declared by RSPSi/OpenRune Studio.

The project must not:

- assume `dev.or2:filestore` represents the entire OpenRune ecosystem;
- reimplement a capability that is already published by OpenRune;
- add aggregate modules merely because they exist;
- expose OpenRune types through neutral Studio APIs;
- upgrade the OpenRune version pin without running the compatibility/parity gates;
- confuse the Maven host with only first-party OpenRune code, because it also mirrors/forks supporting artifacts.

Before implementing cache acquisition, packing, definitions, source tooling, JS5 serving, RuneScript/CS2, central services, or related infrastructure, consult this catalog and the source repository that owns the artifact.

---

## 2. Current Studio pin

`gradle.properties` currently pins:

    openruneFileStoreVersion=3.0.2

The core OpenRune FileStore family published in `OpenRune/hosting` currently has **3.0.3** as the latest/release version for the main 3.x modules.

That makes 3.0.3 an upgrade candidate, not an automatic upgrade.

A FileStore version change is a parity event and must run:

1. `foundationGate`
2. external `verifyOsrsRevisionMatrix` / `parityGate` where available
3. writable definition round-trip checks
4. real-cache map/location verification
5. capability audit/document updates

Do not mix a dependency upgrade into an unrelated correctness PR unless the upgrade is required to fix that correctness issue.

---

## 3. What Studio declares today

`Client/build.gradle` directly declares the following OpenRune 3.0.2 modules:

| Artifact | Current use/direction |
| --- | --- |
| `dev.or2:filesystem` | low-level cache filesystem through adapters |
| `dev.or2:filestore` | shared cache/definition access |
| `dev.or2:osrs-fs` | OSRS-specific FileStore integration |
| `dev.or2:osrs` | OSRS definition codecs/types |
| `dev.or2:definition` | neutral definition foundation/builders/codecs |
| `dev.or2:tools` | writable `CacheDelegate` today; also publishes many additional tools described below |

The project additionally declares Netty explicitly because OpenRune definition encode APIs expose `ByteBuf` in their signatures.

`dev.or2:opcode` remains published and useful as an OpenRune capability, but it is no longer a direct Studio dependency because current Studio code does not consume its API and current OSRS codec paths do not require it transitively. Re-add it only with a concrete consumer.

The `tools` dependency currently excludes `me.filby:clientscript-compiler` because the previously referenced optional compiler dependency chain was not needed for the writable-cache adapter. Revisit that exclusion only when Studio actually implements CS2/source compilation.

Studio also retains the legacy external Displee cache library for compatibility paths. OpenRune itself publishes its own `dev.or2:displee` fork, so long-term duplication should be evaluated deliberately rather than allowed to grow accidentally.

---

## 4. First-party `dev.or2` artifact catalog

The following artifact directories are currently present in `OpenRune/hosting`.

### 4.1 Core FileStore / definitions family

| Artifact | Latest observed release | Relevance to Studio |
| --- | ---: | --- |
| `dev.or2:all` | 3.0.3 | aggregate POM; do not use merely for convenience |
| `dev.or2:all-osrs` | 3.0.3 | definition + OSRS aggregate; narrower than `all`, still unnecessary when explicit dependencies are clearer |
| `dev.or2:definition` | 3.0.3 | **high**; shared definition types/builders |
| `dev.or2:opcode` | 3.0.3 | **high**; definition opcode framework |
| `dev.or2:osrs` | 3.0.3 | **high**; OSRS codecs/definitions |
| `dev.or2:filesystem` | 3.0.3 | **high**; low-level cache access |
| `dev.or2:filestore` | 3.0.3 | **high**; shared FileStore abstraction |
| `dev.or2:osrs-fs` | 3.0.3 | **high**; OSRS FileStore implementation |
| `dev.or2:tools` | 3.0.3 | **high**; acquisition, packing, building, writable cache, source tooling |
| `dev.or2:displee` | 3.0.3 | medium; OpenRune fork/compatibility cache library |
| `dev.or2:r718` | 3.0.3 | out of current OSRS scope |
| `dev.or2:r718-fs` | 3.0.3 | out of current OSRS scope |
| `dev.or2:rs3` | 3.0.3 | out of current OSRS scope |

`dev.or2:all:3.0.2` aggregates filesystem, definition, opcode, OSRS, 718, filestore, OSRS/718 FileStore, tools, and Displee. Studio intentionally uses explicit dependencies instead so the supported surface remains visible and non-OSRS modules are not pulled in without need.

`dev.or2:all-osrs:3.0.2` is much smaller than its name might imply: its published POM contains `definition`, `osrs`, and Netty. It does **not** replace `filestore`, `filesystem`, `osrs-fs`, or `tools`.

### 4.2 FileStore server / hosted data services

| Artifact | Latest observed release | Relevance |
| --- | ---: | --- |
| `dev.or2:filestore-server` | 0.6 | future remote/hosted cache service investigation |
| `dev.or2:filestore-server-osrs` | 0.6 | future OSRS-specific hosted FileStore integration |
| `dev.or2:wiki` | 0.8 | future content/wiki enrichment, not renderer truth |
| `dev.or2:server-utils` | 0.8 | supporting server utilities; evaluate only with a concrete server-side need |

These are not required for the local map editor today. They should be reviewed before Studio invents remote cache hosting, cache metadata services, or wiki-data ingestion.

### 4.3 RsConfig / TOML source tooling

| Artifact | Latest observed release | Relevance |
| --- | ---: | --- |
| `dev.or2:toml-all` | 1.1 | aggregate; future source-mode convenience |
| `dev.or2:toml-core` | 1.1 | source/config model |
| `dev.or2:toml-annotations` | 1.1 | annotation support |
| `dev.or2:toml-konbini` | 1.1 | TOML tooling support |
| `dev.or2:toml-rsconfig` | 1.1 | **high future value** for RuneScape-oriented source configs |

`tools` already depends on `toml-rsconfig` at runtime. Studio should not add a second source/config parser without first checking these APIs.

### 4.4 OpenRune Central family

| Artifact | Latest observed release | Relevance |
| --- | ---: | --- |
| `dev.or2:central-all` | 1.3.2 | legacy/aggregate central service |
| `dev.or2:central-common` | 2.0.1 | future remote project/service integration |
| `dev.or2:central-server` | 2.0.1 | future service integration |
| `dev.or2:central-worldlink` | 2.0.1 | future world/server link |
| `dev.or2:openrune-central` | 2.0.1 | future central-service integration |
| `dev.or2:openrune-central-common` | 1.0 | central common support |

These are **not** map-editor dependencies today. They are catalogued so Studio does not invent overlapping cloud/remote services later without checking OpenRune's existing work.

### 4.5 Historical/special compatibility artifacts

| Artifact | Latest observed release | Notes |
| --- | ---: | --- |
| `dev.or2:cache` | 2.4.11-nc | older/special cache compatibility artifact; do not substitute for the 3.x FileStore stack without a specific reason |

---

## 5. OpenRS2-related artifacts in the host

The Maven host contains two distinct OpenRS2-related families.

### 5.1 OpenRune-relocated OpenRS2 artifacts

Published under `dev.or2.openrs2`:

| Artifact | Observed release |
| --- | ---: |
| `dev.or2.openrs2:buffer` | 1.0.0-openrune |
| `dev.or2.openrs2:cache` | 1.0.0-openrune |
| `dev.or2.openrs2:compress` | 1.0.0-openrune |
| `dev.or2.openrs2:crypto` | 1.0.0-openrune |
| `dev.or2.openrs2:util` | 1.0.0-openrune |

Treat these as implementation/support artifacts unless a concrete Studio feature requires their API directly. Prefer OpenRune FileStore's higher-level API where it already solves the problem.

### 5.2 Mirrored upstream `org.openrs2` artifacts

The host also contains a broad `org.openrs2` snapshot family, including:

- openrs2
- openrs2-archive
- openrs2-asm
- openrs2-buffer
- openrs2-buffer-generator
- openrs2-cache
- openrs2-cache-550
- openrs2-cache-cli
- openrs2-cli
- openrs2-compress
- openrs2-compress-cli
- openrs2-conf
- openrs2-crc32
- openrs2-crypto
- openrs2-db
- openrs2-decompiler
- openrs2-deob
- openrs2-deob-annotations
- openrs2-deob-ast
- openrs2-deob-bytecode
- openrs2-deob-processor
- openrs2-deob-util
- openrs2-game
- openrs2-http
- openrs2-inject
- openrs2-json
- openrs2-log
- openrs2-net
- openrs2-patcher
- openrs2-protocol
- openrs2-util
- openrs2-xtea-plugin
- openrs2-yaml

These should not be added merely because they are hosted there. The FileStore `tools` module already wraps the OpenRS2 cache-archive use case needed by Studio.

---

## 6. Other mirrored/supporting artifacts in the Maven host

The repository is not exclusively `dev.or2`.

Observed supporting groups include:

### `me.filby`

- `clientscript-compiler`
- `runescript-compiler`
- `runescript-parser`
- `runescript-runtime`

The host currently publishes `clientscript-compiler` through `0.0.7-openrune`.
The pinned `dev.or2:tools:3.0.2` metadata depends on
`clientscript-compiler:0.0.6-openrune`, but Studio intentionally excludes that
toolchain because no current production consumer compiles CS2.

These artifacts are relevant to future CS2/RuneScript authoring and should be
revisited when that roadmap phase begins.

### `cc.ekblad`

- `4koma` (latest observed `1.2.2-openrune`)

The compiler dependency chain referenced by the pinned tools release uses
`4koma:1.2.0-openrune`. Both are present in the OpenRune host. An older Studio
comment claiming 4koma was unpublished was stale and has been removed.

This is a supporting library, not a Studio capability by itself.

The existence of an artifact in `OpenRune/hosting` does not imply that Studio should depend on it directly.

---

## 7. The `tools` module is much more important than our current use suggests

Studio currently brings in `dev.or2:tools` primarily for the writable `CacheDelegate`, but OpenRune FileStore documents and implements a much broader toolchain.

Known capabilities include:

- `FreshCache`
- `OpenRS2` archive discovery/download
- cache ZIP caching and extraction
- revision/subrevision selection
- XTEA acquisition for revisions that still require it
- cache build task ordering
- incremental build state
- config packing
- map packing
- model packing
- sprite packing
- sound/MIDI packing
- CS2 packing
- GameVal publishing/reference tracking
- RsConfig/TOML support
- world-map generation
- writable cache modification utilities

Before Studio implements any corresponding subsystem, inspect the current FileStore source and published module first.

---

## 8. OpenRS2 acquisition should use FileStore, not a second downloader

OpenRune FileStore already implements OpenRS2 cache acquisition in:

    tools/src/main/kotlin/dev/openrune/cache/tools/OpenRS2.kt

and the higher-level working-cache bootstrap in:

    tools/src/main/kotlin/dev/openrune/cache/tools/FreshCache.kt

`FreshCache`:

1. loads OpenRS2 cache metadata;
2. resolves an OSRS cache by revision/subrevision/environment;
3. keeps a reusable local ZIP under OpenRune's cache directory;
4. validates the cached ZIP;
5. downloads when absent/corrupt;
6. extracts a working cache;
7. carries XTEA data where the target revision needs it;
8. can run FileStore cache tasks after extraction.

For Studio's real-cache verification/bootstrap path, prefer this OpenRune-owned acquisition layer over custom HTTP/OpenRS2 code.

Studio still owns for standalone/reference-cache workflows:

- where Studio-managed verification/reference caches live;
- whether acquisition is opt-in;
- provenance/fingerprint recording;
- verification/acceptance policy;
- read-only source versus explicit Studio-managed output policy.

A connected OpenRune Server project is different: its `.data/cache/LIVE` and `.data/cache/SERVER` locations and their synchronization are owned by the OpenRune project/build. Studio discovers those paths and reads them, but does not replace them with its standalone writable-output model. See `OPENRUNE_ECOSYSTEM_INTEGRATION.md`.

---

## 9. XTEA revision rule

This is important and should not be rediscovered repeatedly.

Current OpenRune FileStore source defines:

    RemoveXteas.OBSOLETE_FROM_REVISION = 237

`FreshCache` uses the same boundary:

    revision < 237  -> download OpenRS2 keys.json as xteas.json
    revision >= 237 -> no XTEA key download is required by this workflow

Its cache validation likewise requires `xteas.json` only below revision 237.

Therefore:

### Modern Studio target

For the current OSRS revision-240 target:

- no external XTEA key file is required by OpenRune's current cache workflow;
- `OpenRuneCacheStore` reading map archives with `xtea = null` is consistent with the FileStore revision-240 path;
- the Phase 0 Lumbridge acceptance cache should not add a fake XTEA requirement.

### Historical caches below revision 237

The situation is different.

OpenRune's low-level cache API accepts XTEA keys for map archive reads, and `FreshCache` obtains the matching keys for older revisions.

RSPSi's current `OpenRuneCacheStore.read(...)` passes `null` as the XTEA argument.

So supporting arbitrary pre-237 encrypted map caches requires one of:

1. extend the adapter/session to load and pass the appropriate region XTEAs; or
2. preprocess the working cache through OpenRune's XTEA-removal task before Studio opens it.

Do not silently claim generic historical-cache support until this is implemented and tested.

The 237 boundary is an **OpenRune FileStore implementation contract for its current workflow**, not a blanket statement that every unrelated private/cache format in existence follows the same rule.

---

## 10. Capability adoption matrix

Use this matrix before adding new infrastructure.

| Need | Preferred existing resource | Studio status |
| --- | --- | --- |
| open/read modern OSRS cache | filesystem + filestore + osrs-fs | active |
| decode OSRS definitions | definition + osrs + opcode | active |
| encode/edit definitions | builders/codecs | active/in progress |
| explicit writable output | tools / CacheDelegate | active |
| acquire reference cache by revision | tools / FreshCache + OpenRS2 | **available, should be adopted** |
| acquire XTEAs for old cache | tools / OpenRS2/FreshCache | available; only relevant below 237 |
| map packing | tools / PackMaps | available; future authored-world persistence |
| model packing | tools | available; future |
| sprite packing | tools | available; future |
| sound/MIDI packing | tools | available; future |
| incremental builds | tools | available; future project build registry |
| GameVals/symbols | tools/FileStore + existing Studio provider | partially active |
| RsConfig/TOML | toml-rsconfig + tools | available; future source mode |
| CS2 compilation/packing | tools + Filby compiler artifacts | intentionally deferred |
| RuneScript compiler/runtime | me.filby artifacts | future content/runtime research |
| local JS5/FileStore server | filestore-server family / OpenRune repos | future; research before implementation |
| remote/central services | central family | future; no current dependency |
| wiki enrichment | wiki module / FileStore Server | future; never renderer authority |
| raw OpenRS2 cache primitives | dev.or2.openrs2 / org.openrs2 | normally use FileStore wrapper instead |

---

## 11. Dependency rules

1. Prefer the smallest explicit OpenRune module set needed by production code.
2. Do not switch to `dev.or2:all` merely to make dependency discovery easier.
3. Do not expose `dev.openrune.*` types from neutral editor/plugin APIs.
4. A transitive dependency is not automatically an approved public Studio dependency.
5. If Studio starts using a transitive artifact directly, consider declaring it explicitly so the dependency is intentional and version policy is clear.
6. Before implementing a cache/content/compiler/server utility, search OpenRune FileStore, OpenRune Server, and this Maven catalog.
7. Keep version upgrades separate from feature work unless the newer version is required for correctness.
8. Record why a module is added and the first production consumer.
9. Remove a dependency when its last production use disappears, unless it remains required transitively by another intentional module.
10. Re-run this catalog when the OpenRune pin changes materially.

---

## 12. Immediate consequences for the current roadmap

For Phase 0 and the semantic API work:

- use FileStore `FreshCache`/OpenRS2 tooling as the preferred **reference-cache** bootstrap;
- never treat that bootstrap as OpenRune Server project-open behavior;
- use revision 240 without XTEA-key requirements;
- keep the external-cache verifier responsible for semantic/render acceptance;
- do not add direct OpenRS2 dependencies for cache downloading;
- do not upgrade from FileStore 3.0.2 to 3.0.3 inside PR #47 unless a verified 3.0.3 fix is required;
- separately schedule a 3.0.3 compatibility spike after the current correctness PR if we want the latest FileStore fixes.

For later content-studio phases:

- audit `PackMaps`, incremental build machinery, GameVals, RsConfig, model/sprite/audio packing, and compiler integrations before creating Studio-native replacements.
