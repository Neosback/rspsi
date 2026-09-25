# AI Architecture Overview

> **Audience:** coding agents and contributors who need a reliable mental model before changing OpenRune Studio.
>
> **Read order:** this document first, then `AI_CHANGE_PLAYBOOK.md` for implementation steps,
> `EDITOR_DEVELOPMENT_ARCHITECTURE.md` for internal code-placement rules,
> `UI_WORKSPACE_CONTRACT.md` for shell/layout rules, and the domain-specific docs linked below.

# 1. What OpenRune Studio is

OpenRune Studio is a Java/Kotlin modular-monolith desktop application for OSRS map and content authoring.

It has:

- a headless/core `Client` module;
- a native Dear ImGui + GLFW + OpenGL `Editor` module;
- a Studio-owned authored world model;
- undoable command-based editing;
- OpenRune FileStore as the modern OSRS cache backend;
- built-in OpenRune Server project/content integration;
- renderer-neutral scene compilation plus a native OpenGL renderer;
- a public external extension API.

It is **not**:

- a RuneLite plugin;
- an OpenRune Server plugin;
- a collection of internal plugins;
- a multi-backend modern-OSRS cache editor;
- a UI where every panel can live everywhere.

# 2. The top-level mental model

```
                    OpenRune Studio
                           |
               +-----------+-----------+
               |                       |
             Client                  Editor
       semantics / authoring     native presentation
               |                       |
               +-----------+-----------+
                           |
                     Studio runtime
                           |
         +-----------------+-----------------+
         |                                   |
 CoreEditorModules                    external EditorPlugins
 always installed                    optional extensions
         |                                   |
         +-----------------+-----------------+
                           |
            one registry + one service graph
                           |
                    EditorSession
                           |
             WorldDocument / Selection
                           |
              undoable EditorCommands
```

The most important architectural rule is:

**There should be one canonical production path for each responsibility.**

Do not create another manager, registry, decoder, selection model, render-settings object, tool
registration path, or project-integration graph because the existing name feels plugin-specific or
because a caller is inconvenient.

# 3. Modules and ownership

## Client

`Client` owns behavior and semantics that must be testable without a window or OpenGL context.

It owns:

- cache adapters and OSRS cache semantics;
- definitions and neutral assets;
- the authored world model;
- region decode/encode;
- commands and undo/redo;
- selections;
- tools;
- brushes;
- generator/change-plan foundations;
- semantic scene/query APIs;
- render-neutral packets/compiler inputs;
- settings keys/registry/compiler contracts;
- core editor modules;
- external extension runtime;
- server/project semantic integrations.

`Client` must not depend on Dear ImGui, GLFW, or native OpenGL UI state.

## Editor

`Editor` owns native presentation.

It owns:

- Studio application lifecycle;
- GLFW/ImGui windows;
- native OpenGL scene rendering;
- viewport camera/input projection;
- right sidebar;
- bottom tool rail and Context Drawer;
- left Brush Rail;
- floating picker rail / Quick Palette;
- HUD projection;
- native compatibility projections for neutral tool/UI descriptors.

Business rules should move toward Client-neutral contracts rather than being implemented only here.

# 4. Application composition

OpenRune Studio is a modular monolith.

## Core features

Core features are `CoreEditorModule`s listed exactly once in `CoreEditorModules`.

Current core modules cover:

- terrain;
- Tile Painter;
- paths;
- objects;
- selection/transforms;
- diagnostics;
- core UI declarations.

Core modules are:

- compile-time;
- always installed;
- recreated for each runtime host;
- not shown as enable/disable plugins;
- registered into the same neutral registry/services external extensions use.

## External extensions

External installable JARs use `EditorPlugin`.

The plugin lifecycle exists for:

- discovery;
- dependency/version checks;
- enable/disable;
- reload/unload;
- cleanup;
- externally supplied behavior.

Do not use `EditorPlugin` as dependency injection for features that ship with Studio.

# 5. Authored-world flow

The authoritative authoring flow is:

```
input/tool
   |
ToolContext / SurfaceHit / Selection
   |
edit service or EditorCommand
   |
EditorSession.execute(...)
   |
WorldDocument
   |
history + change notification
   |
scene recompilation / dirty region
   |
render packet
   |
native renderer
```

Rules:

- tools do not mutate native renderer state to edit the map;
- map changes are represented in the authored model;
- logical user actions should become one undoable transaction;
- rendering is a projection of authored/resolved state;
- selection is canonical state, not a widget-local copy.

# 6. Coordinates

There are two important coordinate spaces.

## Local/document space

Used by:

- `WorldDocument`;
- local tiles;
- many render packet/model builders.

## Absolute world-tile space

Used by:

- viewport camera;
- world picking;
- `WorldTile`;
- cross-region semantics;
- external/editor-facing map identity.

Never assume local and world coordinates are interchangeable. Convert through the canonical
document-coordinate APIs.

# 7. Cache architecture

## Modern OSRS

**OpenRune FileStore is the single production backend.**

```
OSRS cache/project
      |
OpenRune FileStore
      |
OpenRuneCacheStore
      |
Studio neutral CacheStore / DefinitionProvider / AssetRepository
      |
World/session/tooling
```

FileStore owns:

- filesystem/archive access;
- modern OSRS cache structures;
- OpenRune definition codecs/types;
- writable standalone output through `CacheDelegate`;
- FreshCache/reference-cache acquisition;
- OpenRune ecosystem cache semantics.

## Studio-owned map codec

`OsrsRegionDecoder` and `OsrsRegionEncoder` are the one Studio semantic terrain/location
round-trip codec.

They translate FileStore-provided bytes into the mutable Studio world model and back.

Do not add another map decoder/encoder.

## Texture exception

`OpenRuneTextureDefinitionDecoder` is an explicit adapter-boundary exception for the current
pinned FileStore behavior where modern compact texture records are skipped by the generic
definition decoder.

It is not a second cache stack.

## Displee

Displee is legacy/custom-cache compatibility only.

Do not use it for:

- modern OSRS projects;
- modern OSRS output;
- OpenRune Server projects;
- fallback decoding because FileStore integration is inconvenient.

## OpenRS2

OpenRS2 is reference/acquisition/research infrastructure, not a Studio production backend.

Use it indirectly where appropriate for:

- historical cache acquisition;
- historical XTEAs;
- revision/reference research;
- independent verification.

Do not create an OpenRS2 Studio cache backend unless the architecture is deliberately revised.

# 8. OpenRune Server integration

OpenRune Server support is built-in product integration.

```
StudioApplication
      |
ServerIntegrationService
      |
OpenRuneServerProvider
      |
OpenRuneServerAdapter
      |
project inspection / cache roles / build tasks / source semantics
      |
symbols + references + spawns + overlays + Kotlin semantic index
```

Implementation namespace:

`com.rspsi.server.openrune`

It is not an `EditorPlugin`.

Connected project rules:

- project source is authoritative for publishable authored resources;
- LIVE and SERVER caches are generated/read products, not generic writable targets;
- Studio should invoke the project's canonical build path rather than patching generated caches;
- resources without a lossless source mapping stay read-only in connected-project mode.

# 9. Rendering architecture

Rendering has an explicit boundary.

```
WorldDocument / definitions
      |
scene resolution
      |
render-neutral packets
      |
RenderConfig + presentation
      |
native renderer
      |
OpenGL
```

The renderer does not own authored truth.

## Renderer settings

One renderer setting flow exists:

```
UI
 |
typed RenderSettingKeys
 |
SettingsStore
 |
RenderConfigCompiler
 |
immutable RenderConfig
 |
viewport / renderer
```

Do not add ad-hoc renderer booleans beside this path.

A real renderer setting requires:

- a typed key;
- registration/metadata;
- a declared consumer;
- compiler wiring;
- native/semantic consumption;
- a test proving the destination changes.

Disabled/unavailable settings should explain why. Do not leave clickable controls that do nothing.

# 10. Workspace design

The layout expresses workflow, not arbitrary docking preference.

## Right rail: inspect / understand / edit exact data

Right-side surfaces include:

- Tile Inspector;
- WorldMap;
- Object Viewer / Properties;
- Outliner;
- World Knowledge;
- Player State;
- Map Settings;
- future theme/context intelligence.

The right side can edit the exact inspected semantic object/data.

A future action may send an inspected asset/preset into an authoring tool, but the right rail does
not become a second placement toolbar.

## Left rail: shared brush mechanics

The left rail is the contextual Brush Rail.

