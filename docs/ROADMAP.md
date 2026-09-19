# OpenRune Studio — Completion Roadmap

This is the one execution roadmap for turning RSPSi into OpenRune Studio.
It replaces the previous progress ledger and deliberately starts a new track.
The objective is to finish a coherent product instead of continuing to add
parallel partial systems.

The roadmap is ordered. Work may be parallelized inside a phase, but a later
phase does not become active until the current phase's exit gate passes.

## Product commitment

OpenRune Studio is one desktop application with one source of truth:

```text
Native GLFW window
    ↓
OpenGL 3.3 core context
    ↓
Dear ImGui shell
    ↓
Application services
    ├── SettingsService
    ├── CacheSessionService
    ├── WorkspaceManager
    ├── PluginManager
    ├── TaskService
    ├── NotificationService
    └── LiveClientBridge
    ↓
Workspaces
    └── Map Editor first
        ├── EditorSession / WorldDocument
        ├── OSRS scene resolver
        ├── Render planner
        ├── Software reference renderer
        ├── OpenGL renderer
        ├── tools and inspectors
        └── first-party plugins
```

The following decisions are locked for this track:

- One application window and one OpenGL context for the whole session.
- Dear ImGui docking is enabled; multi-viewport windows are deferred.
- Map rendering goes to a viewport FBO and is displayed as an ImGui image.
- `WorldDocument`, `EditorSession`, commands/history, selection, FileStore
  adapters, scene resolution, render planning, and the software renderer stay.
- OSRS semantics live above OpenGL. OpenGL consumes resolved scene data and is
  never allowed to become a second world model.
- OpenRune FileStore is the cache/asset backend. It is not the editor, plugin
  system, world model, or renderer.
- The typed settings registry is the only settings system.
- Plugins contribute through neutral Studio APIs and never receive a mutable
  world-model back door or direct cache ownership.
- The software renderer remains the deterministic reference implementation.
- JavaFX, Swing, AWTGLCanvas, FXML, legacy static settings, and legacy plugin
  launch paths are transitional and have a removal gate.

## How we keep ourselves on track

Every roadmap item is either a concrete implementation task, a test/fixture,
or an acceptance/documentation task. A task is complete only when its code,
automated coverage, and acceptance evidence exist.

Use these statuses:

- `queued` — planned, not active.
- `active` — current phase work.
- `verified` — implementation and exit evidence pass.
- `blocked` — an external dependency or decision is genuinely required.
- `deferred` — intentionally outside the current completion track.

Rules for the repository:

1. Only one phase is `active`.
2. New feature work must use the locked neutral APIs. If an API is missing,
   fix the API in the current phase before adding a legacy bridge.
3. A phase cannot close on compilation alone. It needs the listed gate.
4. A failed gate reopens the owning phase; it does not get worked around by
   marking the feature complete.
5. New ideas go into the deferred section until they are explicitly placed in
   the sequence.
6. Every renderer or cache claim must identify its source of truth and fixture.
7. No second cache, renderer, scene graph, history system, or settings store
   may be introduced.

The normal verification ladder is:

```text
./gradlew test
./gradlew check
./gradlew foundationGate
./gradlew parityGate       # explicit external fixtures/caches
./gradlew platformGate     # release/platform matrix
```

`foundationGate`, `parityGate`, and `platformGate` are part of the roadmap,
not optional polish.

## Protected foundation — do not restart

These pieces already exist in the repository and must be extended or repaired,
not replaced by another architecture:

- `WorldDocument`, `EditorSession`, `EditorCommand`, command history, undo/redo,
  selection, dirty regions, and autosave/session concepts.
- OpenRune FileStore cache/session adapters and the neutral definition/asset
  facade.
- `OsrsSceneResolver`, `ResolvedScene`, `OsrsRenderPlanner`,
  `GpuScenePacket`, and `GpuUploadPlan`.
