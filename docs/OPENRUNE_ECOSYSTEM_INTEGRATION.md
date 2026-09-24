# OpenRune Ecosystem Integration Notes

> **Scope:** this is a subsystem integration reference, not the project execution order.
> `docs/ROADMAP.md` decides what is worked on next. Phase labels in this document describe
> OpenRune integration dependencies only.

This document records the OpenRune capabilities that are relevant to RSPSi / OpenRune Studio so implementation work does not repeatedly rediscover the same backend features or build competing abstractions.

For the published artifact inventory, current versions, mirrored OpenRS2/compiler artifacts, adoption status, and XTEA revision boundary, see `docs/OPENRUNE_MAVEN_CATALOG.md`. For the verified OpenRune Server cache/build/GameVal/raw-map/semantic/fork model, see `docs/OPENRUNE_SERVER_INTEGRATION_MODEL.md`. Both must be checked before adding new cache/content infrastructure.

## Architectural direction

OpenRune Studio should treat the OpenRune ecosystem as the underlying content toolchain, not merely use OpenRune-FileStore as a cache reader.

The generic standalone persistence flow is:

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

That flow applies when Studio is editing an independently selected cache and publishing to a separate Studio-managed output. It is **not** permission to write directly into an OpenRune Server project's generated cache directories. Connected OpenRune projects use the stricter source/build flow below.

OpenRune-specific classes must remain behind cache/content adapters. Editor-facing APIs should continue to use RSPSi-owned neutral views and edit contracts.

## Project modes and cache ownership

Studio has two intentionally different cache workflows. They must not be collapsed into one generic "open cache and save it" path.

### Standalone local-cache mode

When OpenRune Server integration is disabled or no server project is connected:

1. the user chooses a cache directory explicitly;
2. Studio opens that selected cache read-only;
3. authored edits live in Studio transactions/workspaces;
4. persistence targets a separate, explicitly selected Studio output cache;
5. copy-on-build, staging, provenance, stale-output checks, verification, and rollback protect both the selected source and prior output;
6. OpenRS2/FreshCache is optional acquisition tooling only and is never invoked merely because the user opened Studio.

This is the normal path for a user who only wants the map editor. No OpenRune Server project layout is required.

### Connected OpenRune Server mode

When the user imports/opens an OpenRune Server Studio project, that external project already owns its cache lifecycle. The launcher/project descriptor is the sole owner of the connection; the retired settings-driven `OpenRuneServerPlugin` path must not be recreated.

Current OpenRune Server source establishes these roles:

| Project path/resource | OpenRune role | Studio policy |
| --- | --- | --- |
| `.data/cache/LIVE` | full client-facing cache produced by the OpenRune cache build | read-only generated artifact; default scene/render/cache-definition input |
| `.data/cache/SERVER` | server-oriented/minimized cache produced alongside the live cache | read-only server-semantic/runtime input; never substitute it for the client scene cache |
| `.data/raw-cache/**` | resource-specific declarative/generated inputs consumed by OpenRune tooling | inspect and edit only through a resource-specific publisher that understands the exact schema |
| `content/**/*-pack/src/main/resources/**` | source-controlled custom cache/content pack inputs | preferred authoring surface when a supported OpenRune publisher exists |
| `content/**/gamevals.toml` | source-controlled custom symbolic ids | edit this source form when supported rather than overwriting generated GameVal outputs |
| `.data/gamevals/**` and `.data/gamevals-binary/**` | generated/resolved GameVal data used by the project | primarily read/inspect; do not treat generated files as the universal source of custom symbols |
| `game.yml` | project revision/environment configuration | inspect for project identity and compatibility; never silently rewrite it as part of map editing |

The current upstream implementation can be re-verified in:

- `OpenRune/OpenRune-Server/or-cache/src/main/kotlin/dev/openrune/CacheTools.kt`
- `OpenRune/OpenRune-Server/or-cache/src/main/kotlin/dev/openrune/ServerCacheManager.kt`
- `OpenRune/OpenRune-Server/server/app/src/main/kotlin/org/rsmod/server/app/GameServer.kt`

`CacheTools.kt` explicitly targets both `.data/cache/LIVE` and `.data/cache/SERVER`, and its normal build path constructs the server cache separately from the client-facing cache. `ServerCacheManager` reads the server cache while also loading client JS5 groups from the live cache. The distinction is therefore part of OpenRune's runtime/build contract, not just a naming convention.

