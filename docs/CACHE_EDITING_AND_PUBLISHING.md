# Cache Editing and Publishing

> **Status:** authoritative persistence and cache-publication blueprint.
>
> This document defines what Save means, what Publish means, and how standalone caches differ from connected OpenRune projects.

## 1. Core rule

Authoring state is not the cache file.

OpenRune Studio must let a user edit, save, close, reopen, undo, inspect, and continue work without repeatedly packing a cache.

The normal lifecycle is:

    read-only source
      -> canonical Studio edit state
      -> durable Studio project state
      -> explicit validation
      -> explicit publish/build
      -> verified output

This separation is required for safety, performance, recoverability, and predictable OpenRune integration.

## 2. Three baselines

A project should track three concepts independently.

### Source baseline

The immutable input identity the edit session began from.

Examples:

- selected standalone cache identity;
- connected OpenRune LIVE cache identity plus project/source fingerprints.

### Edit baseline

The last durable Studio-owned project state.

A user may have zero or many edits relative to the source while still being fully saved inside Studio.

### Publication baseline

The last edit state successfully turned into verified external output.

This lets the UI distinguish:

- unsaved Studio changes;
- saved but unpublished changes;
- fully published state;
- externally changed/stale source or output.

Do not collapse these into one dirty boolean.

## 3. Current reusable implementation

Several existing classes already establish the right pieces.

### SessionAutosaveStore

SessionAutosaveStore writes a complete canonical WorldDocument snapshot atomically and can read it without opening or modifying a cache.

That is the correct architectural proof that edit persistence can be cache-independent.

Current limitation: it is a recovery snapshot, not yet the full durable project-edit model.

### SessionAutosaveCoordinator

This binds recovery snapshots to project identity and writes on editor state changes without changing the saved marker.

Keep that behavior. Autosave should protect work, not pretend it has been published.

### OsrsRegionEncoder

This is the canonical Studio-owned encoder for OSRS terrain and location payloads.

It validates representability, shared terrain heights, revision-sensitive terrain encoding, object shapes, and ordered location packing.

### OsrsRegionSaveCoordinator

This prepares all region payloads before the first write, writes them through MapService, flushes, and only then marks sessions saved.

That is useful for explicit cache-output publication.

It should not be the default Save Project path because it is a cache writer.

Because a write batch can still fail after some backend writes have begun, use this coordinator against a staging/output cache, not the original source cache.

## 4. Save Project

### Required behavior

Save Project should persist Studio-owned authored state without packing cache archives.

Conceptual format:

    project edit store
      manifest
        formatVersion
        projectId
        sourceIdentity
        createdAt
        updatedAt
        resource list
        publication baseline references

      resources/
        maps/
          region-id/
            terrain/object edit snapshot or canonical region snapshot
        definitions/
          ...
      history or recovery metadata as required

The exact on-disk schema may evolve, but it must be:

- versioned;
- atomic at the resource/manifest boundary;
- tied to source identity;
- reopenable without publishing;
- deterministic enough for tests;
- independent of native renderer state;
- capable of representing multiple dirty regions;
- able to distinguish saved from published.

### Near-term implementation route

Do not invent a second serialization stack.

Evolve the existing WorldFragment / SessionAutosaveStore representation into a durable project edit store:

1. keep SessionAutosaveStore as crash recovery;
2. extract/reuse canonical snapshot encoding where necessary;
3. add a project resource manifest;
4. store source identity and region/world coordinates;
5. add explicit Save Project;
6. restore saved project edits on open after validating source identity;
7. preserve publication baseline separately.

Autosave and Save Project may share encoding primitives but have different lifecycle semantics.

## 5. In-memory edit and preview flow

Map edit:

    tool input
      -> command / ChangePlan
      -> WorldDocument
      -> command history
      -> resource dirty state
      -> incremental scene invalidation
      -> renderer preview

No cache encoding is required for this loop.

This is essential for interactive performance. A brush stroke should not pack archive data.

## 6. Standalone cache publication

Standalone mode uses an explicitly selected source cache and an explicit output destination.

The source remains read-only.

Canonical publication transaction:

    source cache
      -> create staging clone/output
      -> validate project source identity
      -> gather saved/dirty resources selected for publish
      -> encode every changed payload before first write where practical
      -> write only changed archives/files through canonical adapters
      -> flush/update affected cache reference tables
      -> close staging writer
      -> reopen staging output read-only
      -> decode and compare semantic output
      -> run relevant parity/round-trip gates
      -> atomically promote or expose verified output
      -> advance publication baseline

If validation fails, do not advance the publication baseline.

### Incremental does not mean unsafe in-place source mutation

A publish may update only changed archives in the output cache. It does not need to rebuild every cache index.

The safety property comes from using a staging/output cache and verification, not from rewriting the whole cache.

## 7. Standalone map publication

For changed OSRS regions:

1. resolve every dirty region from durable/canonical Studio state;
2. encode terrain with OsrsRegionEncoder;
3. encode locations with OsrsRegionEncoder;
4. fail the whole preparation step if any region is not representable;
5. write prepared payloads through the output cache MapService;
6. flush once for the batch;
7. reopen output;
8. decode every published region;
9. compare semantic terrain/object state;
10. advance publication state only after verification.

