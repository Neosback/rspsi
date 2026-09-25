# OpenRune Server Integration Model

> **Scope:** authoritative integration notes for how OpenRune Studio should interoperate with an
> imported OpenRune Server checkout. This document describes ownership, cache/toolchain/version
> boundaries, GameVals/RSCM, raw-cache map data, server semantics, fork compatibility, and runtime
> integration. Product sequencing still belongs in `ROADMAP.md`.

## 1. Verified upstream baseline

The vendored `OpenRune-Server-main/` tree was compared by Git blob SHA with
`OpenRune/OpenRune-Server` main on 2026-09-24. All 4,081 vendored functional files matched
upstream exactly. The twelve files present only upstream were repository metadata such as
`.github/**`, `.gitignore`, `.editorconfig`, and `.idea/icon.png`.

At that point upstream main was:

- commit `d5c3542156f1a162a67807e09eb6827ec40c37cb`;
- OR2 `3.0.3`;
- Kotlin `2.2.0`;
- coroutines `1.10.2`;
- rsprot `1.0.0-ALPHA-20260912`;
- protocol artifacts `net.rsprot:osrs-240-api` and `net.rsprot:osrs-240-shared`.

Studio's OpenRune cache adapter also identifies its embedded FileStore boundary as
`OpenRune FileStore 3.0.3`. That exact match is useful, but Studio must never assume an imported
server will always pin the same OR2 version.

## 2. OpenRune import identity

A connected project should derive lightweight import metadata from the project itself before
opening expensive cache or source domains.

Preferred inputs, in order:

1. `game.yml`, falling back to `game.example.yml`;
2. `gradle/libs.versions.toml`;
3. `openRune-intelliJ-tools.toml`;
4. detected LIVE/SERVER/cache/source paths;
5. evaluated Gradle model only after an explicit trusted workflow requests it.

The import preview should show at least:

- configured game/server name;
- revision and optional subrevision;
- environment;
- world where present;
- detected LIVE path/status;
- detected SERVER path/status;
- server OR2 version;
- Studio OR2/FileStore version;
- rsprot protocol target when detectable;
- selected Studio access policy.

The configured server name is a better default project name than the directory name when it is
non-generic. A user must still be free to rename the Studio project independently.

Never edit `game.yml` merely to make Studio metadata agree with the project.

## 3. Revision support is a compatibility matrix

OpenRune's cache tooling is revision-parameterized. `CacheTools.readRevision()` accepts
`major` or `major.minor` revision values from `game.yml`; Fresh Install passes revision,
subrevision, and environment into the OR2 Builder; cache packers and decoders receive the
configured revision.

That does **not** mean the complete server becomes arbitrary-revision by changing one YAML value.

For an imported project Studio must track four independent compatibility dimensions:

| Dimension | Meaning |
| --- | --- |
| FileStore format | Can the selected OR2/FileStore implementation open the cache containers? |
| Definition codecs | Can the required object/NPC/item/interface/map/etc. codecs correctly decode this revision? |
| Studio scene/editor | Has Studio verified its rendering/map semantics for this revision? |
| Server protocol | Does the checkout use a matching rsprot/client protocol implementation? |

The current upstream checkout is protocol-pinned to revision 240 through
`net.rsprot:osrs-240-*`. A different cache revision may remain FileStore-readable while the
server protocol is still incompatible.

Therefore UI wording must distinguish:

- **revision detected**;
- **cache readable**;
- **Studio verified**;
- **server protocol matches**.

Never collapse those into a single green "revision supported" indicator.

## 4. OR2/FileStore dependency policy

The server currently consumes OR2 through Maven artifacts including:

- `dev.or2:filesystem`;
- `dev.or2:filestore`;
- `dev.or2:definition`;
- `dev.or2:all`;
- `dev.or2:tools`.

The OpenRune hosting Maven repository reports `3.0.3` as the current release for the core
FileStore/filesystem/definition artifacts as of 2026-09-24.

### Studio must not runtime-auto-upgrade its embedded OR2

Do not dynamically replace Studio's classpath because an imported project pins a newer OR2.
That would make a tested desktop build mutate underneath itself and risks ABI/classloader conflicts.

Use this policy instead:

1. Studio ships a tested OR2/FileStore version.
2. Import reads the server's OR2 pin from its version catalog when available.
3. Studio records `exact-match`, `known-compatible`, or `unknown-mismatch`.
4. Pure FileStore reads may remain enabled when compatibility is verified.
5. Any build or source-to-cache publication is executed through the imported project's own
   Gradle wrapper/tooling, so it uses **that server's** OR2 version.
6. Studio's dependency is updated through normal development/CI dependency updates and the full
   compatibility test suite, not at application runtime.

A CI task may query OpenRune Maven metadata and report that a newer OR2 release exists. It may
open a dependency-update PR. It must not silently advance the production dependency.

### Future version-matched tool bridge

For operations that require server-native classes rather than neutral Studio models, prefer an
out-of-process helper launched from the imported project. It can run with the project's own Gradle
runtime classpath and communicate with Studio over a small versioned protocol.

This is safer than loading arbitrary server jars into Studio's JVM and naturally solves OR2
version skew.

OpenRune already demonstrates this isolation pattern with `:tools:osrs-mcp`: a stdio process
using the server's own dependencies that can reload/search GameVals and decoded LIVE/SERVER
caches without being part of the game-server runtime.

## 5. Cache lifecycle and generated-output ownership

OpenRune's canonical cache paths are:

- `.data/cache/LIVE`;
- `.data/cache/SERVER`.

`CacheTools.kt` owns their lifecycle.

### Fresh Install

`FRESH_INSTALL`:

1. reads revision/subrevision/environment;
2. invokes the OR2 Builder;
3. creates/refreshes LIVE and SERVER;
4. clears incremental build state;
5. dumps base GameVals from the fresh cache.

Fresh Install is a bootstrap/reset operation. Studio must never invoke it as project-open behavior.

### Normal Build

`BUILD`:

1. loads GameVals/RSCM;
2. discovers plugin packs;
3. resolves CS2 overrides and pack tasks;
4. incrementally builds LIVE;
5. runs a separate SERVER-cache build;
6. finalizes server GameVals/DB tables/enums/codegen.

OpenRune maintains separate incremental state:

- `.data/cache/incremental_live`;
- `.data/cache/incremental_server`.

LIVE uses fingerprint verification. SERVER uses output-CRC verification.

**Studio must not bypass this pipeline and then expect OpenRune's incremental state to remain
authoritative.**

## 6. Source-of-truth matrix

The most important integration rule is that Studio edits the same authority OpenRune itself
consumes.

| Resource | Authoritative project source | Generated/derived output | Studio write policy |
| --- | --- | --- | --- |
| Client terrain/loc map archives | currently no general stock OpenRune text source identified | LIVE map files 0/1, then inherited into server use | inspect/edit in Studio transaction; connected-project publish remains disabled until an explicit OpenRune-consumed map source/build hook exists |
| NPC map spawns | `.data/raw-cache/map/npcs/*.toml` | SERVER map file 5 | safe candidate for structured source editor + normal OpenRune build |
| ground-object map spawns | `.data/raw-cache/map/objs/*.toml` | SERVER map file 6 | safe candidate for structured source editor + normal OpenRune build |
| map areas | `.data/raw-cache/map/area/*.toml` | SERVER map file 7 | safe candidate for polygon editor + normal OpenRune build |
| server object/NPC/item/etc. overlays | `.data/raw-cache/server/**/*.toml` plus plugin-pack config dirs | SERVER config archives | safe only through schema-aware source editor + OpenRune build |
| loc/NPC examines | `.data/raw-cache/examines/*.csv` | SERVER definitions | source edit only |
| plugin custom GameVals | module `gamevals.toml` | merged RSCM/generated mappings | edit originating TOML, then merge/build |
| cache-derived base GameVals | official GameVal cache data dumped by OpenRune | `.data/gamevals-binary/gamevals.dat` | inspect; never hand-edit as ordinary content |
| merged/generated mappings | `.data/gamevals/**` and generated dat outputs | mapping provider inputs | source-aware inspection; write only when OpenRune identifies that file as the entry's source |
| interfaces/components | cache definitions + server Kotlin content + GameVal mappings | LIVE/SERVER structures | do not assume "interfaces are TOML"; author according to the actual source domain |
| teleports | usually Kotlin source and/or cache params; sometimes runtime-derived | runtime behavior | semantic inspection/navigation first; only edit through supported source representation |

