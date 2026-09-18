# OpenRune Studio UI Foundation

Status: **originally implemented in the JavaFX shell; that shell is now the
transitional/reference surface.** Per `docs/ROADMAP.md` (the authoritative
execution plan), the native GLFW + OpenGL 3.3 + Dear ImGui shell
(`com.rspsi.studio.*`) is the production frontend and JavaFX is scheduled for
removal in `ROADMAP.md` Phase 9. The shell composition, workflow model, and
theme intent recorded below remain the design target — they should be
projected onto the native ImGui shell, not redefined, per
`docs/IMGUI_ADAPTER.md`.

This document defines the production UI shell contract for OpenRune Studio.
It is intentionally a shell contract, not a second editor architecture. Both
the JavaFX and native ImGui frontends project the same neutral session, scene
snapshot, selection, commands, asset repository, and plugin contributions that
already belong to Studio core.

## Locked decisions

- The native GLFW + Dear ImGui shell is the production frontend; JavaFX 21
  remains available only as a transitional/reference surface until
  `ROADMAP.md` Phase 9 removes it.
- AtlantaFX Primer Dark is the JavaFX reference theme; the native shell uses
  its own Dear ImGui style pass implementing the same dark
  viewport/panel-separation and accent intent described below (see "Theme and
  icon rules").
- Ikonli Material Design is the icon source for JavaFX shell controls; the
  native shell uses its own icon/glyph approach for the same semantic set.
- ControlsFX remains available for mature JavaFX utility controls.
- DockFX and MaterialFX are not added to the foundation. Studio uses a small,
  constrained docking model (ImGui docking, multi-viewport disabled) that is
  easier to test and keep consistent, matching this document's "controlled
  layout behavior" below.
- The legacy JavaFX viewport (`EmbeddedOpenGlViewport`) is compatibility-only
  and not the production scene renderer; the native FBO viewport
  (`NativeSceneViewport`) is.

AtlantaFX is used as a CSS-first JavaFX theme layer, Ikonli supplies JavaFX
icon nodes, and ControlsFX remains an optional utility-control library. Their
upstream references and license decisions are recorded in
[`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md).

## Shell composition

The controlled shell has five stable hosts:

```text
application/menu + workspace tabs
    ├── tool rail
    ├── context toolbar
    ├── viewport host
    ├── active right category panel
    ├── utility drawer
    └── status bar
```

`ControlledWorkspaceShell` owns placement and frontend lifecycle. It does not
own world state, history, cache access, scene truth, or plugin-owned business
logic. `ControlledWorkspaceBridge` supplies the current compatibility panels:
the legacy viewport, canonical tool rail, asset browser, inspector, history,
validation, console, and plugin command surfaces.

The viewport remains the dominant area. The left tool rail and composite right
sidebar are fixed-width; neither exposes a nested resize divider. The right
category rail is immediately beside the viewport and its active panel uses the
full sidebar height. The context toolbar is above the viewport, the existing
FXML brush/height controls are rehosted below it, and the selector strip stays
persistent below the active tool panel.

The Map Editor uses a Displee-inspired right-side composition:

```text
tool rail | viewport | category rail | category panel
```

The left rail is intentionally compact and width-constrained because it is a
tool switcher, not a settings form. The right category rail is also compact;
its categories select the adjacent panel for General map settings, Rendering,
Terrain, Objects, Collision/Diagnostics, World Outliner, and Inspector. The
panel contents remain JavaFX projections over neutral state and presentation
settings; the category rail does not create another editor model.

The complete visibility and settings catalog is maintained in
[`VIEWPORT_SETTINGS.md`](VIEWPORT_SETTINGS.md). It deliberately separates
visibility, selection, editability, and locks, and keeps the always-visible
toolbar small while advanced/debug controls live in category panels and
presets.

## Workspaces

`WorkspaceCatalog` is the neutral registration point. The initial catalog
registers only `map` as a real workspace, displayed as **Map Editor**. Future
Asset, Interface, Model, Cutscene, and Build/Validation workspaces must be
registered through the same catalog and may only appear when their
contribution is available. The shell must not advertise unfinished editors.

Terrain, height, object, collision, validation, and debug actions are Map
Editor tools or panels. They are not separate top-level applications.

Every workspace uses the shared project/session lifecycle, command history,
selection model, asset repository, plugin registry, and scene contracts. A
workspace may provide specialized controls, but it may not create a second
world model, cache loader, history stack, or renderer scene graph.

## Controlled layout behavior

The first production layout intentionally supports a predictable subset of
Blender-style personalization:

- resize detachable utility windows within minimum bounds;
- reorder bottom utility panels as tabs;
- hide and restore optional panels from View;
- detach a utility panel into a floating JavaFX window;
- redock a detached panel;
- reset the active workspace layout;
- remember layout state per workspace in the user configuration directory.

Arbitrary nested docking, unlimited splits, and unrestricted panel placement
are deferred. The constraint is deliberate: it keeps keyboard navigation,
small-window behavior, migration, and automated acceptance tractable while
still letting users arrange the tools they use most.

`WorkspaceLayout`, `PanelLayoutState`, and `WindowBounds` are UI-only records.
`JsonWorkspaceLayoutStore` writes versioned JSON below `~/.rspsi/ui/` using an
atomic best-effort replacement. Corrupt or stale preferences are ignored and
the workspace returns to defaults. Layout state never enters `WorldDocument`,
project source, cache output, or autosave data.

## Map Editor interaction model

The Map Editor rail exposes five primary workflows:

1. Select / Transform
2. Terrain Painter
3. Height Sculptor
4. Object Placer
5. World Fragment / Stamp

The context area rehosts the current FXML controls and their `Options`
bindings instead of creating a second brush, height, object-filter, or Z
position state. Additional legacy subtools remain behind the collapsed
Subtools and flags section. The selector strip forwards to the existing
selection/tool controls. The Floor Palette utility tab owns the existing
swatch/shape surface; it is not duplicated in the context panel.

Before a map is loaded, the viewport shows an actionable quick-launch card for
the existing local-cache, coordinate/region, blank-map, and OSRS-project
workflows. It is replaced when the existing client map-ready event fires.

The visible menu bar is a Studio menu projection over the existing FXML
`MenuItem` command targets. Persistent settings live in category panels;
one-shot map operations remain in the Map menu. Existing listeners are
preserved without exposing the old duplicate menu bar.

The World Outliner is a navigation and visibility projection for loaded
regions, planes, terrain, objects, and diagnostics. The inspector is the
selection-aware property surface. Neither panel opens FileStore or caches
its own world state.

`Ctrl+Space` toggles the utility drawer. It starts collapsed when no saved
layout exists so an empty utility area cannot compete with the viewport. Scene-
level shortcuts continue to be translated through the neutral input path, with
focused text fields retaining their normal editing precedence.

## Theme and icon rules

New shell code uses semantic tokens from `workspace.css` and does not add
per-FXML `modena_dark.css` imports. The theme layer provides:

- dark viewport/panel separation;
- OpenRune navy/blue accents;
- visible focus rings;
- success, warning, error, and read-only states;
- compact tree/table spacing;
- normal UI typography plus monospace coordinates, IDs, revisions, and
  diagnostics.

New icon-only controls use `StudioIcon` and `StudioIconFactory`. Each one must
have a tooltip, accessible text, visible focus state, and a nearby text label
or command-palette equivalent. Raw FontAwesome, arbitrary inline SVG, and new
JFoenix controls are compatibility-only choices during migration.

## Plugin and frontend projection

Plugins continue to register neutral contribution metadata: tools, context
settings, inspectors, asset providers, overlays, commands, menus, status
values, and workspaces. JavaFX maps those stable IDs to Nodes. Dear ImGui will
map the same IDs to ImGui panels later. Plugin unload must remove all mounted
contributions and tracked resources from either frontend.

The shell may provide a JavaFX-only `JavaFxPanelFactory`, but JavaFX types must
not enter `Client` editor-core packages. Layout persistence is also frontend
state; it is not a plugin substitute or an excuse for plugins to own the
application frame.

## Startup dashboard

The startup route is documented in
[`STUDIO_DASHBOARD.md`](STUDIO_DASHBOARD.md). `StudioDashboard` is a
JavaFX-only navigation surface with workspace and settings cards; it
deliberately does not own cache loading, sessions, history, or renderer state.
The launcher now shows the dashboard first, persists the selected cache through
the existing settings store, and opens Map Editor with that cache directly.
An optional region field supports direct debug launches; an empty field keeps
the editor's normal actionable empty state. Legacy cache/plugin controls remain
reachable from the dashboard's content settings card, not as a second startup
gate.

## Acceptance gates

The UI foundation is complete when:

- the Map Editor opens with a dominant usable viewport;
- tools, context settings, selection, outliner, inspector, status, and
  utility panels observe the same session;
- layouts save/load/reset and recover from invalid versions;
- panels can hide, resize, detach, and redock without leaking nodes;
- plugin contributions mount and unload cleanly;
- icon controls expose labels, tooltips, and accessible descriptions;
- JavaFX and the native ImGui projection produce identical command,
  selection, history, dirty-state, and plugin-lifecycle results;
- macOS, Windows, and Linux packaged runtimes load fonts/icons, detached or
  docked windows, and restored layout state for whichever frontend they run
  (AtlantaFX/Ikonli for the JavaFX reference surface, the native style pass
  for the ImGui shell).

The remaining interactive gate is deliberately separate from the foundation
compile gate: the legacy viewport must pass open/edit/undo/save/reopen smoke
coverage in the controlled shell before legacy panels are retired.

JavaFX can support future floating HUDs through undecorated transparent
`Stage`/`PopupControl` windows. That capability remains deferred; the current
shell intentionally uses the fixed Displee-style composition.