- Software/reference rendering and the existing render-parity harness.
- Terrain topology, OSRS coordinates, bridges/effective planes, collision,
  route/LOS preview, and neutral validation contracts.
- Typed settings keys/registry/store and the first-party plugin registry,
  lifecycle, dependency handling, and contribution contracts.
- The current JavaFX shell as a compatibility/reference surface while the
  native shell is built.

The compatibility surface is allowed to remain temporarily. It is not allowed
to grow new product features after the native track begins.

## Phase 0 — Lock the foundation and build the gates

**Status: active**

This is the reset point for the new track. Establish the boundaries before
building more UI or renderer features.

### Work

- [x] Record the locked architecture and ownership rules in code/package
  checks where practical.
- [x] Add `verifySettingsContract` to the foundation gate.
- [x] Validate that every registered setting has a declared consumer and that
  every renderer/tool consumer resolves a registered key.
- [x] Remove callback-backed `EditorSetting` state from new code; keep only a
  compatibility adapter until migration is complete.
- [x] Mark unsupported renderer settings honestly. For example, MSAA remains
  unavailable until the complete FBO/MSAA acceptance gate passes.
- [x] Add package/import checks that prevent new renderer code from using the
  retired plugin package or raw cache backends.
- [x] Split verification into deterministic foundation checks and explicit
  external parity checks.
- [ ] Freeze representative fixtures: plain terrain, shaped overlays, water,
  bridges, roofs, walls, alpha models, animated objects, and region boundaries.
- [ ] Add a short acceptance record for each gate so green tests cannot hide a
  missing interactive workflow.

### Exit gate

`foundationGate` passes with no new legacy imports, the settings contract is
validated, representative fixtures are versioned/identified, and the next
phase can be implemented without inventing another state system.

## Phase 1 — OSRS semantics and renderer correctness

**Status: queued**

Fix correctness issues that would otherwise be baked into the native renderer.
This phase is intentionally before the large UI migration.

### Cache and definition semantics

- [ ] Correct FileStore object semantics for `clipped`, `clipType`, shadow
  behavior, contouring, opcode 22 merge-normal behavior, and object contrast.
- [ ] Preserve object morphs/transforms through the neutral definition model,
  including varbit/varp-driven state.
- [ ] Add an upstream/live fixture for animation-start behavior and explicitly
  track unsupported modern animation fields.
- [ ] Rename “revision detection” to capability/profile selection. Show the
  selected profile and evidence in the dashboard rather than implying a
  stronger detection guarantee than the code provides.
- [ ] Keep FileStore 3.0.2 pinned until the adapter and parity gates justify an
  upgrade.
- [ ] Preserve source-cache read-only behavior and make staged/direct output
  capability visible to the user.

### Vanilla-compatible rendering corrections

- [x] Remove synthetic priority-to-NDC depth bands from vanilla mode. Keep
  them only as an explicit debug visualization if useful. (`GpuPriority` and
  `OpenGlSceneRenderer`'s vertex shader now apply only the true client
  face-bias byte, matching RuneLite's `vert.glsl`
  `screenPos.z += float(bias) / 128.0`; verified against a real revision-240
  cache render with `foundationGate` and the full render test suite passing.)
- [x] Implement the real face bias convention and CPU-side priority ordering,
  including the 12 priority groups and special interleave behavior. (Alpha
  ordering already matched via `RsFaceOrderPlanner`; removing the depth bias
  also required adding priority-descending opaque draw ordering in both
  `SoftwareSceneRenderer` and `OpenGlSceneRenderer` so exactly-coplanar
  opaque faces, e.g. a decal on the terrain height it decorates, resolve by
  draw order rather than a synthetic depth offset, matching RuneLite's
  "priority affects order, never depth" behavior. Render modes
  `SORTED`/`SORTED_NO_DEPTH`/`UNSORTED`/`UNSORTED_NO_DEPTH` below remain
  unimplemented.)
