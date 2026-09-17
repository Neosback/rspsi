# OpenRune Studio ideas and possibilities

This is the parking lot for valuable product capabilities that should not
distort the current map/editor foundation. An item here is a direction to
evaluate, not approval to add a second world model, cache system, renderer, or
server runtime dependency.

## Cache provenance and capability panel

Add a small project/status surface that reports the active cache source as
`OpenRune FileStore (OSRS)`, the validated revision/subrevision and fingerprint,
and the output capability (`READ_ONLY`, `STAGED`, or explicit `DIRECT`). This
would answer the user's “which loader is active?” question without creating a
`FileStorePlugin` or exposing a 317 decoder as an interchangeable modern
choice. It should be a shell/status contribution over `OsrsBundle` and
`CacheStoreCapabilities`, not a second cache-selection architecture.

Possible later extensions are cache identity comparison, source/output path
clarity, and warnings when a project opens read-only because its base cache
does not match. This remains a UX enhancement; the current foundation already
has the underlying capability data.

## Server integration through adapters

The strongest long-term idea is a capability-based server integration layer.
Studio edits OSRS content; a server adapter teaches it how a particular server
stores, builds, launches, reloads, and consumes that content. Studio remains
fully useful with cache-only mode and does not assume an OpenRune project is
laid out beside the editor.

### Progressive integration levels

| Level | Capability | Expected support |
|---|---|---|
| 0 | Cache-only editing, browsing, validation, import/export | Any OSRS cache |
| 1 | Project layout, cache paths, content roots, GameVal roots | Most servers |
| 2 | Build, pack, process, and structured build output | Servers with known build commands |
| 3 | Local runtime bridge for reloads, teleport, inspection, and tests | Servers with an installed Studio bridge |

Each level is optional. A fork can provide filesystem/build integration while
omitting runtime integration.

### Proposed neutral API

Keep the core small and capability-oriented:

```text
ServerAdapter
├── detect(root) -> DetectionResult
├── project(root) -> ServerProject
├── buildProvider() -> optional ServerBuildProvider
└── runtimeProvider() -> optional RuntimeConnectionProvider

ServerProject
├── root
├── cacheLocations()
├── revision()
├── contentRoots()
└── gameValRoots()

RuntimeConnection
├── capabilities: Set<RuntimeCapability>
└── execute(RuntimeRequest) -> RuntimeResult
```

The adapter owns filesystem/build/runtime details. Studio owns the shell,
canonical session, commands, scene snapshot, asset facade, and contribution
hosts. A fork implements the small adapter contract around its own internals;
Studio never reaches into `playerManager`, cache managers, or server classes.

### Official OpenRune adapter possibilities

OpenRune is the first-party target, but it should be represented as adapters
and providers rather than an `OpenRune mode` in core:

- detect `game.yml`, `.data/`, `content/`, `or-cache/`, and `gradlew`;
- identify revision/environment and LIVE/SERVER cache locations;
- browse pack modules and separate scripts from cache-pack resources;
- expose `buildCache`, `freshCache`, `cleanCs2`, and `mergePluginGamevals`;
- show structured cache-build progress and changed regions/chunks;
- discover and edit symbolic GameVals/RSCM mappings;
- create OpenRune content-pack resource layouts;
- integrate CS2/interface pack outputs;
- launch, stop, restart, and debug the development server; and
- later connect an optional `openrune-studio-bridge` over localhost.

### Runtime bridge possibilities

The bridge should advertise capabilities instead of making Studio assume a
stock server layout. Possible capabilities include:

- teleport to a selected tile, region, or object;
- reload an edited map square or nearby region;
- spawn/highlight/inspect an object in a development world;
- query actual server collision, route, LOS, and reach behavior;
- open an interface on a development player; and
- run a test CS2 script with controlled arguments.

This would let Studio compare its neutral preview with the actual server
implementation without importing server runtime code. Unsupported buttons
should disappear when a fork does not advertise the capability.

### Customization tiers

1. Configuration: override paths, filenames, and commands.
2. Adapter plugin: implement larger project/build differences.
3. Runtime bridge: add live-server behavior independently of the adapter.

This keeps a modified OpenRune fork useful even when only Levels 0–2 work.

## Full RuneLite/OpenOSRS fork as an architecture reference

The full `melxin/runelite` checkout is now captured outside the product tree
at `../RSPSi-resources/RuneLite-melxin`, pinned to commit
`1ad572d7dcdbc0fb67a4a00f0c2f959d5ab25abc`. It is a reference checkout, not a
runtime dependency or a second client/cache backend.

The highest-value areas to study are:

- `runelite-client/plugins`: plugin lifecycle, external plugin discovery,
  classloader isolation, PF4J integration, dependencies, disable/reload, and
  broken-plugin handling;
- `runelite-api`, `runescape-api`, `runelite-mixins`, and
  `injected-client`: the stable semantic-API → implementation-adapter →
  client-hook layering that informs the future Studio runtime bridge;
- `runescape-client` and `deobfuscator`: focused scene/rendering references for
  `Scene`, tiles, models, collision, rasterization, and revision mapping;
- `cache`: an independent cache/definition behavior oracle only; and
- `runelite-jshell`: a possible model for interactive diagnostics and live
  scene inspection.

The Studio possibilities this adds are:

- a stable `StudioRuntimeApi` behind OpenRune/custom-server bridge adapters;
- external-plugin manifests, dependency checks, classloader ownership,
  permissions, disable/reload, and broken-plugin recovery;
- a focused current-client scene/render reference package instead of copying a
  whole client; and
- a RuneLite-cache comparison harness for definitions, models, sprites,
  minimaps, and renderer characterization.

