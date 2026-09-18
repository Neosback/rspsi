# OpenRune Server Integration

## Purpose

OpenRune Studio integrates with an OpenRune Server project through a
first-party `OpenRuneServerAdapter`. The adapter understands a server
checkout, its cache roles, content sources, GameVals, pack modules, external
plugin metadata, and build tasks. It does not load server classes and does not
make the server a runtime dependency of cache-only Studio use.

```text
Server checkout
  -> OpenRuneServerAdapter inspection
  -> FileStore/OSRS cache adapter
  -> neutral Studio assets and scene
  -> EditorSession and feature plugins
  -> staged output or explicit server build
```

The integration is a Studio service/adapter, not an OpenRune `PluginScript`.
OpenRune `PluginScript` and `PluginPack` remain server-side concepts.

## OpenRune Server structure

The inspected revision-240.2 checkout is a modular Kotlin/Gradle server:

| Area | Role in Studio integration |
|---|---|
| `engine/` | Game loop, events, routing, map structures, and plugin framework |
| `api/` | Server APIs for cache, entities, scripts, interfaces, DB, and routing |
| `content/` | Built-in gameplay modules |
| `content/**/pack` | Cache resources and `PluginPack` declarations |
| `or-cache/` | Cache builder, map codecs, GameVals, CS2, interfaces, DB tables, and packing |
| `.data/raw-cache/` | Source cache/configuration inputs |
| `.data/cache/LIVE/` | Client-facing built cache |
| `.data/cache/SERVER/` | Server-oriented built cache |
| `.data/gamevals/` | Text GameVal/RSCM mappings |
| `.data/gamevals-binary/` | Generated binary GameVal/component mappings |
| `plugins/` | External runtime plugin JARs or compiled class directories |
| `game.yml` | Server configuration, including revision and project name |
| `gradlew` / `gradlew.bat` | Platform-specific build entry points |

The server has two plugin paths that must not be confused:

1. Built-in classpath plugins and `PluginPack` modules are compiled into the
   server/cache build. `or-cache` discovers pack classes from its classpath.
2. External runtime plugins are discovered from `plugins/`, require a
   `plugin.properties` manifest, and are loaded into the running server.

Studio inventories both categories for provenance and diagnostics. It never
executes arbitrary server plugin JARs or imports their Guice/server classes.

## Connection model

Users select a server root through the Studio project wizard. Studio does not
search the entire desktop by default. A connection stores the root and uses
relative paths where possible, so the server may live anywhere on disk.

The neutral connection model is represented by `ServerConnection` and can be
stored using the supported TOML subset in `ServerConnectionToml`:

```toml
[server]
adapter = "openrune-server"
root = "/path/to/server"

[paths]
live_cache = ".data/cache/LIVE"
server_cache = ".data/cache/SERVER"
raw_cache = ".data/raw-cache"
gamevals = ".data/gamevals"
gamevals_binary = ".data/gamevals-binary"
content = "content"
packs = "content"
runtime_plugins = "plugins"
cs2 = "cs2"

[commands]
build-cache = "./gradlew :or-cache:buildCache"
fresh-cache = "./gradlew :or-cache:freshCache"
merge-plugin-gamevals = "./gradlew :or-cache:mergePluginGamevals"
run-server = "./gradlew run"
```

Relative path values resolve from the selected root. Absolute values are
allowed for caches or tools stored elsewhere. When no override is present,
the adapter checks the stock layout plus common lowercase and `data/` variants.

## Inspection and fork resilience

`OpenRuneServerAdapter.inspect` produces an immutable
`ServerProjectInspection`. Inspection is read-only and does not open a cache,
run Gradle, or execute plugin code.

The inspection contains:

- detection evidence and confidence;
- resolved paths and path overrides;
- revision/subrevision text when present;
- LIVE and SERVER cache availability;
- built-in pack/resource inventory;
- GameVals, map, config, model, sprite, texture, interface, CS2, DB, and
  server-script entries;
- external plugin metadata only;
- Git commit, branch, and dirty state when Git is available;
- declared build tasks and output paths;
- SHA-256 project/cache fingerprint;
- diagnostics and supported capabilities.

The status is one of:

```text
SUPPORTED
SUPPORTED_WITH_OVERRIDES
PARTIAL
STALE
INCOMPATIBLE
NOT_DETECTED
```

The adapter is not commit-locked. A fork can continue to work when it moves
its caches or renames paths by supplying overrides. Studio re-inspects the
connection and compares fingerprints after a reconnect or before a build.
Changed project files, cache metadata, Git state, or content are reported as
stale; Studio preserves its edits and disables only affected capabilities.

An explicitly detected non-240 revision is incompatible with the current
first-party OSRS bundle. It must not fall back to a 317 loader.

## Cache and content boundary

The adapter describes cache locations; it does not implement another cache
reader. Both LIVE and SERVER caches are opened through the existing
FileStore-backed `CacheStore` and OSRS bundle:

```text
OpenRuneServerAdapter
  -> selected LIVE/SERVER path
  -> CacheStoreFactory.openOsrs
  -> OsrsBundle
  -> AssetRepository / WorldDocument / EditorSession
```

FileStore remains responsible for DAT2 files, archive identity, definitions,
models, sprites, textures, and raw bytes. RSPSi remains responsible for world
semantics, scene derivation, collision policy, editing, history, validation,
and workspace/plugin lifecycle.

The adapter exposes provenance without leaking backend types. For example:

```text
Object 63477
Source: content/events/shooting-stars/pack
Type: custom pack resource
Cache: LIVE
GameVal: obj.poh_tablet_shootingstar
```

Studio's default source cache remains read-only. Edits belong to the Studio
project/session and are later emitted as semantic patches or explicit output
cache changes. Opening a server connection never modifies the server checkout.

## Build integration

OpenRune build actions are declarative `ServerBuildTask` values. Studio uses
`ServerBuildRunner` to execute an adapter-declared argument list from the
selected server root. It does not embed Gradle or concatenate shell commands.

The runner provides:

- `gradlew`/`gradlew.bat` selection;
- streamed combined output;
- exit status and duration;
- cancellation through process termination on timeout;
- bounded timeouts;
- explicit output paths for reinspection.

The current adapter exposes these conventional tasks when a wrapper or command
override is available:

- `:or-cache:buildCache`;
- `:or-cache:freshCache`;
- `:or-cache:cleanCs2`;
- `:or-cache:mergePluginGamevals`;
- `run` when the server application is detected.

Task availability is capability-based. A fork can provide only the actions it
supports. The build runner does not run any task while merely opening a
project.

## Staging and applying changes

The implemented integration is currently inspection/build-task focused. The
write policy is already fixed for the next phase:

1. Studio records edits in its own semantic project/session.
2. `Build Preview` writes to a separate output location.
3. The output is reopened through FileStore and inspected.
4. `Apply to Server Project` is an explicit action with a target, backup, diff,
   and validation report.

Studio will not write directly into `.data/raw-cache` until the semantic source
format and map/config round trips are proven. Opaque cache mutation is an
output operation, never the authoritative project source.

## Deferred runtime bridge

Live server integration is intentionally separate from project/build
integration. A future optional OpenRune bridge plugin may expose a local,
authenticated capability protocol for teleport, region reload, collision
reload, entity inspection, route tests, interface opening, and development-only
client-script execution.

The bridge will communicate through a stable Studio protocol. Studio will not
load OpenRune Guice modules, server classloaders, `PluginScript` classes, or
internal repositories. Forks may implement only a subset of bridge
capabilities.

## Current implementation status

Completed:

- neutral server connection and path-key models;
- TOML read/write for connection overrides;
- OpenRune detection with explainable evidence;
- stock and alternate path discovery;
- revision-240 compatibility status;
- project/cache/content fingerprints;
- Git state inspection;
- pack/resource inventory;
- external plugin manifest inventory without execution;
- capability-based build task descriptions;
- bounded external build runner;
- fixture tests for arbitrary roots, moved paths, stale connections, revision
  rejection, inventory, and connection persistence.

Deferred:

- source-file apply/export;
- cache output coordination from server tasks;
- structured Gradle progress parsing;
- server process lifecycle UI;
- runtime bridge;
- execution or loading of external Studio/server plugins.