- [ ] Add explicit render modes: `DEFAULT`, `SORTED`, `SORTED_NO_DEPTH`,
  `UNSORTED`, and `UNSORTED_NO_DEPTH`.
- [ ] Match RuneLite/OSRS projection and reversed-depth behavior in
  `OSRS_CLIENT` mode; keep editor perspective, orthographic, and top-down
  projections separate.
- [ ] Match compatibility blending state, including framebuffer alpha behavior.
- [ ] Implement RuneLite-style per-model alpha metadata and camera-relative
  alpha/model sorting instead of scene-global average-depth ordering.
- [ ] Model roofs as explicit groups/ranges and implement authored/effective
  plane, bridge-upper, roof, and below-bridge visibility rules.
- [ ] Replace approximate occlusion activation with a client-compatible,
  fixture-backed visibility/occluder traversal.
- [ ] Make terrain color randomization explicit and deterministic by default.
- [ ] Report missing textures as renderer diagnostics and implement the actual
  compatibility fallback; do not silently turn missing assets gray.

### Exit gate

The software render plan matches the semantic fixtures for terrain, objects,
priorities, alpha, depth, blend, roofs, bridges, and occluders. The native
renderer has no known camera-upload regression. Any remaining divergence is
named, fixture-backed, and explicitly accepted as deferred.

## Phase 2 — Native application vertical slice

**Status: active**

Build the smallest complete OpenRune Studio: boot, load, switch workspace,
render one map, and return home without creating a second window or context.

### Native host

- [x] Finalize `StudioMain`, `NativeWindow`, `ImGuiHost`, `StudioApplication`,
  and `WorkspaceManager` around GLFW, OpenGL 3.3 core, and Dear ImGui.
- [x] Enable docking and keep multi-viewport disabled.
- [x] Establish the single application loop: poll events, update services,
  begin ImGui frame, render workspace, render ImGui, swap buffers.
- [x] Ensure all OpenGL resource creation/destruction happens on the render
  thread.

### Startup and cache session

- [x] Reuse `OsrsCacheSessionService` and expose explicit startup phases:
  `EMPTY`, `DISCOVERING`, `LOADING`, `READY`, `FAILED`, and `CLOSING`.
- [ ] Add task progress, cancellation, notifications, and actionable errors.
- [x] Persist recent cache/project selection without making a cache global.
- [x] Keep the cache session alive when navigating Dashboard ↔ workspace.
- [x] Block Map Editor until the session is genuinely `READY`.

### First vertical slice

- [x] Blank Dashboard appears in the native window with cache validation.
- [x] A selected cache loads asynchronously and reports profile, backend,
  capabilities, and comprehensive FileStore decoder counts (18 decoders).
- [x] Map Editor opens in the same window.
- [x] An existing `GpuUploadPlan` renders one opened region.
- [x] Orbit, pan, zoom, and home/dashboard navigation work.
- [ ] A dirty session prompts Save/Discard/Cancel before it is closed.

### Exit gate

The complete vertical slice works on the supported development platform with a
real or explicit external cache fixture, and the native app owns the workflow.
No second Stage, Swing/AWT viewport, or duplicate cache prompt is involved.

## Phase 3 — FBO renderer and incremental GPU resources

**Status: active**

Make the renderer a proper viewport backend before adding the full editor shell.

### Renderer decomposition

- [ ] Split `OpenGlSceneRenderer` into focused components:
  `GlDevice`, `GlShaderProgram`, `GlSceneResources`, `GlTextureRepository`,
  `GlFramebuffer`, `GlWorldRenderer`, `GlPickingPass`, `GlOverlayRenderer`,
  and `GlRendererStats`. (`GlFramebuffer` already extracted and in production.)
- [x] Render the world into a scene FBO with color, depth/stencil, optional
  MSAA attachments, resolved color, and picking attachment. Implemented in
  `GlFramebuffer`, with MSAA samples clamped to driver `GL_MAX_SAMPLES`.