### Canonical connected-project flow

The connected workflow should be:

```
OpenRune project root
   |
   +-- game.yml
   +-- content/**/*-pack/src/main/resources
   +-- content/**/gamevals.toml
   +-- .data/raw-cache/**              (resource-specific only)
   |
   | Studio discovers + fingerprints project/source state
   | Studio reads LIVE and SERVER as generated inputs
   v
Studio edit transaction / ChangePlan
   |
   | supported, lossless project publisher only
   v
OpenRune-owned source/staging artifacts
   |
   | project Gradle wrapper
   v
:or-cache:buildCache
   |
   +------------------------------+
   |                              |
   v                              v
.data/cache/LIVE             .data/cache/SERVER
client-facing output         server-facing output
   |                              |
   +-------------+----------------+
                 |
                 | reopen + verify expected revision/content/fingerprints
                 v
          Studio marks publish successful
```

The OpenRune build owns synchronization between the two generated caches. Studio must not attempt to keep them synchronized by independently patching both binary directories.

### No-clobber contract for connected projects

The following rules are mandatory for OpenRune project integration:

1. **Connecting is discovery/read-only.** Connecting a project must never download, replace, rebuild, or normalize an existing cache.
2. **Never auto-run `FreshCache`.** `FreshCache` / `:or-cache:freshCache` is an explicit bootstrap/reset operation, not project-open behavior. An existing project may contain intentional custom content.
3. **No direct Studio writes to `LIVE` or `SERVER`.** Both are generated build products in connected mode even though FileStore can technically open writable caches.
4. **Read the correct binary for the concern.** Use `LIVE` for client scene/render/cache semantics. Use `SERVER` only for server-oriented semantics that genuinely come from that cache.
5. **Publish source-first.** A resource may be published into an OpenRune project only when Studio has a defined, lossless mapping to an OpenRune-owned source/staging representation consumed by the project's normal build.
6. **Stale-source detection is required.** Before modifying project source, compare the current source/project fingerprint with the baseline Studio inspected. If another tool or developer changed it, abort the write and surface the conflict rather than overwriting it.
7. **Source writes are transactional.** Stage and validate source-file changes, replace atomically where possible, and retain enough provenance to revert or explain exactly what Studio changed.
8. **Use the project's build entry point.** Invoke the detected project Gradle wrapper/task rather than recreating OpenRune's `CacheTool`, pack ordering, GameVal merge, or LIVE/SERVER synchronization inside Studio.
9. **Build failure is not publication.** If the OpenRune build fails, Studio keeps the edit unpublished and must not advance its publication baseline.
10. **Reopen and verify both outputs after a successful build.** A connected publication is complete only after the expected LIVE and SERVER artifacts can be reopened and the resource-specific acceptance checks pass.
11. **External rebuilds invalidate cached assumptions.** If either generated cache or a source baseline changes while Studio is open, invalidate affected publication state and reload/reconcile before a subsequent publish.
12. **Unsupported resource mappings stay read-only.** Studio must not patch `LIVE` as a fallback when it cannot safely express a change in OpenRune's project source model. It should report that connected-project publishing for that resource is not yet supported.

The last rule is particularly important for map editing. OpenRune currently has resource-specific map/server packers and raw map data, but that must not be assumed to be a complete source representation for arbitrary client terrain/location archive edits. A full Studio map publisher needs an explicit OpenRune-consumed source format/build hook before integrated map publishing can be enabled safely.

### Expected user experience

Project creation/opening is owned by the Project Launcher described in `PROJECT_LAUNCHER_AND_DASHBOARD.md`.

Standalone project:

```
New Project
   -> Standalone OSRS Cache
   -> name project
   -> choose supported source cache
   -> create
   -> project loading gate opens/verifies cache
   -> Content Studio
   -> edit
   -> Publish/Export to separate explicit output cache
```

Connected OpenRune project:

```
New Project / Link Existing
   -> OpenRune Server
   -> name Studio project
   -> choose OpenRune project root
   -> Studio discovers revision, LIVE, SERVER, source roots, build tasks
   -> choose Inspect / Author / Managed Build / Developer policy
   -> create/link
   -> project loading gate binds LIVE + SERVER + required services
   -> Content Studio
   -> edit
   -> Publish to Project when policy/resource supports it
   -> update supported OpenRune source artifacts
   -> run :or-cache:buildCache when Managed Build permits it
   -> reload and verify LIVE + SERVER
```