Generated LIVE/SERVER cache files are never the fallback write target for an unsupported connected
resource.

## 7. RSCM and GameVals are a source-aware symbol system

RSCM is more than a convenient name-to-ID text file.

OpenRune's `RSCMType` defines namespaces including areas, content groups, interfaces/components,
locs, NPCs, objs, params, DB tables/rows/columns, sequences, queues, stats, synths, varbits,
varps, and others.

`GameValProvider` merges several sources:

- `.data/gamevals-binary/gamevals.dat`;
- generated GameVal data;
- module-local `content/**/gamevals.toml`;
- `api/**/gamevals.toml`;
- `.data/gamevals/*.rscm`.

It also tracks which file supplied a mapping.

That provenance is critical. Studio's symbol model should therefore be:

```
GameValEntry(
    namespace,
    name,
    id,
    sourceFile,
    sourceKind,
    generated,
    generation
)
```

rather than merely `Map<String, Int>`.

### Why source provenance matters

`PluginGamevalMerger` scans module `gamevals.toml` files and merges new values into central
RSCM tables. Editing the merged RSCM blindly can create a second source of truth.

Studio should use the originating module TOML when that is the authored source.

### IntelliJ tooling metadata

`openRune-intelliJ-tools.toml` currently declares RSCM mapping roots:

- `.data/gamevals`;
- `.data/gamevals-binary`;
- `content/`;
- `api/`.

It also enables file-provider and alter-constant-provider behavior.

Studio should consume this file as a discovery hint where present instead of hardcoding only the
stock mapping directories. This is also an important compatibility hook for forks.

## 8. Map packing and Map Studio integration

OpenRune SERVER map groups extend normal map data with server-only files.

`GameMapDecoder` reads:

- file 0: terrain/map data;
- file 1: loc data;
- file 5: NPC spawns;
- file 6: ground-object spawns;
- file 7: server area data.

The server builds collision/loc-zone state from terrain and locs, applies bridge/LINK_BELOW plane
resolution, loads area indexes, and emits NPC/ground-object spawns.

`MapPackers` creates files 5/6/7 from raw TOML.

### Map Studio should render one world but preserve ownership

Map Studio can overlay:

- client terrain and locs;
- OpenRune NPC spawns;
- OpenRune ground-object spawns;
- OpenRune area polygons;
- semantic content markers;
- extracted teleports;
- future dynamic/runtime observations.

Every selectable overlay entity must retain a source badge such as:

- `LIVE map`;
- `raw-cache NPC source`;
- `raw-cache area source`;
- `Kotlin semantic fact`;
- `runtime observation`.

"Save" must route to the entity's authority, not to whichever cache happens to be open.

A useful right-click flow is:

```
NPC spawn
  -> Inspect npc.npc_name
  -> Open raw-cache source
  -> Show references
  -> Show related scripts
  -> Open SERVER definition
```

For areas:

```
area polygon
  -> edit vertices in Map Studio
  -> validate OpenRune area limits
  -> write source TOML transactionally
  -> optional project build
  -> reload SERVER map file 7
```

## 9. PackServerConfig and server-enriched definitions

`PackServerConfigOSRS.kt` is a central integration point. It merges client definitions with
server-specific TOML/config data and packs SERVER definitions.

Current packed domains include object, NPC, item, varp, inventory, sequence, health bar,
mesanim, walktrigger, varn/varnbit, varcon/varconbit, varobj, hunt, stat, projectile, bas, and
related server-only semantics.

For merged types the packer fingerprints the corresponding base client archive, so a change to
the LIVE base definition can force the dependent SERVER type to repack.

Examples of useful server-only semantics include:

- `ObjectServerType.contentGroup`;
- routing/collision flags;
- server params;
- server actions;
- object transforms/category/description;
- item content groups, equipment/trade metadata and params;
- server sequence timing metadata.

Studio should inspect these types and their authored TOML sources. It should **not** copy
`PackServerConfig` into Studio or independently reproduce its merge/pack ordering.

## 10. Interfaces and teleports

### Interfaces

Do not model OpenRune interfaces as "TOML interfaces".

There are several layers:

- actual client interface/component definitions in the cache;
- symbolic interface/component GameVals;
- Kotlin server scripts that open, update and react to interfaces;
- optional pack inputs/tooling such as OR2 `PackIfType`.