- [x] Display the resolved color texture through `ImGui.image(...)`.
- [x] Support viewport resize, render scale, screenshots, and future 2D/3D/
  split views through the same framebuffer boundary.

### Resource lifetime and invalidation

- [ ] Make textures cache-session resources with stable OSRS texture-ID to
  texture-array-layer mapping and lazy upload.
- [ ] Separate topology, geometry, texture, material, visibility, and
  animation generations/fingerprints.
- [x] Make camera movement update uniforms/visibility/order only; it must never
  rebuild scene VBOs/EBOs or recreate the texture array. (Fixed:
  `OcclusionPlanFilter` camera-fingerprint embedding removed; replaced with
  `GpuCommandVisibility` per-frame BitSet over static uploaded plan geometry.
  `OpenGlSceneRenderer.draw()` gates upload on camera-independent plan
  fingerprint.)
- [ ] Use 8×8 zones/chunks as GPU ownership and invalidation units with opaque
  geometry, alpha geometry, object metadata, roof ranges, pick IDs, and bounds.
- [ ] Invalidate neighboring chunks only for real seam/lighting dependencies.
- [ ] Update animated objects locally. Texture animation uses a cycle/uniform;
  static terrain and static objects do not rebuild every frame.

### Picking and diagnostics

- [ ] Add a GPU ID-buffer picking pass with stable object/tile/vertex IDs.
  (`GpuPlanPicker` retained as deterministic reference fallback.)
- [x] Add renderer statistics for uploads, chunk rebuilds, texture misses,
  draw calls, visible zones, and frame time. (`OpenGlSceneRenderer.Statistics`
  now reports per-frame `geometryUploaded`, `textureUploaded`, and `drawCalls`,
  surfaced live in the Map Editor viewport panel.)
- [ ] Keep software and native render plans comparable for the same scene.

### Exit gate

Camera movement produces zero geometry/texture uploads in the steady state;
localized edits rebuild only affected zones; FBO resize/MSAA/picking work;
software-vs-native fixture comparisons remain within the defined tolerance.

## Phase 4 — Map Editor shell and input migration

**Status: active**

Port the concepts of the existing controlled shell to ImGui. Do not copy the
JavaFX implementation or recreate every old button.

Progress note: `DashboardView` and `MapEditorView` use the production
`StudioTheme` dark palette. `MapEditorView` hosts a real `ImGui.dockSpace`
with default `Tools | Viewport | Inspector` split plus bottom utility drawer,
built via DockBuilder with layout reset support. `CommandPaletteModal` exists
and consumes actual registered plugin commands.

### Input and viewport

- [ ] Route GLFW keyboard/mouse events through `EditorInputRouter`.
- [ ] Add a neutral `ViewportController` for orbit, pan, zoom, focus, camera
  modes, and viewport-to-world picking.
- [ ] Remove camera logic from AWT/Swing listeners.
- [ ] Support 3D perspective, top-down, orthographic, and future split views
  through the same viewport controller.

### Default Map Editor layout

- [ ] Compact left tool rail: Select, Terrain, Objects, Path, Water,
  Structures, Scatter, Regions, Collision, Markers, and Audio.
- [ ] Context toolbar with Mode → Tool → Context levels so advanced controls
  do not overwhelm the active task.
- [ ] Large center viewport with plane, roof, bridge, grid, collision, and
  Vanilla/Editor/Debug view controls.
- [ ] Right Outliner above contextual Inspector.
- [ ] Bottom drawers for Assets, History, Validation, Console, Tasks,
  Selection, Minimap, and Live Client.
- [ ] Persistent status bar showing profile, region, plane, world/local
  coordinates, FPS, cache capability, and task/error state.
- [x] Save/reset workspace layouts and provide strong defaults; layout reset
  rebuilds default dock structure cleanly.

### Core interaction surfaces

