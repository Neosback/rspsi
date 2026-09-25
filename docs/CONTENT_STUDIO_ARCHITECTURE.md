# OpenRune Content Studio Architecture

## Purpose

OpenRune Studio is not a dashboard wrapped around a map editor. The project home is **OpenRune Content Studio**: a project-scoped workbench where cache, server source, mappings, content, simulation, packet captures and authoring tools can cooperate without each tool inventing its own project connection.

The permanent product model is:

```
OpenRune project
  |
  +-- Content Studio
       |
       +-- Map Studio
       +-- Interface Studio
       +-- Object / Asset Studio
       +-- Server Content Studio
       +-- Skill / content flow views
       +-- NPC / spawn tools
       +-- Cutscene Studio
       +-- RSProx Session Studio
       +-- diagnostics / build / publish tools
       +-- future tools and plugins
```

A tool is a view over shared project services. It is not a separate cache/server connection.

## 1. Project-open contract

Opening an imported OpenRune project must stay cheap.

The blocking project-open gate may:

1. validate the Studio descriptor;
2. detect the OpenRune checkout;
3. resolve known project roles such as LIVE and SERVER;
4. read lightweight revision/config identity;
5. determine declared build availability without executing Gradle;
6. prove that FileStore can open LIVE and obtain a stable cache identity;
7. enter Content Studio.

The blocking gate must **not**:

- decode the full cache;
- run the broad cache decoder census;
- load all definitions, models, sprites, interfaces or audio;
- parse every RSCM/GameVal file;
- inventory content trees;
- evaluate Gradle;
- build Kotlin PSI;
- build semantic content graphs;
- index NPC spawns;
- recursively fingerprint source/cache trees.

Those services are activated by the workspace that needs them.

The first Kotlin implementation of this contract lives in
`Editor/src/main/kotlin/com/rspsi/studio/ProjectOpenCoordinator.kt`.

## 2. Progressive project readiness

"Project open" and "everything decoded" are different states.

Content Studio should expose progressive readiness by domain:

```
PROJECT
  detected
  paths resolved

FILESTORE
  LIVE healthy
  SERVER path known
  cache identity known

CACHE DEFINITIONS
  not loaded | loading | ready | failed

RSCM / GAMEVALS
  not indexed | indexing | ready | stale | failed

SERVER SOURCE
  not indexed | indexing | ready | stale | failed

SPAWNS
  not indexed | indexing | ready | stale | failed

CONTENT GRAPH
  not built | building | ready | stale | failed

RSPROX SESSION
  none | reading | decoded | replay-ready | incompatible
```

The home should be usable while optional domains remain "not indexed".

Counts such as NPC spawns, scripts, quests or graph nodes are useful **after** the corresponding domain has been activated. Content Studio must not show misleading zeroes for work it intentionally has not performed.

## 3. Shared project context

Long term, every workspace should consume a single project context rather than reaching into global services.

Target shape:

```kotlin
interface StudioProjectContext {
    val project: StudioProjectDescriptor
    val openRune: OpenRuneProjectContext?
    val liveCache: CacheDomain
    val serverCache: ServerCacheDomain?
    val gameVals: LazyDomain<GameValIndex>
    val source: LazyDomain<SourceIndex>
    val spawns: LazyDomain<SpawnIndex>
    val contentGraph: LazyDomain<ContentGraph>
    val navigation: WorkspaceNavigation
    val changes: ProjectChangeBus
}
```

This is a conceptual contract, not a requirement to move every existing class immediately.

The important rule is ownership:

> one Studio project owns the source checkout, cache roles, permissions, domain generations and change notifications.

No plugin or tool creates a second OpenRune connection behind that project's back.

## 4. Workspace-demanded cache loading

The current full cache session creates eager definition state and the broad decoder summary. That is appropriate for cache-backed editor workspaces but not for Content Studio startup.

The transition is:

```
Import/Open project
    |
    v
lightweight FileStore health
    |
    +------> Content Studio usable
                    |
                    +-- Open Map Studio
                    |      -> load definitions / map services / scene
                    |
                    +-- Open Object Studio
                    |      -> load definition + asset domains
                    |
                    +-- Open Interface Studio
                           -> load interface/sprite domains
```

