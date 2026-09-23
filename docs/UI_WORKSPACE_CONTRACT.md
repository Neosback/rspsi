# OpenRune Studio UI Workspace Contract

_This document defines the layout contract for the native OpenRune Studio map/content editor. It is intentionally stricter than a collection of movable panels._

## 1. Name and scope

OpenRune Studio will use the project term **Contextual Multi-Rail Workspace** for its main editor shell.

That is an OpenRune architectural name, not a claim that the industry has one universally standardized term for this exact arrangement. The individual patterns are established and recognizable: tool or mode rails, shelves or palettes, drawers, floating palettes/HUDs, and inspectors.

The goal is to optimize three things at the same time:

1. Keep the 3D viewport as large and unobstructed as possible.
2. Reduce pointer travel during repetitive world-authoring tasks.
3. Surface only the controls that are relevant to the current tool, selection, and editing context.

The shell must remain predictable. Plugins contribute into known slots instead of inventing new chrome around the viewport.

## 2. Workspace hierarchy

The canonical layout is:

    +--------------------------------------------------------------------------------+
    | Top Status Strip: coordinates | plane | project | build/dirty | diagnostics   |
    +--------------------------------------------------------------------------------+
    | Left Contextual     |                    3D VIEWPORT             | Right Rail   |
    | Brush/Stamp Shelf   |                                           | + Inspector  |
    |                     |       Floating Quick Palette / HUDs        |              |
    | progressive         |       Hover / pinned information           | selection    |
    | disclosure          |                                           | editing      |
    +---------------------+---------------------------------------------+--------------+
    |                        Active Bottom Context Drawer                              |
    +--------------------------------------------------------------------------------+
    |                         Persistent Bottom Tool Rail                              |
    +--------------------------------------------------------------------------------+

The hierarchy is:

    Primary operation
        -> Bottom Tool Rail

    Tool-specific content and libraries
        -> Bottom Context Drawer

    Brush/stamp execution dynamics
        -> Left Contextual Brush Shelf

    Fast target/material/object switching
        -> Floating Quick Palette

    Exact selected-item inspection and direct editing
        -> Right Inspector

    Persistent or glanceable information
        -> HUD layer

## 3. Core surface definitions

### 3.1 Bottom Tool Rail

Canonical name: **Primary Tool Rail**

Role:

- persistent horizontal rail
- selects the active editing tool or high-level authoring operation
- remains visible unless the entire editor chrome is intentionally hidden
- owns activation, not deep configuration

Examples:

- Select / Inspect
- Tile Painter
- Height Sculpt
- Path / Linear Feature
- Object Placement
- Flags / Collision
- Building / Fragment
- Biome / Generation

Rules:

- the rail is not a generic plugin button dumping ground
- each visible item activates an editor tool or authoring mode
- tools may be grouped by category, but activation remains singular
- only one modal world-editing tool is active at a time
- tool buttons must have icon, tooltip, shortcut when applicable, and active state
- deep asset catalogs, sliders, and inspectors do not belong on this rail

### 3.2 Bottom Context Drawer

Canonical name: **Context Drawer**

Role:

- a single expandable panel directly above the Primary Tool Rail
- hosts the active tool's broad content UI
- one drawer is visible at a time
- collapsible without deactivating the tool

Examples:

Tile Painter:
- underlay palette
- overlay palette
- tile shape palette
- rotation
- tile flags or material presets
- conditional replacement rules

Object Placement:
- searchable object catalog
- category filters
- cached thumbnails
- selected object variants

Height Sculpt:
- flatten target
- ramp controls
- noise presets
- height operation mode

Biome / WFC:
- generation preset
- seed
- rule set
- diagnostics
- preview/commit controls

Rules:

- a tool should not open multiple competing bottom panels
- drawer content is contextual to the active tool
- switching tools swaps drawer ownership atomically
- drawer open/collapsed state is remembered per tool
- the drawer must not duplicate brush size, brush shape, falloff, spacing, or stamp dynamics when those belong on the Left Brush Shelf
- large asset libraries belong here, not in the floating quick palette

### 3.3 Left Contextual Brush Shelf

Canonical name: **Brush Shelf**

Role:

- conditional vertical surface on the left edge of the viewport
- normally hidden
- appears only when the active tool declares brush, stamp, scatter, or repeated-placement dynamics
- isolates execution behavior from content selection

Typical controls:

- brush radius / size
- brush footprint shape
- hardness
- falloff
- strength
- spacing
- stamp spacing
- jitter
- density
- scatter radius
- rotation variation
- scale variation where the asset supports it
- noise enable / amount when it directly modifies brush execution

The Brush Shelf is not limited to built-in tools. Plugins may contribute controls to it when their active tool has a valid brush/stamp capability.

Rules:

- the surface itself is context-gated
- a plugin cannot force it permanently visible just because it has settings
- controls must describe how the active operation is applied, not which content asset is selected
- common brush controls should use shared components and shared state
- tool-specific brush modifiers may append their own groups below the common controls
- if a tool has no brush/stamp dynamics, the shelf collapses to zero width
- user pinning may keep it visible for convenience, but the underlying contextual eligibility remains explicit

Important migration note:

The current LeftBrushRail behaves primarily as a small button that toggles the Brush Settings HUD. That is not the final contract. The rail should become the actual Brush Shelf described here. The HUD becomes an independent optional quick summary/control surface.

### 3.4 Floating Quick Palette

Canonical name: **Viewport Quick Palette**

Role:

- modeless semi-transparent palette over the viewport
- minimizes pointer travel for frequently switched targets
- contains a small active working set, not the full library

Examples in Tile Painter:

- armed underlay
- armed overlay
- armed shape
- recent materials
- favorite tile presets

Examples in Object Placement:

- active object thumbnail
- current rotation
- recent objects
- favorites / quick slots

Examples in Select / Inspect:

- tile picker
- object picker
- selection mode
- additive/subtractive mode

Rules:

- keep the palette compact
- 4 to 8 quick slots is the expected scale, not thousands of assets
- deep search and browsing belongs in the Context Drawer
- users may move or pin the palette within the viewport
- inactivity auto-collapse may be supported
- a plugin can contribute a quick palette only when the content genuinely benefits from near-cursor switching
- the quick palette may mirror the currently armed value from the drawer, but must not become a second full settings panel

### 3.5 Right Tool Rail and Inspector

Canonical names:

- **Inspector Rail** for the vertical category/tool strip
- **Inspector Panel** for the expanded details surface

Role:

- selection-driven inspection
- precise direct editing
- diagnostics and metadata
- one primary inspector panel at a time

The Inspector Panel is editable, not read-only.

Typical sections:

Tile:
- exact world/local coordinate
- plane
- corner heights
- effective/render plane
- underlay
- overlay
- shape
- rotation
- flags

Objects:
- all locs on the selected tile
- stable scene identity
- object id/name
- shape/type
- rotation
- footprint
- animation
- collision
- transforms/multiloc state
- delete/replace/rotate actions

Rendering diagnostics:
- draw layer
- texture
- priority
- depth bias
- model bounds
- contour metadata
- scene plane/cull level

Rules:

- inspect and edit the selected thing, not the currently armed placement asset
- selection changes update the inspector immediately
- tools may contribute inspector sections tied to supported selection types
- inspectors must not create modal dialogs for ordinary numeric property edits
- direct edits go through canonical command/change-plan APIs so they remain undoable

### 3.6 HUD Layer

Canonical name: **Viewport HUD Layer**

Role:

- small persistent/glanceable information over the canvas
- may stay visible even when the related drawer or inspector is closed
- user-configurable and pinnable where appropriate

Examples:

- coordinate/plane/elevation badge
- current brush summary
- active material/object summary
- generation preview diagnostics
- hovered tile/object information
- pinned comparison probes
- performance counters

Rules:

- HUDs are informational first
- small quick actions are allowed, but a HUD must not become a hidden replacement for a full panel
- multiple HUDs are managed by one placement/collision system
- plugins may contribute HUDs independently of drawer visibility
- users can hide, pin, move, or reset HUDs without disabling the underlying tool/plugin when practical

## 4. Context state model

Workspace visibility must be derived from explicit state, not incidental widget placement.

