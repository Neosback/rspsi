# OpenRune Server Foundation Reference

This document records the verified OpenRune Server architecture that OpenRune Studio depends on.

Baseline inspected:

- upstream repository: `OpenRune/OpenRune-Server`;
- upstream branch: `main`;
- upstream commit: `6f7bd42d2cd613c01a351f25c227f6333c24cd62`;
- verified: 2026-09-28.

The purpose of this document is not to mirror every OpenRune implementation detail. It identifies the stable architectural seams Studio should integrate with, the areas that are intentionally derived/read-only, and the assumptions that still need verification before a live server agent is implemented.

## Architectural summary

OpenRune Server is a Gradle multi-project Kotlin/JVM application with five major systems that matter to Studio:

```text
Gradle project / source tree
        |
        +-- content modules + pack modules
        +-- GameVals / RSCM
        +-- or-cache build tooling
        |
        v
GameServer boot
        |
        +-- discover PluginModule classes
        +-- discover external plugin modules
        +-- build Guice injector
        +-- load configuration
        +-- initialize SERVER cache + LIVE JS5 provider
        +-- decode map / queue static spawns
        +-- start services
        +-- discover and start PluginScript classes
        +-- mark PluginScriptBootGate ready
        |
        v
Running server
        |
        +-- Guice-owned services
        +-- EventBus registrations
        +-- EngineQueueCache
        +-- CheatCommandMap
        +-- built-in plugin scripts
        +-- external plugin classloaders
```

Studio should treat these as distinct integration domains:

1. **project/source system**;
2. **cache/build system**;
3. **plugin/module system**;
4. **event/runtime system**;
5. **tooling system**.

StudioService owns project/source/cache/build knowledge. A future in-server Studio agent owns runtime knowledge.

---

## 1. Server boot sequence

The authoritative application entry point is `server/app/.../GameServer.kt`.

The verified boot sequence is:

```text
ensureProperInstallation()
        |
        v
loadModules()
        |
        +-- PluginModuleLoader.load(PluginModule)
        +-- ExternalPluginLoader.loadModulesAtBoot()
        |
        v
Guice.createInjector(
    GameServerModule +
    discovered plugin modules
)
        |
        v
loadConfig(injector)
        |
        v
ServerCacheManager.init(revision)
        |
        +-- GameValProvider.load()
        +-- open .data/cache/SERVER
        +-- load .data/cache/LIVE through CacheJs5GroupProvider
        +-- decode server-facing type tables
        |
        v
loadMap(cache, injector)
        |
        +-- decode NPC/OBJ map spawns
        +-- populate collision/static loc state
        +-- queue delayed entity spawns
        |
        v
startupGame()
        |
        +-- services start
        +-- plugin scripts load in parallel
        |
        v
loadScripts(injector)
        |
        +-- PluginScriptLoader.load(...)
        +-- ExternalPluginLoader.loadScriptsAtBoot(...)
        +-- ScriptContext.startup() for every script
        +-- flush queued map entities
        +-- PluginScriptBootGate.markReady()
        |
        v
GameService.setup()
        |
        +-- await PluginScriptBootGate
        +-- GameProcess.startup()
        |
        v
server ready
```

### Studio implication

A runtime agent must not assume that:

- Guice availability means scripts have started;
- caches being loaded means runtime handlers are registered;
- network/game services being started means content boot is complete.

The first reliable content-ready signal in the current architecture is the script boot gate becoming ready after plugin startup and delayed spawn flush.

A future agent should therefore expose lifecycle state explicitly rather than a single Boolean `connected`.

Suggested neutral phases:

```text
BOOTSTRAPPING
INJECTOR_READY
CACHE_READY
MAP_READY
SCRIPTS_LOADING
CONTENT_READY
RUNNING
SHUTTING_DOWN
```

Studio should not invent those states from timing. The agent must map actual server lifecycle evidence into them.

---

## 2. Guice and module system

OpenRune uses Guice for application composition.

The root injector is constructed from:

```text
GameServerModule
+
built-in PluginModule implementations
+
external plugin modules present at boot
```

`GameServerModule` installs the server's primary module groups. Plugin modules extend OpenRune's `PluginModule`, which itself extends Guice `AbstractModule`.

Plugin modules can contribute:

- singleton implementations;
- providers;
- base/interface implementations;
- Guice multibindings.

### Built-in modules

Built-in plugin modules are discovered with `PluginClasspathScan` and `PluginModuleLoader`.

`PluginClasspathScan` uses ClassGraph and scans:

- `org.rsmod.api`;
- `org.rsmod.content`;
- selected resource roots.

The scan is cached and shared between discovery consumers to avoid repeated full classpath scans.

### External modules

External plugin sources may also declare `PluginModule` classes.

At **boot**, those modules are added to the same root injector as built-in modules.

For a plugin **hot-loaded after boot**, the main injector already exists. OpenRune creates a child injector for that source when the source declares modules.

Important limitation:

> A hot-loaded plugin module can affect that source's own script instances through its child injector, but it cannot retroactively introduce bindings into the already-created root injector.

### Studio implication

The Studio agent should prefer using existing root services over introducing deep server-wide bindings at hot-load time.

If Studio later requires a binding that must participate in the root injector, that integration needs either:

- a boot-time agent module; or
- an upstream-supported extension point.

Do not assume a hot-loaded external module can mutate root dependency composition.

---

## 3. Plugin discovery and script lifecycle

OpenRune has two related plugin concepts:

- `PluginModule`: dependency-injection contributions;
- `PluginScript`: runtime content registration/startup behavior.

A `PluginScript` implements:

```kotlin
fun ScriptContext.startup()
```

and may optionally implement:

```kotlin
fun ScriptContext.shutdown()
```

### Built-in scripts

Built-in scripts are discovered through the shared ClassGraph scan, instantiated through Guice, then started with a shared `ScriptContext`.

The loader excludes abstract classes and interfaces.

### External plugins

`ExternalPluginLoader` supports plugin sources from the configured plugins directory.

A source may be:

- a JAR; or
- a directory of compiled class files.

Every source requires a root `plugin.properties` containing:

- `name`;
- `description`;
- `revision`;
- `author`.

External plugin enabled state is persisted in a properties file.

### External classloaders

Each external plugin source receives a dedicated `URLClassLoader`.

At boot, a source's module and script passes intentionally reuse the same classloader so shared plugin types retain class identity.

OpenRune tracks:

- loaded source paths;
- source classloaders;
- loaded script instances;
- enabled/disabled source state.

After boot, OpenRune may close external URL classloaders to release JAR file locks. Already loaded classes retain identity, but lazy loading of previously untouched classes can become problematic.

### Reload semantics

Reloading an already-loaded source performs:

1. `PluginScript.shutdown()` for tracked scripts;
2. removal of EventBus handlers belonging to the source classloader;
3. removal of cheat commands belonging to the source classloader;
4. removal of engine-queue registrations associated with the classloader;
5. closing the old classloader;
6. creation of a fresh classloader;
7. source re-scan;
8. fresh script instantiation;
9. `startup()` again.

### Reload is not transactional

OpenRune explicitly does **not** promise full rollback of arbitrary plugin side effects.

Examples that are not automatically undone:

- spawned entities;
- mutated shared state;
- arbitrary coroutines;
- state stored outside tracked registries.

A plugin that requires clean reload behavior must implement its own shutdown cleanup.

### Studio implication

A future Studio plugin manager can expose:

```text
source identity
manifest
enabled
loaded
classloader identity
script classes
module classes
reload generation
shutdown support
runtime registration counts
```

But Studio must never label reload as a complete state rollback.

---

## 4. Event system

OpenRune's event core is intentionally small.

There are three event categories:

### Unbound events

```kotlin
interface UnboundEvent
```

A single event type can have multiple subscribers.

Storage:

```text
Class<Event> -> List<handler>
```

### Keyed events

