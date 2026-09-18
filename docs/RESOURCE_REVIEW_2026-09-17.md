# RSPSi Foundation Resource Review — 2026-09-17

This review rechecks the resources that could influence the OSRS map-editor
foundation. It is a design and provenance decision, not an instruction to
combine repositories. The product must have one canonical world model, one
editing/history system, and one production cache boundary.

## Decision

RSPSi remains the product and owns the canonical semantics:

```text
JavaFX / future frontend
          ↓
first-party workflow plugins
          ↓
RSPSi editor core
  WorldDocument / Session / Commands / Selection
          ↓
RSPSi world + scene + collision contracts
          ↓
RSPSi OSRS cache adapter
          ↓
OpenRune FileStore
```

## Review of the proposed architecture and final integrated decision

The attached `Choose Client Base.md` and the pasted analyses are treated as
design proposals, not as source-authoritative instructions. After comparing
them with the current RSPSi code and the five checked-out resource
repositories, the useful ideas are adopted below. Proposals that would
introduce a second cache, renderer, world model, or frontend foundation are
explicitly rejected.

### FileStore is the OSRS cache backend, not a feature plugin

The current implementation confirms that OpenRune FileStore can replace the
old modern-cache reader responsibility:

```text
OsrsBundle
  -> CacheStoreFactory.openOsrs(...)
  -> OpenRuneCacheStore / OpenRune FileStore
  -> revision-aware RSPSi map and definition adapters
  -> OsrsStudioProject / EditorSession
  -> first-party feature plugins
```

FileStore supplies cache opening, archive/file access, cache identity,
definitions, models, sprites, XTEA, and cache tooling. RSPSi supplies the
revision profile, explicit DAT2 archive identities, map/location semantics,
the authored world, bridges and instances, collision policy, derived scene,
commands, history, and frontend-neutral plugin contracts. FileStore is not
large enough to replace those editor responsibilities, and expanding it until
it owns them would create the second architecture this review is meant to
prevent.

There is therefore no `FileStorePlugin` and no feature-registry checkbox for a
cache library. `OsrsBundle` is the selectable OSRS format/provider boundary.
If the UI needs to show the choice, it should be labelled **Cache source:
OpenRune FileStore (OSRS)** and display the capability state (`read-only`,
`staged output`, or explicit `direct output`). It must not present OpenRune
FileStore and a 317 loader as interchangeable decoder choices for the same
modern cache. The legacy Displee path is quarantined compatibility code and
has no automatic modern-cache fallback.

The existing `OSRSPlugin` is not deleted in this cleanup. It still installs
static loader adapters consumed by the compatibility/software renderer. It is
now a migration bridge only. It may be retired after the renderer and viewport
consume the neutral definition, model, texture, map-scene, animation, and
`RenderScene` services directly, and the compatibility path has characterization
and parity coverage. No new editor feature may add another dependency on those
static loaders.

### Final source choice by concern

| Concern | Integrated choice | Why the other resources remain references |
|---|---|---|
| Production cache I/O and typed OSRS data | OpenRune FileStore behind RSPSi adapters | It is the only selected production ecosystem dependency; upstream types do not escape the adapter |
| Map/location meaning and authored world | RSPSi neutral codecs and `WorldDocument` | TSPS, RuneLite, and the revision-240 editor provide comparison evidence without creating competing state |
| Scene topology, bridges, instances, and render inputs | RSPSi neutral scene pipeline | TSPS and RuneLite are semantic references; the Environment Exporter is a GPL-bounded forensic reference |
| Collision and route projections | RSPSi neutral collision layer, checked against OpenRune-Server semantics | Server internals and TSPS client clip flags are not interchangeable collision authorities |
| Feature/plugin lifecycle | RSPSi contribution registry and host | RuneLite-melxin informs lifecycle, dependencies, ownership, and later external-plugin work; PF4J/hot reload is deferred |
| Editor workflow | RSPSi JavaFX shell and vertical feature packages | OpenRune-Editor-Neosback and TSPS inform tools, picking, history, and edit-mode workflow only |
| Current OSRS behavior | RuneLite and independent fixtures | RuneLite is an oracle, not a production cache/client dependency |
| Server/build integration | Optional RSPSi `ServerAdapter`, first-party OpenRune adapter | OpenRune-Server contributes layout/build/runtime capability descriptions, not server classes to Studio core |

### Client-base decision

