# OpenRune Studio Roadmap

_Expanded 2026-09-23 after the content-authoring foundation review, UI workspace review, Phase 0 trust review, and RuneLite-inspired semantic API review._

This document is the single prioritized product roadmap for OpenRune Studio.

Detailed supporting contracts:

- CONTENT_STUDIO_FOUNDATION.md - advanced authoring foundation and future Theme/Context Engine direction
- UI_WORKSPACE_CONTRACT.md - strict Contextual Multi-Rail Workspace layout and plugin UI rules
- STUDIO_SEMANTIC_API.md - Studio-owned authored-world/resolved-scene API contract and RuneLite reference policy
- OPENRUNE_ECOSYSTEM_INTEGRATION.md - OpenRune Server/cache/source integration guardrails
- RENDERING_PARITY_MANIFEST.json - live rendering correctness backlog

The roadmap deliberately does not duplicate every entry in the rendering parity manifest. The manifest remains the detailed renderer checklist. This file decides product order and architectural dependencies.

## Source-of-truth hierarchy

When project documents disagree, use this order:

1. current production code plus passing tests for what the repository actually does
2. RENDERING_PARITY_MANIFEST.json for rendering-status claims
3. ROADMAP.md for project execution order and architectural sequencing
4. STUDIO_SEMANTIC_API.md for the stable authored-world/resolved-scene boundary
5. UI_WORKSPACE_CONTRACT.md for editor-shell and plugin UI placement
6. CONTENT_STUDIO_FOUNDATION.md for advanced-authoring prerequisite detail
7. OPENRUNE_ECOSYSTEM_INTEGRATION.md for OpenRune subsystem integration detail
8. explicitly historical acceptance/reference documents for background only

A lower item must not silently override a higher item. When work makes a lower document stale, update it in the same PR when practical.

## Reference authority by concern

Use the correct reference for the question being answered. Do not treat all external projects as equivalent evidence.

1. **Real OSRS cache/map fixtures** are the final acceptance evidence for authored placements, definition availability, and representative scene behavior.
2. **Vendored RuneLite `runescape-client` source** is the primary reproducible reference for OSRS client scene semantics: object transforms, tile paint/model construction, loc-shape model selection, plane/bridge behavior, contouring, lighting order, scene traversal, visibility, and related client rules.
3. **Vendored RuneLite `runelite-api` and public Javadocs** are the primary naming/concept reference for stable semantic API design. They are not by themselves proof of implementation behavior.
4. **Vendored RuneLite mixins/GPU/client renderer code** is consulted when the question is specifically how RuneLite exposes or submits client scene/render state.
5. **OpenRune FileStore/definitions/builders** are the primary backend reference for cache decoding, encoding, writable definition semantics, reference-table updates, and OpenRune project/content integration.
6. **TSPS and other open implementations** are secondary algorithm/architecture cross-checks, not the final source of OSRS truth.
7. **Legacy RSPSi behavior** is historical evidence only unless locked by current tests or independently validated.
8. **OSRS Wiki/data tools** are useful for IDs, names, locations, and human context, not for renderer math or client traversal semantics.

Prefer the vendored RuneLite revision for reproducibility. The public RuneLite API documentation may be newer and is useful for discovering concepts, but a parity claim must identify the pinned source or fixture that supports it.

Do not copy RuneLite interfaces wholesale. Reuse established OSRS vocabulary where useful, implement behavior behind Studio-owned neutral contracts, and keep live-client/GPU bookkeeping out of ordinary editor APIs.

---

## 1. Product target

OpenRune Studio is intended to become a professional OSRS content-authoring environment, not only a tile editor.

The target includes:

- OSRS-accurate terrain and object rendering
- fast world navigation and reliable picking
- professional tile/material painting
- advanced conditional tile and object replacement
- copy/paste/rotate/mirror of complete structures across all planes
- path, road, stream, fence, bridge, and shoreline authoring
- deterministic noise/scatter/ground-decoration workflows
- biome generation and WFC-assisted world building
- rich object and definition inspection/editing
- plugin-first extensibility
- a stable Studio-owned semantic API for authored-world and resolved-scene access
- later, an explainable Theme/Context Engine learned from real OSRS world placement
- later, broader OpenRune content-studio workflows for server/content data

The architecture rule is simple:

    advanced tools stay thin
            |
            v
    shared queries / geometry / fragments / modifiers
            |
            v
    validated previewable ChangePlan
            |
            v
    one undoable commit
            |
            v
    world-aware persistence

No advanced tool should privately reimplement region traversal, undo stacks, transform rules, autotiling, condition logic, noise, save logic, or cache writes.

---

## 2. Non-negotiable architectural invariants

These are product rules, not suggestions.

### 2.1 Canonical authored state

WorldDocument and its eventual multi-region successor remain the canonical authored map state.

Renderers, UI panels, previews, generators, and plugins derive from canonical state. They do not become alternate sources of truth.

### 2.2 All edits are undoable

Normal authoring changes pass through EditorCommand or the future generalized ChangePlan commit boundary.

Direct mutation from UI callbacks is not an acceptable public/plugin workflow.

### 2.3 World coordinates are first-class

OSRS cache region boundaries are persistence details, not authoring boundaries.

A road, stream, brush, building paste, biome, selection, or replacement must behave continuously across region boundaries when the required regions are loaded.

### 2.4 Preview before destructive complexity

Complex operations calculate and validate before commit.