- [x] Command palette with plugin-contributed commands.
- [ ] Global task center and notification center.
- [ ] Contextual inspector for tiles, objects, vertices, groups, and selections.
- [ ] Outliner with editor-only groups that can contain terrain, objects,
  markers, audio, and other authored elements.
- [ ] Eyedropper/Selection Peek for “what is this?” inspection without leaving
  the viewport.
- [ ] Interactive minimap with camera, selection, dirty areas, bookmarks,
  collision, and loaded-region state.
- [ ] Original/Edited/Split/Ghost/Difference comparison modes.

### Exit gate

A user can open a region, inspect a tile/object, change camera/view controls,
select and undo an edit, use the command palette, see async task/error state,
and save/reset the layout without touching JavaFX/AWT input paths.

## Phase 5 — Complete the Map Editor workflow

**Status: queued**

Finish the useful editor before expanding to other workspaces.

### Editing fundamentals

- [ ] Complete neutral session binding for terrain, object, selection,
  inspector, history, autosave, save, and recovery.
- [ ] Finish terrain tools: paint, height, slope, smooth, flatten, noise,
  shapes, blending, flags, and neighbor-aware seam handling.
- [ ] Finish object tools: place, move, rotate, duplicate, delete, replace,
  multi-select, snap-to-tile/wall, collision preview, and definition actions.
- [ ] Keep every edit command-backed, grouped where appropriate, and fully
  undoable.
- [ ] Finish tile/object/vertex/lasso/area/fragment selection behavior.
- [ ] Finish live collision, route, LOS, reachability, bridge, roof, and grid
  overlays.
- [ ] Finish validation for tile seams, steep height changes, object overlap,
  invalid flags, missing definitions, collision, and region boundaries.

### Asset and reuse workflow

- [ ] Asset browser supports Nearby, Favorites, Recent, Objects, Models,
  Materials, Prefabs, and Similar views.
- [ ] Drag/drop assets into the viewport with contextual placement behavior.
- [ ] Add working palettes that can contain materials, objects, prefabs,
  tools, and colors.
- [ ] Add region palette analysis with usage counts, thumbnails, IDs, Use,
  Find, and Favorite actions.
- [ ] Add connected-structure detection and prefab creation without changing
  the legal OSRS output representation.
- [ ] Add bookmarks and reliable navigation between world, region, and local
  views.

### Procedural and authoring tools

- [ ] Path tool with auto-shape, auto-rotate, material blend, shoulders,
  smoothing, collision preview, and route diagnostics.
- [ ] Water tool with river/pool/shore/flow controls, banks, bridge clearance,
  and legal tile-shape output.
- [ ] Scatter tool with weighted assets, spacing, slope/collision constraints,
  deterministic seed, preview ghosts, and regenerate.
- [ ] Structure/prefab workflows for rooms, buildings, bridges, and grouped
  authored content.
- [ ] New-region templates: blank, flat, neighboring terrain, island, and
  underground/dungeon setup.
- [ ] Dungeon generator presented as a user-level generator with rooms,
  tunnels, water, elevation, seed, preview, accept, and cancel. WFC/local
  grammar remains an implementation detail unless Advanced is opened.
- [ ] Every procedural operation uses a nondestructive preview layer and only
  becomes normal editable content on Accept.
- [ ] Add spatial audio regions and map/world markers as editor-visible
  authored elements where the target output format supports them.
- [ ] Add “Explain this tile” and “Diagnose tile” reports that teach the OSRS
  representation instead of hiding it.

### Exit gate

The Map Editor supports the complete edit → inspect → validate → preview →
accept → undo → save/recover loop on representative regions, including a
terrain/object workflow and a procedural preview workflow. This is the first
product-complete milestone.

## Phase 6 — Plugin platform and Live Client integration

**Status: queued**

Stabilize extension points only after the native shell and Map Editor use them.

### Plugin API

- [ ] Add settings, task, notification, event-bus, safe overlay, and neutral UI
  contribution services to `EditorPluginContext`.