```kotlin
interface KeyedEvent {
    val id: Long
}
```

Storage:

```text
Class<Event> -> key -> handler
```

Only one keyed handler may occupy the same type/key combination through the normal subscription path.

### Suspend events

```kotlin
interface SuspendEvent<R> {
    val id: Long
}
```

Storage is likewise keyed by event class and long ID, with a suspend receiver/action.

### EventBus

The public `EventBus` exposes:

- publish operations;
- subscription operations;
- keyed containment checks;
- classloader-based removal.

The underlying maps are:

- `UnboundEventMap`;
- `KeyedEventMap`;
- `SuspendEventMap`.

The map storage itself is intentionally not a broad public introspection API. Some backing collections are internal/private implementation details.

### External plugin unload integration

Event handlers are removed by comparing the handler lambda/method-reference classloader with the unloading plugin's classloader.

That classloader identity is therefore part of OpenRune's current runtime ownership model.

### Studio implication

A live agent should initially use public EventBus behavior and explicit OpenRune accessors.

If the agent needs a complete registration inventory, preferred order is:

1. supported public API;
2. small upstream accessor contributed to OpenRune;
3. narrowly scoped reflection;
4. bytecode instrumentation only when observation cannot otherwise be achieved safely.

Do not make private EventMap field layouts part of the Studio protocol.

---

## 5. ScriptContext and adjacent runtime registries

Current `ScriptContext` provides scripts with:

- `EventBus`;
- `CheatCommandMap`;
- `EngineQueueCache`.

These are important because external unload already treats them as tracked runtime registrations.

### CheatCommandMap

Commands are mutable runtime registrations and retain classloader ownership metadata through their handlers.

### EngineQueueCache

The queue cache tracks default and labelled script availability. It also records classloader ownership for external plugin cleanup.

### Studio implication

These registries are better runtime evidence than static source analysis.

A future runtime snapshot can report:

```text
plugin script loaded
event registrations active
cheat commands active
engine queue bindings active
```

StudioService can then compare those facts against source-index expectations.

---

## 6. Cache architecture

OpenRune distinguishes at least two generated cache roles that Studio must keep separate:

### LIVE cache

Path:

```text
.data/cache/LIVE
```

This is the client/live cache build output.

It is also used by `CacheJs5GroupProvider` while the server cache manager initializes.

### SERVER cache

Path:

```text
.data/cache/SERVER
```

This is a server-oriented generated cache that is intentionally stripped/packed differently from the live cache.

`ServerCacheManager` opens SERVER and decodes server-facing data including:

- NPCs;
- objects/locs;
- items;
- inventories;
- sequences;
- varbits/varps;
- structs;
- DB rows/tables;
- interfaces;
- stats;
- projectile types;
- hit splats;
- BAS;
- walk triggers;
- server var types;
- params;
- hunt modes;
- additional server-oriented definitions.

Published lookup tables are exposed as read-only views after decoder population.

### ServerCacheManager initialization

Verified high-level order:

```text
GameValProvider.load()
        |
        v
open SERVER cache
        |
        +-- load LIVE through CacheJs5GroupProvider
        +-- decode fonts
        +-- decode server definition tables
        +-- adopt read-only views
        +-- derive transmit varps
        +-- load DB master-row indexes
```

### Studio implication

StudioService may inspect LIVE or SERVER through FileStore, but it should not pretend they are interchangeable.

For browser/editor workflows:

- LIVE is generally the client/render/export authority;
- SERVER contains server-oriented packed definitions and generated server data;
- source files and pack definitions remain the publication authority where available.

Generated caches should not be silently edited as substitutes for source publication.

---

## 7. Cache build and pack system

The `or-cache` module is the OpenRune cache-build application.

Important Gradle tasks include:

```text
:or-cache:buildCache
:or-cache:freshCache
:or-cache:cleanCs2
:or-cache:mergePluginGamevals
```

### Content pack isolation

A particularly important architectural rule is visible in `or-cache/build.gradle.kts`:

> only dedicated content `pack` submodules are placed on the cache-build classpath.