The initial implementation may still reuse the existing full `LoadedOsrsCacheSession` when the first cache-backed workspace opens. Later work should split that session into narrower lazy domains so opening Object Studio does not necessarily warm everything Map Studio needs and vice versa.

## 5. RSCM and GameVals

RSCM files are project mappings from human-readable symbols to numeric cache/server IDs. In the vendored OpenRune checkout, entries are plainly represented as mappings such as:

```
fullscreen_worldmap=65475
music_last_id=65476
```

Studio should treat RSCM/GameVals as a **refreshable symbol domain**.

### Activation

Do not parse them during project startup. Activate them when a tool needs symbolic names, references or content relationships.

### Refresh

Once activated:

- watch the known GameVal/RSCM directories for changes;
- also verify their generation when the app regains focus or the relevant workspace is activated;
- do not rely solely on filesystem watcher events, because moves, checkout replacement and platform watcher behavior can lose events;
- refresh atomically into a new immutable symbol snapshot;
- increment a domain generation number;
- notify consumers through the project change bus.

A tool must never hold a forever-live mutable reference to the old symbol tables.

Conceptually:

```
RscmGeneration(
    generation = 14,
    fingerprint = ...,
    loadedAt = ...,
    symbols = immutableIndex
)
```

If a build generates new mappings, the same refresh path handles them.

## 6. Source and generated-cache changes

Source, mappings and generated caches are separate domains and must not share one giant repository fingerprint.

### Source change

A Kotlin/TOML/JSON source edit may stale:

- source index;
- references;
- content graph;
- spawn index if the changed source contains spawns.

It does not automatically mean LIVE changed.

### RSCM/GameVal change

Stales symbol resolution and any semantic views that depend on those symbols.

### LIVE change

A changed LIVE cache identity means cache-backed workspaces may be stale.

If a workspace has no unsaved state, Studio may offer or perform a safe reload according to the workspace contract.

If a workspace has unsaved edits, Studio must **not** silently replace its backing cache. Show the new generation and require a deliberate reconcile/reload action.

### SERVER change

SERVER is a separate OpenRune-generated server cache. It should have its own adapter-specific health/generation state. Do not assume it is interchangeable with LIVE merely because both are FileStore-backed.

## 7. Moved or replaced server checkouts

A Studio project identity must not be derived permanently from the absolute server path.

The first relocation implementation preserves:

- Studio project ID;
- Studio private project-data directory;
- project name;
- access policy;
- workspace metadata;

and changes only the external OpenRune checkout root.

The Projects screen exposes **Relocate** for an imported OpenRune project.

Future improvements:

- detect when a recent project's descriptor exists but its source root is unavailable;
- show "Server checkout moved" distinctly from "Studio project descriptor missing";
- offer Relocate directly from that state;
- optionally remember a repository identity hint such as Git remote + root markers, but never silently relink to a guessed folder.

## 8. Access model

Access policy answers what Studio is allowed to do to the external OpenRune project. It is not a user-role hierarchy.

| UI label | Read project/cache | Write supported source | Declared cache/GameVal/CS2 builds | Server launch | Adapter-declared external commands | Fresh/reset |
| --- | --- | --- | --- | --- | --- | --- |
| Read only | yes | no | no | no | no | no |
| Read + write | yes | yes | no | no | no | no |
| Read + write + build | yes | yes | yes | no | no | no |
| Development access | yes | yes | yes | yes | yes | **no, separate authorization** |

"Supported source write" means Studio may write a source representation it can round-trip safely. It does not mean direct patching of generated LIVE or SERVER caches.

`FreshCache`, reset, replacement or similarly destructive operations remain explicit actions even with Development access.

## 9. Cross-tool navigation

A major Content Studio goal is that tools understand the same content identities and locations.

Example:

```
Mining Skill Flow
  -> Iron rock interaction
      -> Kotlin handler/source
      -> symbolic loc "iron_rock"
      -> world usages / spawn locations
      -> Open in Map Studio
          -> region loads
          -> camera centers on location
          -> matching objects highlighted
```

Do not hard-wire one tool directly to another. Introduce neutral navigation requests such as:

```kotlin
sealed interface StudioTarget {
    data class WorldLocation(...) : StudioTarget
    data class SourceLocation(...) : StudioTarget
    data class CacheDefinition(...) : StudioTarget
    data class ContentSymbol(...) : StudioTarget
    data class SessionTick(...) : StudioTarget
}
```