Examples:

- rotated building paste
- conditional replacement
- biome generation
- WFC
- bridge generation
- large scatter
- bulk object replacement

### 2.5 Public plugins use neutral capabilities

Public EditorPlugin code consumes neutral editor/asset/data services.

Direct cache backend types, Dear ImGui, GLFW, and OpenGL remain implementation details of Studio.

### 2.6 UI placement is contractual

Plugins contribute into host-owned workspace slots defined by UI_WORKSPACE_CONTRACT.md.

The editor does not grow by adding arbitrary permanent panels around the viewport.

### 2.7 The semantic API is the scene boundary

Studio must distinguish:

    canonical authored world
            |
            v
    OSRS resolution / scene semantics
            |
            v
    Studio Semantic API
            |
            +---- first-party tools
            +---- inspectors / HUDs
            +---- parity tests
            +---- public plugins
            |
            v
    renderer/backend internals

`EditorSceneSnapshot`, `SceneTileSnapshot`, `TerrainRenderPacket`, `ModelRenderPacket`, and related neutral types are foundations to adapt, not reasons to expose renderer packets as the public plugin contract.

The public semantic API should answer questions such as:

- what was authored on this tile?
- what plane does the client effectively/render it on?
- is the surface simple paint or a shaped tile model?
- what are the resolved corner colors and texture?
- what placed object definition is this?
- what transform/display definition supplies its visible appearance?
- what model/type resolution occurred?
- why did an authored object fail to render or become unpickable?

See STUDIO_SEMANTIC_API.md.

### 2.8 Scene reads are immutable; edits use plans

RuneLite exposes setters because it integrates with a live client. Studio public APIs must not copy that mutation model.

Scene/world semantic views are read-only.

Edits flow through EditorCommand today and the generalized ChangePlan boundary as it matures:

    calculate -> validate -> preview -> commit -> undo

A plugin must not mutate a tile, scene paint, object definition, or renderer packet directly merely because a RuneLite API exposes a similarly named setter.

---

# PHASE 0 - Editor Trust Gate

No advanced authoring system should be built on top of a viewport the user cannot trust.

This phase is now the immediate priority.

## 0.1 Rendering parity status

RENDERING_PARITY_MANIFEST.json remains authoritative.

At this roadmap rewrite the live manifest reports:

- 33 covered
- 4 deferred
- 0 partial
- 0 blocked

All currently tracked P0 rendering entries are covered, including native.depthPriorityFacing.

Do not reopen already-covered P0 work from stale prose or historical acceptance documents. Reopen a covered item only when new reproducible evidence demonstrates a regression or an uncovered semantic case.

Phase 0 rendering work is therefore targeted trust validation around the user-observed failures below, not a speculative parity rewrite.

## 0.2 Missing, null, or invisible objects

User-observed failure:

- some placed objects appear as null in tooling
- some placed objects do not render at all

Current groundwork already resolves default multiloc definitions in ModelPacketBuilder, but that does not prove every placed loc can resolve correctly.

Required investigation must distinguish at least:

1. definition genuinely missing from the selected cache
2. definition exists but has a blank/null display name
3. multiloc shell with a resolvable default transform
4. multiloc with no usable default in editor state
5. model id absent or geometry decode unavailable
6. model type does not match placed loc shape
7. intentionally non-renderable/invisible loc
8. render packet produced but scene submission drops it
9. object rendered but occlusion/plane/depth logic hides it
10. picker/inspector cannot resolve a visible scene object back to authored identity
11. UI/type taxonomy is stale even when the object is valid. Object type labels must come from the canonical OSRS loc-shape model rather than duplicated arrays. The current ObjectViewerPanel labels type 11 and type 22 inconsistently with OsrsLocShape and should be corrected during this trust work.

### Acceptance criteria

For a curated real-cache fixture set:

- every authored object has an explicit resolution status
- no object silently degrades to "null"
- unresolved objects expose a diagnostic reason
- renderable objects produce expected scene packets
- transformed/multiloc objects expose both placed identity and display definition
- object selection/inspector identity survives scene rebuilds

## 0.3 Interior floor picking and height correctness

User-observed failure:

- inside buildings the tile inspector/picker can jump or report implausible height/target information

Relevant current behavior needs tightening:

- DDA picking chooses rendered triangles, not an abstract tile grid
- multiple planes can be visible
- bridge/effective-plane semantics exist
- TileInfoHudPlugin currently displays the tile's south-west corner height rather than a sampled height at the actual picked point
- current lastPick behavior is selection-oriented, while hover and pinned inspection should become separate concepts

### Target

Create one canonical pick/hover snapshot containing:

- actual hit world position
- authored tile
- authored plane
- effective/render plane
- hit triangle/layer
- sampled surface height at the hit point
- object identity if applicable
- tile corner heights
- bridge/roof context
- stable scene object identity
- source draw-command metadata for diagnostics

Then route tile inspector, hover HUD, object picker, and right inspector through the same result.

### Acceptance criteria

- interior floors do not oscillate between unrelated surfaces
- active-plane restriction is honored when editing
- show-all-planes can still visually render other levels without stealing picks
- reported height corresponds to the surface actually hit
- bridge tiles identify authored and effective planes separately
- hover inspection and click selection do not fight each other

## 0.4 Interior color/material correctness

User-observed failure:

- colors inside some buildings appear wrong

Do not assume one cause.

Relevant parity families include:

- models.colors
- floor underlay/overlay color math
- textured model color/alpha paths
- lighting
- contouring
- priority/depth interactions
- roof/interior visibility

### Target

Build a small set of real OSRS interior golden scenes and compare Studio output against a trusted reference.

At minimum include:

- stone/castle interior
- wooden interior
- textured floor
- shaped overlay floor
- wall decorations
- floor decorations
- roof transition area
- bridge/elevated interior if a stable fixture is available

Fix the actual discrepancy found rather than compensating with Studio-specific color tuning.

## 0.5 Object preview quality

User-observed failure:

- object preview often frames poorly
- default camera angle is poor
- object is not reliably centered
- scale relationship to OSRS tiles is not communicated well

Current preview strengths:

- real object geometry and materials
- automatic geometry bounds
- orbit yaw/pitch/zoom
- a reference floor patch
- double-sided preview workaround to match the main renderer's no-cull presentation

Current issues:

- camera fit uses vertical FOV and a bounding sphere but must also respect preview aspect ratio
- confirmed coordinate mismatch: ObjectPreviewRenderer.boundsOf() uses ModelRenderPacket local model bounds, while GpuUploadPlanBuilder renders model vertices at anchor.x * 128 / anchor.y * 128 plus renderPlacementHeight. The preview object is deliberately anchored at tile (1,1), so camera centering can be displaced by a full tile in X/Z and by placement height in Y.
- fixed default yaw/pitch is not guaranteed to present every object well
- 3x3 floor visualization is a fallback rather than a purpose-built scale grid
- unresolved objects return an empty preview without sufficiently rich diagnosis

### Target preview contract

- robust center from final renderable world/preview-space bounds
- fit both horizontal and vertical FOV
- predictable three-quarter default view
- user orbit and zoom
- Reset View
- one-tile grid with clear 128-unit scale
- optional footprint visualization based on object definition width/length
- optional bounding box
- transparent/checker or neutral backdrop
- no near-plane clipping
- large/tall/wide objects remain visible
- clear unresolved-model reason instead of blank content

## 0.6 Bootstrap the Studio Semantic API from trust work

Do not pause Phase 0 to create a speculative framework.

Instead, every trust fix must publish the smallest reusable semantic contract needed by the next caller.

Phase 0 should establish:

### Object semantics

- shared object-definition transform resolution
- placed definition versus display/transformed definition
- transform path and explicit unresolved status
- safe display labels that treat the client sentinel name `"null"` as unnamed
- loc shape and model-type selection diagnostics
- selected model IDs and geometry availability
- stable SceneObjectIdentity
- packet/submission/visibility diagnostic stages

### Surface semantics

- one canonical SurfaceHit used by hover, click selection, inspectors, and tools
- actual hit position
- sampled surface height
- authored/effective/render/cull plane where applicable
- tile surface kind
- stable object identity when an object was hit

### Tile semantics

Adapt existing scene compilation into Studio-owned views conceptually equivalent to:

- SceneView
- SceneTileView
- TileSurfaceView
- TilePaintView
- TileModelView
- SceneObjectView
- ObjectResolutionView

RuneLite `Tile`, `SceneTilePaint`, and `SceneTileModel` are design references. Studio must omit ordinary public GPU buffer offsets and retain editor-specific authored/resolved state.

### Phase 0 semantic acceptance

By the end of Phase 0:

- first-party trust/debug UI can explain an authored object's end-to-end resolution
- interior parity tests can compare semantic tile paint/model values before pixel debugging
- pick/hover consumers share one surface result
- no public contract requires OpenGL/VBO knowledge
- the semantic API remains internal/provisional until first-party callers prove it
- public plugin ABI freeze waits until Phase 6

---

# PHASE 1 - Strict Contextual Multi-Rail Workspace

The new UI contract is defined in UI_WORKSPACE_CONTRACT.md.

This is not a cosmetic reskin. It is the interaction architecture for the editor and the plugin system.

## 1.1 Canonical surfaces

OpenRune Studio will have these first-class workspace slots:

1. Top Status Strip
2. central 3D Viewport
3. persistent Bottom Primary Tool Rail
4. one Bottom Context Drawer
5. conditional Left Brush Shelf
6. floating Viewport Quick Palette
7. Right Inspector Rail + Inspector Panel
8. managed Viewport HUD Layer

## 1.2 Primary Tool Rail

Persistent horizontal tool selector at the bottom.

Examples:

- Select / Inspect
- Tile Painter
- Height Sculpt
- Path / Linear Feature
- Object Placement
- Flags / Collision
- Building / Fragment
- Biome / Generation

The rail activates tools. It does not host deep settings.

## 1.3 Context Drawer

One active bottom drawer above the Primary Tool Rail.

Examples:

Tile Painter:
- underlay/overlay palette
- tile shape palette
- rotation
- flags/material presets
- conditional rules

Object Placement:
- object catalog
- search
- filters
- thumbnails

Biome/WFC:
- presets
- seed
- rules
- preview diagnostics

One drawer at a time. Collapsing it does not deactivate the tool.

## 1.4 Brush Shelf

The left surface becomes a real contextual brush/stamp dynamics panel.

It should own:

- radius
- footprint shape
- falloff
- strength
- spacing
- jitter
- density
- scatter/noise dynamics where appropriate

The current LeftBrushRail, which mainly toggles BrushSettingsHud, is transitional.

Target behavior:

- actual controls live on the Brush Shelf
- it collapses completely for tools that do not need brush/stamp dynamics
- plugin tools may add brush/stamp groups when they declare the relevant capability
- BrushSettingsHud becomes optional compact HUD/quick state rather than the sole brush editor

## 1.5 Viewport Quick Palette

Floating semi-transparent near-canvas palette.

Purpose:

- reduce pointer travel
- keep current armed values close to the scene
- show recent/favorite values

It is not the deep asset browser.

Examples:

Tile Painter:
- active underlay
- active overlay
- active shape
- recent swatches

Object Placement:
- active loc
- rotation
- recent/favorite locs

Select:
- tile/object/area picker mode

## 1.6 Right Inspector

Selection-driven and directly editable.

The Inspector must become the authoritative exact-property editor for:

- tile corner heights
- materials/shapes/rotation
- flags
- placed locs
- object transforms/rotation/type
- collision
- render diagnostics
- scene identity

Ordinary exact edits should not require modal dialogs.

## 1.7 HUDs remain first-class

HUDs are valuable precisely because users may want information to remain visible when the corresponding drawer or inspector is closed.

Examples:

- coordinates/elevation
- brush summary
- active material
- active object
- selection summary
- generation warnings
- performance counters

Plugins keep the ability to contribute managed HUDs.

## 1.8 Strict tool UI descriptor

Move away from permissive "put my tool on arbitrary surfaces" semantics.

Introduce an explicit capability/slot descriptor, conceptually:

    ToolUiDescriptor
      primaryToolEntry
      drawer
      brushCapabilities
      quickPalette
      inspectors
      huds
      shortcuts

Tool behavior should not be inferred from placement.

## 1.9 UI migration targets

Migrate first-party tools to the contract before encouraging third-party authors to depend on old patterns.

Key migrations:

- LeftBrushRail -> real Brush Shelf host
- BrushSettingsHud -> optional HUD/quick controls
- TilePainterPalette -> Context Drawer
- ObjectViewerPanel -> split deep catalog vs selected-object inspector
- right-side settings panels -> Inspector Rail/Panel model
- existing floating selection toolbar -> Viewport Quick Palette/picker model
- StudioToolPlugin/UiSurfaceContribution -> capability-based compatibility adapter

## 1.10 UI state tests

Even without a pixel-perfect ImGui test harness, the state resolver must be testable.

Verify:

- one active modal world tool
- one active drawer
- brush shelf visibility from capability
- per-tool state memory
- plugin unload cleanup
- HUD independence
- inspector selection routing
- workspace reset
- minimum viewport dimensions

---

# PHASE 2 - Authored Multi-Region World

This remains the most important editing foundation after the trust/UI gate.

## 2.1 Problem

Editable EditorSession currently centers on one WorldDocument, and the ordinary OSRS session loader opens one 64x64 region.

Multi-region support already exists for rendering/context:

- WorldRegion
- WorldRegionWindow
- RegionNeighborhood
- WorldTileAddress
- world-aware TerrainVertexLattice mode
- batched region save

What is missing is one authored edit transaction spanning several regions.

## 2.2 Target

Introduce a world-space authored edit abstraction that:

- resolves loaded authored regions by absolute WorldTile
- preserves region ownership
- can mutate multiple loaded regions atomically
- reports missing/unloaded regions explicitly
- creates one undo history entry for one user operation
- records which cache regions became dirty
- saves changed regions through the existing region encoders/batch save boundary

## 2.3 Acceptance

A brush stroke, path, stream, fence, selection, replacement, or pasted building can cross x/y multiples of 64 without changing semantics.

No operation silently fabricates missing neighboring regions.

---

# PHASE 3 - Query, Condition, and Selection Engine

Advanced tools require one shared predicate language.

## 3.1 Current groundwork

- SelectionQuery.ObjectFilter
- SelectionQuery.TileFilter
- AttributeSelectionTool
- legacy TileCondition
- object replacement commands

## 3.2 Target condition families

Tile:

- plane
- underlay/overlay
- shape/rotation
- flags
- height
- slope
- bridge/roof/effective plane
- adjacency
- selection membership
- distance to feature
- semantic classification

Object:

- id
- symbolic name
- type/shape
- rotation
- category
- footprint
- collision
- animation
- transforms
- semantic classification

Spatial:

- rectangle
- polygon/lasso
- brush mask
- path distance
- connected component
- room/building membership
- loaded region/window

Procedural:

- deterministic random threshold
- noise threshold
- density
- seeded variation

## 3.3 Composition

Support:

- AND
- OR
- NOT
- reusable named presets

Prefer a data-oriented representation that can eventually be serialized.

## 3.4 Consumers

The same engine must drive:

- select by condition
- tile replace
- object replace
- brush masks
- scatter
- path/stream generation
- biome generation
- WFC constraints
- Theme/Context suggestions

---

# PHASE 4 - General ChangePlan

Promote the good idea behind ProposedChanges into the common complex-edit boundary.

## 4.1 ChangePlan responsibilities

A plan should carry:

- tile mutations
- object additions/removals/replacements
- affected world bounds
- affected region ids
- preconditions
- conflict list
- diagnostics
- deterministic seed/provenance
- merge/paste policy
- preview metadata
- expected source state where stale-edit protection matters

## 4.2 Required workflow

    calculate
      -> validate
      -> preview
      -> resolve conflicts
      -> commit
      -> one undo history entry

## 4.3 No silent clipping