It is normally visible only when the active tool declares shared brush settings.

It may be forced visible by the user.

Use it for common brush mechanics such as:

- footprint/shape;
- radius;
- falloff;
- strength;
- shared brush selection.

If a tool declares `TOOL_OWNED` brush UI, the shared rail/settings stay hidden.

## Bottom: authoring

The bottom contains:

- Primary Tool Rail;
- one active Context Drawer.

Examples:

- Tile Painter;
- Height Sculptor;
- Path/road/shoreline generation;
- Object placement/spawn;
- future building/fragment/biome/generator tools.

The bottom is not a generic console. History, notifications, diagnostics, and arbitrary utility
panels do not get bottom drawer modes.

## Floating rail / Quick Palette: pick and switch quickly

Use the floating rail for:

- single select;
- multi select;
- picker/inspection modes;
- compact near-cursor choices.

Do not duplicate the whole bottom authoring catalog here.

## HUDs

HUDs are glanceable viewport information and are independent of the drawer.

# 11. Tool architecture

A tool has two separate concerns:

## Behavior

Neutral behavior lives in Client:

- `EditorTool`;
- `ToolContext`;
- `SurfaceHit`;
- selection;
- brushes;
- commands/change plans.

## Presentation

A tool descriptor says how Studio should project it:

- label/category/group;
- icon/shortcut/order;
- tool capabilities;
- brush UI ownership;
- Context Drawer;
- Quick Palette;
- Inspector;
- HUD/overlay.

Built-ins and external extensions should converge on the same neutral descriptor semantics.

`StudioPlugin` / `StudioToolPlugin` are transitional native compatibility projections, not a
second business-logic architecture.

# 12. Inspection-to-authoring flow

The intended user flow is:

```
inspect map/object/tile on right
        |
understand/edit exact semantic data
        |
optional "use/place/send to tool" action
        |
bottom authoring tool receives asset/preset
        |
brush/tool preview in viewport
        |
commit undoable map change
```

This is the preferred way to connect inspection and placement. Do not make right-side inspectors
silently mutate placement-tool state through hidden globals.

# 13. Source of truth by concern

| Concern | Source of truth |
| --- | --- |
| core composition | `CoreEditorModules` |
| runtime registry/services | `EditorPluginHost` + `EditorPluginRegistry` + `PluginServices` |
| authored state | `EditorSession` / `WorldDocument` |
| mutation | `EditorCommand` / command services / future ChangePlan |
| selection | `SelectionModel` |
| pointer targeting | `SurfaceHit` |
| brush semantics | `editor.brush` |
| modern OSRS cache | OpenRune FileStore via `OpenRuneCacheStore` |
| terrain/location roundtrip | `OsrsRegionDecoder/Encoder` |
| OpenRune project semantics | `OpenRuneServerAdapter` + core provider/services |
| renderer settings | `RenderSettingKeys -> RenderConfigCompiler -> RenderConfig` |
| render parity backlog | `RENDERING_PARITY_MANIFEST.json` |
| native shell placement | `UI_WORKSPACE_CONTRACT.md` |
| internal code placement | `EDITOR_DEVELOPMENT_ARCHITECTURE.md` |
| external extension contract | `PLUGIN_EXTENSION_SDK.md` |

# 14. Things an AI must not create casually

Before creating any of these, search for the existing canonical concept first:

- Manager
- Registry
- Service
- Controller
- Context
- Store
- Cache backend
- Decoder/encoder
- Render settings object
- Selection model
- Tool registry
- Brush registry
- Plugin host
- Project inspection model

A new abstraction is justified only when the existing canonical responsibility genuinely cannot own
the behavior.

# 15. Architecture invariants

The repository should continue adding automated tests for these invariants:

- one production registration per stable ID;
- no built-in plugin namespace;
- no modern OSRS Displee path;
- no plugin-named OpenRune Server path;
- right inspection panels stay right-only;
- authoring palettes stay bottom-only;
- bottom drawer has no generic console modes;
- floating rail has no fallback duplicate tool registry;
- renderer settings have registered consumers;
- renderer settings compile into `RenderConfig`;
- native UI does not enter Client;
- raw FileStore/Displee types remain inside adapter boundaries.

When an architecture bug is fixed, prefer adding a boundary/inventory test so the repository
remembers the decision without relying on conversation history.
