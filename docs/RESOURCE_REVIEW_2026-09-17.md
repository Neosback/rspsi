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

The other projects are deliberately assigned narrower roles:

| Resource | Final role | Use | Do not use |
|---|---|---|---|
| RSPSi | Product foundation | Desktop shell, project/session model, commands, world model, renderer API, UI-neutral contracts | Legacy `SceneGraph` as the new architecture |
| OpenRune FileStore | Production cache/data dependency | Filesystem, raw map/location bytes, OSRS definitions, models, sprites, XTEA, packing, cache tools | Its internal cache types, render helpers, or a second editor world model in Studio |
| RuneLite | Independent OSRS oracle | Coordinate/plane/bridge semantics, cache behavior, DevTools inspection, GPU/model correctness references | RuneLite client/cache as a second production backend |
| TSPS / Elvarg TypeScript client | Scene and client-behavior donor/oracle | `SceneBuilder`, `SceneTileModel`, collision, instances, model preparation, scene-to-render data, and edit-mode region-pack behavior | TypeScript client/server runtime, WebGL renderer, browser UI, or server content |
| Neosback OpenRune-Editor | Editing-workflow donor | Brush/tool lifecycle, plugin host ideas, history grouping, region stamps, picking, editor UX | React/Tauri/WebGL/docking architecture or upstream types |
| Domw71 revision-240 editor | Java integration and revision-240 case study | Standalone map-editor workflow, RuneLite-derived terrain/location codec, save/reload behavior, 2D/3D UX comparison | Runtime dependency, copied RuneLite cache module, or independent correctness authority |
| OpenRune-Server | World/collision donor | Coordinates, location constants, collision flags, route/LOS/reach semantics | Server runtime, gameplay, networking, content, or cache manager types |
| OpenRS2 and revision tools | Archaeology/audit reference | Historical caches, compression/XTEA diagnosis, revision drift investigations | Production cache API |

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

The current pinned application version is `2.4.19`. The research checkout has
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

### TSPS / Elvarg lineage — scene semantics plus edit-mode evidence

TSPS is a BSD-2-Clause TypeScript OSRS client/server continuation with a
WebGL/browser client and an edit-mode plugin. The scene code is valuable for
terrain topology, floor/material inputs, bridges, instance transforms,
location/model placement, contouring, lighting preparation, and scene-to-GPU
data extraction.

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
   validation, debug, and preview workflows behind the existing neutral plugin
   boundary.