A plan that reaches unloaded or unavailable authored data must report that fact.

Complex generation must not quietly discard out-of-document edits.

---

# PHASE 5 - WorldFragment Transform and Structure Editing Foundation

WorldFragment already provides terrain + objects across all planes.

Now make it professional.

## 5.1 Canonical transforms

Support:

- translate
- rotate 90/180/270
- mirror X/Y
- configurable pivot
- all planes

Transform correctly:

- tile positions
- object positions
- object rotation
- overlay shape
- overlay rotation
- corner heights
- flags
- structure footprint

## 5.2 Paste policies

Support policy objects such as:

- replace all
- terrain only
- objects only
- merge objects
- preserve destination heights
- use absolute source heights
- height offset from anchor
- fit foundation
- blend perimeter
- skip conflicts
- report conflicts
- replace only matching categories

## 5.3 Building/fragment acceptance

A multi-plane building copied, rotated, and pasted should preserve internal geometry and object relationships and land correctly at the new world-space anchor.

This transform service becomes shared infrastructure, not a private Building Tool implementation.

---

# PHASE 6 - Plugin Platform Hardening

The plugin system is already a major strength.

## 6.1 Preserve current capabilities

Keep:

- external JAR plugins
- API version
- manifests
- semantic versions
- dependency resolution
- isolated classloaders
- repository/update infrastructure
- tools
- settings
- overlays
- HUDs
- menus
- shortcuts
- inspectors
- validators
- generators
- knowledge analyzers
- decoded data
- extension points
- cleanup/unload lifecycle

## 6.2 Correct the public boundary

EditorPlugin already has neutral cache-facing data through AssetRepository and DecodedDataCatalog.

Documentation must not continue teaching that cache-backed functionality automatically requires StudioPlugin.

Correct rule:

- EditorPlugin: public neutral plugin boundary
- StudioPlugin: internal/native presentation projection until replaced
- direct DefinitionProvider/cache backend and ImGui/OpenGL types remain internal

## 6.3 Capability services

Expose future shared foundations through PluginServices:

- authored world reads
- resolved scene reads through the proven Studio Semantic API
- worldEdit
- queries
- fragments
- changePlans
- autotile
- linearFeatures
- modifiers
- brushes
- assets/definitions
- knowledge
- generators
- overlays
- permissioned diagnostics

Public plugins should normally inspect tiles/objects through semantic views rather than `TerrainRenderPacket`, `ModelRenderPacket`, `GpuScenePacket`, OpenRune backend classes, or native renderer state.

## 6.4 Permission enforcement

PluginPermission is currently not enough as metadata.

Before a public Plugin Hub is treated as secure, enforce capabilities.

A plugin without WORLD_EDIT must not be able to mutate canonical world state through a raw context escape hatch.

## 6.5 Neutral rich UI

Public plugins should not require direct ImGui code for normal rich tool UI.

Build a neutral component model for:

- groups
- rows/columns
- buttons
- toggles
- numeric/text fields
- combos
- asset pickers
- tables/lists
- progress
- compact previews

Project these components into the strict workspace slots from UI_WORKSPACE_CONTRACT.md.

## 6.6 Plugin compatibility and deprecation policy

The public plugin API must be versioned as a product contract, not only compiled until it breaks.

Rules:

- new neutral services are additive where possible
- old StudioToolPlugin/UiSurfaceContribution placement behavior remains behind compatibility adapters while first-party tools migrate
- deprecated public APIs receive a documented replacement path
- removal requires an intentional Plugin API version change
- plugin load failures must report the missing/incompatible capability clearly
- persisted plugin settings use stable plugin/control ids and survive UI surface migration when semantics are unchanged
- internal StudioPlugin/native APIs may change more aggressively because they are not the supported third-party boundary

A workspace redesign is not permission to silently break external plugins.

Before freezing the next public Plugin API version, first-party tools must exercise the semantic scene contracts introduced during Phase 0. The public surface should be promoted from proven internal contracts, not designed speculatively.

A later optional `studio-runelite-compat` adapter may ease porting scene-oriented RuneLite algorithms, but RuneLite plugin source/binary compatibility is not a product requirement. Live-client concepts such as actors, widgets, ticks, varbits, menus, and game networking do not map directly to a map editor.

---

# PHASE 7 - Performance and Incremental Scene Rebuild

The GPU residency layer is already more advanced than the CPU authoring path.

## 7.1 Existing strength

OpenGlSceneRenderer/ZoneVboManager already partitions native geometry into canonical 8x8 zones and can reuse unchanged GPU allocations.

## 7.2 Remaining gap

Studio still rebuilds too much upstream CPU-derived scene data after edits.

## 7.3 Target

- make the world-window compile path incremental
- preserve padded/stitching context
- invalidate bounded dependency zones
- reuse unchanged scene packet/upload-plan partitions
- retain conservative full rebuild for topology/cache reload cases
- instrument scene rebuild cost continuously

## 7.4 Authoring relevance

This becomes increasingly important once brushes, scatter, conditional replace, and generators can affect large areas interactively.

---

# PHASE 8 - Shared Procedural Authoring Primitives

Do not build separate Path, Stream, Fence, Bridge, and Biome math stacks.

## 8.1 OSRS AutoTileService

Promote path-specific shape/rotation rules into a shared topology service.

Support:

- cardinal and diagonal connectivity
- interior/exterior corners
- OSRS overlay shapes/rotations
- material transition sets
- arbitrary masks
- paths
- streams/banks
- shorelines
- biome boundaries
- bridge approaches
- plugin-defined rule sets
- deterministic tie-breaking

## 8.2 Linear Feature Service

Extract shared path/ribbon geometry:

- control points
- curve evaluation
- centerline
- tangent/normal
- width
- distance along path
- left/right side
- rasterized footprint
- corners
- junctions

Consumers:

- road
- path
- stream
- river
- fence
- hedge
- wall
- shoreline
- bridge

## 8.3 Deterministic modifier/noise stack

Create reusable fields/modifiers:

- brush weight
- falloff
- seeded noise
- fractal noise
- threshold
- density
- distance to edge
- distance to centerline
- slope
- elevation
- jitter
- clustering
- mask intersection

Rules:

- same seed + same inputs = same output
- sample in absolute world coordinates
- preview and commit use identical samples
- iteration order cannot change randomness
- plugins may contribute modifiers

---

# PHASE 9 - Advanced Authoring Tools

Only after the shared foundations above are stable.

## 9.1 Professional Tile Painter

Target capabilities:

- underlay/overlay/shape/rotation/flags
- brush weighting
- falloff/strength
- stamp mode
- conditional paint
- replace mode
- deterministic noise
- edge-aware autotiling
- palette presets
- eyedropper/sample
- preview
- selection-aware apply
- world-space strokes across regions

## 9.2 Advanced Tile Replacement

Examples:

- replace overlay A with B only on plane 0
- replace material inside selection except under roofs
- replace by slope/elevation
- replace only connected area
- replace using weighted/noise variation
- change shape/rotation automatically from neighborhood

## 9.3 Advanced Object Editing

- multi-object move
- rotate
- duplicate
- replace
- conditional replace over arbitrary area
- preserve or remap type/rotation
- replace by category/footprint/collision/semantic tag
- conflict preview

## 9.4 Object scatter and ground decoration

- weighted object sets
- density
- exclusion radius
- slope/height constraints
- distance from paths/water/buildings
- deterministic seed
- rotation variation
- clustering
- preview
- semantic/biome presets

## 9.5 Path/Road tool

Build on shared linear geometry and autotiling.

Later enhancements:

- weighted tile variants
- sub-tile coverage quality
- turn-angle-aware smoothing
- local obstacle rerouting
- terrain adaptation
- road dressing

## 9.6 Stream/River tool

- centerline/width
- terrain lowering
- bank shaping
- water/material application
- shoreline autotile
- noise variation
- crossings
- bridge handoff

## 9.7 Auto Fence / Hedge / Wall tool

- line/spline input
- straight pieces
- corners
- posts
- gates
- object rotation
- optional terrain following
- conditional gaps/intersections

## 9.8 Bridge tool

- detect/declare crossing
- deck
- approaches
- railings
- terrain/plane/flag semantics
- width and direction
- integration with stream/path systems

## 9.9 Building / Structure Stamp

Built on WorldFragment transforms.

- full multi-plane copy
- rotate/mirror
- terrain policy
- foundation fit
- perimeter blend
- conflict detection
- repeated stamping
- saved fragment library

---

# PHASE 10 - Biomes, WFC, and Procedural World Building

GenerationSchema and GeneratorService already establish the non-destructive generator direction.

## 10.1 Biome system

A biome is not just a noise texture.

It should combine:

- terrain profile
- floor/material families
- object/scatter families
- density constraints
- path/water relationships
- elevation/slope
- transition rules
- semantic tags
- deterministic seed

## 10.2 WFC

Use real OSRS-derived and curated pattern sets.

Requirements:

- explainable constraints
- deterministic seed
- preview
- conflict/unsatisfied-cell diagnostics
- partial regeneration
- locked cells/areas
- multi-plane awareness where supported
- ChangePlan output

## 10.3 Building/interior WFC

Later target:

- room/corridor grammar
- walls/doors
- floor families
- decoration families
- stairs/vertical relations
- roofs
- object dressing

Do not build this before fragment transforms, conditions, and Theme/Context data are mature.

---

# PHASE 11 - Theme and Context Engine

This remains intentionally deferred but preserved as a major product direction.

The engine should learn explainable relationships from real OSRS world placement and cache data.

Potential relations:

- ATTACHED_TO
- SAME_KIT_AS
- ADJACENT_TO
- ABOVE
- BELOW
- INSIDE_ROOM_WITH
- SAME_BUILDING
- USES_FLOOR
- USES_ROOF_WITH
- NEAR_PATH
- TRANSITIONS_TO
- SHARES_MODEL
- SHARES_TEXTURE
- SHARES_RECOLOR_FAMILY
- MORPH_OF
- MAP_ICON_OF
- LOCATED_IN_AREA
- ASSOCIATED_WITH_NPC

Sources may include:

- official map placements
- object definitions
- shapes/rotations
- models
- recolors/retextures
- floor/material context
- room/building topology
- world map semantics
- transforms/interactions
- optional OpenRune server/NPC/source context
- optional curated 117HD area/material metadata

Every inference should retain confidence and provenance.

Consumers:

- contextual asset ranking
- "objects that belong with this" suggestions
- semantic brushes
- biome generation
- structure dressing
- WFC candidate ranking
- replacement suggestions
- content validation

The Theme Engine consumes the same editor foundations. It does not become a second architecture.

---

# PHASE 12 - Broader OpenRune Content Studio