A connected user should not have to re-select `.data/cache/LIVE` manually under the normal layout. Non-standard projects may use explicit saved path overrides, but Studio should never silently search outside the connected project root.

The integration preset is only a convenience over granular capabilities. It never changes the no-clobber rules above, and even Developer policy does not make `FreshCache` an automatic action.

### Project-owned integration path

The settings-driven `OpenRuneServerPlugin` path has been retired. There must be one imported-project
connection model:

- `OpenRuneServerAdapter` plus `ServerConnection`, `ServerProjectInspection`,
  `ServerPathKey`, and `ServerBuildTask` own structural detection, cache roles, overrides,
  fingerprints, and declared project actions;
- `OpenRuneServerProvider` binds optional source/symbol/content domains from that inspected project;
- `ServerIntegrationService` coordinates the active project session.

The provider must reuse the neutral project inspection/connection model for:

- project identity and fingerprint;
- LIVE/SERVER/raw/source path discovery;
- revision/environment compatibility;
- build-task discovery;
- path/command overrides;
- cache-role binding;
- stale-project detection.

`ServerIntegrationService` remains the application/session coordinator. The OpenRune provider delegates project-layout/cache/build discovery to the shared neutral adapter/inspection layer instead of recreating it. `CacheSourceProvider` may expose the connected project's LIVE cache as a read-only cache source, but it must not own project publishing.

This convergence also prevents the UI, plugin, and legacy adapter paths from disagreeing about where a project's caches live or which build command is authoritative.

### Version/toolchain compatibility

Do not equate an OpenRune project revision with complete compatibility.

The current upstream server pins OR2 3.0.3 and rsprot `osrs-240` artifacts, while its cache
builder accepts revision/subrevision/environment from `game.yml`. Studio must separately track
FileStore readability, decoder compatibility, Studio scene support, and server protocol support.

Studio must not runtime-auto-upgrade its embedded OR2 dependency to match an imported checkout.
Build/publication operations should use the imported project's own Gradle wrapper so they execute
against that project's exact OR2/toolchain. See `OPENRUNE_SERVER_INTEGRATION_MODEL.md`.

### Resource authority

Map Studio and other tools may present client/cache state and server-authored state together, but
must preserve source ownership. In particular, OpenRune packs raw NPC spawns, ground-object spawns
and area polygons from `.data/raw-cache/map/**` into SERVER map files 5, 6 and 7. Those are
source-editable domains. Ordinary terrain/loc map files 0/1 do not currently have an equivalent
general stock OpenRune text-source publication contract, so connected-project binary patching is
not an acceptable fallback.

### Startup inspection boundary

Project startup is a separate, bounded inspection tier:

- detect the OpenRune checkout;
- resolve known cache-role paths;
- read revision/config identity;
- discover declared build availability without executing Gradle;
- open LIVE through the normal cache session.

Startup does **not**:

- run `git status`;
- recursively fingerprint LIVE, SERVER, raw-cache, content, GameVals, plugin, or source trees;
- inventory server content;
- evaluate the Gradle project model;
- build Kotlin PSI indexes or the semantic content graph.

A lightweight startup identity is sufficient to enter the project shell. Full source/content
fingerprints remain mandatory immediately before workflows that read/write those source domains or
enforce stale-source protection.

### First-class project convergence status

The first implementation slice establishes these invariants:

- `OpenRuneServerAdapter` / `ServerProjectInspection` are the authoritative OpenRune project-detection and layout model used by the first-party integration provider;
- a persisted `ServerConnection` can flow through probe/connect/open without losing path overrides, command overrides, or its expected project fingerprint;
- an active integration session can expose the exact inspection that established LIVE/SERVER/source/build ownership;
- OpenRune GameVal, declarative-reference, and NPC-spawn providers can consume inspected roots instead of silently re-resolving the stock layout;
- custom OpenRune checkouts remain discoverable even when the old stock declarative layout resolver cannot recognize their content tree;
- structural OpenRune project support is separate from cache-revision verification: a revision outside the currently verified revision-240 cache profile produces a diagnostic instead of disabling all project/source integration;
- OpenRune `obj.*` / `gamevals.obj` conventions map into Studio's neutral item namespace without changing the backend-neutral public namespace contract.