At minimum:

    WorkspaceContext
      activeTool
      activeToolCapabilities
      selection
      hoveredTarget
      activePlane
      activeAsset
      drawerState
      pinnedHudState

A tool exposes capabilities. The shell resolves those capabilities into surfaces.

Example conceptual descriptor:

    ToolUiDescriptor
      toolId
      primaryRailEntry
      drawerContribution
      brushShelfCapability
      quickPaletteContribution
      inspectorContributions
      hudContributions
      shortcuts

This should replace ad hoc checks such as "is this plugin currently placed on the left rail?" as a way of inferring tool behavior.

## 5. Tool capability model

Useful capability flags should include concepts such as:

- BRUSH_FOOTPRINT
- BRUSH_FALLOFF
- BRUSH_STRENGTH
- STAMP
- SCATTER
- ASSET_PALETTE
- QUICK_PICK
- TILE_PICK
- OBJECT_PICK
- SELECTION_INSPECTION
- DIRECT_PROPERTY_EDIT
- PREVIEWABLE_CHANGE_PLAN

A capability declares behavior. Surface placement is then derived from behavior plus optional plugin contributions.

This keeps the UI strict without preventing plugins from having rich controls.

## 6. Control ownership rules

Every editor control has one primary home.

### Brush dynamics

Primary home: Brush Shelf

Examples:
- size
- shape
- falloff
- strength
- spacing
- jitter
- scatter density

### Content selection

Primary home: Context Drawer

Examples:
- underlay/overlay catalog
- tile shapes and presets
- object catalog
- biome presets
- WFC rule sets

### Fast armed-state switching

Primary home: Viewport Quick Palette

Examples:
- recent materials
- recent objects
- active rotation
- favorite presets

### Exact selected-state editing

Primary home: Inspector Panel

Examples:
- exact height values
- object id
- object rotation
- flags
- render metadata

### Glanceable state

Primary home: HUD Layer

Examples:
- coordinates
- current brush radius
- current material
- selection summary
- warnings

Controls may be mirrored only when the second copy is intentionally a compact quick control or read-only summary. Two full editors for the same property are not allowed.

## 7. Context matrix

| Active tool | Brush Shelf | Context Drawer | Inspector | Quick Palette | HUD |
|---|---|---|---|---|---|
| Tile Painter | Size, footprint, falloff, strength, noise/scatter dynamics | Underlays, overlays, shapes, rotation, flags, conditional rules | Exact selected tile properties | Armed/recent tile materials | Brush/material summary |
| Height Sculpt | Size, falloff, strength | Raise/lower/flatten/ramp/noise presets | Exact vertex/corner heights, slope | Target height quick control | Elevation/slope summary |
| Object Placement | Hidden for single placement, visible for scatter/stamp mode | Object search/catalog/categories/thumbnails | Selected placed object properties | Armed object, rotation, recent objects | Active object summary |
| Flags / Collision | Stamp size/shape if stamping | Flag/collision presets | Exact bitmask and collision diagnostics | Recent flag presets | Hovered flag badge |
| Select / Inspect | Hidden | Selection groups/history/actions when useful | Full tile/object inspector | Tile/object picker modes | Hover/pinned inspection HUD |
| Path / Stream / Fence | Width/brush profile when applicable | Material, linear-feature type, rule presets | Selected feature/control-point details | Active path/fence material | Width/grade/rule summary |
| Building / Fragment | Stamp footprint modifiers when applicable | Fragment library, transform, paste policy | Selected fragment/placed content details | Active fragment and rotation | Paste/conflict summary |
| Biome / WFC | Brush mask if painting generation area | Generator/rules/seed/theme controls | Generated-cell diagnostics | Active preset | Constraint/conflict summary |

## 8. Pointer-travel and input rules

The layout should minimize repeated edge-to-center movement.

Target interactions:

- Alt + left click on terrain samples tile material into the active tile workflow.
- Alt + right click on an object samples its object id/type/rotation into the active object workflow.
- R rotates the active placement or fragment where unambiguous.
- Mouse wheel may rotate active placement when the current tool explicitly owns that gesture.
- Holding Space may later expose a temporary cursor-local quick palette, but this is optional and should not precede the core workspace contract.
- Escape cancels current transient interaction before changing tool state.
- common shortcuts remain stable across tools where their meaning is shared.