A future Interface Studio should join these layers but preserve their provenance.

### Teleports

There is no single canonical teleport TOML registry in the current checkout.

For example, standard spell teleports are implemented in Kotlin. Destinations may be:

- explicit `CoordGrid` values in source;
- alternate source-code destinations;
- derived from cache/server params such as `spell_telecoord`;
- calculated dynamically at runtime;
- gated by quests, areas or runtime state.

Therefore a Map Studio **Teleports** overlay should be a semantic projection, not a generic TOML
editor.

Each teleport marker should report confidence/provenance:

```
Varrock alternate teleport
Destination: 3164,3487,0
Source: SpellTeleportScript.kt
Kind: static source coordinate
Confidence: exact
```

For a dynamic target:

```
Destination: runtime/computed
Source: <exact code span>
Open source
```

Studio must not claim a complete destination when the code computes it dynamically.

## 11. How much RSMod/OpenRune API Studio should understand

Studio should understand **semantic contracts**, not become a second game server.

The Mining implementation shows the useful boundary well.

### High-value concepts to recognize

- RSCM/GameVal symbols and reverse mappings;
- `ObjectServerType`, `ItemServerType`, `SequenceServerType` and other enriched definitions;
- content groups such as `content.rock`;
- script registration APIs such as `onOpContentLoc1/2/3/U`;
- DB table/row relationships such as `MiningRocksRow`;
- params and typed param references;
- `CoordGrid` locations;
- `LocRepository.add/del/change` world mutation;
- map-cycle/timer/queue relationships;
- `anim`, `spotanim`, `soundSynth`, `telejump` and similar observable effects;
- skilling products/rewards;
- drop tables;
- quest requirements;
- area checks;
- interface/button event registration.

These become neutral graph facts such as:

```
Handler(content.rock, op=1)
UsesDbTable(dbtable.mining_rocks)
RockRow(loc.ironrock1 -> obj.iron_ore, level=15, ...)
MayChangeLoc(emptyRock, respawnCycles)
AwardsSkill(stat.mining)
PlaysAnimation(seq...)
```

### Concepts that remain server-owned

Studio should not embed or reproduce:

- `Player` runtime implementation;
- `ProtectedAccess`;
- dependency injection/runtime object graph;
- live repository mutation internals;
- script scheduler execution;
- inventory transaction engine;
- quest runtime state;
- server networking/protocol state.

Those may be **observed** through a bridge or modeled by a bounded Studio simulator, but OpenRune
remains the runtime authority.

## 12. OpenRune semantic knowledge packs

Avoid hardcoding dozens of API method names throughout Studio.

Introduce a versioned OpenRune semantic adapter/knowledge pack describing recognizable concepts:

```
openrune-semantic-profile
  script registrations
  symbolic namespaces
  world mutation APIs
  coordinate constructors
  table DSLs
  interface APIs
  teleport APIs
  skilling/reward APIs
  timers/queues
  observable client effects
```

The PSI/source index produces neutral facts using that profile.

The profile should be selected by detected OpenRune/RSMod API shape, not merely by folder name.
Unknown APIs degrade to generic source navigation rather than unsafe guessed semantics.

## 13. Running-server integration

Do **not** make JVM attach/reflection against the running game server the normal integration path.

Direct attach has poor properties:

- exact classloader/version coupling;
- intrusive JVM permissions;
- race conditions against mutable game state;
- hard local-process assumptions;
- weak remote-server support;
- risk of destabilizing the server.

Prefer an explicit, versioned **Studio Bridge**.

A bridge can run in one of two places:

1. a small OpenRune plugin/module in the server process exposing intentionally selected runtime
   snapshots/events; or
2. an out-of-process helper launched through the imported checkout's Gradle/runtime classpath.

Start read-only. Useful runtime facts include:

- server build/revision/project fingerprint;
- current map clock/tick;
- loaded content/plugins;
- registered handlers;
- dynamic loc changes;
- NPC/object runtime spawns;
- selected var/state snapshots;
- active areas;
- simulation events useful to RSProx/cutscene/content tooling.

Every connection should verify that the bridge project fingerprint matches the Studio project.

The existing `tools:osrs-mcp` module is useful evidence for the process-isolation model. It is not
itself a complete Studio runtime bridge, but it already supports reloadable GameVal and
LIVE/SERVER cache inspection from the server's own classpath.