The stock `OpenRuneProjectLayoutResolver` remains useful as a **format-specific declarative adapter** for known OpenRune sidecars and manifests, but it is no longer allowed to decide whether a checkout is an OpenRune project.

The second implementation slice adds the connected project's evaluated Gradle model:

- passive folder detection remains non-executing;
- ordinary project startup does **not** invoke the checkout's Gradle wrapper;
- when a trusted content/source/build workflow explicitly requests the Gradle model, Studio invokes that checkout's wrapper with a temporary Studio init script;
- the resulting neutral model records every evaluated project, project directory, build file, source set, source/resource/output root, declared project dependency, applied plugin implementation class, and task path;
- source/content inventory is enriched from those evaluated source/resource roots, so a module does not have to live under stock `content/**`;
- known OpenRune cache/server actions bind to the actual discovered Gradle task path, so a task such as `:cache-tools:buildCache` works without pretending the module is named `:or-cache`;
- explicit command overrides remain the escape hatch when a fork renames the semantic task itself;
- the evaluated model is attached to `ServerProjectInspection` and exposed through the active first-party OpenRune session.

Gradle build configuration is executable code. The project browser, import flow, and ordinary project-loading gate therefore **must not** evaluate Gradle. Model evaluation belongs only to an explicit trusted content/source/build workflow after the project is open. The injected reporting task reads configured build structure and deliberately avoids resolving external dependency configurations or executing game/server classes.

With exact source roots now available, the third implementation slice adds Kotlin PSI-backed source indexing over the evaluated production source sets:

- PSI/compiler types remain private to the first-party OpenRune adapter; Studio-facing APIs receive neutral semantic facts only;
- every fact carries exact file, offset, line, and column provenance;
- the initial fact vocabulary covers declarations, calls, symbolic references, `PluginScript`, `QuestScript`, script-handler registrations, quest constructor metadata, and var bindings;
- symbolic strings such as `loc.*`, `npc.*`, `obj.*`, `varbit.*`, `varp.*`, `content.*`, `stat.*`, and `synth.*` are indexed without assuming one stock content layout;
- source facts retain Gradle project path, source-set, and package metadata;
- clearly test/integration/benchmark source sets are excluded by default, while custom production source-set names remain eligible;
- the PSI compatibility version is independently pinned through `openruneKotlinSemanticVersion` and currently matches the bundled OpenRune Kotlin 2.2.0 toolchain;
- acceptance covers both synthetic custom-layout fixtures and the bundled OpenRune Mining/Quest source.

This layer is intentionally **structural**, not a claim of full K2 symbol/type resolution. A handler recognized from the PSI call shape is a high-confidence source fact, but cross-module overload/type resolution remains a later enrichment step. That distinction must be preserved in UI wording and edit safety.

The fourth implementation slice introduces the first read-only Semantic Content Graph:

- GameVal/RSCM mappings, Kotlin PSI source facts, and declarative TOML/JSON references are joined into one Studio-owned graph;
- OpenRune aliases such as `obj.*` canonicalize to Studio's neutral `item.*` identity while preserving the original aliases as provenance;
- graph nodes currently represent symbols, scripts, quests, handlers, and source/resources;
- graph edges currently represent declaration ownership, handler targets, ordinary references, quest state use, and var-state bindings;
- every node/edge can carry one or more evidence records rather than collapsing source certainty into a boolean;
- PSI evidence retains exact source spans while older declarative/symbol indexes may provide only file/line-level provenance;
- `CONTENT_GRAPH` is a composite capability: Studio may privately construct source/symbol/reference prerequisites without implicitly enabling those lower-level services for unrelated consumers;
- the graph remains query/read-only. Presence in the graph is **not** proof that a value can safely be rewritten.

The first acceptance joins `onOpContentLoc1("content.rock")`, a `QuestScript` quest var, a `boolVarBit` binding, declarative TOML references, and numeric GameVal/RSCM mappings into one graph while proving `obj.coal` and neutral `item.coal` are one identity.

The fifth implementation slice attaches map-object identity to authored OpenRune server semantics:

- OpenRune's client/LIVE object definition is **not** treated as the source of `contentGroup`; that field belongs to `ObjectServerType` in the generated SERVER cache;
- `PackServerConfig` / `ObjectServerCodec` populate that server field from source-controlled `[[object]]` TOML overlays, so Studio indexes the authored overlay first;
- structured object overlays preserve `id`, `inherit`, `contentGroup`, `[object.params]`, resolved loc ID, and block/field source spans;
- a map/world object's numeric ID can query an `OBJECT_DEFINITION` graph node directly;
- the object node links to its loc identity, inherited loc, content group, parameter IDs, and symbolic parameter values;
- authored-source evidence is marked separately from generic line-scanned declarative references;
- duplicate object overlays are diagnosed instead of silently choosing a source;
- `writableSource=true` means only that the fact came from an authored source file. It does **not** grant mutation authority until an edit lens has stale-source and verification rules.

This gives Studio its first concrete end-to-end map/content path:

    WorldObject.id
      -> loc.* identity
      -> authored [[object]]
      -> content.*
      -> Kotlin handler registration

SERVER-cache `ObjectServerType` decoding should later be added as generated-result verification/fallback, not used to erase the authored provenance that makes low-code editing safe.

The next convergence step is to promote useful domain facets over this graph (for example an object/content inspector and handler lookup), then define the first verified edit lens for a narrow TOML field before attempting arbitrary Kotlin rewriting. CS2 and runtime traces can later attach to the same graph rather than creating parallel relationship systems.

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

- `FreshCache` revision-aware reference-cache acquisition
- `OpenRS2` cache/keys download and cache metadata lookup
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
11. Prefer FileStore `FreshCache` / `OpenRS2` for reference-cache acquisition rather than building another downloader.
12. For the current FileStore workflow, revision 237+ does not require XTEA key acquisition; pre-237 cache support needs explicit key handling or preprocessing.
13. Audit `OpenRune/hosting` before adding a new dependency or reimplementing a published OpenRune capability.
14. Treat OpenRune project `LIVE` and `SERVER` caches as generated read-only artifacts in connected mode.
15. Never auto-run `FreshCache` when connecting an existing OpenRune project.
16. Publish connected-project changes through supported OpenRune source artifacts and the project's canonical cache build, not through Studio's generic writable-output cache.
17. Reuse one neutral project inspection/path/build model across `OpenRuneServerProvider` and `OpenRuneServerAdapter`; do not maintain competing project-discovery implementations.
18. If no lossless OpenRune source mapping exists for a resource, keep integrated publishing disabled for that resource rather than creating LIVE/SERVER drift.

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

The first verified Studio publishing path is now implemented for **standalone explicit-output mode** with these invariants. These rules must not be repurposed to mutate an OpenRune project's generated `LIVE` or `SERVER` cache directly:

- the connected-project flow above remains source-first and build-owned by OpenRune;
- a later OpenRune project publisher must reuse the same transaction/provenance discipline at the project-source boundary, then invoke the canonical OpenRune build and verify both generated caches.

The standalone publishing path currently has these invariants:

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

### Phase B.5: OpenRune connected-project publication

Before enabling cache-changing Studio tools inside a connected OpenRune Server project:

- converge `OpenRuneServerProvider` with the neutral `ServerProjectInspection` / `ServerConnection` path model;
- bind the editor scene to detected `LIVE` read-only and server semantics to detected `SERVER` read-only;
- define a project-source publisher contract with baseline hashes, atomic writes, diagnostics, and rollback metadata;
- implement resource-specific OpenRune publishers only where a lossless source representation exists;
- route publication through the detected `:or-cache:buildCache` task;
- reload and verify LIVE plus SERVER before advancing publication provenance;
- treat external source/cache rebuilds as baseline invalidation;
- leave arbitrary terrain/location project publishing disabled until OpenRune has an explicit source/build hook Studio can target without patching generated caches.

This phase is an integration-safety prerequisite, not a reason to interrupt the current Phase 0 object-correctness PR.

### Phase C: broader content studio

- NPC/item/struct/enum/varbit definition editors
- reusable parameter editor
- GameVal browser and symbolic references
- RsConfig/TOML source mode
- map/config cross references
- interface / CS2 / model / audio tools
- OpenRune Server content bindings
