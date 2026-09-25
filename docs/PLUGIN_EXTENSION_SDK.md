# OpenRune Studio Plugin and Extension SDK

> **Status:** target public extension contract and implementation roadmap.
>
> This document defines how first-party and third-party extensions should add functionality to OpenRune Studio without bypassing the semantic API, undo system, renderer boundary, or workspace layout contract.
>
> See also:
>
> - `STUDIO_API_SYSTEM.md`
> - `STUDIO_SEMANTIC_API.md`
> - `UI_WORKSPACE_CONTRACT.md`

## 1. Core decision

OpenRune Studio should keep the **plugin/extension** concept as the packaging, lifecycle, dependency, and distribution unit.

**Installed extensions are first-class editor modules, not second-class contributors.** The host must not reserve a richer map-editing API for built-ins. A third-party extension may request and use the same supported semantic, editing, selection, preview, UI, project, automation, and renderer-neutral capabilities as a first-party tool.

The only boundaries are architectural and trust-related: extensions use supported host APIs rather than private implementation objects, and capabilities with security or destructive impact may require explicit declaration/authorization. These boundaries apply to first-party code too as it migrates onto the SDK.

A plugin is not synonymous with a panel.

A plugin may contribute any number of capabilities:

- map-editing tools;
- scene queries/extensions;
- commands;
- validators;
- generators;
- asset providers;
- inspectors;
- panels;
- context drawers;
- brush controls;
- quick palettes;
- HUDs;
- overlays;
- menus;
- shortcuts;
- settings;
- project/source integrations;
- semantic extension points.

The public API should therefore be an **extension SDK**, with plugins as the container. In product terms, an enabled extension is a peer module of the editor. The word *contribution* in this document refers only to a registration object such as a tool, panel, HUD, or command, never to a lesser permission tier.

The target relationship is:

```
EditorPlugin
   |
   +-- MapToolContribution
   +-- PanelContribution
   +-- InspectorContribution
   +-- HudContribution
   +-- SceneOverlayContribution
   +-- CommandContribution
   +-- ValidatorContribution
   +-- GeneratorContribution
   +-- AssetProviderContribution
   +-- ExtensionPoint<T>
```

---

## 2. Why the current system feels limited

The repository currently has two different levels of power.

### Neutral plugin layer

- `EditorPlugin`
- `PluginApi`
- `EditorTool`
- `EditorToolRegistration`
- `UiSurfaceContribution`
- `EditorPanelRegistration`
- `OverlayDraw`
- `OverlayComponent`

This layer is renderer/UI independent and is the correct public direction.

### Native Studio layer

- `StudioPlugin`
- `StudioToolPlugin`
- `StudioPanelContext`
- native brush/HUD/panel interfaces

This layer knows more about how a tool actually appears in Map Studio:

- button surfaces;
- brush UI mode;
- context drawer;
- owned right panel;
- floating UI;
- native viewport overlay;
- rail priority.

That difference is the real limitation.

The solution is **not** to expose `StudioToolPlugin` or Dear ImGui to third-party plugins.

The solution is to move the useful semantics of `StudioToolPlugin` into a frontend-neutral public descriptor, then have native Studio render/project it.

---

## 3. First-class map-tool extension

A map tool should be registered as one coherent extension descriptor rather than a collection of unrelated registrations. Built-in tools should be describable by this same descriptor.

Conceptual target:

```java
api.mapTool("terrain.biome")
    .label("Biome Painter")
    .category("Terrain")
    .group("terrain")
    .icon(Icon.material("forest"))
    .shortcut("B")
    .order(40)

    .capabilities(
        ToolCapability.TILE_TARGET,
        ToolCapability.BRUSH_FOOTPRINT,
        ToolCapability.BRUSH_FALLOFF,
        ToolCapability.PREVIEW,
        ToolCapability.WORLD_EDIT)

    .brushUi(BrushUiMode.SHARED_SETTINGS)

    .drawer(ctx -> ...)
    .quickPalette(ctx -> ...)
    .inspector(ctx -> ...)
    .hud(...)
    .overlay(...)

    .factory(BiomePainterTool::new)
    .register();
```

The exact Java API may change, but these semantics should remain.

### Required descriptor fields

A map-tool extension should define:

- globally stable contribution ID;
- label;
- category;
- logical tool group;
- icon;
- shortcut;
- deterministic order;
- engine/tool factory;
- tool capabilities;
- brush UI ownership;
- supported selection/target types;
- input/capture policy;
- context-drawer content;
- optional quick palette;
- optional inspector sections;
- optional HUDs;
- optional scene overlay/preview;
- optional settings;
- replacement/augmentation metadata.

