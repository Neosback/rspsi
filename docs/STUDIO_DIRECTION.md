# OpenRune Studio Direction

This document records the long-term product direction discovered while
reviewing the source-first editor and cache-builder workflow shown in the
reference material. It is a design target, not permission to begin every
workspace at once.

RSPSi remains the current product name and the map-editor implementation. The
working name for the broader product is **OpenRune Studio**. The map editor is
the first workspace and the foundation gate remains the immediate priority.

## The central product decision

The cache is a build artifact, not the project's source format.

The authoritative flow is:

```text
immutable base OSRS cache
          +
Git-controlled OpenRune project sources
          ↓
      WorldDocument
          ↓
 commands / validation / preview
          ↓
 incremental project build
          ↓
 disposable output cache
```

This is a future source-project milestone. The current implementation still
uses the verified cache adapter, project metadata, autosave, and staged output
paths while the source format is designed and tested. We must not silently
convert the existing cache workflow into a half-built source compiler.

## Three authoritative layers

### 1. Base OSRS cache

The base cache is an immutable input identified by revision, subrevision,
fingerprint, and source location. It supplies upstream terrain, locations,
definitions, models, sprites, textures, and other assets. Painting must never
mutate it implicitly.

### 2. OpenRune project source

The project source is what a team reviews, commits, branches, and backs up.
It should eventually contain human-readable metadata and semantic world
changes rather than opaque `dat2`/index mutations. The open document remains
in memory as a `WorldDocument`; source writes occur at explicit save or
transaction boundaries.

### 3. Built cache

The built cache is disposable output for a client, server, fixture, or local
preview. It can be deleted and rebuilt from the same source tree and base
cache. It is never the only record of a change.

## Proposed project source shape

The exact file format is deliberately not frozen yet. The initial shape is a
compatibility target for design and tests:

```text
project/
├── openrune.toml                 # format, game, base-cache identity
├── gamevals/                     # symbolic names and optional mappings
├── configs/                      # future definition source overrides
│   ├── objects/
│   ├── overlays/
│   ├── underlays/
│   └── ...
├── maps/
│   └── <region-id>/
│       ├── terrain/              # future chunk-oriented source units
│       ├── locations.toml
│       └── patches.toml          # optional base-region edits
├── models/
├── sprites/
├── textures/
├── cs2/                          # later content workspace
├── studio/
│   ├── fragments/
│   ├── palettes/
│   ├── layers/
│   └── environments/
├── autosave/                     # local recovery, not authored source
├── edits/                        # transitional command/session records
└── build/                        # generated and disposable
```

The source format must be versioned, deterministic, and explicit about its
base cache. It must not require the editor to know archive IDs, opcodes, XTEA
keys, or backend-specific types.

## Two map authoring modes

The compiler should support both workflows and emit the same OSRS cache data:

| Mode | Meaning |
|---|---|
| Base-region patch | Start from an immutable OSRS region and store semantic tile/object changes only |
| Full-region source | Author a new or fully-owned region without depending on every base tile |

Eight-by-eight chunks remain the natural invalidation and future build unit.
They are also a good version-control boundary, but we will not force a new
on-disk chunk format until the canonical map model, round trips, and source
merge rules are proven. A semantic patch is more valuable than a pretty-printed
dump of every tile.

Example future source intent:

```toml
[tile."2604,3091,0"]
overlay = "floor.dark_stone"
shape = 3
rotation = 1

[[location.remove]]
x = 2605
y = 3092
plane = 0
type = 0

[[location.add]]
symbol = "loc.old_door"
x = 2605
y = 3092
plane = 0
type = 0
rotation = 2
```

Symbolic references are preferred where GameVal/RSCM can resolve them, while
numeric IDs remain available and are recorded for diagnostics. Automatic ID
migration is a later, validation-backed feature.

## Source/build services to design toward

These are contracts to introduce only after the foundation gate, not a second
world model:

- `BaseCacheResolver` — opens and identifies the immutable source cache;
- `SourceProjectLoader` / `SourceProjectWriter` — maps project files to and
  from the canonical document and project metadata;
- `WorldCompiler` — resolves base data plus source changes into a validated
  document and encoded output;
- `BuildReport` — reports changed regions/chunks, warnings, errors, and output
  identity;
- `SemanticDiff` — describes terrain, height, object, flag, and metadata
  changes without comparing opaque cache files;
- `RevisionAudit` — checks whether the source still resolves against a new
  OSRS revision.

The existing `CacheStore`, `MapService`, `WorldDocument`, `EditorSession`,
commands, and validators are the seams these services must use. They do not
create a parallel cache, map, or history implementation.

## Shared Studio shell, specialized workspaces

The broader product should feel like one application with a stable shell and
context-specific work areas:

```text
OpenRune Studio
├── World
│   ├── Map Editor                 # current product and first priority
│   ├── World Map                  # later overview/reference workspace
│   └── Collision / Navigation     # later diagnostic workspace
├── Assets
│   ├── Asset Browser               # first-party workflow plugin later
│   ├── Model / texture preview
│   └── Definition inspector
├── Content                        # later, not map-foundation scope
│   ├── Interfaces
│   ├── CS2
│   └── Cutscenes
└── Build
    ├── Validate
    ├── Diff
    ├── Pack
    └── Revision Audit
```

The shell is shared, but the data rules are not blurred. Each workspace
consumes neutral project, asset, scene, command, and validation contracts. A
future asset browser can be a plugin; it cannot become a second definition
cache. A future interface editor can use the same inspector and build report;
it cannot redefine the map editor's world model.

The controlled layout remains intentionally constrained:

- stable application shell and navigation toolbar;
- permanent centered work area;
- tool/navigation rail beside the work area;
- context inspector in a side rail;
- controlled bottom tabs for Assets, History, Changes, Validation, Build, and
  Console;
- floating search/command palette;
- persistent status row showing project, revision, dirty/build state, and
  world context;
- no unrestricted docking system in the foundation phase.

The current JavaFX implementation of this shell is tracked in
[`UI_UX_FOUNDATION.md`](UI_UX_FOUNDATION.md). It adds a Map Editor workspace
tab, context toolbar, outliner/inspector hosts, utility drawer, semantic
Ikonli icons, and user-scoped layout persistence while keeping future
Interface, Model, Cutscene, Asset, and Build workspaces on the same shell.

JavaFX renders these contracts first. Dear ImGui remains a future frontend
option because the core contracts contain no JavaFX, ImGui, LWJGL, or OpenGL
types.

## Workflow ideas worth adopting

The reference editor's strongest ideas are workflow ideas, not its 742-era
runtime or UI:

- semantic source-file changes instead of opaque cache diffs;
- right-side context inspector shared by every workspace;
- meaningful Changes, Validation, Build, and Debug panels;
- visible source-versus-built status;
- incremental rebuild of affected content;
- a transaction producing one command, one history entry, and one source
  change;
- before/after/ghost/diff views for review;
- deterministic builds suitable for CI;
- readable map-specific change summaries.

Later, these can support:

- visual Git/change review;
- chunk-aware merge assistance;
- construction layers such as Roads, Buildings, and Decorations;
- asset palettes/kits committed with the project;
- visual history checkpoints;
- CI reports with before/after/diff images and map validation;
- server/client cache packaging from a clean checkout.

None of those is required to complete the current map foundation.

## Explicit exclusions

The reference screenshots do not change product scope. The following remain
out of the foundation and are not being copied:

- 742 or other non-OSRS cache/runtime support;
- a general item/NPC/config editor before the map workflow is excellent;
- a server runtime, login, networking, or gameplay editor;
- a browser-style IDE or a full Git hosting client;
- unrestricted docking;
- live cache mutation on every brush event;
- public plugin distribution, Lua, CS2 execution, interfaces, or cutscenes;
- a renderer replacement or Dear ImGui migration.

## Decision rule for future work

Before implementing a new Studio feature, answer four questions:

1. Does it improve the OSRS map-editor foundation or unlock the source/build
   pipeline?
2. Does it use the existing canonical document, command, validation, asset,
   and workspace contracts?
3. Can it be represented as a focused plugin/workspace without owning a
   competing runtime or persistence model?
4. What executable fixture or deterministic report proves it works?

If the answer to any of these is no, the work belongs in research or a later
roadmap phase rather than the foundation.

The shared platform is intentionally broader than the current map editor.
OpenRune FileStore is the OSRS cache/definition layer; the OSRS bundle adapts
it into neutral project and asset contracts; workspaces own feature behavior
and UI contributions. This keeps a future interface, item, or model editor
from introducing a second cache loader, history model, or renderer scene graph.

OpenRune Server is connected as a project integration, not as a required
co-located dependency. The user selects the server root, Studio resolves and
saves path/command overrides, and a fingerprint reports Git, cache, and source
changes. Studio defaults to its own staged project/output state; applying
changes to the server checkout is an explicit later operation with backup,
diff, and validation.

The RuneLite rendering review is split into scene semantics, GPU packet
translation, and DevTools/overlay references. The implementation direction is
packet-first: `WorldRegionWindow`/`SceneWindow` provide context,
`RenderScene`/`EditorSceneSnapshot` provide immutable derived state, and
`TerrainRenderPacket`/`ModelRenderPacket` provide complete renderer inputs.
OpenGL 3.3 is the selected embedded desktop backend, but native GPU handles
remain outside neutral editor contracts. JavaFX hosts the surface, the
software renderer remains the deterministic reference, and Dear ImGui must
consume the same packets and diagnostic annotations when added.