Keyboard/mouse gestures must route through the active tool and must not bypass undoable command/change-plan boundaries.

## 9. Screen-space rules

Core rails are structural, not freely dockable windows.

Rules:

- Primary Tool Rail is anchored to the bottom.
- Context Drawer is anchored immediately above it.
- Brush Shelf is anchored to the viewport's left edge when active.
- Inspector Rail/Panel is anchored right.
- the viewport owns the central remaining space.
- HUDs and the Quick Palette are viewport overlays.
- plugin content cannot create a second competing core rail.
- user-resizable widths/heights are allowed within defined limits.
- core layout state is recoverable with one "Reset Workspace" action.
- free multi-viewport tear-off may be supported for non-core panels later, but core rails must remain recoverable and predictable.

Recommended layout tokens should live in one theme/layout configuration rather than magic numbers spread across plugins:

- rail thickness
- brush shelf width
- inspector width
- drawer default/min/max height
- HUD margin
- spacing
- animation duration

## 10. Progressive disclosure rules

A surface should appear only when it answers a current user question.

Examples:

"What operation am I doing?"
- Primary Tool Rail

"What content am I applying?"
- Context Drawer

"How am I applying it?"
- Brush Shelf

"What do I need to switch quickly?"
- Quick Palette

"What exactly did I select?"
- Inspector

"What do I want to keep seeing while I work?"
- HUD

This decision model is the baseline for reviewing every new panel or plugin contribution.

## 11. Plugin UI contract

Public plugins should contribute declaratively into host-owned surfaces.

A plugin may contribute:

- tool rail activation entry
- context drawer content associated with a tool
- brush/stamp shelf control group associated with eligible capabilities
- viewport quick palette content
- selection-aware inspector section
- HUD contribution
- scene overlay
- menu/shortcut/settings entries

A plugin may not:

- create its own permanent competing bottom/left/right rail
- bypass the one-active-drawer rule
- infer editing capability from arbitrary surface placement
- require direct Dear ImGui access for ordinary public-plugin UI
- mutate world state directly from a UI callback outside canonical command/change-plan services

The eventual neutral rich UI component model should cover:

- labels
- buttons
- toggles
- numeric fields
- text fields
- combo/select
- asset pickers
- groups/sections
- rows/columns
- lists/tables
- progress
- compact previews

Studio projects these neutral components to ImGui.

Direct ImGui/GLFW/OpenGL UI remains an internal Studio implementation boundary.

## 12. State memory

Context changes must not reset authoring state unexpectedly.

Remember per tool:

- drawer collapsed/open state
- last selected asset
- brush radius
- brush shape
- falloff
- strength
- scatter/noise parameters
- quick palette contents
- tool-specific presets
- inspector section expansion where appropriate

Remember globally:

- rail/panel sizes
- HUD positions
- pinned HUD state
- user visibility preferences
- workspace reset baseline

A plugin unload must release its contributions without corrupting the remaining layout.

## 13. Object and tile picking relationship

Pickers and inspectors are different concerns.

Picker tools answer:

- what should the active operation target?
- tile, object, vertex, area, path point, or fragment?

Inspector surfaces answer:

- what is currently selected?
- what are its exact properties?

The floating picker palette may change targeting mode without replacing the Inspector Panel.

Selection should carry stable semantic identity wherever possible so scene rebuilds do not invalidate the inspector unnecessarily.

## 14. Current migration targets

The existing code is useful but must converge on this contract.

### LeftBrushRail

Current:
- narrow rail whose principal action opens BrushSettingsHud

Target:
- actual contextual Brush Shelf
- common brush controls inline
- plugin-specific brush/stamp groups appended declaratively
- zero-width when inactive unless user-pinned

### BrushSettingsHud

Current:
- primary brush settings UI

Target:
- optional compact HUD/quick summary and perhaps small quick adjustments
- not the sole owner of brush configuration

### TilePainterPalette

Target home:
- Context Drawer

### ObjectViewerPanel

Target split:
- deep object catalog/browser in Context Drawer while Object Placement is active
- exact selected placed-object editing in Inspector Panel
- quick armed object/rotation in Viewport Quick Palette

