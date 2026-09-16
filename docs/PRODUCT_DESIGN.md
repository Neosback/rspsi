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

Panels declare a preferred region, allowed regions, and minimum dimensions.
JavaFX renders these neutral contracts now; Dear ImGui may render them later.

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
5. Workflow: terrain sculpting, object transforms, selection, collision tools,
   asset browsing, inspectors, and controlled JavaFX workspaces.
6. Retirement: remove legacy product paths and make OpenRune the supported
   default only after the previous gates pass.

No renderer rewrite or Dear ImGui migration starts before gates 1–4 pass.