Once map/world authoring is stable, expand into the broader server/content workflow.

Potential domains:

- NPC spawn/content visualization
- object-to-script/content bindings
- interface editing
- script/client-script inspection where supported
- GameVal/symbolic references
- OpenRune plugin/source scanning
- content validation
- simulated server ticks/NPC behavior
- build/publish pipeline

Use OPENRUNE_ECOSYSTEM_INTEGRATION.md as the guardrail.

---

# 13. Project-level build and dirty-resource model

Map edits, definition edits, interfaces, scripts, GameVals, and future authored resources should eventually participate in one project build lifecycle.

Target:

    ProjectResource
      identity
      dirty state
      dependencies
      validator
      builder/publisher
      provenance

The editor should be able to answer:

- what has changed?
- what must be rebuilt?
- what depends on it?
- what has been validated?
- where was it published?
- can it be safely reverted?

This should grow from the durable publication/provenance work already landed.

---

# 14. Testing strategy

Professional authoring tools need OSRS-semantic fixtures, not only synthetic unit tests.

## 14.1 Golden scene fixtures

Maintain real-cache fixtures for:

- interiors
- walls
- wall decorations
- ground decorations
- bridges
- roofs
- shaped floors
- textured floors
- multilocs
- animated objects
- large footprints
- cross-region edges

## 14.2 Transform fixtures

Cover:

- every tile overlay shape/rotation under 90/180/270 transforms
- every relevant object shape/rotation
- multi-plane fragment rotation
- mirror
- cross-region paste
- shared terrain vertices

## 14.3 Procedural determinism

Verify:

- same seed produces same output
- different traversal order produces same output
- region boundaries do not reset noise
- preview exactly matches committed plan

## 14.4 Plugin lifecycle

Verify:

- permissions
- unload cleanup
- dependencies
- UI contribution cleanup
- capability-based surface resolution
- no stale settings/resources

## 14.5 Semantic parity boundary

Before debugging final pixels, compare the Studio semantic scene against trusted reference behavior.

For simple tile paint validate:

- south-west, south-east, north-west, north-east resolved colors
- texture ID
- flatness
- minimap color
- authored underlay/overlay IDs
- effective/render plane

For shaped tile models validate:

- shape and rotation
- vertex positions/heights
- face topology
- per-face colors
- texture IDs
- resolved underlay/overlay semantics

For objects validate:

- placed definition
- transform path
- display definition
- shape/model-type compatibility
- selected model IDs
- geometry decode
- packet creation
- scene submission
- visibility
- stable identity/picking

This semantic checkpoint determines whether a defect is in authored/cache resolution versus final rendering.

---

# 15. Execution discipline

The project should continue the one-focused-PR-at-a-time workflow.

Rules:

1. One architectural objective per PR.
2. Do not start an advanced tool when its shared prerequisite is missing.
3. Every PR states acceptance criteria before implementation.
4. New OSRS behavior gets a fixture or trusted-reference validation.
5. Rendering claims update RENDERING_PARITY_MANIFEST.json.
6. Roadmap state is updated when a phase materially changes.
7. Avoid duplicate tool-specific systems when a shared service is the correct abstraction.
8. Keep first-party tools on the same public-neutral APIs we want community plugins to use whenever practical.
9. Native/UI exceptions must be explicit.
10. Run foundationGate and live Studio validation for UI/rendering changes.
11. For OSRS semantic behavior, cite the vendored RuneLite/OpenRune source path or real-cache fixture used as evidence in the PR.
12. Prefer semantic parity fixtures before adding renderer-specific compensations.
13. Do not expose a new public plugin API merely because an equivalent RuneLite method exists; prove the Studio use case with first-party callers first.

---

# 16. Near-term PR order

This is the dependency-ordered implementation sequence from the current main branch.

The sequence deliberately grows the Studio Semantic API out of correctness work. Do not open a separate "copy RuneLite API" project or stop feature work for a speculative API rewrite.

## PR A - Scene object completeness diagnostics and resolution foundation

Primary references:

- vendored RuneLite `ObjectComposition` / dynamic object transform behavior
- OpenRune decoded object definitions
- real Lumbridge/representative map placements

Deliver:

- shared object transform resolver
- safe object display labels
- placed definition versus display definition
- transform-path diagnostics
- resolved appearance from the display definition
- classify missing definition, no default transform, missing transformed definition, transform cycles/depth, wrong/no model for shape, missing geometry
- trace packet creation, scene submission, visibility, and stable identity
- build a real Lumbridge Castle entrance/bush fixture from actual map/cache placements rather than a guessed object count
- correct stale object-type labels by deriving them from `OsrsLocShape`

Acceptance:

- every authored fixture object is accounted for
- no object silently displays client sentinel `null`
- unresolved objects report a reason
- multiloc/default resolution matches pinned client semantics
- display appearance and selected models come from the same resolved definition
- stable placed identity survives scene rebuilds

## PR B - Canonical SurfaceHit and pick/hover contract

Primary references:

- RuneLite tile/world/render-level concepts
- existing GpuPlanPicker exact-triangle behavior
- Studio bridge/effective-plane rules

Deliver:

- canonical `SurfaceHit`
- separate hover from click selection
- actual hit world/local position
- barycentric/surface sampled hit height, not south-west-corner height
- authored/effective/render/cull plane where applicable
- stable object identity
- active-plane pick restriction
- show-all-planes rendering without pick stealing
- route TileInfo HUD, inspector, object picker, and tool hover through the same semantic result