This prevents the cache builder from needing the entire game-script dependency graph.

Pack modules can contribute things such as:

- configuration sources;
- models;
- sprites;
- DB tables;
- cache tasks;
- CS2 scripts/symbols.

### PluginPacks

`PluginPacks` discovers pack implementations with ClassGraph.

Pack data is composed into the cache build.

CS2 overrides can combine:

- generated GameVal symbols;
- pack script sources;
- pack symbol files.

### Cache build flow

The verified build path is approximately:

```text
GameValProvider.load()
        |
        v
PluginPacks.discover()
        |
        v
packs.validate()
        |
        +-- build CS2 overrides
        +-- assemble pack tasks
        |
        v
build LIVE cache
        |
        v
build SERVER cache
        |
        v
dump generated server metadata/GameVals
        |
        v
generate server table/enum code
```

The cache tool uses incremental state and output verification.

### Fresh install

`freshCache` is materially more destructive/bootstrap-oriented than an incremental build.

Studio must never run it automatically when a project is opened.

---

## 8. GameVals and RSCM

GameVals are a core identity layer in OpenRune.

Examples:

```text
content.rock
loc.some_location
npc.king_dragon
dbtable.mining_rocks
stat.mining
```

OpenRune supports multiple RSCM namespaces and validates prefixes.

The runtime `RSCM` helper resolves symbolic names through the loaded constant provider and caches resolutions.

### Plugin-local GameVals

Content modules may contain:

```text
src/main/resources/gamevals.toml
```

`PluginGamevalMerger` merges those declarations into:

```text
.data/gamevals/<namespace>.rscm
```

The merger:

- validates namespaces against known RSCM types;
- preserves existing generated keys;
- appends new valid integer mappings.

### Studio implication

Studio should retain provenance:

```text
symbol
namespace
numeric id
authoritative module TOML
generated RSCM path
runtime resolution state
```

Generated `.rscm` files are derived outputs when an authoritative plugin TOML entry exists.

Studio's current content index correctly treats TOML/RSCM as source/derived inputs rather than replacing them with a parallel proprietary source format.

---

## 9. Map and spawn loading

During server boot, OpenRune decodes the map after cache initialization and before plugin-script startup completes.

NPC and ground-object spawns are queued through delayed repositories.

The queued map entities are flushed only after script startup so content handlers exist before those entities become visible to players.

### Studio implication

The eventual runtime agent should distinguish:

- cache/map definitions;
- decoded static map state;
- queued spawn state;
- live repository/entity state.

Static project/cache inspection is not the same thing as the running server world state.

---

## 10. Services and game thread

The server has an explicit service lifecycle.

`GameBootstrap` starts configured services and owns shutdown coordination.

`GameService` runs the game process on a dedicated single-thread executor named `game` and targets a 600 ms game tick.

`GameService.setup()` waits for `PluginScriptBootGate` before starting the game process.

### Studio implication

Any future agent operation that reads or mutates live game state must define its threading model.

StudioService's HTTP request thread must never be assumed safe for direct mutation of game-thread-owned state.

Initial live-agent capabilities should therefore be read-only and snapshot-oriented until a supported game-thread scheduling mechanism is explicitly identified and tested.

---

## 11. OpenRune tooling

OpenRune includes tooling beyond the game server.

One especially relevant example is `:tools:osrs-mcp`, a local stdio MCP server that already exposes:

- wiki search/page access;
- GameVal search/reload;
- decoded LIVE/SERVER cache search/reload.

It is explicitly a local developer tool and the game server does not depend on it.

### Studio implication

This confirms a useful OpenRune design pattern:

> developer tooling may be a separate local process consuming the same project/cache outputs without becoming part of the game server.

That aligns directly with the StudioService architecture.

We should reuse concepts and source authorities from OpenRune tooling, but Studio does not need to wrap MCP internally. StudioService's versioned HTTP/WebSocket protocol serves a different purpose.

---

## 12. Studio integration model

The target architecture should remain:

```text
Browser Studio
      |
      | versioned HTTP / WebSocket
      v
Kotlin StudioService
      |
      +-- project inspection
      +-- FileStore
      +-- GameVals/RSCM
      +-- Kotlin PSI/source graph
      +-- Gradle/cache builds
      +-- filesystem watching
      |
      | future local runtime protocol
      v
OpenRune Studio Agent
      |
      +-- lifecycle state
      +-- loaded plugin/script identity
      +-- active runtime registrations
      +-- selected public Guice/runtime services
      +-- runtime diagnostics/tracing
      v
OpenRune Server JVM
```

### StudioService responsibilities

StudioService remains authoritative for:

- project/source facts;
- static source graph;
- generated output provenance;
- cache/build tooling;
- source editing/publication;
- filesystem state.

### Agent responsibilities

The Agent should be authoritative only for facts that exist because a server is currently running:

- server lifecycle;
- actual loaded scripts;
- external plugin status/classloaders;
- active runtime registrations;
- runtime cache/service state;
- runtime traces;
- reload results.

The agent should not become a second source indexer or cache builder.

---

## 13. Recommended first live-agent shape

Do not begin with bytecode instrumentation.

A minimal agent should first prove a stable neutral contract using supported OpenRune mechanisms.

Suggested first capabilities:

```text
runtime.identity
runtime.lifecycle
runtime.plugins
runtime.scripts
runtime.capabilities
```

Potential first snapshot:

```json
{
  "lifecycle": "CONTENT_READY",
  "jvm": "...",
  "serverRevision": 240,
  "scripts": [
    {
      "className": "org.rsmod.content.skills.mining.scripts.Mining",
      "source": "built-in"
    }
  ],
  "externalPlugins": [
    {
      "id": "example",
      "enabled": true,
      "loaded": true
    }
  ]
}
```

The exact transport should be local and authenticated/scoped before mutation capabilities are introduced.

---

## 14. Reflection policy

Reflection is useful, but it should be a fallback adapter technique.

Use order:

1. public OpenRune API;
2. dependency-injected service;
3. upstream-friendly accessor added to OpenRune;
4. narrow reflection against a version-checked field/method;
5. bytecode instrumentation when observation cannot be achieved safely otherwise.

Every reflection adapter should have:

- an explicit OpenRune compatibility check;
- graceful capability degradation;
- a focused test;
- no reflective implementation detail leaking into browser DTOs.

Do not build generic "dump every field" endpoints.

---

## 15. Byte Buddy assessment

Byte Buddy can be valuable later, especially for a live content debugger, but it is not required for the core Studio foundation.

OpenRune Server currently does not use Byte Buddy.

### Where Byte Buddy could provide unique value

Potential future uses include:

- method entry/exit tracing without modifying upstream source;
- tracing `EventBus.publish` dispatch;
- tracing selected plugin-script methods;
- recording handler execution latency;
- correlating a runtime interaction with the actual implementation method reached;
- instrumenting dynamically loaded external plugin classes;
- attaching diagnostic metadata to runtime flows.

This could eventually support a Studio trace such as:

```text
Loc interaction
  -> EventBus publish
  -> registered content.rock handler
  -> Mining.attempt
  -> Mining.mine
  -> XP/product result
```

### Why Byte Buddy should not be foundational

Bytecode instrumentation adds substantial complexity:

- Java-agent or attach lifecycle;
- retransformation support;
- classloader-specific instrumentation;
- external plugin reload interaction;
- Kotlin-generated lambda/suspend state-machine classes;
- performance overhead;
- instrumentation ordering;
- JDK attach/security restrictions;
- compatibility testing across OpenRune/JDK versions.

A class may also be loaded before instrumentation is installed, requiring retransformation or earlier agent startup.

### Recommended phase

Byte Buddy belongs in an optional **runtime tracing/instrumentation phase** after:

1. public/runtime registry inspection works;
2. Studio has stable runtime DTOs;
3. plugin reload ownership is understood;
4. thread-safety boundaries are documented;
5. we have an explicit trace feature that requires instrumentation.