A central `WorkspaceNavigation` service resolves "Open in Map Studio", "Open source", "Show references", "Show in flow", etc.

This also gives plugins a stable way to participate.

## 10. Skill/content flow views

The semantic graph work already in the repository is useful, but it should become an on-demand Content Studio domain rather than startup state.

A mining view could combine:

- Kotlin PSI facts;
- RSCM/GameVal symbols;
- object definitions;
- interaction handlers;
- requirements;
- animations/GFX;
- item outputs;
- XP rewards;
- world locations;
- NPC/object spawn data;
- linked scripts and configs.

The graph should preserve evidence for every relationship so users can jump to the source or cache fact that produced an edge.

The graph is not the source of truth. It is a navigable projection of source/cache truth.

## 11. RSProx integration

RSProx is a strong future Content Studio integration because it is Kotlin and MIT licensed, and its current source already exposes structured binary-session and replay primitives.

Useful upstream references in `blurite/rsprox`:

- `proxy/.../binary/BinaryHeader.kt`
- `proxy/.../binary/BinaryStream.kt`
- `proxy/.../replay/ReplayTimeline.kt`
- `proxy/.../replay/ReplayTranscriber.kt`

The supplied sample `rsprox-4309-rev239.bin` was successfully recognized as an RSProx header/version-1 Old School capture for revision 239. It should remain a local validation sample only; captures may contain account/session metadata and must not be committed as repository fixtures without sanitization.

### Phased integration

#### Phase A: Session inspector

- choose/drop a `.bin`;
- parse header without decoding the entire stream;
- show revision, client, world/session metadata, duration and packet counts where available;
- validate decoder/cache availability.

#### Phase B: Transcript/timeline

Reuse or adapt RSProx's revision-aware packet decoder and replay timeline:

- packet direction;
- timestamps;
- server ticks;
- decoded packet names;
- searchable properties;
- filters/bookmarks.

#### Phase C: Content Studio projection

Translate decoded observations into neutral Studio events:

- player/NPC position;
- object changes;
- animations;
- graphics;
- varps/varbits;
- interfaces;
- camera/cutscene changes;
- zone rebuilds;
- chat/menu interactions where useful.

These events can feed existing simulation and visualization services without making Map Studio depend on RSProx classes.

#### Phase D: Replay

Two replay meanings should remain distinct:

1. **Protocol replay**: RSProx-style local fake client/server replay using recorded packet timing.
2. **Studio replay**: deterministic playback of normalized Studio events against the editor/simulation scene.

Protocol replay is useful for authentic client behavior. Studio replay is more useful for tools such as cutscene analysis, content visualization and jumping around a timeline.

A future Cutscene Studio could use an RSProx session as reference material, extract camera/animation/event timing, then author a separate editable cutscene representation.

## 12. Kotlin direction

Kotlin is preferred for new application orchestration and Content Studio services where it improves the model:

- coroutines / structured concurrency;
- immutable data projections;
- sealed state models;
- flows for project-domain updates;
- cancellation;
- lazy service activation;
- concise adapters around Java cache/rendering code.

Do not rewrite stable Java rendering/cache code solely for language consistency.

A practical migration direction is:

```
Kotlin-first
  application lifecycle
  project context
  workspace orchestration
  server/content integrations
  semantic/content tooling
  task/progress models
  RSProx bridge

Java or Kotlin
  mature cache codecs
  definitions
  existing neutral editor APIs

native/JVM-specific
  GLFW / ImGui / OpenGL
  native renderer
```

## 13. Future Kotlin/JS / web version

A future web version is plausible, but today's JVM desktop code cannot simply be recompiled to Kotlin/JS. Java dependencies, FileStore filesystem access, LWJGL and ImGui/OpenGL are JVM/native-specific.

To preserve a web path, new Content Studio logic should keep portable boundaries:

- immutable DTOs and IDs;
- no ImGui types in content/domain modules;
- no `java.nio.file.Path` in cross-platform model contracts where a URI/project-relative path can work;
- no direct JVM process execution in domain logic;
- serializers at boundaries;
- coroutines/Flow rather than thread/executor assumptions in new orchestration code.

Later, portable pieces can move into a Kotlin Multiplatform module:

```
studio-model (KMP)
  content graph
  symbols
  navigation targets
  project/domain states
  RSProx-normalized events
  authoring models

desktop-jvm
  filesystem
  Gradle/process control
  FileStore
  ImGui/OpenGL

web-js
  browser storage / remote project service
  web renderer
  web UI
```

The goal is not "make all current Java compile to JavaScript". The goal is to make Content Studio's **domain model** portable enough that a web shell can reuse it later.

## 14. Server-integration audit

The project-first lifecycle does **not** mean every existing server abstraction is obsolete.

### Keep / converge

- `OpenRuneServerAdapter`: authoritative OpenRune layout detection, cache-role resolution and declared build capabilities.
- `OpenRuneServerProvider`: provider-neutral binding from an imported project into optional Studio domains.
- `ServerProjectInspection`: useful immutable project snapshot.
- `ServerIntegrationService`: keep as the active project-domain binder, but evolve it toward lazy refreshable domains rather than ad-hoc connection UI.
- `ServerBuildTask` / `ServerBuildRunner`: useful declared-command execution boundary.

### Retired in the first Content Studio slice

- `OpenRuneServerPlugin` and its project-path/enable/symbol/content-index settings.

Those settings represented the same OpenRune project a second time and could register another provider from the map-editor plugin lifecycle. The launcher/project descriptor is now the sole owner of the external checkout.

### Transitional retirement candidates

These should be reference-audited before removal or migration:

- `ServerConnectionToml`: a separate server-connection persistence format now overlaps the Studio project descriptor. Overrides may still be valuable, but their persistence should ultimately belong to project settings.
- `ServerProject`: an older compact projection that overlaps `ServerProjectInspection`.
- `OsrsBundle.withServerAdapter(...)` / `withServerConnection(...)`: older cache-bundle coupling that may no longer belong once Content Studio owns the project context.
- interactive `probe(...)` paths that existed for the old connection UI. Provider probing is still useful for import/repair, but should not recreate a second in-project connection system.

Do not remove a transitional API until callers/tests are enumerated and its remaining responsibility has a project-owned replacement.

## 15. OpenRune authority and compatibility

Content Studio may understand an imported OpenRune project deeply, but every projected fact must
retain both its **domain** and its **authority**.

Examples:

- LIVE terrain/loc: generated cache domain;
- raw NPC spawn: `.data/raw-cache/map/npcs` authored source;
- raw area polygon: `.data/raw-cache/map/area` authored source;
- GameVal symbol: source-aware mapping with originating file;
- teleport marker: semantic fact extracted from Kotlin/cache params;
- dynamic loc: runtime observation from a future bridge.

The same Map Studio viewport may render all of those at once. A write action must route back to the
correct authority rather than mutating the nearest binary representation.

OpenRune revision compatibility is also multidimensional. A project can have a FileStore-readable
cache while its server protocol or Studio rendering profile is not verified for that revision.
Content Studio must show these statuses separately.

See `OPENRUNE_SERVER_INTEGRATION_MODEL.md` for the verified cache/build/map/GameVal/API/fork model.

## 16. Near-term implementation order

1. Add project-domain generation/change tracking for LIVE, SERVER, mappings, raw map source and source semantics.
2. Add lightweight import metadata for game name, revision/environment/world, OR2 version and rsprot protocol target.
3. Make RSCM/GameVals source-aware and refreshable, including `openRune-intelliJ-tools.toml` mapping roots.
4. Split the current monolithic full cache session into narrower workspace-demanded domains.
5. Add raw NPC/ground-object/area overlays to Map Studio with explicit source provenance.
6. Add source-safe editors for raw spawn/area TOML and publish only through the project's build.
7. Define neutral cross-tool navigation targets and evidence-aware semantic facts.
8. Expand Mining into the first end-to-end skill-flow acceptance fixture.
9. Add semantic teleport extraction and a Map Studio teleport overlay.
10. Add lazy server-config overlay inspection for PackServerConfig-authored semantics.
11. Prototype a read-only, version-matched Studio Bridge instead of live JVM reflection/attach.
12. Add RSProx Session Inspector/Timeline and normalized Studio replay.
13. Add cutscene tooling on top of semantic/runtime/session events.
14. Revisit Kotlin Multiplatform boundaries before beginning a web client.

The architectural invariant is simple:

> Content Studio opens the project. Tools activate data. Tools share identities, generations and navigation. No tool owns a second project connection.