## 14. Fork compatibility

OpenRune support should be capability-based, not a single yes/no test.

### Compatibility tiers

**Tier 0: Generic OSRS cache**

- cache editor/rendering only;
- no OpenRune project semantics.

**Tier 1: Structural OpenRune project**

- recognizable project/config/build markers;
- revision/config metadata;
- LIVE/SERVER path roles;
- declared build task discovery.

**Tier 2: Symbol-compatible**

- recognized RSCM/GameVal providers or mapping metadata;
- symbolic inspection/references.

**Tier 3: Raw-cache schema compatible**

- recognized NPC/object spawn/area/server-config source schemas;
- structured source inspection;
- writes only for schemas Studio can round-trip losslessly.

**Tier 4: Semantic-source compatible**

- evaluated production source sets;
- Kotlin PSI resolves enough known OpenRune/RSMod API shapes to build content facts/graphs;
- teleports, handlers and skill flows become available.

**Tier 5: Validated toolchain integration**

- compatible build tasks and known publication contracts;
- Studio may publish supported source resources then invoke the project build and verify outputs.

A fork can support one tier without supporting the next.

### What variations should be tolerated

Tolerate through capability discovery:

- moved modules;
- renamed Gradle project paths;
- additional source sets;
- alternate cache/source paths through explicit overrides;
- additive custom content;
- mapping roots declared by tooling metadata;
- known build tasks discovered from evaluated Gradle model.

### When Studio must stop guessing

Fall back to read-only/generic behavior when:

- source schema is unknown;
- a custom packer changes ownership semantics Studio cannot prove;
- GameVal provenance cannot be established;
- build task semantics are ambiguous;
- an OR2/API mismatch is unverified;
- protocol revision does not match;
- a fork replaces stable APIs with unrecognized equivalents.

The correct extension point is then a fork-specific adapter/profile/plugin, not another layer of
path heuristics in the stock OpenRune provider.

## 15. Synchronization model

Studio needs domain generations rather than one "project dirty" bit.

Suggested domains:

```
PROJECT_LAYOUT
GAME_CONFIG
TOOLCHAIN
LIVE_CACHE
SERVER_CACHE
GAMEVALS
RAW_MAP_SPAWNS
RAW_MAP_AREAS
SERVER_CONFIG_SOURCE
SOURCE_MODEL
SEMANTIC_GRAPH
RUNTIME_BRIDGE
```

When an external OpenRune build runs, Studio should detect changed LIVE/SERVER identities and mark
dependent domains stale.

When Studio writes supported source:

1. compare source baseline/fingerprint;
2. stage and atomically write the source;
3. mark dependent generated domains stale;
4. optionally invoke the project's declared build task if policy allows;
5. reopen/verify outputs;
6. advance baselines only after verification succeeds.

If a user edits server source externally while Studio is open, Studio refreshes the affected
source/index domains. It never silently overwrites the external change with an older editor model.

## 16. Concrete next integration work

1. Add a lightweight `OpenRuneGameConfig` model for name/revision/subrevision/environment/world.
2. Parse `gradle/libs.versions.toml` for OR2 and rsprot protocol target during import.
3. Show those values in the OpenRune import preview before access selection.
4. Add a compatibility status separating FileStore, definition, Studio-scene and protocol support.
5. Implement project-domain generation/change tracking.
6. Replace flat GameVal maps with source-aware entries and refresh generations.
7. Consume `openRune-intelliJ-tools.toml` mapping roots when present.
8. Add Map Studio overlays for raw NPC spawns, ground-object spawns and area polygons.
9. Implement source-safe editors for raw map spawns/areas before allowing writes.
10. Add teleport semantic extraction and a Map Studio teleport overlay.
11. Add server-config overlay inspection using PackServerConfig source semantics.
12. Version the OpenRune semantic knowledge profile and expand Mining as the first full skill-flow acceptance fixture.
13. Prototype a read-only version-matched Studio Bridge rather than JVM attach/reflection.
14. Add CI/tooling that detects newer OR2 releases and opens/flags dependency updates without runtime auto-upgrade.

The governing invariant is:

> Studio may understand OpenRune deeply, but it must never become a competing authority for
> OpenRune-owned source/build/runtime state.
