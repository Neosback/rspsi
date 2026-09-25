# UI Workspace Contract

> **Status:** authoritative post-project workspace layout.

## 1. Product model

The main editor shell is a **Contextual Multi-Rail Workspace**.

The goal is predictable placement with progressive disclosure. The user should not have to hunt through arbitrary floating windows to find the controls for the active operation.

## 2. Canonical layout

    +---------------------------------------------------------------+
    | project tabs / workspace tabs                                |
    +---------------------------------------------------------------+
    | Brush Shelf |              Viewport             | Inspector   |
    | when needed |                                   | right rail  |
    |             |          movable HUD layer         |             |
    |             |                                   |             |
    +---------------------------------------------------------------+
    |                 Context Drawer                               |
    +---------------------------------------------------------------+
    |                 Primary Tool Rail                            |
    +---------------------------------------------------------------+

The viewport should regain space when a contextual surface is not needed.

## 3. Primary Tool Rail

The bottom horizontal rail chooses the active editing mode.

Examples:

- select;
- multi-select;
- terrain sculpt;
- tile paint;
- object placement;
- path/road tools;
- future structure/procedural tools.

Rules:

- one active primary editing mode;
- icon + tooltip;
- shortcut where useful;
- visible active state;
- no unrelated settings in the rail itself.

The rail is navigation between tool modes, not a dumping ground for commands.

## 4. Context Drawer

The drawer directly above the Primary Tool Rail contains workflow-specific controls for the active tool.

Examples:

- tile material/palette;
- selected object/catalog;
- path parameters;
- replacement query;
- generator preview controls.

Rules:

- only active-tool content is shown;
- a tool should not open multiple competing bottom panels;
- shared brush mechanics do not belong here when Brush Shelf already owns them;
- the drawer may collapse when a tool needs no extended controls.

## 5. Brush Shelf

The left Brush Shelf exists only when the active operation declares shared brush capability.

It owns shared mechanics such as:

- shape;
- radius/size;
- falloff;
- strength;
- spacing;
- stamp dynamics where shared.

If the Path tool currently does not use brush mechanics, selecting Path must not open Brush Settings.

A tool-specific custom palette stays in its Context Drawer unless it is genuinely a shared brush mechanic.

## 6. Brush Settings docking

Brush Settings may be:

- docked as a narrow panel beside the Brush Shelf;
- detached and moved as a floating panel;
- re-docked;
- hidden automatically when the active tool has no brush capability.

Detaching must return its docked width to the viewport.

The same settings state backs both docked and floating presentation. Do not maintain duplicate brush settings models.

## 7. Viewport Quick Palette

The Viewport Quick Palette is a compact floating picker for values that benefit from near-cursor switching.

Examples:

- armed material;
- object rotation;
- active plane;
- small variant choice.

It may mirror the armed value shown in the Context Drawer, but it must not become a second full configuration panel.

## 8. Right Inspector

The right side is for exact understanding and property work.

It owns:

- selection details;
- tile/object properties;
- exact numeric edits;
- map/render settings;
- validation/diagnostics;
- content relationships;
- source/provenance information.

Direct edits route through commands/change plans.

The inspector does not mutate the renderer or cache directly.

## 9. HUD layer

HUDs are compact, glanceable viewport information.

Examples:

- current tile/world coordinate;
- sampled height;
- selected object identity;
- active brush summary;
- render/plane diagnostics;
- performance counters;
- inspection preview.

Rules:

- movable and pinnable where useful;
- opacity/position persisted separately from tool semantics;
- no full settings form hidden inside a HUD;
- consume canonical snapshots/services, not repeated world rescans.

## 10. Inspection HUDs

Inspection features should be able to choose which facts appear in the viewport.

Example tile inspection options:

- coordinate;
- plane;
- height;
- underlay/overlay;
- shape/rotation;
- flag summary;
- miniature tile/surface preview.

The same inspection snapshot feeds both the Inspector and HUD presentation.

Do not implement a second tile-inspection query for the HUD.

## 11. Context state model

Workspace visibility must derive from explicit state:

    WorkspaceContext
      activeTool
      activeToolCapabilities
      selection
      activeResource
      brushState
      inspectionState
      viewportState
      projectCapabilities

Presentation reads this context.

Do not infer behavior from whether a widget happens to be currently docked.

## 12. Tool capability model

A built-in tool may declare capabilities such as:

- requires brush shelf;
- has context drawer;
- supports viewport quick palette;
- supports HUD data;
- accepts object selection;
- accepts tile selection;
- owns wheel rotation;
- provides preview;
- commits through ChangePlan.

The capability describes behavior, not arbitrary coordinates on screen.

The shell determines presentation.

## 13. Icon policy

Built-in actions should use one consistent icon vocabulary.

Preferred sources:

1. host Material icon for standard actions;
2. bundled PNG resource when a custom visual identity is genuinely needed;
3. deterministic fallback icon if the asset is missing.

PNG resources are loaded/cached by the host UI layer. Domain/tool code does not own native texture IDs.

The same semantic action should use the same icon throughout Studio.

## 14. Input ownership

Input routes through the active tool/controller.

Rules:

- viewport drag/orbit/pan gestures are centralized;
- active tool receives edit gestures only when the viewport owns input;
- text/numeric input prevents viewport hotkeys from leaking through;
- Escape cancels transient interaction before changing tool state;
- mouse wheel behavior is explicit per active tool;
- edits still pass through command/change-plan boundaries.

## 15. Panel persistence

Persist presentation state separately from domain state.

Safe persisted UI data includes:

- panel widths;
- collapsed/expanded state;
- floating Brush Settings position;
- HUD position/opacity;
- active workspace tab;
- last selected tool where appropriate.

Corrupt or obsolete layout state must fall back to a known default.

Never make project data unrecoverable because a UI layout file failed.

## 16. Progressive disclosure

A surface appears only when it answers a current user question.

Examples:

- Brush Shelf appears because the current tool uses a brush.
- Context Drawer appears because the tool has context controls.
- selection inspector content appears because something is selected.
- diagnostic HUD appears because the user enabled that diagnostic.
- project/build controls appear only when the project supports them.

This keeps the viewport large and the interaction model learnable.

## 17. Performance

Dear ImGui rendering must not become a cache-processing loop.

Do not perform these synchronously every frame from a panel:

- broad cache decoding;
- recursive project/source scans;
- semantic graph rebuilds;
- whole-world selection scans;
- texture decoding;
- expensive source parsing.

Use generation-aware snapshots and background/domain work already owned by the appropriate service.

## 18. Accessibility and scaling

- controls must remain usable with high DPI;
- text fields retain keyboard focus correctly;
- icons require tooltips;
- active states cannot rely on color alone;
- inspectors reflow before forcing horizontal scroll;
- contrast must remain readable over both bright and dark scenes.

## 19. Migration direction

Older classes may still contain placement logic that predates this contract.

When touching them:

- move behavior into explicit tool capabilities/state;
- keep one brush state;
- remove arbitrary placement inference;
- avoid new permanent panels;
- migrate inspection data to shared snapshots;
- keep native rendering details out of tool/domain code.

## 20. Acceptance

A workspace change is complete when:

1. active tool is unambiguous;
2. irrelevant surfaces disappear;
3. brush settings appear only for brush-capable tools;
4. detached/docked Brush Settings share one state;
5. Context Drawer never duplicates shared brush mechanics;
6. inspectors edit through canonical commands;
7. HUD and inspector agree on semantic facts;
8. layout survives restart;
9. corrupt layout can reset safely;
10. UI work does not add expensive per-frame domain processing.