`Choose Client Base.md` is useful for a future browser/web client decision. Its
TypeScript, React/Tauri, WebGL/WebGPU, and xRSPS/Neosback recommendations do
not replace the current JVM desktop editor foundation. The present product
remains Java 21 with JavaFX as the shell, a neutral scene and command contract,
and a future ImGui adapter over those same contracts. There will be no Kotlin
rewrite, WebGPU migration, or React/Tauri shell added as a foundation
prerequisite. The selected long-term desktop viewport is now an embedded
LWJGL/OpenGL surface hosted by JavaFX. It is an isolated packet consumer, not
a replacement for JavaFX, the neutral scene model, or the software parity
renderer, and remains opt-in until interaction and client-parity acceptance
passes.

This keeps the repository coherent: one production cache boundary, one
authored world/session/history model, one scene contract, one plugin registry,
and multiple references used to verify behavior rather than multiple systems
used at runtime.

The other projects are deliberately assigned narrower roles:

| Resource | Final role | Use | Do not use |
|---|---|---|---|
| RSPSi | Product foundation | Desktop shell, project/session model, commands, world model, renderer API, UI-neutral contracts | Legacy `SceneGraph` as the new architecture |
| OpenRune FileStore | Production cache/data dependency | Filesystem, raw map/location bytes, OSRS definitions, models, sprites, XTEA, packing, cache tools | Its internal cache types, render helpers, or a second editor world model in Studio |
| RuneLite | Independent OSRS oracle | Coordinate/plane/bridge semantics, cache behavior, DevTools inspection, GPU/model correctness references | RuneLite client/cache as a second production backend |
| melxin RuneLite/OpenOSRS fork | Architecture and plugin-platform reference | Full plugin lifecycle, PF4J/classloader patterns, API/mixin/injection layering, deobfuscation flow, focused client/rendering study, and JShell diagnostics | Forked RuneLite client/cache as production code or as an independent semantic oracle from RuneLite |
| TSPS / Elvarg TypeScript client | Scene and client-behavior donor/oracle | `SceneBuilder`, `SceneTileModel`, collision, instances, model preparation, scene-to-render data, and edit-mode region-pack behavior | TypeScript client/server runtime, WebGL renderer, browser UI, or server content |
| Neosback OpenRune-Editor | Editing-workflow donor | Brush/tool lifecycle, plugin host ideas, history grouping, region stamps, picking, editor UX | React/Tauri/WebGL/docking architecture or upstream types |
| Domw71 revision-240 editor | Java integration and revision-240 case study | Standalone map-editor workflow, RuneLite-derived terrain/location codec, save/reload behavior, 2D/3D UX comparison | Runtime dependency, copied RuneLite cache module, or independent correctness authority |
| OpenRune-Server | World/collision donor | Coordinates, location constants, collision flags, route/LOS/reach semantics | Server runtime, gameplay, networking, content, or cache manager types |
| OpenRS2 and revision tools | Archaeology/audit reference | Historical caches, compression/XTEA diagnosis, revision drift investigations | Production cache API |
| OSRS Environment Exporter | Focused scene/render/export donor | Scene-region lighting/material preparation, object/model placement, renderer backend separation, glTF and headless export workflow | Displee cache backend, Swing/OpenGL application shell, GPL-covered source or assets |

## What each resource actually contributes

### OpenRune FileStore — the only production ecosystem dependency

The repository is a multi-module OSRS cache/tooling suite: filesystem,
filestore, OSRS filesystem support, definition codecs, model/sprite support,
RSCM/GameVal, XTEA, OpenRS2 download tooling, world-map tooling, and
incremental packing. Its published build metadata declares Apache-2.0, while
the pinned checkout does not contain a standalone root license file; the
artifact/version evidence must therefore remain attached to the selected
dependency before redistribution.

RSPSi should consume only a small adapter surface. The adapter may use
FileStore's filesystem and definition implementations, but Studio sees
`CacheStore`, neutral definitions, `OsrsMapService`, and project metadata. The
FileStore model/render helper classes are not allowed to become Studio's
canonical model or renderer.

The current pinned application version is `3.0.2`. The research checkout has
newer build metadata, so upgrades are compatibility events, not routine
dependency bumps. Every upgrade must rerun cache, map, location, definition,
output, and scene parity evidence.

### RuneLite — independent truth, not infrastructure