---

## 4. Tool capabilities

Surface placement must derive from semantics, not from arbitrary plugin wishes.

Recommended capabilities:

### Targeting

- `TILE_TARGET`
- `OBJECT_TARGET`
- `VERTEX_TARGET`
- `AREA_TARGET`
- `PATH_TARGET`
- `FRAGMENT_TARGET`

### Brush behavior

- `BRUSH_FOOTPRINT`
- `BRUSH_FALLOFF`
- `BRUSH_STRENGTH`
- `STAMP`
- `SCATTER`

These can reuse/evolve the existing `BrushCapability` model.

### Editing

- `WORLD_READ`
- `WORLD_EDIT`
- `PREVIEWABLE_CHANGE_PLAN`
- `DIRECT_PROPERTY_EDIT`

### UI behavior

- `CONTEXT_DRAWER`
- `QUICK_PALETTE`
- `SELECTION_INSPECTOR`
- `HUD`
- `SCENE_OVERLAY`

### Input behavior

- `POINTER_CAPTURE`
- `SCROLL_INPUT`
- `KEY_INPUT`
- `CANCELABLE_INTERACTION`

Capabilities describe what a tool does. The host decides how that maps onto the current UI implementation.

---

## 5. Brush ownership

The three-state brush rule merged into Map Studio should become public SDK vocabulary.

### `NONE`

The tool does not use shared brush controls.

Examples:

- single select;
- object picker;
- ordinary path node placement.

### `SHARED_SETTINGS`

The tool uses the host's common brush system.

The host shows:

- Brush Rail;
- Brush Settings;
- common brush radius/shape/falloff/strength controls;
- any SDK-supported shared brush modifiers.

The plugin can still put content selection in its Context Drawer.

### `TOOL_OWNED`

The tool is brush-like but its brush mechanics are specialized enough that controls belong in its own drawer.

The shared Brush Rail/Settings stay hidden.

### Important rule

A tool button appearing on a certain rail does not imply brush ownership.

Brush behavior is explicitly declared in the tool descriptor.

---

## 6. Workspace placement rules

Plugins must use the existing OpenRune layout, not invent competing chrome.

### Primary Tool Rail

Contains activation entries for modal map-editing operations.

A plugin can contribute a tool here when it represents a real editing/targeting mode.

### Context Drawer

Contains broad tool-specific controls and catalogs.

Examples:

- material browser;
- object catalog;
- generator settings;
- path style;
- biome rules.

Only the active tool owns the drawer.

### Brush Rail + Brush Settings

Contain shared execution mechanics only when the active tool declares `SHARED_SETTINGS`.

### Viewport Quick Palette

Contains a small frequently switched working set.

It is not a second full tool editor.

### Inspector

Shows exact selected-state properties and direct semantic edits.

A tool may contribute selection-aware sections but does not replace the entire inspector.

### HUD

Contains glanceable persistent information.

HUD visibility remains independent of drawer/inspector visibility.

### Panels

Persistent utilities that are not inherently owned by the active tool may use standard dockable panel contributions.

---

## 7. Declarative public UI

Direct Dear ImGui access should remain internal.

Public plugin UI should be expressed through host-owned neutral components.

Recommended general component model:

```
UiNode
  Text
  Heading
  Button
  Toggle
  NumberField
  Slider
  TextField
  SearchField
  Select
  RadioGroup
  Swatch
  AssetPicker
  Thumbnail
  ThumbnailGrid
  List
  Table
  Tree
  Section
  Row
  Column
  Separator
  Progress
  Preview
  Tooltip
```

The existing `OverlayComponent` tree demonstrates that this model works for HUDs. The general UI tree should reuse the same principles while adding interactive controls.

### Why host-owned UI matters

The host can then guarantee:

- the Studio theme;
- DPI scaling;
- layout spacing;
- keyboard focus;
- tooltips;
- accessibility;
- state persistence;
- right-panel overflow rules;
- one-drawer ownership;
- brush rail behavior;
- future frontend portability.

### Escape hatch

If a future plugin genuinely needs custom interactive graphics, expose a host-owned `Canvas`/drawing callback with a stable 2D drawing API.

Do not make raw ImGui the escape hatch.

---

## 8. Icons

Public plugins should have two normal icon paths.

### Material semantic icon

Preferred.

Example conceptual form:

```java
.icon(Icon.material("brush"))
```

The host owns:

- glyph/font;
- size;
- active/inactive color;
- DPI scaling;
- fallback.

### Bundled PNG/resource icon

Used for custom identity/artwork.

