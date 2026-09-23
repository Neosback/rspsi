# OpenRune Ecosystem Integration Notes

> **Scope:** this is a subsystem integration reference, not the project execution order.
> `docs/ROADMAP.md` decides what is worked on next. Phase labels in this document describe
> OpenRune integration dependencies only.

This document records the OpenRune capabilities that are relevant to RSPSi / OpenRune Studio so implementation work does not repeatedly rediscover the same backend features or build competing abstractions.

## Architectural direction

OpenRune Studio should treat the OpenRune ecosystem as the underlying content toolchain, not merely use OpenRune-FileStore as a cache reader.

The preferred flow is:

```
Studio UI
   |
Neutral editor contracts
   |
Edit transactions / validation
   |
OpenRune builders + codecs
   |
Dirty definition / resource registry
   |
Explicit writable output cache
   |
Incremental packing / reference updates
```

OpenRune-specific classes must remain behind cache/content adapters. Editor-facing APIs should continue to use RSPSi-owned neutral views and edit contracts.

## OpenRune-FileStore

### Definitions, builders, and codecs

Useful modules and types include:

- `dev.or2:definition`
- `dev.or2:osrs`
- `dev.or2:opcode`
- `ObjectType`
- `ObjectTypeBuilder`
- `ObjectType.toBuilder()`
- `ObjectCodec`
- `BuilderDefinitionCodec`
- `DefinitionOpcode`
- `DefinitionOpcodeProperty`
- `OpcodeList`

Important consequence:

Studio should not construct object-definition byte streams manually. Object edits should flow through OpenRune builders and codecs.

Canonical object flow:

```
ObjectType
  -> toBuilder()
  -> edit builder
  -> build()
  -> ObjectCodec.encode(...)
  -> raw config payload
```

A newly encoded payload should be decoded again and re-encoded before it is eligible for persistence. This provides a codec round-trip validation gate.

### Opcode 249 parameters

OpenRune models parameterized definitions through reusable `Parameterized` / `MutableParameterized` abstractions. Object opcode 249 is therefore not a special-case data model.

Studio should eventually expose one reusable parameter editor that can serve objects, NPCs, items, structs, and other parameterized definitions.

The neutral object inspector introduced by PR #35 already exposes typed parameter IDs and values. Mutation should extend that same neutral boundary rather than exposing OpenRune maps directly to the UI.

### Writable cache adapter

RSPSi already contains an explicit writable OpenRune adapter:

- `OpenRuneCacheStore.open(Path)` remains read-only.
- `OpenRuneCacheStore.openWritable(Path)` wraps OpenRune `CacheDelegate`.
- `CacheDelegate` delegates writes to `CacheLibrary.put(...)`.
- `flush()` / close update reference tables through `Cache.update()`.

This means Studio does not need another low-level cache-writing implementation.

The safety rule remains:

**Never write through the normal source-cache session. Writes must target an explicitly selected output cache.**

### DSL support

OpenRune-FileStore includes content DSL helpers such as:

- `objectType(id) { ... }`
- `ObjectType.edit { ... }`

These are useful references for edit semantics, but Studio should keep a neutral transaction API instead of coupling its UI directly to Kotlin DSL blocks.

## FileStore tools module

The `dev.or2:tools` module contains significantly more than the writable cache delegate.

Relevant capabilities include:

- `CacheDelegate`
- cache build task ordering / `TaskPriority`
- incremental cache target tooling
- object/config DSL helpers
- map packing
- model packing
- sound/MIDI packing
- CS2 packing
- interface reference updating
- world-map generation
- GameVal publishing and reference indexing

This should be treated as reusable infrastructure for future Studio modules rather than reimplemented from scratch.

## Incremental packing

OpenRune tools already model cache-building as ordered/incremental work.

Studio should eventually maintain a dirty-resource registry such as:

```
Dirty object definitions
Dirty map squares
Dirty scripts
Dirty interfaces
Dirty gamevals
...
```

A project build should encode and write only affected resources and any required derived/reference data.

The target experience is closer to:

```
Build Project
  - 3 object definitions encoded
  - 1 map square packed
  - 2 client scripts compiled
  - GameVal references updated
  - cache reference tables updated
```

rather than a monolithic "save cache" operation.

## GameVals and symbolic references

OpenRune contains GameVal / RSCM infrastructure and RSPSi already has `OpenRuneSymbolicNameProvider`.

Relevant concepts include:

- GameVal publishing
- `GameValReferenceIndex`
- symbolic mappings for objects, params, varbits, varps, enums, scripts, interfaces, areas, categories, DB rows/tables, etc.
- `.rscm` source data

Studio should prefer symbolic names when available while always retaining numeric IDs underneath.

Example:

```
Object: rocks_copper [1276]
Animation: mining_pickaxe_swing [8321]
Param: mining_level [451]
```

This becomes especially important for a no/low-code content workflow.

## openrune-toml-parser

This repository is not just a generic TOML parser. It includes RuneScape-oriented configuration machinery such as:

- `RsConfig`
- `RsTableHeaderBehavior`
- tokenized replacement
- constant replacement
- typed table-row handling
- TOML serializers/deserializers
- encoder/decoder registries