RuneLite's cache loaders and client APIs make excellent semantic references for
`WorldPoint`, regions, chunks, scene planes, bridges, tile models, locations,
DevTools, minimap behavior, and GPU/model quirks. RuneLite is BSD-2-Clause.

Its cache module must not become another production cache backend. Its client
is injected and renderer-coupled, while RSPSi needs an offline editable
document. We use RuneLite to ask whether RSPSi agrees with current OSRS
behavior, not to determine the shape of RSPSi's public API.

### melxin RuneLite/OpenOSRS fork — architecture reference, not infrastructure

The full fork is pinned outside the product repository at
`../RSPSi-resources/RuneLite-melxin`, commit
`1ad572d7dcdbc0fb67a4a00f0c2f959d5ab25abc`. Its module breadth makes it more
useful than `runelite-api` alone for understanding the complete relationship
between stable APIs, injected client implementations, mixins, deobfuscation,
cache behavior, and external plugins.

The most relevant paths are `runelite-client/plugins`, `runelite-api`,
`runescape-api`, `runelite-mixins`, `injected-client`, `runescape-client`,
`deobfuscator`, `cache`, and `runelite-jshell`. These inform Studio's future
plugin/runtime-bridge and scene-reference work. The concrete scene findings
are recorded in [`RUNELITE_SCENE_REFERENCE.md`](RUNELITE_SCENE_REFERENCE.md),
including tile-layer order, bridge/effective-plane behavior, and the split
between scene projections and authored state. They do not change the
production rule: RSPSi owns the editable world and history, and OpenRune
FileStore remains the only planned OSRS cache dependency.

### TSPS / Elvarg lineage — scene semantics plus edit-mode evidence

TSPS is a BSD-2-Clause TypeScript OSRS client/server continuation with a
WebGL/browser client and an edit-mode plugin. The scene code is valuable for
terrain topology, floor/material inputs, bridges, instance transforms,
location/model placement, contouring, lighting preparation, and scene-to-GPU
data extraction.