Conceptual form:

```java
.icon(Icon.resource("icons/biome.png"))
```

The host loads bytes, caches the texture, and owns GPU lifetime.

A plugin should never receive or provide a raw OpenGL texture ID through the public API.

---

## 9. Tool input

The current neutral `EditorTool` contract is a good minimal start but map-tool plugins need a richer input vocabulary.

Target events should cover:

- pointer down;
- pointer up;
- pointer move;
- pointer drag;
- scroll;
- key down/up;
- modifiers;
- double click where useful;
- cancel/Escape;
- focus loss;
- viewport enter/leave;
- optional drag transaction boundaries.

The host should route input only to the active tool according to a declared input policy.

### Canonical hit context

Input events should be able to carry or cheaply query the canonical scene hit:

```
ToolInput
  screen position
  modifiers
  optional SurfaceHit
  current selection
  active plane
```

Tools should not reimplement renderer picking.

---

## 10. Map editing services

Third-party tools need enough edit power to build features Studio does not ship.

They should be able to edit all canonical map state through stable services.

Minimum required operations:

### Terrain

- read tile;
- set underlay;
- set overlay;
- set shape;
- set rotation;
- set flags;
- read/write corner/vertex heights;
- batch tile edits.

### Objects

- query objects;
- place;
- delete;
- move;
- rotate;
- replace;
- batch operations.

### Selection

- read and write semantic selection;
- select tiles/objects/areas/vertices;
- preserve stable identities.

### Change plans

Complex operations should produce a previewable transaction:

```
ChangePlan
  description
  affected region
  before/after facts
  validation issues
  preview geometry
  persistence impact
  commit()
```

Commit should become one undoable operation even if hundreds or thousands of tiles/objects are affected.

This is especially important for:

- WFC/generation;
- biome painters;
- road/path tools;
- building tools;
- mass replacement;
- terrain smoothing;
- procedural decorators.

---

## 11. Does a plugin need renderer support first?

Usually, **no**.

A plugin does not need a custom renderer implementation if it manipulates world properties Studio already understands.

For example, a community developer can build a new:

- tile painter;
- terrain sculptor;
- object scatter tool;
- collision painter;
- road/path generator;
- selection tool;
- procedural map fixer;

using semantic world/edit APIs. The normal renderer automatically displays the resulting canonical map state.

### Preview behavior

The plugin can draw temporary preview/diagnostic information through:

- tile fills/outlines;
- lines;
- circles;
- boxes;
- labels;
- model hulls;
- future ghost-model/mesh primitives.

### When deeper renderer support is required

Only when the plugin introduces a new visual concept that is not representable by canonical Studio world data.

Examples:

- custom non-OSRS helper geometry;
- temporary generated mesh previews beyond current overlay primitives;
- custom post-processing;
- wholly new persistent extension-owned scene entities.

For these cases, add a **neutral render contribution** API before exposing raw OpenGL.

---

## 12. Render extension model

Recommended future layers:

### Level 1: overlay primitives

Existing `OverlayDraw`.

Safe for most tooling.

### Level 2: semantic preview primitives

Add host-owned concepts such as:

- ghost object;
- model instance;
- temporary terrain patch;
- gizmo;
- polyline/ribbon;
- mesh preview;
- host texture/material handle.

This should cover most advanced map-editor plugins.

### Level 3: renderer extension

For trusted/advanced plugins only, if required.

It should still be a versioned renderer abstraction rather than direct OpenGL state.

### Level 4: native renderer access

Not part of the normal public SDK.

If ever supported, treat it as privileged, unstable, renderer-specific, and separately versioned.

---

## 13. Replacing or changing built-in tools

Plugins should be able to provide alternatives to built-ins without monkey-patching internal classes.

Recommended mechanisms:

### Alternative implementation

A plugin tool may declare the same logical group:

```
group = "terrain.tile-painter"
```

The host can let the user choose the default implementation.

### Explicit replacement

A plugin may declare:

```
replaces("openrune.tool.tile-painter")
```

Replacement should require:

- explicit metadata;
- compatibility check;
- user-visible confirmation/selection;
- safe fallback to the built-in implementation.

### Augmentation

A plugin may add:

- extra inspector sections;
- asset sources;
- brush types;
- validators;
- quick-palette actions;
- commands;

without replacing the tool.

### Never allow implicit interception

A plugin should not silently hijack another tool by registering the same ID or listening to raw input ahead of it.

---

## 14. Extension points between plugins

The current typed `ExtensionPoint<T>` registry is a useful foundation.

Public extension points should be:

- typed;
- versioned;
- named by the defining plugin/host;
- ordered;
- lifecycle-safe;
- discoverable.

A plugin should publish an extension contract only when another plugin genuinely needs to integrate with it.

Do not use arbitrary global service locators.

---

## 15. Lifecycle and cleanup

Every public registration must have an explicit lifetime.

Target rule:

**Every registration returns a tracked handle.**

Examples:

```
RegistrationHandle handle = api.mapTool(...).register();
api.track(handle);
```

The fluent API should normally track automatically.

This applies to:

- tools;
- panels;
- UI surfaces;
- menus;
- commands;
- shortcuts;
- events;
- overlays;
- HUDs;
- inspectors;
- validators;
- asset providers;
- generators;
- extension points.

This prevents contributions created after plugin initialization from leaking across disable/reload.

---

## 16. ID namespacing

Plugin-facing APIs should automatically scope local IDs.

If plugin ID is:

```
com.example.biomes
```

then:

```
api.mapTool("paint")
```

should resolve internally to something equivalent to:

```
com.example.biomes:paint
```

The plugin can still reference its contribution by local ID through its own API object.

Global IDs should be reserved for explicit host extension points and documented interoperability contracts.

The same rule should apply to:

- settings;
- tasks;
- debounce keys;
- tools;
- panels;
- commands;
- menus;
- HUDs;
- events where named;
- persisted plugin state.

---

## 17. Capabilities, permissions, and trust

The current `PluginPermission` vocabulary is useful, but it must be treated correctly. It must **not** become an artificial feature tier where built-ins are powerful and external extensions are intentionally weaker.

An extension may request the complete supported capability set. The host may require explicit user authorization for sensitive capabilities, but once authorized the extension receives the same service contract a first-party module would receive for that capability.

### Studio capability enforcement

Capabilities exist to make authority explicit, auditable, and safe to unload. They are not a "community plugin mode."


The host should enforce declared capabilities when a plugin asks for Studio services.

Examples:

- no `WORLD_READ` -> no authored-world/scene query service;
- no `WORLD_EDIT` -> edit/change-plan service rejects access;
- no `ASSET_READ` -> definition/model access denied;
- no `PROJECT_WRITE` -> connected-project write service unavailable;
- no `LIVE_CLIENT_CONTROL` -> simulation/live-control mutations unavailable.

### Not a Java sandbox

These permissions cannot by themselves stop malicious Java bytecode from using JDK APIs.

A normal in-process JAR can potentially access:

- filesystem;
- network;
- environment;
- reflection and other JVM facilities subject to platform restrictions.

Therefore Plugin Hub UX must not claim that permission denial makes arbitrary Java plugins safe.

For stronger isolation, a future untrusted-extension runtime would need a separate process, WASM, or another real sandbox boundary.

---

## 18. External classloader boundary

The current external runtime already isolates plugin JARs, but the host package boundary is too broad.

Target parent-first/shared packages should be explicit, for example:

- `java.*`;
- selected logging API;
- `com.rspsi.api.*`;
- the published plugin SDK package.

External plugins should not be able to treat all `com.rspsi.*` internals as supported dependencies.

A plugin that imports a host-internal package should fail early with a clear compatibility error.

---

## 19. Versioning

A single exact integer API version is too restrictive for a growing SDK.

Recommended model:

```
SDK major.minor
```

Rules:

- major change may break source/binary compatibility;
- minor change is additive/backward compatible;
- patch is implementation/documentation fix.

A plugin manifest should declare:

- minimum supported SDK;
- optionally maximum tested major;
- required capabilities and their minimum versions.

Example conceptual metadata:

```json
{
  "studioApi": {
    "min": "1.4",
    "major": 1
  },
  "capabilities": {
    "map-tool-ui": 2,
    "scene-query": 1
  }
}
```

This allows the SDK to grow without invalidating every plugin on every additive release.

---

## 20. Threading contract

The SDK must make thread ownership explicit.

Recommended rules:

### Editor thread

Required for:

- selection mutation;
- change-plan commit;
- settings callbacks that affect active editor state;
- tool lifecycle/input;
- contribution registration that mutates live frontend state.

### Background executor

Appropriate for:

- scanning;
- indexing;
- procedural planning;
- cache-independent analysis;
- network work when permitted;
- expensive immutable computations.

### Immutable handoff

Background work should consume immutable snapshots and return immutable results.

The host should provide:

```
EditorThread
  isEditorThread()
  invokeLater(...)
  invokeAndWait(...)
```

Plugins should not create unmanaged thread pools for routine work.

---

## 21. State and persistence

Plugin state needs clear ownership.

