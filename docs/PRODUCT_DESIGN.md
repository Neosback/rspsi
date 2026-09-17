# RSPSi OpenRune/OSRS Studio Product Design

This document locks the product direction. The executable progress ledger is
[`ROADMAP.md`](ROADMAP.md); external provenance is in
[`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md).

## Product contract

RSPSi is a maintainable desktop world/map editor for Old School RuneScape
caches, built around the OpenRune ecosystem.

Production scope:

- current and recent OSRS caches supported by OpenRune;
- terrain, locations, models, floors, collision, minimap, and world editing;
- cache-aware projects with explicit revision identity;
- JavaFX frontend first, with a future Dear ImGui frontend kept possible.

Non-goals:

- 317, 474/508, 667, 718, RS3, or arbitrary historical formats as product
  targets;
- full game-server runtime, networking, login, combat, or gameplay content;
- public plugin marketplace, Lua/CS2 IDE, collaboration, cloud cache, or
  renderer replacement before the foundation gates pass.

Legacy code is quarantined to protect characterization coverage during the
migration. It receives no new product features and is retired after the OSRS
cache, scene, command, and parity gates pass.

## Ownership map

| Concern | RSPSi decision |
|---|---|
| Cache filesystem, OSRS definitions, map/location data, packing | OpenRune FileStore behind RSPSi adapters |
| Cache-backed engine, `or-cache`, coordinates, map utilities | Focused behavior from Neosback OpenRune-Server |
| Collision and route semantics | OpenRune-Server donor, RuneLite verification |
| Terrain and scene construction | RSPSi implementation verified against TSPS and RuneLite |
| Editing workflows, brushes, history, region stamps | RSPSi commands/tools informed by Neosback OpenRune-Editor |
| Current OSRS truth | RuneLite and RuneLite DevTools |
| Canonical world model, commands, selection, UI contracts | RSPSi-owned |

External repositories are research inputs. Whole-project dependencies and
upstream runtime types do not cross into the editor core.

## FileStore and plugin boundary

OpenRune FileStore remains the production cache/data foundation. It owns cache
filesystem access, raw map/location bytes, OSRS definitions, models, sprites,
XTEA, packing, and generic cache/revision tooling. RSPSi owns the canonical
editable world model and the semantic layers built from that data: coordinates,
terrain, bridges, instances, collision, scene construction, commands, and
rendering contracts. Collision and scene behavior are not folded into
FileStore merely because FileStore supplies their inputs.

The FileStore review identifies useful future candidates—an upstream-friendly
map codec, a unified lazy asset facade, and revision-conformance tooling—but
RSPSi first completes those seams behind its own neutral adapters. We do not
copy a second `MapRegion`, scene graph, collision map, or model runtime into
FileStore. Any future upstream contribution must be generic, provenance-safe,
and independently useful outside the RSPSi editor.

Workflow features are first-party plugins once the foundation is stable. The
initial plugin set is expected to include terrain tools, object tools,
selection, collision/route previews, asset browsing and definition inspection,
validation/debug overlays, and minimap or scene-preview workflows. Plugins
may register neutral tools, panels, inspectors, and commands, but they may not
own `WorldDocument`, history, project identity, cache writes, or frontend
types. Asset browsing is therefore a plugin-facing workflow over the core
`AssetRepository`; it is not a second cache layer.

The foundation must be completed before this plugin surface grows. The editor
must have executable evidence for OSRS metadata and rules covering revision
formats, explicit/generated heights, floor blending, shaped tiles and
rotations, regions/chunks, coordinate conversions, bridges/effective planes,
location categories/types/orientations, object configs and footprints,
collision flags, model transforms, and instance boundaries. This prevents
plugins from encoding competing interpretations of the game world.

## Data flow

```text
OpenRune FileStore
        ↓
RSPSi OSRS cache adapter
        ↓
neutral map and definition services
        ↓
WorldDocument
        ↓
EditorSession + commands + selection
        ↓
scene builder / collision / renderer / frontend
```

Revision-specific behavior belongs in cache adapters and codecs. The world
model does not contain archive IDs, opcodes, XTEA keys, or revision branches.

Projects record format version, `game = oldschool`, cache revision,
subrevision, and cache fingerprint. A mismatch opens read-only in the first
implementation; `ProjectCompatibility` makes that decision explicit for
callers. Automatic ID migration is deferred.

The frontend composition root is `OsrsBundle`, which creates an
`OsrsStudioProject`. It exposes an opened
canonical session, neutral definitions, and asset search to the JavaFX bridge
without exposing OpenRune, Displee, or archive types. The current safe output
arrangement reads the OpenRune source and stages writes into a distinct output
cache; a writable OpenRune packer remains a parity-gated milestone.

The shell may display the selected cache source and its capability state, but
this is not a feature-plugin selection. Supported modern OSRS projects use
OpenRune FileStore through the bundle; `READ_ONLY`, `STAGED`, and explicit
`DIRECT` describe output capability. The quarantined 317 compatibility path is
not an automatic fallback for a modern cache.

The JavaFX project-open workflow offers the same two explicit choices: inspect
the source read-only, or select a separately prepared output cache for editing.
It never turns the selected source cache into an implicit write target.
Editable sessions attach project-scoped neutral autosave and offer
identity-checked recovery as one undoable command.

Each project directory contains `project.json`, `autosave/`, and `edits/`.
`OsrsStudioProject.initializeProject(...)` records the selected cache identity
there without copying the cache; later opens compare that identity and fail
closed to read-only when it differs.

## Long-term Studio direction

The cache is a generated output, not the authoritative project format. The
future source-first flow is documented in [`STUDIO_DIRECTION.md`](STUDIO_DIRECTION.md):

```text
immutable base OSRS cache + Git-controlled project sources
                         ↓
                    WorldDocument
                         ↓
              commands / validation / preview
                         ↓
                  disposable built cache
```

This is intentionally a later milestone. The current map editor must first
finish its OSRS metadata, scene, collision, command, and parity gates. Until
then, the verified cache adapter, project metadata, autosave, and staged output
arrangement remain the supported implementation. The source project must not
be introduced as a partial second persistence system.

Long term, RSPSi can grow into OpenRune Studio with one shell and specialized
workspaces: Map Editor first; then World Map/Collision diagnostics, Asset
Browser/definition inspection, and much later Interfaces, CS2, and Cutscenes.
These are workspace or plugin surfaces over the same core, not separate
applications or competing world/cache models. The 742 editor and its runtime
are out of scope; only its source-diff, inspector, build-status, and
incremental-packaging ideas are retained.

The stable shell should use a centered work area, side tools, a contextual
inspector, controlled bottom tabs for Assets/History/Changes/Validation/Build/
Console, and a persistent project/revision/dirty/build status row. JavaFX is
the first frontend; Dear ImGui remains possible because these contracts stay
UI-neutral.

## Editor layout

Use controlled workspaces rather than unrestricted docking:

```text
┌──────────────────────────────────────────────────────────────┐
│ File Edit View Map Tools Help                 Search / Cmd-P  │
├────────┬───────────────────────────────────────┬─────────────┤
│ Tools  │              VIEWPORT                │ Inspector   │
│        │                                       │             │
│ Select │                                       │ Selection   │
│ Terrain│                                       │ Properties  │
│ Object │                                       │             │
│ Debug  │                                       │             │
├────────┴───────────────────────────────────────┴─────────────┤
│ Assets | History | Validation | Console                      │
├──────────────────────────────────────────────────────────────┤
│ Region | Plane | World X/Y | Cache revision | Dirty | FPS    │
└──────────────────────────────────────────────────────────────┘
```

Standard workspaces are Map, Terrain, Objects, Collision, and
Validation/Debug. The viewport is permanent and centered. Tools stay beside
it, the inspector stays at a side, and the lower area switches between assets,
history, validation, and console. Command palette and search are overlays.

The asset browser resolves selected model geometry through the neutral
`AssetRepository` contract and renders a small JavaFX preview. This is an
inspection aid, not a second scene renderer; the eventual map renderer keeps
its own gated `SceneRenderer` implementation.

Panels declare a preferred region, allowed regions, and minimum dimensions.
JavaFX renders these neutral contracts now; Dear ImGui may render them later.
The controlled JavaFX shell also keeps a persistent status row visible for
project/region context, revision, compatibility, editability, and saved/dirty
state. This is state feedback, not a second document model.

## Advancement gates

Work advances only when the current gate has executable evidence:

1. Safety: real fixture tests, semantic round trips, undo/redo coverage, and
   manual smoke checklist.
2. OSRS cache: OpenRune loading, map index, neutral definitions, output-cache
   encoding, and project identity.
3. Scene correctness: 52 shaped tiles, shared edges, four planes, bridges,
   locations, collision, instances, minimap, and region boundaries.
4. Editing core: all mutations through `EditorSession` and commands, grouped
   history, unified selection, and dirty-region updates.
5. Foundation completion: revision features, metadata, scene/world semantics,
   unified asset access, representative fixtures, and revision audits are
   complete before broad workflow extraction.
6. Workflow: terrain sculpting, object transforms, selection, collision tools,
   asset browsing, inspectors, validation/debug overlays, and controlled
   JavaFX workspaces are first-party plugins over the stable core.
7. Retirement: remove legacy product paths and make OpenRune the supported
   default only after the previous gates pass.

No renderer rewrite, Dear ImGui migration, or broad plugin extraction starts
before gates 1–5 pass.