Acceptance:

- building floors no longer report unrelated corner heights
- bridge/interior plane identity is explicit
- hover and selection do not fight each other
- all first-party picking consumers agree on the same hit

## PR C - Tile semantic views and interior rendering parity

Primary references:

- vendored RuneLite `SceneTilePaint`, `SceneTileModel`, scene/tile construction source
- OpenRune floor/texture definitions
- real OSRS interior fixtures

Deliver:

- provisional `SceneTileView`, `TileSurfaceView`, `TilePaintView`, and `TileModelView`
- adapt existing `SceneTileSnapshot` / `TerrainRenderPacket` rather than duplicate rendering compilation
- expose resolved four-corner paint colors, texture, flatness, shape, rotation, model vertices/faces, and plane semantics
- real stone/castle, wood, textured-floor, shaped-overlay, decoration, roof-transition fixtures
- semantic comparison before pixel comparison
- isolate whether the observed interior mismatch is HSL/blend math, texture handling, lighting, shape composition, plane/roof behavior, or final renderer state
- fix the actual discrepancy
- update parity manifest only where evidence warrants

Acceptance:

- trusted fixture semantic values match before final pixels are considered correct
- Studio-specific color tuning is not used to hide a semantic mismatch

## PR D - Object preview framing, scale, and resolution reuse

- consume PR A object resolution instead of inventing preview-only transform logic
- correct the confirmed local-bounds vs anchored-GPU-geometry camera mismatch
- include render placement height in preview-space bounds
- derive/verify bounds from the same coordinate space submitted to the preview renderer
- aspect-aware auto-fit
- better default view
- footprint/tile scale visualization
- diagnostics for unresolved models

## PR E - Workspace state model

- implement capability-based ToolUiDescriptor
- one drawer mutex
- Brush Shelf visibility
- Quick Palette host
- Inspector routing
- HUD independence

## PR F - First-party UI migration

- Tile Painter
- Height Sculpt
- Object Placement
- Select/Inspect
- Flags/Collision
- Path tool

After those correctness and workspace foundations are stable:

## PR G - Multi-region authored world boundary

- absolute-world authored region lookup
- explicit unloaded neighbors
- multi-region transaction
- one undo history entry
- dirty-region ownership
- batched persistence

## PR H - Query/condition engine

- shared tile/object/spatial predicates
- AND/OR/NOT composition
- world-coordinate evaluation
- selection integration
- serializable preset-friendly condition data where practical

## PR I - General ChangePlan

- calculate/validate/preview/commit boundary
- conflicts and unloaded-data diagnostics
- stale-source preconditions where needed
- deterministic provenance
- one undo entry across all affected regions

## PR J - Fragment transform foundation

- rotate/mirror complete multi-plane fragments
- transform object type/rotation and tile shape/rotation correctly
- pivot/origin policy
- paste/merge/elevation policies
- preview through ChangePlan

## PR K - Semantic API public hardening

Only after Phase 0 and first-party migration have proved the contracts:

- stabilize `SceneView`, tile/surface/object views
- expose them through public EditorPlugin capabilities
- enforce WORLD_READ / ASSET_READ boundaries
- define compatibility/deprecation policy
- keep renderer internals privileged/internal
- add an optional RuneLite-scene compatibility adapter only if real porting use cases justify it

## PR L - Shared procedural primitives

- AutoTileService
- linear-feature geometry
- deterministic modifier/noise stack
- world-coordinate sampling
- plugin extension points

After PR L, build advanced Tile Painter, Replace, Scatter, Path, Stream, Fence, Bridge, Structure, Biome, WFC, and later Theme/Context workflows as thin consumers of the shared services.

---

# 17. Current foundation we should not rebuild

Already-established strengths include:

- backend-neutral WorldDocument
- EditorSession/history
- atomic CompositeEditCommand
- shared TerrainVertexLattice
- BrushEngine and weighted brush samples
- object transform commands
- WorldFragment and codec
- generator/ProposedChanges direction
- spline path authoring
- first-pass OSRS autotiling
- region/window rendering context
- 8x8 GPU zone residency
- neutral AssetRepository
- DecodedDataCatalog
- knowledge/corpus groundwork
- external plugin runtime
- plugin dependency/version/update infrastructure
- HUD manager
- stable scene object identity groundwork
- provisional neutral scene snapshots and renderer-neutral terrain/model packets
- OpenRune source/cache integration boundaries

The roadmap is about connecting and hardening these pieces, not replacing them.

---

# 18. Definition of success

OpenRune Studio reaches the intended architecture when a community plugin can implement an advanced world-authoring feature by combining stable services instead of reaching into editor internals.

For scene-aware plugins specifically, success means they can answer "what tile/object is this, how did OSRS semantics resolve it, and what can I safely edit?" through Studio-owned authored-world and resolved-scene APIs without importing OpenRune backend types, RuneLite live-client types, or renderer/GPU packets.

The ideal plugin should be able to:

    declare a tool
    declare its UI slots
    query world/selection context
    use shared brush/linear/autotile/modifier services
    produce a ChangePlan
    preview it
    commit it
    extend the inspector/HUD
    unload cleanly

At that point, advanced Tile Painter, Object Replace, Stream, Fence, Bridge, Building, Biome, WFC, and Theme workflows can grow independently without destabilizing the editor core.