- [ ] Version the plugin API and reject incompatible plugins with actionable
  diagnostics.
- [ ] Give each external plugin an isolated classloader and deterministic
  lifecycle cleanup.
- [ ] Remove active/inactive JAR moving. Enable/disable is persisted intent;
  dependency cascades are derived state.
- [ ] Add capabilities/permissions before allowing external code to access
  files, network, server roots, live-client control, or process execution.
- [ ] Add safe mode for repeatedly crashing plugins.
- [ ] Expose a versioned neutral Studio UI API; plugins do not receive ImGui
  internals.
- [ ] Add Plugin Hub metadata, reproducible dependency rules, hashes/signing,
  CI validation, and review workflow only after the local API is stable.

### Live Client / Developer Tools

- [ ] Add a neutral `LiveClientBridge` with read-only inspection first.
- [ ] Negotiate Developer Tools schema/version/capabilities instead of relying
  on hard-coded commands.
- [ ] Add structured scene-reference capture, tile/object/model/texture
  inspection, camera/projection state, render-mode state, and screenshot/
  pixel-reference commands.
- [ ] Add a local pairing/authorization token before Studio can control a
  client or request mutating operations.
- [ ] Rename or isolate old Flux naming in the Developer Tools protocol.
- [ ] Make live-client fixtures usable by `parityGate` without making the live
  client a runtime dependency of the editor.

### Exit gate

First-party and external plugin lifecycle tests pass, a deliberately limited
sample plugin can contribute a tool/panel/setting/command, safe mode works,
and read-only Live Client capture produces repeatable renderer-reference data.

## Phase 7 — Source project and build pipeline

**Status: queued**

Move from transitional cache/output workflows to reproducible semantic projects.

- [ ] Define a versioned source manifest containing base-cache identity,
  project settings, semantic edits, dependencies, and output policy.
- [ ] Keep the base cache immutable; store Git-controlled semantic sources and
  disposable built-cache output separately.
- [ ] Implement deterministic source loading/writing and round-trip fixtures.
- [ ] Add `WorldCompiler`, `BuildReport`, `SemanticDiff`, and revision-audit
  outputs that reuse the canonical world/validator/asset layers.
- [ ] Turn FileStore incremental packing into a Studio `BuildPackService` with
  task progress, cancellation, diagnostics, and explicit output capabilities.
- [ ] Never start with opaque cache-file diffs or incremental packing rules
  that bypass the semantic source format.
- [ ] Add project open/save/recovery migration for existing `project.json`,
  autosave, staged output, and legacy projects.

### Exit gate

Two identical builds from the same source produce equivalent output and
reports; semantic diff explains changes; a project can be cloned, reopened,
rebuilt, and recovered without mutating its source cache.

## Phase 8 — Additional workspaces

**Status: queued**

Only start these once the Map Editor exit gate and source/build contracts pass.
They reuse the same shell, services, asset facade, plugin API, scene/runtime
state, settings, and task center.

- [ ] Object Studio: definition search, placement/model preview, variants,
  morphs, animation, collision, recolor/retexture, and “open from map”.
- [ ] Model Studio: model hierarchy/parts, materials, transforms, animation,
  lighting, and cache-definition inspection.
- [ ] World Map: local/region/world zoom levels, loaded/dirty/custom region
  status, search, bookmarks, and jump-to-local editing.
- [ ] Interface Studio: widget hierarchy, preview, properties, CS2/events,
  variables, and live-client reference capture.
- [ ] Build/Validation workspace: project graph, reports, semantic diff,
  revision checks, pack tasks, and output preview.
- [ ] Procedural workspace only when the nondestructive generator contracts
  prove insufficient for advanced users; do not require a node graph for the
  ordinary map workflow.

## Phase 9 — Remove the transitional architecture and release harden

**Status: queued**

Deletion is a deliverable, but only after the native acceptance gates pass.