Do not introduce Byte Buddy merely to enumerate plugins, bindings, or EventBus state.

---

## 16. Known unknowns before a live agent

We now understand the architectural foundation, but these points still require deliberate verification before promising them as Studio capabilities.

### Runtime registration enumeration

We know how handlers are stored and removed, but there is no verified public API for enumerating every event registration with content-friendly metadata.

Need to decide between:

- upstream accessor;
- reflection adapter;
- instrumentation.

### Handler key semantics

OpenRune has many event classes and helper DSLs. We still need a verified mapping from specific handler DSLs such as `onOpContentLoc1` to:

- concrete event class;
- key packing;
- content/GameVal interpretation.

Static PSI currently preserves the registration call and string arguments, which is safe. Runtime parity requires exact event-key understanding.

### Main injector visibility

The agent can receive bound services when instantiated through Guice, but direct access to root injector internals should not be assumed until explicitly tested in OpenRune's composition.

### Game-thread scheduling

Read-only service snapshots may be safe for some immutable/read-only structures. Live world mutations require a verified scheduling/queue mechanism onto the game thread.

### External plugin reload + agent instrumentation

If the agent later instruments external plugin classes, retransformation and classloader disposal/reload behavior must be tested explicitly.

### Runtime cache rebuild/reload

The server's normal boot cache manager is clear. Safe live cache replacement/reinitialization semantics are not yet a Studio capability and should not be inferred from build tooling.

### Service lifecycle hooks

Before the agent runs its own listener/socket inside the server JVM, identify the correct service lifecycle integration so startup and shutdown are deterministic.

---

## 17. Core foundation priorities for Studio

Before building a broad source browser or runtime debugger, Studio should establish these foundations in order.

### A. Neutral protocol/core contracts

Define stable DTOs for:

- project capabilities;
- content symbols;
- source facts;
- runtime identity;
- runtime lifecycle;
- plugin/script identity;
- diagnostics.

### B. Static project graph

Already underway:

- project discovery;
- FileStore inspection;
- GameVal/RSCM indexing;
- Kotlin PSI structural indexing;
- content symbol -> source resolution.

### C. Runtime plugin/event foundation

Next runtime milestone:

- minimal OpenRune agent;
- lifecycle snapshot;
- loaded plugin/script inventory;
- capability discovery;
- no mutation;
- no Byte Buddy.

### D. Cache/build foundation

Then complete:

- explicit Gradle task discovery;
- `buildCache` operation lifecycle;
- output fingerprints;
- generated output verification;
- source authority/provenance;
- no direct generated-cache fallback publication.

### E. Event/content runtime graph

After runtime identity is stable:

- runtime handler inventory;
- static-vs-runtime parity;
- selected-symbol runtime status;
- plugin reload diagnostics.

### F. Optional instrumentation

Only after the above:

- Byte Buddy/runtime traces;
- timing/profiling;
- method-level flow capture.

---

## 18. Non-goals

The JVM foundation does not own unrelated editor/rendering systems or arbitrary desktop UI state.

Static project inspection must remain useful without a running server.

The Agent should stay narrow: runtime identity, lifecycle, plugins/scripts, supported event registration facts, cache/runtime state, and diagnostics. It must not become a second server framework or an unrestricted remote-control surface.

---

## 19. Confidence statement

At this baseline we have a **verified architectural understanding** of:

- server boot ordering;
- Guice module composition;
- built-in plugin discovery;
- external plugin loading/reloading;
- classloader ownership;
- script lifecycle;
- EventBus categories/storage model;
- external registration cleanup;
- cache roles;
- ServerCacheManager initialization;
- cache build tasks;
- plugin pack isolation;
- GameVal/RSCM merge/resolution;
- map load timing;
- game service boot gating;
- local MCP tooling role.

We do **not** yet claim complete runtime introspection knowledge.

The open questions in section 16 must be resolved as focused research/implementation slices rather than hidden behind generic reflection.