OsrsRegionSaveCoordinator already implements much of steps 2 through 6 for a writable MapService. The surrounding output/staging transaction owns steps 1 and 7 through 10.

## 8. Connected OpenRune project ownership

A connected OpenRune project is not a standalone cache with a convenient folder layout.

The imported project owns:

- .data/cache/LIVE;
- .data/cache/SERVER;
- its incremental build state;
- build task ordering;
- content/source merge semantics;
- GameVal/RSCM generation;
- SERVER reseeding/finalization.

Studio reads these generated outputs but does not maintain them independently.

## 9. Verified OpenRune build flow

Current OpenRune Server source uses this high-level normal build:

    authoritative project source/config
      -> GameVal/source loading
      -> content-pack/task discovery
      -> ordered cache tasks
      -> incremental LIVE build
      -> SERVER build seeded from LIVE
      -> server-only pack tasks
      -> GameVal / DB / enum finalization

The current OpenRune configuration keeps separate incremental databases for LIVE and SERVER and uses different verification strategies for them.

This matters because directly patching LIVE or SERVER behind OpenRune's back can invalidate the assumptions recorded by its build system.

Studio therefore delegates connected-project binary generation to the imported project's detected build entry point.

## 10. Fresh cache versus normal build

FreshCache/FRESH_INSTALL is bootstrap/reset behavior.

It can acquire or construct a new baseline cache and reset incremental state.

It must not run automatically when opening an existing project. An existing project may intentionally contain custom content.

Normal editing uses the existing project and its normal build.

## 11. Connected-project publication

For each resource, first identify its authoritative source.

If Studio has a lossless supported publisher:

    saved Studio edit
      -> compare source fingerprint with inspected baseline
      -> abort on stale external change
      -> write authoritative source atomically
      -> invoke detected OpenRune build
      -> wait for successful completion
      -> reopen LIVE and SERVER as applicable
      -> verify expected result
      -> advance publication baseline

If no lossless source mapping exists, Studio must not use generated LIVE/SERVER as a fallback.

## 12. Map-specific OpenRune gap

The current stock OpenRune project has explicit source formats for several server map overlays, including NPC spawns, ground-object spawns, and area data.

A general authoritative text/source representation for arbitrary client terrain and location archives has not been established as a stock OpenRune input.

Therefore:

- Map Studio may edit terrain/locations in canonical Studio project state;
- users may save and reopen those edits without packing;
- standalone output-cache publication may be supported;
- connected OpenRune publication of arbitrary terrain/loc changes remains disabled until Studio has an explicit OpenRune-consumed source/build hook;
- Studio must not patch connected LIVE to work around this gap.

This is a product capability gap, not a reason to violate cache ownership.

## 13. OpenRune server-oriented map sources

Where OpenRune already owns a source representation, route edits there.

Examples currently identified:

| Resource | Authoritative source | Generated destination |
| --- | --- | --- |
| NPC map spawns | .data/raw-cache/map/npcs/*.toml | SERVER map file 5 |
| ground-object map spawns | .data/raw-cache/map/objs/*.toml | SERVER map file 6 |
| map areas | .data/raw-cache/map/area/*.toml | SERVER map file 7 |
| server definition overlays | OpenRune raw/config sources | SERVER definitions |
| module GameVals | originating gamevals.toml | merged/generated mapping outputs |

Source provenance must stay attached to the semantic entity so Save/Publish knows where the change belongs.

## 14. UI language

Use distinct actions:

- **Save Project**: persist Studio edits.
- **Save All**: persist all dirty Studio resources.
- **Publish Cache**: build/verify a standalone cache output.
- **Build Project**: run the connected project's supported build after source publication.
- **Revert Unpublished**: return selected saved/working resources to publication/source baseline.
- **Reload External Changes**: reconcile after source/cache fingerprints changed outside Studio.

Avoid a generic Save button that sometimes writes Studio files and sometimes mutates cache archives.

## 15. Failure model

### Save Project failure

- source/cache remains untouched;
- existing previously saved project state remains usable;
- show exact resource/path failure;
- keep current in-memory edits dirty.

### Standalone publish failure

- source cache remains untouched;
- staging output is discarded or preserved for diagnostics;
- publication baseline does not move.

### Connected source-write conflict

- abort before write;
- surface changed source identity;
- require reload/reconcile.

### Connected build failure

- authored source edits may exist;
- generated-cache publication is incomplete;
- publication baseline does not move;
- preserve build log and affected resource state.

## 16. Acceptance tests

At minimum:

1. edit map, Save Project, close, reopen, recover exact semantic state without cache publication;
2. autosave does not advance Save Project or publication baseline incorrectly;
3. source cache bytes remain unchanged during normal authoring and Save Project;
4. multi-region save survives region switching;
5. standalone publish modifies only output/staging cache;
6. encoded region reopens semantically equivalent;
7. failed publish does not advance baseline;
8. connected project open does not run FreshCache;
9. connected publish refuses unsupported terrain/location binary fallback;
10. stale OpenRune source fingerprint blocks overwrite;
11. successful connected build reopens and verifies appropriate generated caches.

## 17. Do not create

Do not add:

- another generic cache writer;
- another terrain/location codec;
- a UI-owned cache save path;
- a second OpenRune build implementation;
- an alternate LIVE/SERVER synchronization routine;
- a save mode that silently writes the source cache;
- a project format that stores renderer buffers instead of authored state.