The boundary remains strict: RSPSi owns the editable world, commands/history,
scene contracts, and frontend contributions. RuneLite/OpenOSRS may inform
behavior and architecture, but its injected client, cache module, and renderer
must not become Studio's production spine. The fork is also not treated as an
independent oracle from RuneLite for semantics it inherits; TSPS, OpenRune,
and live-client evidence remain necessary for cross-checking.

### Scene tooling possibilities from the full reference

The pinned full checkout also gives us a useful future feature list. These are
ideas for first-party plugins or later runtime adapters, not foundation work:

- a scene inspector that shows the four plane layers, tile paint/model data,
  object category, footprint, authored plane, physical/render plane, bridge
  link, and source-region provenance together;
- a bridge/effective-plane overlay that compares authored, collision, minimap,
  and renderer projections on the same tile;
- a renderer parity workspace that exports tile geometry, model faces, colors,
  textures, alpha, priorities, and transforms for comparison against a live
  client or RuneLite GPU capture;
- a DevTools-style chunk/region/world-coordinate overlay with tile polygons,
  object bounds, line-of-sight, route, and loading-context diagnostics;
- a model and scene-export tool for OBJ/mesh snapshots and deterministic scene
  fingerprints;
- a live Studio runtime bridge that asks a compatible client/server for
  selected scene facts without exposing client internals to Studio plugins; and
- a scene-aware plugin SDK where tools consume immutable snapshots and render
  overlays while all edits still execute through Studio commands/history.

The source-backed vocabulary and remaining evidence gaps for these ideas are
kept in [`RUNELITE_SCENE_REFERENCE.md`](RUNELITE_SCENE_REFERENCE.md).

## 3D scene fidelity and renderer diagnostics

The RuneLite review adds a focused set of renderer ideas. These should remain
downstream of the neutral render-packet contract rather than becoming ad hoc
features in the viewport:

- a scene inspector that shows terrain corner colors, shaped-face topology,
  texture/UV inputs, object model-type selection, transformed bounds, alpha,
  face priority, authored/effective plane, and bridge links for one tile;
- camera/frustum, visible-tile, occluder, roof, and bridge traversal overlays
  that explain why geometry is or is not being drawn;
- a static map-scene render-packet exporter with deterministic fingerprints,
  optional OBJ/glTF output, and per-face debug attributes for RuneLite/TSPS
  comparison;
- a bounded GPU scene cache using 8×8 zones plus neighboring-zone dependency
  tracking for shared edges, blended lighting, textures, and dirty rebuilds;
- explicit capability diagnostics for unsupported dynamic features such as
  sequences, particles, billboards, item piles, or animated textures; and
- a renderer comparison workspace that can inspect the same immutable scene
  snapshot through the JavaFX compatibility view, the eventual 3D backend,
  and exported packets without creating separate world state.

The immediate implementation choice is to finish static revision-240 map
scenes first. These ideas are valuable because they make visual mismatches
explainable, but they do not authorize copying RuneLite's renderer or adding a
second scene graph.

The detailed TSPS source adjudication is kept in
[`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md), including the decisions
to adopt TSPS's render-preparation behavior while keeping RSPSi's immutable
scene ownership and OpenRune-compatible collision authority.

## Scene export and renderer diagnostics

The reviewed [OSRS Environment Exporter](https://github.com/ConnorDY/OSRS-Environment-Exporter)
adds a focused set of possibilities. Its `SceneRegionBuilder`, renderer
uploaders, glTF exporter, and headless CLI are useful references for features
that should consume RSPSi's immutable scene projections:

- renderer diagnostics for terrain brightness, underlay blending, overlay
  texture/sentinel handling, object-layer order, alpha, priorities, UVs,
  contouring, and normal merging;
- a deterministic scene-export snapshot and fingerprint for bug reports and
  parity comparisons;
- a first-party glTF/mesh export plugin for Blender and model inspection;
- a headless scene export/verification command for CI and external renderer
  comparisons; and
- configurable CPU/GPU renderer characterization cases, including alpha mode,
  MSAA, and face-priority behavior.

These remain post-foundation ideas. The exporter repository is GPL-3.0 and
must not be copied or added as a dependency; the RSPSi implementation must be
neutral, test-backed, and attached through the renderer/export plugin API.

## Other deferred Studio feature directions

These are possible first-party workspaces or plugins after the foundation and
source/build contracts are accepted:

- semantic build, diff, pack, and revision-audit reports;
- world-map and collision/navigation diagnostics;
- model, texture, sprite, and definition preview workflows;
- interface and CS2 editors with live development-client refresh;
- cutscene/timeline editing;
- committed asset palettes and content-pack creation; and
- visual history checkpoints and chunk-aware merge assistance.

All of these must consume the existing `WorldDocument`, `EditorSession`,
commands, typed asset repository, immutable scene snapshots, and plugin
contribution registry.

## Explicit non-goals for now

- Do not couple Studio core to OpenRune-Server internals.
- Do not require a server to be colocated with Studio.
- Do not add a runtime bridge before cache/build/project boundaries are stable.
- Do not start public plugin distribution, scripting permissions, or a Plugin
  Hub before built-in lifecycle and ownership behavior is verified.

## Future workspaces and FileStore-backed capabilities

The long-term Studio product is not limited to map editing. Planned
workspaces include interfaces, items, models, assets, validation, and build
tools. They must reuse the shared session/history/selection/asset/render
contracts. OpenRune FileStore's ComponentDecoder, CS2 tools, GameVals, DB
tables, item/NPC sprite factories, and world-map tools are recorded as future
adapters, not separate cache systems.

Lighting remains a first-class renderer foundation item: faithful OSRS/TSPS
lighting is the default, while frontend exposure is an editor-only display
control.