Long term, Studio should support both visual and source representations backed by the same content model:

```
Visual Editor <-> Content Model <-> RsConfig/TOML Source
                         |
                    OpenRune codecs
                         |
                       Cache
```

This allows OpenRune projects to remain source-controlled and human-readable while Studio provides visual tooling.

## OpenRune-Server

OpenRune-Server contributes the server/content half of the Studio goal.

Relevant areas include:

- server-side definition codecs such as `ObjectServerCodec`
- GameVal datasets
- server content definitions
- runtime/content systems that can eventually be linked to client-cache definitions

This supports the longer-term Studio model where selecting a world object can show both client and server context:

```
Rocks [1276]

CACHE
  models
  actions
  animation
  params

SERVER
  mining binding
  required level
  reward
  respawn

REFERENCES
  map placements
  scripts
  gamevals
```

The Studio should not hard-code OpenRune Server into the map renderer. Server/content integration belongs behind separate project/content adapters.

## Other OpenRune repositories

The broader organization also contains:

- OpenRune-Developer-Tools
- OpenRune-IntelliJ-Tools
- OpenRune-FileStore-Server
- js5server
- OpenRune-Central-Server
- Launcher / Bootstrap
- openrs2 fork

These should be checked before implementing developer UX, source tooling, remote cache/project services, JS5 serving, or launcher/bootstrap features.

## Decisions for current implementation

1. Keep source cache sessions read-only.
2. Use explicit output-cache sessions for persistence.
3. Keep OpenRune types out of Editor-facing APIs.
4. Use OpenRune builders and codecs instead of hand-writing definition opcodes.
5. Validate encoded edits through decode -> encode round-trip before persistence.
6. Model opcode 249 as reusable typed parameters.
7. Reuse GameVal/symbol infrastructure instead of inventing a second naming system.
8. Prefer incremental build/write flows over whole-cache rewrites.
9. Treat RsConfig/TOML as a future first-class source representation.
10. Treat OpenRune Server integration as a separate content layer, not renderer coupling.

## Immediate implementation sequence

### Phase A: safe in-memory definition editing

- begin an object edit transaction
- clone through `ObjectType.toBuilder()`
- edit supported scalar fields
- add/update/remove opcode 249 params
- build
- encode through `ObjectCodec`
- decode encoded bytes
- encode again
- reject non-canonical/non-equivalent round trips
- expose preview data through neutral RSPSi views

No cache write occurs in this phase.

### Phase B: output-cache persistence

- explicit output-cache target
- copy/source provenance checks
- dirty definition registry
- write object config payload
- flush/update reference tables
- reopen and verify written definition
- integrate undo/redo at the project transaction layer

### Phase B implementation status

The first verified Studio publishing path is now implemented with these invariants:

- `LoadedOsrsCacheSession` owns a cache-scoped `ObjectDefinitionEditWorkspace`; UI panels no longer own transaction lifetime.
- `dirty()` means the preview differs from the immutable read-only source.
- `hasUnpublishedChanges()` separately means the current preview differs from the last successfully-published output baseline. Before the first publish, the immutable source is the baseline.
- Publishing records the exact decoded preview that was written; it does not reset the transaction, so normal `EditorCommand` undo/redo remains valid. Undoing a published edit back to the source value therefore becomes an unpublished output change until that reversion is published.
- Output builds snapshot an immutable `BuildPlan` before filesystem work begins. Edits made while a build runs cannot alter the bytes in that build and remain unpublished afterward.
- New output creation remains copy-on-build: source cache -> staging clone -> validated object payload writes -> flush -> read-only reopen -> byte/semantic/canonical verification -> publish staging directory.
- An explicitly-selected existing output can be updated transactionally: verify its edited definitions against the expected prior published snapshots -> clone the existing output to staging -> write/verify there -> move the old output to a rollback sibling -> publish the verified staging directory -> remove the rollback copy.
- A stale or unrelated existing output is rejected before replacement when an edited definition does not match its expected publication baseline.
- After the first successful publish, the loaded cache session binds its publication snapshots to that explicit output directory. Studio does not silently carry "published" state across a path change.
- The source identity, bound output path, and exact verified published object snapshots are persisted in a versioned Studio provenance file with atomic replacement.
- On cache reload, persisted provenance is accepted only when the source path and cache identity still match and the output cache still decodes canonically to every saved published snapshot.
- Provenance revalidation/restoration runs during cache-session initialization before the service publishes the new cache as READY, so editor UI cannot begin from a stale source-only preview.
- Restored transactions hydrate lazily to the verified published preview. The workspace also refuses a stale restoration if a live publication target has already won the initialization race.
- Already-published definitions are excluded from new build plans; only unpublished snapshots are persisted.
- Studio close/dirty gating consults the cache-scoped workspace as well as the current map session, so changing regions cannot hide unpublished definition edits.

The next persistence work should generalize this durable transactional publication model beyond object definitions and into a project-level dirty-resource/build registry.

### Phase C: broader content studio

- NPC/item/struct/enum/varbit definition editors
- reusable parameter editor
- GameVal browser and symbolic references
- RsConfig/TOML source mode
- map/config cross references
- interface / CS2 / model / audio tools
- OpenRune Server content bindings
