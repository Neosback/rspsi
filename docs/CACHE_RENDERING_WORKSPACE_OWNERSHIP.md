# Cache, OpenRune, Rendering, and Workspace Ownership

> **Status:** authoritative implementation ownership map.
>
> Read this together with `EDITOR_DEVELOPMENT_ARCHITECTURE.md`,
> `OPENRUNE_MAVEN_CATALOG.md`, `OPENRUNE_ECOSYSTEM_INTEGRATION.md`,
> and `UI_WORKSPACE_CONTRACT.md`.

## 1. Modern OSRS cache rule

**OpenRune FileStore is the single production cache backend for modern OSRS.**

Modern OSRS code must not choose between FileStore and Displee.

| Concern | Canonical owner |
| --- | --- |
| cache filesystem/archive access | OpenRune `filesystem` / `filestore` / `osrs-fs` |
| OSRS definition codecs/types | OpenRune `definition` / `osrs` |
| Studio cache adapter | `OpenRuneCacheStore` |
| writable standalone output cache | FileStore `CacheDelegate` via `OpenRuneCacheStore.openWritable` |
| reference-cache acquisition | FileStore `FreshCache` / tools |
| GameVals / RSCM mappings | OpenRune mappings through Studio neutral symbol adapters |
| connected OpenRune Server cache/build ownership | `OpenRuneServerAdapter` + core OpenRune integration |
| legacy/custom non-modern cache compatibility | `LegacyDispleeCacheStore` only |

`CacheStoreFactory.legacy(...)` is an explicitly legacy API. It must never appear in
modern OSRS project, map, server, or workspace code.

## 2. Why Displee still exists

The external Displee dependency remains for old RSPSi/Jagex/custom-cache compatibility.

It is **not**:

- an OSRS output fallback;
- an alternate OSRS decoder;
- an alternate project backend;
- an accepted way to work around a missing FileStore feature.

If modern OSRS needs a capability FileStore does not provide, add one documented exception at the
adapter boundary and a test proving why it exists. Do not silently route the feature through
Displee.

The build separates the Displee and OpenRune import allowlists and fails if either backend escapes
its intended boundary.

## 3. Studio-owned codec exceptions

Single-backend ownership does not mean every byte-to-editor transformation belongs upstream.

### Region terrain/location codec

`OsrsRegionDecoder` and `OsrsRegionEncoder` are the canonical Studio-owned map wire codec.

FileStore supplies and stores the map archive bytes. Studio owns the transformation between those
bytes and the mutable authoring model because the editor needs:

- `WorldDocument` / `WorldRegion` semantics;
- explicit four-corner shared-height behavior;
- revision-aware terrain opcode handling;
- exact location ownership;
- undoable mutations;
- lossless save/reopen validation;
- fail-fast checks for map states the wire format cannot represent.

Do not add a second region decoder/encoder. If FileStore later publishes an editor-suitable,
roundtrip-tested map semantic codec, replace this pair deliberately behind tests.

### Modern texture records

`OpenRuneTextureDefinitionDecoder` is a narrow adapter exception.

The audited OpenRune generic definition path currently skips the compact post-233 texture records
used by the revision-240 cache. Studio decodes only that missing record shape at the OpenRune
adapter boundary and still returns OpenRune/Studio semantic texture data.

This exception should be removed when the pinned FileStore version decodes those records correctly
and the real-cache texture parity tests pass without it.

### Revision detection

`OpenRuneCacheStore.revisionFromVersionData` mirrors FileStore's version.dat helper because the
upstream Kotlin helper is not importable from this named Java package. It is metadata parsing, not
a second cache backend.

## 4. OpenRune Server is built-in integration

OpenRune Server support is core product functionality.

Canonical implementation:

```
ServerIntegrationService
        |
        v
OpenRuneServerProvider
        |
        v
OpenRuneServerAdapter
        |
        +-- project detection / paths
        +-- LIVE + SERVER cache roles
        +-- build tasks
        +-- content/source inventory
        +-- fingerprints
        +-- runtime plugin inventory
        |
        +-- OpenRune symbols
        +-- references
        +-- NPC spawns
        +-- object semantic overlays
        +-- Kotlin semantic source index
```

The implementation lives under `com.rspsi.server.openrune`.

It is **not** an `EditorPlugin`, does not participate in Plugin Manager enable/disable/reload,
and must not return to a `com.rspsi.plugins...` namespace.

The word `ServerIntegrationProvider` describes a typed provider behind the core integration
service. It does not mean installable Studio plugin.

## 5. Workspace ownership

The editor shell has one job per surface.

### Right rail

Right-side panels are inspection, exact-property editing, map/render settings, world intelligence,
simulation state, and future theme/context intelligence.

Current right-owned panels include:

- Tile Inspector;
- WorldMap;
- Object Viewer / Properties;
- Outliner;
- World Knowledge;
- Player State;
- Map Settings.

A right-side inspector may edit the selected semantic object/data. A future action may send that
inspected/edited asset into the active bottom authoring drawer for placement. The right panel does
not itself become a second placement tool rail.

### Left brush rail

The left rail is the contextual shared-brush rail.

It appears automatically only when the active tool declares shared brush settings, unless the user
explicitly forces it visible. A tool with tool-owned brush UI keeps the shared rail hidden.

It is not a general tool switcher.

### Bottom tool rail + Context Drawer

The bottom rail activates authoring operations. The one Context Drawer above it belongs to the
active authoring tool.

Examples:

- Tile Painter;
- Height Sculptor;
- Path / road / shoreline generators;
- Object placement/spawn authoring;
- future building/fragment/biome/generation tools.

History, notifications, renderer diagnostics, and generic utility panels do not own bottom-drawer
modes.

### Floating rail / quick palette

The floating rail is for viewport pickers/selection modes and compact contextual choices. It must
not duplicate the entire bottom tool catalog.

## 6. Renderer settings ownership

Renderer settings follow this chain:

```
UI control / shortcut / persisted preference
        |
        v
typed SettingKey in RenderSettingKeys
        |
        v
SettingsStore
        |
        v
RenderConfigCompiler
        |
        v
immutable RenderConfig
        |
        +-- semantic packet filtering / visibility
        +-- presentation
        +-- MSAA
        +-- native culling
        +-- viewport plane targeting
        |
        v
native renderer
```

Rules:

1. do not create renderer booleans/ints beside `RenderSettingKeys`;
2. a live UI renderer setting must use a registered typed key;
3. every registered setting must have a declared consumer;
4. renderer-facing code consumes `RenderConfig`, not arbitrary settings snapshots, whenever the
   value belongs to the compiled renderer contract;
5. unavailable settings are shown disabled with a reason, never as clickable controls that do
   nothing;
6. transient camera/player/roof state is not persisted as a renderer preference;
7. adding a renderer setting requires a compiler/test destination in the same change.

## 7. AI / contributor checklist

Before adding cache, renderer, server-integration, or workspace code:

1. search this ownership map;
2. search the canonical class named above;
3. extend that path instead of introducing another manager/provider/decoder/settings object;
4. if the canonical dependency truly lacks the capability, document the exception beside the
   adapter and add a regression test;
5. never fix a boundary problem by creating a second production path.