### Host-managed settings

Use typed settings for user-configurable values.

### Workspace state

The host owns:

- drawer open/collapsed state;
- HUD position/opacity/visibility;
- panel placement;
- selected tool implementation;
- quick-palette placement;
- rail sizing.

### Plugin document/project data

If plugins need persistent custom project data, add an explicit namespaced storage API.

Do not encourage plugins to scatter arbitrary files into the project tree.

### Extension-owned map metadata

If custom metadata must travel with an edited map/project, define a versioned extension-data container rather than adding ad-hoc fields to core map classes.

---

## 22. Public SDK testing kit

Before the SDK is called stable, ship a test kit.

It should provide:

- in-memory world fixture;
- fake semantic scene;
- fake assets;
- plugin host;
- input simulator;
- command/undo assertions;
- UI contribution snapshot assertions;
- permission tests;
- unload/leak checks.

A reference plugin should demonstrate a complete custom map tool.

Recommended sample:

**Biome Painter**

It should show:

- new map tool registration;
- Material icon;
- shared brush controls;
- custom Context Drawer;
- asset/preset selection;
- tile/area targeting;
- overlay preview;
- HUD telemetry;
- inspector section;
- batched ChangePlan;
- undo/redo;
- settings;
- clean unload.

If that plugin can be built outside the Studio repository without importing internal classes, the SDK is approaching the right level of power.

---

## 23. Migration from `StudioToolPlugin`

`StudioToolPlugin` should remain an internal compatibility projection only while the shared descriptor is built. The migration is complete only when first-party tools no longer require a privileged native-only tool contract for ordinary editing features.

Migration sequence:

1. define neutral `MapToolContribution` / `ToolUiDescriptor`;
2. write a native adapter that renders it into the existing Studio rails/drawer/HUD/inspector;
3. convert one simple built-in tool;
4. convert Tile Painter;
5. convert Height Sculptor;
6. convert Path/Object Placement;
7. ensure all built-ins still satisfy the same layout contract;
8. remove behavior from `StudioToolPlugin` until it becomes a thin compatibility shim;
9. eventually retire it if no longer needed.

The SDK is the editor tool API. First-party and third-party map tools should not have separate capability ceilings.

---

## 24. Priority implementation backlog

### P0: required before claiming a powerful public map-tool API

- neutral map-tool descriptor;
- public brush UI mode;
- public tool capabilities;
- general declarative interactive UI;
- context drawer projection;
- quick palette projection;
- inspector section projection;
- HUD integration;
- semantic selection;
- canonical `SurfaceHit`/scene query;
- batch edit/change-plan API;
- lifecycle handles for every contribution.

### P1: required before stable public SDK

- permission/capability enforcement;
- editor-thread service;
- automatic ID namespacing;
- public API artifacts/packages;
- narrow classloader boundary;
- major/minor compatibility model;
- contract test kit;
- external sample plugin;
- built-ins dogfooding the same map-tool API.

### P2: advanced extension power

- semantic preview meshes/model instances;
- extension project storage;
- custom asset catalogs;
- tool replacement/alternative implementation UX;
- richer inter-plugin extension points;
- trusted renderer-extension API if real use cases require it.

### P3: Plugin Hub hardening

- provenance/signing policy;
- permission disclosure UI;
- hashes and reproducible artifact metadata;
- compatibility matrix;
- review/reputation policy;
- rollback;
- optional stronger sandbox strategy.

---

## 25. Acceptance criteria

The public extension SDK is successful when a developer outside the repository can create a JAR that:

1. loads through the managed plugin runtime;
2. uses only published API/SDK artifacts;
3. contributes a completely new modal map tool;
4. appears in the correct Primary Tool Rail position;
5. receives canonical input/hit data;
6. uses shared Brush Settings only when declared;
7. owns one Context Drawer without bypassing the shell;
8. contributes a Quick Palette, HUD, and Inspector section when useful;
9. queries semantic tiles/objects/models without renderer internals;
10. performs a large edit as one validated, previewable, undoable operation;
11. receives automatic scene refresh/persistence behavior;
12. unloads without leaked tools, UI, tasks, subscriptions, or state;
13. survives a compatible Studio upgrade without recompilation where binary compatibility permits;
14. never needs Dear ImGui, GLFW, OpenGL, FileStore internals, or native Studio classes for ordinary map editing;
15. can request every supported map-editor capability that an equivalent first-party tool can request;
16. can replace, augment, or provide an alternative implementation for a built-in workflow through explicit host-supported contracts rather than monkey-patching internals.

That is the bar for a first-class extension system in Map Studio.