### TileInfoHudPlugin

Target:
- remains a HUD contribution
- later consumes a canonical hover/pick snapshot instead of independently reconstructing height details

### StudioToolPlugin / UiSurfaceContribution

Target:
- migrate from permissive surface placement toward an explicit ToolUiDescriptor/capability model
- retain compatibility adapters while first-party tools migrate

## 15. UI correctness acceptance gates

A UI feature is not complete because the widgets render.

The workspace must verify:

1. one modal world tool is active
2. only one Context Drawer owns the bottom content surface
3. Brush Shelf appears only for declared brush/stamp capabilities
4. changing tools preserves relevant prior state
5. closing a drawer does not deactivate the tool
6. HUD visibility is independent of drawer visibility
7. inspectors update from semantic selection
8. plugin unload removes its contributions cleanly
9. no hidden duplicate control owns a conflicting value
10. workspace reset restores a known usable layout
11. the central viewport never receives negative/zero usable dimensions from panel combinations
12. core editing remains usable at supported minimum window size

These rules should be unit-tested at the state/layout resolver level even though pixel-perfect ImGui rendering still needs live verification.

## 16. Accessibility, focus, scaling, and performance

The workspace must remain usable as the tool set and asset catalogs grow.

### Accessibility and discoverability

- every icon-only control requires a tooltip and stable accessible label
- color cannot be the only indicator of active/error/warning state
- keyboard focus order follows the visible workspace hierarchy
- core actions expose shortcuts when practical
- focus must not leak into the viewport while a text/numeric field is actively capturing input
- Escape should cancel transient tool interaction before closing persistent workspace surfaces
- destructive actions require clear intent and remain undoable where technically possible

### DPI and sizing

- rails, hit targets, fonts, thumbnails, and spacing derive from shared layout/theme tokens
- high-DPI scaling must not require per-plugin pixel constants
- the workspace defines a supported minimum window size
- when space is constrained, contextual surfaces collapse before the central viewport becomes unusable
- user-resized rail/drawer dimensions are clamped to sane min/max values

### Transparency and readability

- translucency is appropriate for viewport HUDs and the Viewport Quick Palette
- core drawers, shelves, and inspectors must retain sufficient contrast for prolonged editing
- text/background contrast must remain readable over bright and dark OSRS scenes
- pinned HUDs may expose opacity controls but cannot become effectively invisible while still intercepting input

### UI/render-loop performance

Dear ImGui rendering must not become an implicit cache-processing loop.

Rules:

- large object/material catalogs use virtualization
- search indexes are cached/incremental rather than rebuilt every frame
- model/thumbnail generation is asynchronous or amortized and never blocks the frame loop on thousands of assets
- cache decoding, world-corpus analysis, and expensive semantic queries do not run synchronously from ordinary render callbacks
- unavailable thumbnails show stable placeholders rather than shifting layout
- plugin UI contributions receive the same performance expectations as first-party UI
- HUDs and inspectors consume canonical snapshots/services instead of repeatedly rescanning the world independently

The target is a stable interactive viewport even while deep libraries contain tens of thousands of definitions.

## 17. Workspace persistence and schema evolution

Workspace state is persistent user data and needs a migration strategy.

Persist using stable semantic ids, not array positions:

- tool id
- surface contribution id
- inspector section id
- HUD id
- quick-palette slot id
- plugin id

Workspace layout/settings storage should carry a schema version.

When surfaces are renamed or migrated, provide an explicit migration where reasonable. Corrupt or obsolete workspace state must fall back to a known default layout rather than preventing Studio startup.

Plugin-owned persisted UI state is removed or quarantined cleanly when a plugin disappears, without corrupting host workspace state.

## 18. Guiding principle

The workspace is contextual, not modal-window-driven.

The editor should feel like one continuous canvas with tools around it, not a collection of dialogs.

The design rule is:

    operation below
    execution dynamics left
    fast target switching over the canvas
    exact details right
    persistent information in HUDs

That rule provides plugins more room, reduces pointer travel, preserves screen space, and gives OpenRune Studio a consistent visual and behavioral language as the tool set grows.