The deeper scene review is now recorded in
[`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md). It confirms that TSPS is
the best concrete donor for revision-240 render preparation: radius-five
underlay blending, final per-vertex HSL/UV terrain data, hidden shaped faces,
shape/type-aware location model selection, ordered model transforms,
contouring, normal merging, bridge projection, and opaque/alpha/priority GPU
packet fields. RuneLite remains the semantic client oracle, while RSPSi keeps
authored data, command history, collision policy, and renderer-neutral packet
ownership. TSPS's mutable scene graph, WebGL/PicoGL buffer layout, browser
runtime, and client collision flags are not adopted as Studio architecture.

The edit-mode code adds a second useful slice: it edits terrain and locations
as an explicit edit log, encodes revision-aware region packs, supports object
placement/deletion, terrain/height/underlay/flag changes, search/palette UX,
world-map navigation, and export. That is a donor for behavior and fixtures,
not a reason to embed a TypeScript runtime. RSPSi's `EditorCommand` and
`WorldDocument` remain the authoritative mutation and storage model.

### Neosback OpenRune-Editor — workflow donor, not product base

The captured repository is a RuneScape map viewer whose source contains a
substantial map editor: built-in height/overlay/underlay/flag/object/region-
stamp plugins, history, picking, scene loaders, and a WebGL renderer. Its
plugin and workbench ideas are directly relevant, but its React/Next/Tauri,
browser cache, WebGL, and flexible docking architecture do not fit RSPSi's
JavaFX-first stabilization path.

We adapt focused concepts into RSPSi-owned interfaces and test each adopted
behavior. We do not import its plugin host, renderer, or world representation.

### Domw71 revision-240 editor — high-value forensic reference

This repository is a standalone Java 11+ Swing editor. Its README states that
it bundles a copied RuneLite cache module and an `editor` package. The source
contains a mutable `RegionModel`, format-detecting terrain codec, terrain and
location savers, cache service, 2D renderer, software 3D renderer, object
definitions, model previews, terrain tools, object tools, and 50-step
undo/redo. Its license explicitly identifies the RuneLite-derived BSD-2-Clause
source and retains per-file notices.

That makes it especially useful for a revision-240 compatibility case study:

- compare its `MapCodec`/`MapSaver`/`LocationsSaver` with RSPSi's neutral
  codecs and TSPS region-pack logic;
- compare explicit-height handling, generated heights, overlay shape/rotation,
  location smart-delta ordering, XTEA, and save/reload behavior;
- use its UI as a manual workflow comparison for terrain/object editing.

It is not independent from RuneLite, and it is not suitable as RSPSi's base.
Its copied cache module would recreate the exact dependency duplication we are
trying to remove.

### OSRS Environment Exporter — scene/render/export reference

The pinned `ConnorDY/OSRS-Environment-Exporter` checkout is a passing Kotlin
reference build at commit `61d461d3bfd8217a470518924415d7de1b074b9c`. Its
`SceneRegionBuilder` gives us a concrete RuneLite-derived implementation to
compare against for underlay blending, directional brightness, overlay
shape/rotation, object footprint placement, contouring, and normal merging.
Its scene tile model and renderer classes also make the boundary between
scene upload, CPU/GLSL priority handling, alpha, texture/UV data, and draw
execution explicit. `SceneExporter`/`GlTFExporter` and `CliExporter` add a
credible future export-plugin and headless-verification direction.

The project is GPL-3.0 and uses Displee plus a Swing/LWJGL application shell;
those are not being promoted into RSPSi. We will reimplement only neutral,
tested contracts behind RSPSi's cache, scene, renderer, and plugin APIs. The
full source-level findings and evidence limits are in
[`OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md`](OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md).

## Foundation invariants before more plugins

No resource is allowed to introduce a competing interpretation of these:

1. **Cache semantics:** revision profile, map-index layout, XTEA, terrain and
   location byte formats, definitions, and encode/decode preservation.
2. **Terrain semantics:** explicit versus generated heights, four planes,
   underlays, overlays, flags, 13 topologies × 4 rotations, shared edges,
   blending, materials, and bridge/effective-plane behavior.
3. **World semantics:** world/region/local/chunk coordinates, 64×64 regions,
   8×8 chunks, loading-line holes, neighboring-region context, instances, and
   rotated templates.
4. **Location semantics:** ID, plane, type/layer, orientation, shape,
   footprint, transform/config metadata, wall/decor/ground categories, and
   object-to-collision derivation.
5. **Scene semantics:** terrain mesh, floor colors/blending, model transforms,
   contouring, normals/lighting inputs, bridge visibility, map-scene/minimap
   behavior, and renderer-independent derived data.
6. **Editing semantics:** one `WorldDocument`, one `EditorSession`, one
   command/history path, one selection service, atomic grouped edits, and
   dirty-region invalidation.

## Required comparison matrix

The foundation gate should compare RSPSi against the right sources, while
recognizing that Domw71 is RuneLite-derived and therefore not an independent
oracle:

| Concern | Primary RSPSi implementation | Independent/reference checks |
|---|---|---|
| Cache I/O/definitions | OpenRune adapter + neutral providers | OpenRune source/API and RuneLite cache behavior |
| Terrain bytes | RSPSi revision-aware codec | RuneLite loader, TSPS `RegionPack`, Domw71 codec |
| Locations bytes | RSPSi neutral decoder/encoder | RuneLite loader, TSPS `RegionPack`, Domw71 saver |
| Tile topology | RSPSi `TerrainMeshBuilder` | TSPS `SceneTileModel`, RuneLite `SceneTileModel` |
| Scene construction | RSPSi `RenderSceneBuilder` | TSPS `SceneBuilder`/`SdMapDataLoader`, RuneLite scene semantics |
| Collision | RSPSi `OsrsCollisionBuilder` | OpenRune-Server route semantics, TSPS client collision, RuneLite inspection |
| Editing workflow | RSPSi commands/plugins | OpenRune-Editor, TSPS edit-mode, Domw71 manual comparison |
| Revision 240 | RSPSi live fixture | Domw71 standalone load/save plus RuneLite-derived decoder behavior |
| Rendering | RSPSi renderer API/backends | RuneLite GPU behavior, TSPS WebGL geometry, Domw71 software view |

Every mismatch must be classified as cache, authored world data, derived scene
data, collision policy, or renderer presentation. We do not “fix” a mismatch
by adding another runtime model.

## Resource-use rules

- One production cache dependency: OpenRune FileStore behind RSPSi adapters.
- One canonical world/editor model: RSPSi-owned.
- One command/history system: RSPSi-owned.
- Donor code is adapted in small, provenance-recorded units only when a test
  demonstrates the behavior we need.
- A derived project such as Domw71 cannot count as independent parity evidence
  against its ancestor RuneLite.
- TypeScript, React, Tauri, Swing, server, networking, and injected-client
  systems remain external references unless a narrowly scoped behavior is
  reimplemented behind a neutral RSPSi contract.
- No cache dumps, models, generated map tiles, or copied source from a
  license-review resource enter the product without a separate distribution
  decision.

## Immediate foundation work

1. Add the 2026-09-17 resource review to the resource ledger and keep all
   checkouts pinned outside the RSPSi tree.
2. Build the three-way revision-240 codec matrix: RSPSi, TSPS region-pack
   behavior, and Domw71/RuneLite-derived behavior, with semantic equality as
   the acceptance criterion rather than byte identity alone.
3. Expand the foundation fixtures for water/swamp, bridge/multi-plane,
   wall-heavy, region-boundary, and instance cases.
4. Complete the explicit revision-feature registry and audit report before
   adding another supported OSRS revision.
5. Only after those pass, extract first-party asset browser, terrain/object,
   validation, debug, preview, and scene-export workflows behind the existing
   neutral plugin boundary.

## FileStore adoption decision

The detailed FileStore review selects it as the primary OSRS cache and
definition backend. Its filesystem, definition codecs, model/texture/sprite
decoders, XTEA, and packing tools are valuable; its global `CacheManager`,
read/write ambiguity, Displee-backed delegate, and build-only concerns are not
allowed to become Studio state. The implementation uses a session-scoped
neutral repository and explicit `READ_ONLY`, `STAGED`, `DIRECT`, and
`BUILD_ONLY` capabilities. See
[`OPENRUNE_FILESTORE_ADOPTION.md`](OPENRUNE_FILESTORE_ADOPTION.md).

The source checkout also has a portability issue: the publishing build
defaults to a hard-coded Windows path, while its README advertises an older
published version. Studio pins published artifacts and treats any upstream
fix as a separately tracked compatibility change.

## OpenRune Server integration review

The OpenRune-Server checkout is useful to Studio in three separate ways:

1. `or-cache` documents and implements the server's cache build, map,
   GameVal, CS2, interface, DB-table, model, sprite, and incremental packing
   workflow.
2. `content/**/pack` provides source-resource provenance for server content.
3. `plugins/` demonstrates external runtime plugin metadata and lifecycle,
   but its server classes must not be loaded into Studio.

Studio now has a read-only adapter for arbitrary server roots, path overrides,
fork diagnostics, fingerprints, content inventory, and external Gradle task
descriptions. FileStore remains the only cache reader. Source application and
the optional local runtime bridge are later milestones.

## RuneLite scene/rendering intake

The full RuneLite checkout is retained as the OSRS client-semantic and GPU
reference. Its scene construction, shaped-tile tables, terrain lighting,
bridge/effective-plane handling, `SceneUploader`, GLSL inputs, and DevTools
overlay conventions are documented in:

- [`RUNELITE_SCENE_RENDERING_REFERENCE.md`](RUNELITE_SCENE_RENDERING_REFERENCE.md)
- [`RUNELITE_GPU_PIPELINE.md`](RUNELITE_GPU_PIPELINE.md)
- [`RUNELITE_DEVTOOLS_OVERLAYS.md`](RUNELITE_DEVTOOLS_OVERLAYS.md)

RuneLite is not a production cache or renderer dependency. Its Java2D overlay
implementation is translated into frontend-neutral marker/diagnostic models;
its GPU path informs packet fields and invalidation but does not own RSPSi
scene interpretation. OpenRune FileStore remains the only production cache and
definition path.

## JavaFX shell and visual foundation intake

The first production frontend is now a controlled JavaFX shell rather than a
collection of legacy FXML panels. AtlantaFX Primer Dark supplies the base CSS
theme, Ikonli Material Design supplies semantic icon glyphs, and ControlsFX is
retained only for utility controls. DockFX and MaterialFX are intentionally not
added because a second docking/styling system would compete with the neutral
workspace contracts.

The implemented shell is described in
[`UI_UX_FOUNDATION.md`](UI_UX_FOUNDATION.md). Its layout persistence,
detachable utility panels, workspace tabs, context toolbar, outliner,
inspector, and status row remain JavaFX-layer state. No theme or icon library
may enter editor-core, and no panel layout may enter project/world data. The
current legacy viewport remains a compatibility host until the independent
scene and renderer acceptance gates pass.