- [ ] Migrate all production startup to the native application.
- [ ] Remove `LauncherWindow`, `MainWindow`, JavaFX/FXML production screens,
  `ControlledWorkspaceShell`, `SwingNode`, `AWTGLCanvas`, and
  `EmbeddedOpenGlViewport`.
- [ ] Remove `Options.*`, `LegacyRenderSettingsAdapter`, and callback-backed
  settings state after all consumers use the registry.
- [ ] Remove legacy plugin launcher/discovery and compatibility-only active/
  inactive JAR behavior.
- [ ] Keep JavaFX only if a deliberately retained diagnostic/test surface is
  still justified; otherwise remove its dependencies and runtime modules.
- [ ] Update README, architecture docs, packaging, and troubleshooting for
  OpenRune Studio rather than RSPSi transitional paths.
- [ ] Add `platformGate` for macOS Intel/ARM, Windows x64, and Linux x64,
  including native library loading, input, FBO, fonts, packaging, and a
  bounded smoke test.
- [ ] Verify clean startup, cache load failure, cancellation, dirty close,
  recovery, plugin crash/safe mode, missing assets, and renderer diagnostics.

### Exit gate

The native OpenRune Studio workflow is the only production path, the old paths
are deleted or explicitly quarantined as tests, and the platform matrix passes.

## Explicitly deferred until the gates are green

These are valid ideas, but they must not interrupt Phases 0–5:

- Modern skeletal/advanced animation formats beyond the supported fixture set.
- Runtime entities, particles, projectiles, and advanced effects.
- Full third-party Plugin Hub ecosystem and broad remote distribution.
- Server source application and runtime bridge beyond the read-only/declared
  task adapter.
- Multi-viewport native ImGui windows.
- Arbitrary node-graph procedural authoring.
- Large-scale interface/client simulation before the core source/build model
  is stable.

## Completion definition

OpenRune Studio is considered complete for this track when:

1. The native application vertical slice and Map Editor exit gates pass.
2. The semantic/render parity gates pass for the declared supported revision
   and external fixture set.
3. Editing is command-backed, undoable, validatable, recoverable, and saved via
   the semantic project/build path.
4. Camera movement and local edits do not cause avoidable full-scene or full-
   texture GPU rebuilds.
5. The plugin and Live Client boundaries are versioned, permissioned, and
   testable.
6. The legacy production paths have been removed or are provably isolated as
   diagnostics/tests.
7. The supported platform smoke matrix passes.

 The next implementation target is Phase 0. The first three concrete tasks are:

1. Finish and enforce the settings-consumer contract.
2. Add the missing semantic/render correctness fixtures that will drive the P0
   corrections in Phase 1.
3. Make the native GLFW + Dear ImGui dashboard vertical slice the only new
   frontend work, with no new JavaFX product features.

Reference documents remain useful as design and research records, but this
file is the sequence that decides what we do next:

- [`PHASE0_ACCEPTANCE.md`](PHASE0_ACCEPTANCE.md)
- [`PHASE0_PARITY_MANIFEST.example.json`](PHASE0_PARITY_MANIFEST.example.json)

- [`ARCHITECTURE.md`](ARCHITECTURE.md)
- [`PLUGIN_ARCHITECTURE.md`](PLUGIN_ARCHITECTURE.md)
- [`IMGUI_ADAPTER.md`](IMGUI_ADAPTER.md)
- [`OPENRUNE_FILESTORE_ADOPTION.md`](OPENRUNE_FILESTORE_ADOPTION.md)
- [`RENDERING_SYSTEM_AUDIT_2026-09-18.md`](RENDERING_SYSTEM_AUDIT_2026-09-18.md)
- [`RUNELITE_GPU_PIPELINE.md`](RUNELITE_GPU_PIPELINE.md)
- [`STUDIO_DIRECTION.md`](STUDIO_DIRECTION.md)
- [`SMART_MAP_TOOLS.md`](SMART_MAP_TOOLS.md)
