# Map Studio UI/UX Backlog (session started 2026-09-20)

Tracking doc for the running list of UI/UX requests from this session so nothing gets lost
and nothing needs repeating. Update the status marker in place as items move; add new asks
to the bottom of their section instead of a new file.

Status key: `[x]` done and verified in a running build, `[~]` in progress / partially done,
`[ ]` not started.

## Done

- [x] Renamed "Map Editor" -> "Map Studio" everywhere user-facing (tab bar label; an older,
      newer tab-bar class had missed the earlier rename).
- [x] Removed everything floating over the 3D viewport that belongs in Settings instead
      (moved Plane/Layers/Wireframe-type controls off the viewport toolbar).
- [x] Fixed viewport toolbar horizontal overflow (removed the temporary debug cull-mode
      control that was pushing content past the viewport edge).
- [x] Dashboard redo: launcher not glued to the bottom, cache path tucked into settings once
      configured, FileStore inspection collapsed to a one-line summary by default, real
      centered loading splash with an animated indeterminate bar.
- [x] Fixed "map doesn't render" regression (stale `terrain.visible=false` etc. in
      `~/.openrune-studio/settings.json`).
- [x] Fixed tofu/diamond-with-question-mark glyphs (Unicode literals outside the loaded
      font's glyph range) across every touched file.
- [x] Simulation Clock moved out of the floating HUD over the viewport (confirmed not wired
      to animation playback yet - relocated honestly, not deleted, not left pretending to work).
- [x] Fixed the Tile Painter tool never actually registering at runtime (click-drag painting
      was silently a no-op even though the palette UI was fully built) - added
      `TilePainterToolPlugin` to `StudioApplication.initializePlugins()`.
- [x] Fixed two dead hotkeys (`B`/`E`) pointing at nonexistent tool ids.
- [x] Split the old combined "Tile Selector" tool into **Single Select** and **Multi Select**,
      full-size stacked buttons on the floating tool rail only (removed the old cramped
      side-by-side sub-toggle pill). Both wired to the real `BoxSelectTool.Mode` engine
      behavior (SINGLE ignores drag, MULTI does real marquee-drag), not a cosmetic flag.
- [x] Removed the tile-inspect icon from the bottom activity row.
- [x] Bottom drawer/shelf shows nothing and cannot be manually reopened while Single/Multi
      Select is active (`StudioToolPlugin.hasContextDrawerContent()`), since their only UI
      surface is the right panel now.
- [x] Renamed "Tile Brush" panel -> **Tile Inspector** (same icon), removed all the old
      apply-style single-value brush fields (Overlay/Underlay/Shape/Rotation/Height pickers)
      that duplicated the removed Tile Painter "apply" concept.
- [x] Tile Inspector now always shows two sections: current selection detail (single tile:
      coordinate, 4-corner heights, overlay shape/rotation, raw flags, underlay/overlay
      swatch; multiple tiles: count) and, always visible below it regardless of selection, a
      "Tiles Used In This Region" survey with swatch grids for distinct underlay/overlay ids.
- [x] Fixed bottom drawer running underneath the right sidebar (now stops at the sidebar's
      left edge instead of spanning the full window width).
- [x] Right sidebar now spans the full content height (down through where the drawer sits)
      instead of stopping short above it.
- [x] Plugins menu item no longer shares the gear icon with Preferences (gear reserved for
      Settings).
- [x] Bottom drawer/Tile Painter panel made a bit taller (`EXPANDED_HEIGHT` 180 -> 220).
- [x] Small gap added between the native menu bar and the workspace tab strip so tabs don't
      look tucked under the menu bar.
- [x] App status bar (very bottom): removed all icons, removed the tile/selection dump
      (duplicated the viewport HUD and Tile Inspector), now shows just "Ready" plus, right
      aligned, the loaded cache path, FPS, and (added this pass) live JVM memory usage in MB.
- [x] **Root-caused and fixed why the Tile Inspector never showed a selected tile.** This was
      the real bug, not a Tile Inspector rendering problem: nothing in the native ImGui
      viewport ever called `EditorTool.pointerDown/pointerDrag/pointerUp`. `NativeSceneViewport`
      has its own separate, older single-click "picker" (`updateSelectionFromInput()`, feeds the
      viewport's hover/status HUD only) that has nothing to do with the real `EditorSession`
      selection model `BoxSelectTool`/Single Select/Multi Select/the Tile Painter brush all
      write to. `EditorToolController`/`EditorInputRouter`/every `EditorTool` implementation
      (`BoxSelectTool`, `CompositeTilePainterTool`, height/path tools, etc.) were fully built
      and correct, just never wired to real mouse input - so no tool's pointer handlers ever
      ran, in this build, ever. Fixed by adding `NativeSceneViewport.dispatchToolInput(...)`
      (translates ImGui mouse state into `PointerEvent`s and calls the active tool's
      pointerDown/Drag/Up, skipping while orbit/pan drags the camera) and calling it from
      `MapEditorView.renderViewport()` right after `viewport.render(...)`. This also makes real
      click-drag Tile Painter brushing (not just the palette's Apply button) and Height
      Sculptor/Object Placement pointer interaction work for the first time.
      **Fully verified working end-to-end** with a real click, after fixing the coordinate-space
      crash this exposed (see next entry) - screenshot shows selection populated, the Tile
      Inspector's full data readout, and a persistent viewport highlight all working together.
- [x] **Fixed the app-crashing coordinate-space bug this pointer-dispatch fix exposed.**
      `NativeSceneViewport.tileAt()`/`pickAt()` return *absolute* OSRS world tile coordinates
      (e.g. `3234,3222`, matching the real map), but `WorldDocument` is a small array indexed by
      *region-local* coordinates (`0..width-1`) - it throws `IndexOutOfBoundsException: Tile
      outside world` for anything else. The moment real pointer events started reaching tools
      (previous entry), every tool that turns a pick directly into a `WorldDocument` lookup
      crashed the whole process on first use. Found via the user's own repro (selecting one tile
      crashed the app) and confirmed by grep: **18 `EditorTool` implementations** call
      `viewport().tileAt(...)`; fixed the ones actually reachable from the Studio's current
      tool-rail/toolbar (the rest are logged below as a known follow-up, not yet reachable so
      not yet exercised):
      - `BoxSelectTool` (Single/Multi Select) - only its `OBJECTS` target branch touched
        `WorldDocument` with the raw absolute bounds; local-converted before indexing. Also: (a)
        stopped clearing `start`/`current` on a successful `pointerUp`, so the selection outline
        now persists in the viewport until the next `pointerDown` or an explicit clear, instead
        of vanishing the instant the mouse is released (this was the "overlay doesn't stay" bug);
        (b) `renderOverlay` for a multi-tile marquee now outlines only the rectangle's border
        tiles instead of every tile inside it (was O(width x height) `tileOutline` calls per
        frame for a big drag - the likely source of "fps drop esp multi select").
      - `CompositeTilePainterTool` (Tile Painter) - both `addTile` (live click-drag) and
        `applyToCoordinates` (the palette's Apply button) now convert to local before touching
        `WorldDocument`, while keeping the dedup/overlay-tracking set on absolute coordinates so
        the brush-stroke overlay still draws at the real world position.
      - `ChangeHeightTool` (Height Sculptor) and `SmoothTerrainTool` (Path Builder's underlying
        engine tool, `terrain.smooth`) - same absolute-for-overlay / local-for-WorldDocument
        split applied to their vertex/height math.
      - `PlaceObjectTool` (Object Spawner) - converts to local before constructing the
        `WorldObject`, matching how objects already loaded from the cache are stored.
      - Added a `Client:test` run of the existing `CoreBoxSelectToolTest` to confirm no
        regression; all fixes compile clean and the app now runs a full select-a-tile cycle
        without crashing (screenshot-verified).
      - **Not yet fixed** (not currently reachable from the Studio's live tool set, so not yet
        exercised, but carry the identical bug): `PaintFlagsTool`, `DuplicateObjectTool`,
        `MoveObjectTool`, `RotateSelectionTool`, `RampTerrainTool`, `PaintUnderlayTool`,
        `PaintOverlayTool`, `RotateObjectTool`, `FlattenTerrainTool`, `ReplaceSelectionTool`,
        `MoveSelectionTool`, `DuplicateSelectionTool`, `LassoSelectTool`, `DeleteObjectTool`.
        Whoever wires any of these into the Studio UI next should apply the same
        absolute-pick/local-document conversion at the point of first `WorldDocument` contact.
- [x] ~~Hover highlight~~ - **implemented, then reverted the same pass** after the user reported
      "extreme lag... following the mouse." Root cause found and confirmed by reading
      `GpuPlanPicker.pick()`: it brute-force ray-triangle-tests *every triangle in the scene*
      (~327k in the test map) with no spatial acceleration structure - fine for a picker only
      called on an actual click, not something to run every frame while the mouse merely moves.
      Reverted `NativeSceneViewport.updateHoverFromInput()`/the hover overlay entirely rather
      than ship a throttled version, since even throttling to a few times a second would still
      cause a visible per-call hitch (the cost is in one synchronous call, not its frequency).
      Real fix needs either a fast picker (the class's own javadoc already names the intended
      direction: "a future native picker may replace the ray test with an ID framebuffer") or
      moving the raycast off the render thread - both bigger investments than this pass, logged
      below rather than rushed.
- [x] Tab bar background color now matches the shared panel gray (`0x26,0x28,0x2B`, same as the
      menu bar) instead of a near-black that clashed with everything else.
- [x] **Fixed the default tool showing as "on" (blue) without actually being active.** The
      active-tool field defaults to Single Select's id so its button lights up from frame one,
      but nothing had ever called `toolController.activate(...)` for it - only a real click via
      `activateTool()` does that - so the very first click after launch was silently just
      turning the tool on for real, matching "you have to reclick it to actually have it work."
      Fixed by activating the default tool for real the first frame the engine host is ready
      (`MapEditorView`, new `defaultToolActivated` guard).
- [x] **Fixed active tool buttons visually reverting to the unselected/hover color until the
      mouse moves away.** `FloatingToolbar`'s per-button styling only pushed `ImGuiCol.Button`
      for the active-blue tint, not `ButtonHovered`/`ButtonActive` - ImGui's own hover/press
      colors were painting over it while the cursor sat on the just-clicked button, so it looked
      "off" until the mouse left. `StudioBottomBar`'s activity row had the identical gap;
      `StudioToolRail` already covered `ButtonHovered` correctly and needed no change. Fixed by
      pushing matching Hovered/Active shades of the same active color in both places.
- [x] **Tile Inspector: replaced the two separate flat underlay/overlay swatches with one
      combined "live tile" preview** - underlay fills the tile, the real overlay (textured, via
      the WYSIWYG work above, when the overlay has one) covers only the portion its shape says
      it should: the full tile for shape 0 (paths, water, plain overlays - the common case), or
      a rotation-aware diagonal half-tile for any other shape as a legible approximation. Full
      per-shape accuracy (OSRS has 12 real overlay shapes over a 14-point tile mesh - found the
      real rule table at `Client/src/main/java/com/rspsi/osrs/rules/tile/TileShapeRules.java`,
      `SHAPE_POINTS`/`ELEMENTS`) was deliberately not attempted this pass: that table has no
      point-to-2D-position mapping anywhere in the codebase (grepped - zero consumers reference
      it today, so it isn't even wired into the real 3D renderer either), and guessing at the 14
      point positions risked shipping a confidently-wrong shape instead of an honest
      approximation. Numeric shape/rotation values are still shown alongside the preview so nothing
      is hidden, and this is logged below as the concrete next step once the real geometry is found.
      User confirmed (with a screenshot: shape 9 rendered as a plain diagonal split, when shape 9
      is actually one of the more complex 4-triangle shapes) that this approximation visibly
      doesn't match for non-trivial shapes - expected, given the above, not a new bug.
- [x] **Plane-restricted picking**: clicking/selecting is now confined to the active plane
      (`RenderSettingKeys.ACTIVE_PLANE`) even while other planes are visible via "show all
      levels" - previously a click could land on any visible plane's geometry regardless of
      which one was actually being edited. `GpuPlanPicker.pick(...)` gained an optional
      `restrictToPlane` parameter (skips draw commands on any other plane before the
      ray-triangle test even runs); `NativeSceneViewport.setPickPlaneRestriction(...)` is set
      from the active-plane setting once per frame in `MapEditorView.renderViewport()`, before
      any pick happens that frame. Existing `GpuPlanPickerTest` still passes unchanged (the new
      parameter defaults to unrestricted via the old overload).
- [x] Status bar now also shows live JVM memory usage (used MB) alongside cache path and FPS -
      **visually confirmed** in a screenshot ("567 MB" next to the FPS counter).
- [x] **WYSIWYG real RS textures for the Tile Inspector's overlay swatches** - findings first,
      confirmed by reading the actual OSRS decoder source
      (`OverlayDecoder.kt`/`UnderlayDecoder.kt`): underlays only ever carry a flat color (opcode
      1), never a texture - the existing flat-color swatch was already fully accurate for them,
      nothing to fix there. Overlays can carry a `textureId` (opcode 2) - our own
      `FloorDefinitionView.texture()` already exposes it, and `DefinitionProvider.texturePixels
      (id, 0.6, 128)` (used internally by the real 3D renderer, `RenderTextureResourceBuilder`)
      returns the actual decoded pixels at the client's fixed brightness/size contract. Added
      `OverlayTextureCache` (`Editor/.../studio/ui/OverlayTextureCache.java`) to upload those
      pixels to a small cached GL texture per OSRS texture id (RGB packed 0x00RRGGBB, pure-black
      treated as the OSRS cutout/transparency sentinel, matching
      `RenderTextureResource.hasTransparentPixels()`'s own convention), and wired
      `TileBrushPanel`'s swatch drawing to render that texture via `ImGui.image(...)` instead of
      a flat rect whenever an overlay has one. **Visually confirmed**: 2 of 7 overlay swatches in
      a test region show real grained ground-texture detail instead of flat color, the rest
      (untextured overlays, and the "unmapped id" magenta placeholder) correctly stay flat.
      Animation note: overlay *textures* can be animated (scrolling UV, e.g. water) via
      `TextureAnimation`/`animationDirection`/`animationSpeed` on the texture definition - this
      is already real and working in the live 3D viewport. The tiny UI swatch intentionally
      shows a static frame rather than looping the animation - a reasonable scope cut, not a gap
      in "what data exists," if a future pass wants swatch animation this is where it'd hook in.
- [x] **Implemented the generalized plugin contribution model** designed after the RuneLite
      review (see "Design decisions" below):
      - `StudioToolPlugin.ToolSurface` enum (`BOTTOM_BAR`/`FLOATING_TOOLBAR`/`TOOL_RAIL`) +
        `surfaces()` default method (defaults to all three, so no existing tool changed
        behavior); `showInBottomBar()` is now a thin convenience view onto `surfaces()`.
        `StudioBottomBar`, `FloatingToolbar`, and `StudioToolRail` all now filter their tool
        list through `StudioPluginManager.effectiveSurfaces(tool)` instead of each having its
        own ad hoc (or nonexistent) filtering.
      - `StudioPluginManager` gained a per-tool surface **override** map
        (`setSurfaceOverride`/`resetSurfaceOverride`/`hasSurfaceOverride`) - this is the
        "restrictions and controls" mechanism: a user can customize which surfaces a given
        tool's button appears on without touching code. Session-scoped only for now (not yet
        persisted across restarts - see "Not started").
      - `StudioToolPlugin.ownedPanel()` (mirrors RuneLite's `NavigationButton.panel`): a tool
        can optionally return a `StudioPanel` it owns, registered once into the right
        sidebar's `StudioPanelManager` when the tool plugin registers
        (`StudioPluginManager.setOwnedPanelSink`, wired in `MapEditorView`) - this directly
        answers "how does a plugin give itself a panel if the tool uses one?" No built-in tool
        uses this yet (none needed a dedicated owned panel beyond what already exists), but the
        mechanism is live and ready for the next plugin that does.
      - Plugin Manager's per-plugin config page (`PluginManagerPanel.renderPluginConfigPage`)
        now shows a "Placement" section with one checkbox per `ToolSurface` for any
        `StudioToolPlugin`, plus a Reset button when an override is active - this is the actual
        UI the user interacts with to customize placement.
      - Compiled and relaunched clean (registration log shows all 6 built-in tool plugins
        registering with no errors); a full interactive click-through of the new checkboxes
        was not screenshotted this session for the same window-focus reason as the pointer-
        dispatch fix above.

## In progress / partially done

- [~] **Tile Painter rework** - the real composite multi-channel brush tool
      (`CompositeTilePainterTool` + `TilePainterPalette` checkboxes) exists and is registered,
      but the user says it "still needs reworked" - not yet re-verified end-to-end after the
      registration fix, and the checkbox-per-category brush-stroke behavior (vs. old
      apply-to-selection replace semantics) needs a fresh pass against Displee's reference.
      The pointer-dispatch fix above may change what "re-verify" even means here (click-drag
      painting may now genuinely work where it silently couldn't before).
- [~] **Full theme/color pass** - some of this landed already (right panel restructure,
      minimap, tile painter checkboxes, tab bar color) but the user has not confirmed
      satisfaction. Explicit new ask: unify panel background colors so they're consistent
      panel-to-panel (right sidebar, tool rails, bottom drawer, dashboard) instead of clashing -
      tab bar fixed, the rest not started as a dedicated pass.
- [~] **Persisting per-tool surface overrides across restarts** - the override mechanism itself
      is implemented and working in-session (see "Done" above); it does not yet survive an app
      restart (would need a small `SettingsStore` entry per tool id, similar to other persisted
      UI state).

## Not started

- [ ] **Hover highlight for tile/object under the cursor** - explicitly wanted, but blocked on
      `GpuPlanPicker` being too slow to call every frame (see "Done" - implemented and reverted
      this pass). Needs a fast path first: either an ID-framebuffer/GPU picker, a spatial index
      (BVH/grid) over the scene's triangles so a raycast doesn't touch all 327k of them, or an
      async off-render-thread raycast with a one-frame-stale result. Once a fast picker exists,
      also make it object-aware (`PickResult` already carries `objectHit()`/`objectId()`) so an
      object under the cursor gets a distinct highlight (`OverlayDraw.box(...)` already exists
      for this) instead of just a tile outline.
- [ ] **Tile-picking accuracy question** - reported twice now, second time more specifically:
      the selected tile is consistently "lower than where the mouse click actually happened,"
      not just a vague perspective mismatch. Read `GpuPlanPicker.pick()`/`ray()` closely: the
      math *looks* like a proper perspective ray constructed from camera yaw/pitch and
      intersected against real terrain vertex positions (Möller-Trumbore), not a naive
      vertical-drop/flat projection - so if there's a real bug it's likely a sign/axis error in
      `ray()`'s camera-space reconstruction, `SceneOcclusionResolver` picking the wrong triangle
      when several overlap under the cursor, or a coordinate-space mismatch between
      `NativeSceneViewport`'s `imageOriginX/Y` subtraction and whatever unit ImGui's mouse
      position is actually in. A consistent, repeatable "lower" offset (rather than a
      direction-dependent one) points more toward a fixed axis/offset bug than a perceptual
      side-effect of perspective picking on sloped terrain. Deliberately still not touched -
      camera-math is exactly the kind of code where a rushed guess under time pressure is more
      likely to make it subtly worse than better; needs a dedicated pass with side-by-side
      screenshots at a few camera angles (and ideally on flat ground, to rule out slope-related
      perceptual effects) to actually confirm what's happening before changing anything.
      **Update**: the user pushed back that CPU raycasting is even the right approach and
      pointed at a reference implementation (`/Users/tylercovalt/Desktop/oldlostproject/src`)
      using GPU picker-ID readback instead - a second integer color attachment on the scene FBO
      that the fragment shader writes a packed id to (`valid bit | plane(2b) | tileX(13b) |
      tileY(13b) | slot(3b)`), read back with a single `glReadPixels`/PBO-mapped pixel at the
      cursor instead of any CPU-side triangle test. This is architecturally the right fix, not
      just a workaround: it's pixel-exact by construction (whatever the GPU actually rasterized
      at that pixel *is* the answer, no ray-vs-mesh math to get subtly wrong) and O(1) per query,
      which would also unblock the hover-highlight feature reverted above for being too slow on
      the current brute-force picker. This is a real rendering-pipeline change (new FBO
      attachment, shader changes, vertex format changes to carry the packed id, PBO
      double-buffering, an `objectForPicker` map keyed by packed id) - not a quick patch, and
      `GpuPlanPicker`'s own javadoc already named this exact direction as the intended future
      path. The actual port is a good candidate for its own dedicated session rather than folding
      into this backlog's incremental-fix cadence, given the size and the fact that it touches
      the OpenGL renderer core directly.

      **Research complete** - concrete facts pulled from
      `/Users/tylercovalt/Desktop/oldlostproject/src` (read-only, nothing ported yet):
      - Bit layout (`PickerId.java`): `bit0 valid | bits1-2 plane | bits3-15 tileX(13b) |
        bits16-28 tileY(13b) | bits29-31 slot(3b)`, 32 bits total. `encodeTile`/`encodeObject`
        pack, `plane`/`tileX`/`tileY`/`slot`/`isValid` unpack via shift+mask.
      - FBO: a second color attachment (`GL_COLOR_ATTACHMENT1`) holding a `GL_R32UI` texture
        (`GL_NEAREST` filtering - IDs must never be interpolated), cleared per-frame with
        `glClearBufferuiv`. Fragment shader has a second `out uint fragPickerId` written from an
        interpolated per-vertex value; `glDrawBuffers` targets both attachments during the main
        pass - no second geometry pass.
      - Vertex format: 14 floats/vertex; the last 4 (`aPickerPlane/X/Y/Slot`) are computed once
        per tile (`emitTerrain`) or once per object slot per tile (`emitTileObjects`) and
        broadcast to every vertex of that tile's/object's triangles. The vertex shader re-packs
        those 4 floats into the same bit layout as `PickerId` in GLSL (mirrors the CPU packer
        exactly, rather than uploading one pre-packed int).
      - Readback: two paths. `pickerReadbackImmediate` does a synchronous `glReadPixels(x,y,1,1,
        GL_RED_INTEGER,GL_UNSIGNED_INT,...)` every frame (used in the main draw loop).
        `pickerReadback` is a double-buffered PBO ring (`GL_PIXEL_PACK_BUFFER`,
        `GL_STREAM_READ`) - frame N issues an async read into `PBO[N%2]` and maps
        `PBO[(N-1)%2]` (last frame's, already GPU-complete) for zero-stall hover reads.
        `resolveMsaaPickerIfNeeded` blits attachment 1 from an MSAA renderbuffer
        (`GL_R32UI` via `glRenderbufferStorageMultisample`) into the non-MSAA texture with
        `GL_NEAREST` before either readback runs, only when MSAA is active that frame.
      - Object resolution: a plain `HashMap<Integer,DefaultWorldObject>` (`pickerObjects`),
        cleared once per frame, populated with `putIfAbsent(picker, object)` during emission -
        `objectForPicker(picker)` is a hashmap hit, no scanning. A *separate*
        `IdentityHashMap<DefaultWorldObject,Boolean>` only dedups re-emitting a multi-tile
        object's geometry once per occupied tile - not used for picker lookup itself.
      - Cheap rejection (`applyGpuPick`): `!PickerId.isValid(picker)` short-circuits
        immediately; a select-object/NPC tool whose pick lands on a different plane than the
        active one falls back to a CPU ray-vs-plane cast just to re-derive tile X/Y (this is
        the *only* per-frame CPU raycast left, and only fires on a wrong-plane pick, not every
        frame); object/NPC resolution is further gated by matching the pick's slot category to
        the active tool before any hashmap lookup happens at all.
      - macOS: the reference project already targets Apple Silicon macOS and works around its
        capped GL support by pinning the *entire* renderer to GL 3.2 Core (forward-compatible,
        no SSBOs/compute shaders) rather than branching the picker code itself per-OS. Every GL
        feature the picker path uses (`GL_R32UI`, integer FBO attachments, `glClearBufferuiv`,
        `GL_PIXEL_PACK_BUFFER`/`glMapBuffer`, `glBlitFramebuffer`, multisampled integer
        renderbuffers) is core since GL 3.0, comfortably inside our own GL 4.1 core ceiling on
        Apple Silicon - no macOS-specific picker workaround should be needed for our port.

      **Our own renderer, mapped for the port** (`Client/.../render/GpuSceneVertex.java`,
      `Editor/.../renderer/opengl/OpenGlSceneRenderer.java`, `Editor/.../studio/GlFramebuffer.java`):
      - `GpuSceneVertex` is 12 floats today, no per-vertex tile/object identity (identity lives
        only on the accompanying `GpuDrawCommand`). Adding 4 picker floats (plane/x/y/slot) is a
        record-field change plus updates at exactly two emission sites,
        `GpuUploadPlanBuilder.terrainVertex`/`modelVertex` - the tile/model reference is already
        a local variable in scope at both, so this is additive, not a restructure.
      - GL context is pinned to **3.3 core** (not 4.1 - that's just what the driver reports as
        max available on this Mac); `GL_R32UI`/`usampler2D`/`layout(location=1) out` are all
        core since GL 3.0, so no context bump is needed.
      - `GlFramebuffer` has exactly one color attachment today (`GL_RGBA8`, `GL_LINEAR`), on
        both its resolve and MSAA-multisample paths, with an existing `GL_NEAREST` blit-resolve
        step already in place for MSAA - extending both paths with a second `GL_R32UI`
        attachment (forced `GL_NEAREST` sampler params) follows an already-established pattern
        rather than inventing a new one, though the resolve blit will need explicit
        `glReadBuffer`/`glDrawBuffers` (MRT) calls it doesn't make today.
      - Only two real call sites of `GpuPlanPicker.pick(...)` exist: `NativeSceneViewport`
        (the main viewport) and `EmbeddedOpenGlViewport` - which **already carries a doc
        comment anticipating this exact change** ("a neutral correctness path until the native
        ID-buffer pass is added; callers receive the same tile/object identity either way"),
        confirming the API was designed to make this swap transparent to both callers.
      - Our plane-restriction fix (added this session, see "Done" above) translates directly
        and even more cheaply to the GPU path: rather than the reference project's fallback of
        re-casting a CPU ray when a pick lands on the wrong plane, we can simply not write a
        valid picker id for fragments outside the active plane in the first place - no
        fallback cast needed at all.

      **Phased implementation plan** (Phase 1 done, rest pending):
      1. **DONE.** Added `PickerId` (`Client/.../render/PickerId.java`) - the canonical bit
         layout (`valid|plane(2b)|tileX(13b)|tileY(13b)|slot(3b)`), matching the reference
         project's scheme exactly, plus `terrainSlot()`/`slotFor(SceneLayer.Kind)` helpers so
         terrain and every model layer (WALL/WALL_DECORATION/GROUND_OBJECT/GROUND_DECORATION)
         get a distinct, stable slot. Added 4 fields to `GpuSceneVertex`
         (`pickerPlane`/`pickerTileX`/`pickerTileY`/`pickerSlot`), populated in
         `GpuUploadPlanBuilder.terrainVertex`/`modelVertex` from the tile/model address already
         in scope at both emission sites, broadcast identically to every vertex of that
         tile's/object's triangles - exactly the plan. Threaded `layer.kind()` into
         `modelVertex` (it didn't receive it before). Fixed the one other production
         `GpuSceneVertex` construction site (`SoftwareSceneRenderer.interpolateVertex`, a
         clip-interpolation helper - carries the payload through unchanged since clipping never
         crosses a tile/object boundary) and 5 test-fixture constructors that needed the new
         trailing arguments. Added `PickerIdTest` (round-trip pack/unpack, one per
         `SceneLayer.Kind`, max-13-bit-coordinate case) and a new
         `GpuUploadPlanBuilderTest.everyVertexCarriesItsTileOrObjectsPickerPayloadBroadcast` test
         asserting the broadcast is correct for both a terrain triangle and a model triangle in
         the same tile. Full `Client` test suite passes, `Editor` compiles clean, app relaunches
         and renders with no regression (the new fields aren't consumed by the GL upload yet -
         phase 2/3 - so this phase is purely additive to the data model).
      2. Extend `GlFramebuffer`: second `GL_R32UI` texture (2D and 2D-multisample variants),
         `GL_COLOR_ATTACHMENT1` on both the multisample and resolve FBOs, `GL_NEAREST` sampler
         params, `glDrawBuffers` targeting both attachments during the scene pass, and an
         MRT-correct resolve blit.
      3. Extend the vertex/fragment shaders: 4 new vertex attributes, GLSL-side bit-packing
         (mirroring `PickerId`'s CPU-side pack/unpack exactly, same bit layout), a second
         `flat out uint`/`in uint` varying, and `layout(location = 1) out uint outPickId` in
         the fragment shader, written unconditionally (or written as 0/invalid for
         non-active-plane fragments per the plane-restriction note above).
      4. Add a synchronous readback path first (`glReadPixels` on `GL_COLOR_ATTACHMENT1`,
         `GL_RED_INTEGER`/`GL_UNSIGNED_INT`) behind a small `PickerId`-equivalent pack/unpack
         utility on our side, wired into `NativeSceneViewport.pickAt`/`tileAt` so both existing
         call sites keep working unchanged. Correctness first, matching
         `pickerReadbackImmediate`.
      5. Add the double-buffered PBO ring for zero-stall reads once the synchronous path is
         proven correct - this is what actually unblocks a lag-free hover highlight (the
         feature reverted earlier this session for being too slow on the CPU raycaster).
      6. Add an `objectForPicker`-equivalent `HashMap<Integer, WorldObject>` populated during
         emission (mirrors `pickerObjects`/`putIfAbsent`) so object picks resolve without any
         per-frame scanning.
      7. Keep `GpuPlanPicker`'s CPU raycaster as-is for the software/non-GL rendering path
         (`SoftwareSceneRenderer`) and tests - it isn't deleted, just superseded for the primary
         GL viewport.

      **What else from the reference project is/isn't worth bundling in** (verified, with
      corrections - the reference project oversold a few of these in the user's own summary):
      - **Worth adopting, self-contained, cheap**: the slope-conforming tile-highlight quad
        (`GL32SelectionOverlay.addTileQuad` - samples the 4 real corner heights + a small lift
        constant so a highlight "hugs" sloped terrain instead of floating flat above it). This
        is small, proven, and directly applicable to our own `ViewportOverlayDraw.tileOutline`
        regardless of the picker port - worth doing either alongside it or on its own.
      - **Already have it, no action needed**: opaque-first depth-tested draw + back-to-front
        sorted translucent pass - confirmed in our own `OpenGlSceneRenderer`
        (`GpuDrawCommand.SubmissionPass.OPAQUE`/`ALPHA`, `opaqueOrder`, blend only enabled for
        the alpha pass). The reference project's version of this is not a gap for us.
      - **Deliberately not adopting**: `GL_STREAM_DRAW`-every-frame buffer reuse. Checked our
        own renderer - it uses `GL_STATIC_DRAW` and only re-uploads on an actual scene-content
        change (fingerprint-gated). That's the *correct* choice for an editor with a mostly-
        static loaded region, not a live game client with a constantly-moving streaming camera;
        their pattern solves a problem we don't have.
      - **Not currently a gap, but worth knowing about**: camera-centered render-distance
        culling. We have none today, but our loaded scenes are one (or a few) 64x64 regions,
        not an open streaming world, so it isn't costing us anything yet. Worth revisiting only
        if/when multi-region editing windows grow large enough that full-region rendering
        becomes the bottleneck.
      - **Correcting the reference project's own summary, not worth porting as originally
        described**: dirty-generation counters are a single global epoch counter in their code,
        not the category-scoped (terrain vs. object) system described - the "terrain-only"
        variant exists but is dead/unwired even there. Software-path occlusion culling
        (`needsRendering`/`tileQueue`) is confirmed dead code - `renderScene()` is never called
        by either of their live renderers. The ghost-tile live preview
        (`addTemporaryTile`/`SceneTile.temporarySimpleTile`) is a clean *idea* but isn't
        actually wired into their live GL renderer either (only their own dead legacy path
        reads it, and its one real caller is a paste/import tool, not a general brush-drag
        preview) - porting it would be new engineering either way, so it's better to design a
        preview mechanism directly against our own `GpuDrawCommand`/vertex architecture than to
        "port" something that was never proven working in the reference project's own current
        renderer.
- [ ] Tile Inspector completeness: user wants the single-tile detail view to be a genuinely
      "realistic" preview - a visual (not just numeric) rendering of the tile's actual overlay
      shape/rotation, and the overlay texture swatch (already added - see "Done" WYSIWYG entry)
      should be checked against tiles that visibly have one, since the one screenshotted showed
      "Overlay: #10" as flat black, suggesting either that overlay genuinely has no texture or
      the swatch isn't finding one that exists - needs a direct check against an overlay id
      known to be textured (e.g. water) rather than assuming.
- [ ] Re-investigate "single select acts funny, you need to re-click the toggle to get a better
      overlay" - not reproduced/diagnosed yet; possibly related to `BoxSelectTool.activate()`
      calling `clear()` on every tool switch (including switching *to* Single Select), which
      would blank any prior overlay until the next click - or possibly a separate issue. Needs
      a precise repro before touching `BoxSelectTool` again given how much just got fixed there.
- [ ] Swatch color accuracy question (raised as "grass green looks a bit off vs the preview"):
      confirmed the renderer draws underlay/overlay tiles from the same `rgb()` field the Tile
      Inspector's flat-color swatches already use (`TerrainAppearanceBuilder` doesn't apply a
      gamma/exponent on top of it) - so the stored color value itself matches. The perceived
      mismatch is most likely the 3D scene's lighting/shading (ambient, directional light,
      fog) making the same underlay look different there than as a flat, unlit 2D swatch,
      which real texture swatches (see the WYSIWYG entry in "Done") don't have this problem for
      once they're textured - not confirmed as an actual wrong-color bug; flagging rather than
      guessing at a fix that might not address the real cause.
- [ ] Centralized, remappable hotkey system: one place in the codebase that owns every
      keybinding (today they're scattered `ImGuiKey` checks in `MapEditorView` methods like
      `routeSessionShortcuts`/`handleGlobalShortcuts`), a way for the user to view/change/add
      bindings, and Mac/Windows-safe, laptop-friendly (no numpad-only, no mouse-required)
      defaults that don't collide with OS-reserved combos on either platform. User supplied
      Displee's own hotkey list as a reference (Ctrl+O open cache, Ctrl+S save, Ctrl+Shift+S
      release/save+bump version, Escape clear selection, double-click pick tile/object,
      Ctrl+Left-click select, Ctrl+Scroll rotate, Ctrl+Shift+Scroll change shape/type, Ctrl+D
      duplicate object, Ctrl+X remove object, Ctrl+1..4 set object level) - explicitly not all
      of it applies 1:1 (some of ours differ), it's a reference starting point, not a spec to
      copy verbatim.
- [ ] Minimap / World Map bug: clicking the minimap to jump the camera doesn't reload the 3D
      viewport (goes black or breaks) - likely related to the pointer-dispatch root cause just
      fixed above (worth re-checking before assuming a separate bug). Also: no point-of-interest
      map icons rendering on the minimap texture itself.
- [ ] Investigate whether having both a "World Map" and a "Minimap" side-panel entry is
      intentional (user said "this is okay maybe we have two then") vs. a duplicate that
      should collapse into one.
- [ ] "On both of them there is a left tool rail that we need all the buttons removed" -
      exact scope unclear (which two surfaces, which rail) - needs a follow-up screenshot/
      clarification before touching anything, rather than guessing.
- [ ] Full theme/color makeover across every panel (see "in progress" above) - needs a real
      design pass, not incremental patches: audit every hardcoded ImGui color across
      `StudioTheme`, `StudioRightSidebar`, `StudioToolRail`, `FloatingToolbar`,
      `StudioBottomBar`, `DashboardView`, and `ui/panels/*`, and converge on one small palette.
- [ ] Bottom drawer / dock panel drag-to-resize height (grab the top edge and drag).
- [ ] Tab-close confirmation ("are you sure" / pending-saves check) generalized to all
      workspaces, not just Map Studio.
- [ ] Root-cause and fix the red rendering artifact in the viewport corner (seen across many
      screenshots, never diagnosed).
- [ ] Evaluate adopting bundled `imgui-java` extensions the user researched and pasted in:
      ImGuizmo (3D transform gizmos + navigation cube), ImNodes (node-graph tooling for map
      streaming / procedural tile rules), ImGuiColorTextEdit (embedded script editor),
      ImPlot (real-time profiler/budget gauges), ImGuiFileDialog (in-style file picker vs.
      OS-native tinyfd dialogs already planned). Also flagged: JOML for raycasting/picking,
      fastutil/HPPC for GC-free tile/coordinate maps, a shader-based world-space grid and
      brush-radius overlay (fragment shader distance field) instead of `ImDrawList` for
      anything that needs to conform to terrain height. This is background research the user
      supplied for future architecture direction, not a committed scope item yet - needs its
      own dedicated planning pass before any of it is implemented.

## Design decisions

### Plugin panel/placement contribution model

The user's question: "if I go add a new plugin and have it go in the right tool rail or
whatever we call it, how does it give itself a panel to use if the tool uses one? what design
structure are we going with for the plugins?" - plus an explicit ask to review RuneLite's
plugin system (`/Users/tylercovalt/Desktop/RSPS/clients/runelite`) since it solves the same
kind of problem (plugins contributing side-panel UI, toolbar buttons, overlays, config).

**What RuneLite actually does** (confirmed by reading its source, not assumed):

- One unifying value type, `NavigationButton` (icon, tooltip, `onClick`, optional `panel`,
  priority), registered with a single call: `clientToolbar.addNavigation(button)`. Whether the
  button becomes a sidebar tab or a plain toolbar button is decided purely by whether
  `.panel(...)` was set - not by two different registration paths.
- Sidebar is single-active-tab (a `JTabbedPane`); a plugin's `PluginPanel` is what's shown when
  its tab is selected. A panel needing its own internal drill-down (list -> detail) uses
  `MultiplexingPluginPanel`, a `CardLayout` stack - still one visible child at a time.
- Config (`@ConfigGroup`/`@ConfigItem` on a plain interface) is **fully reflective** - the
  config panel discovers items via reflection and picks the right widget by return type/
  annotation. No plugin ever writes settings-rendering code.
- Overlays are a separate concern (`OverlayManager.add(Overlay)`, a fixed `OverlayPosition`
  enum of viewport slots) - multiple can render at once, unlike the single-active sidebar.
- **Honest finding: there is no generic extension-point abstraction.** It's a fixed, small menu
  of independent manager singletons (toolbar/sidebar, overlays, config, mouse/key input,
  events) that a plugin's `startUp()` proactively calls into. Adding a genuinely new surface
  means writing a new manager class, not implementing some existing generic contract.

**What this means for us**: building a fully generic "any plugin can define any surface"
system would be *more* general than RuneLite's own proven design, and RuneLite runs the
entire community plugin ecosystem on the fixed-menu approach. So the fixed-but-well-defined
menu is the validated choice, not a compromise. Proposed shape, adapted to what we already
have (`StudioToolPlugin`/`StudioPlugin`/`StudioPanelManager`/`StudioPluginManager`):

1. Generalize the narrow `showInBottomBar()` boolean (added this session) into a
   `Set<ToolSurface> surfaces()` default method, `enum ToolSurface { BOTTOM_BAR,
   FLOATING_TOOLBAR, TOOL_RAIL }`, defaulting to all three (today's behavior, so nothing
   regresses). The Plugin Manager panel gets checkboxes per surface per tool - this is the
   concrete answer to "restrictions and controls" for multi-placement.
2. Mirror `NavigationButton.panel` directly: let a `StudioToolPlugin` optionally return a
   `StudioPanel` it owns (`default Optional<StudioPanel> ownedPanel() { return
   Optional.empty(); }`). When set, activating the tool from *any* surface it's placed on
   opens that panel - the bottom drawer today already does this ad hoc via
   `renderContextDrawer`/`hasContextDrawerContent()`; this generalizes it into the same
   mechanism the right sidebar's `StudioPanelManager` uses, so a plugin doesn't need to know
   or care which physical surface ends up hosting its panel.
3. Keep settings fully reflective already-ish via the existing `SettingsRegistry`/
   `SettingSpec`/`SettingScope` system (`Client/.../editor/settings/`) - this part of our
   architecture already matches RuneLite's reflective-config approach in spirit; the gap is
   only in UI *placement*, covered by points 1-2.
4. Leave `EditorPlugin` (Client-module, engine/tool-registration level) and `StudioPlugin`
   (Editor-module, chrome-level) as two layers rather than forcing a merge - RuneLite doesn't
   have this split because it has one process, but conceptually `EditorPlugin` already plays
   the RuneLite-"Plugin"-as-Guice-module role (owns real tool/settings registration) while
   `StudioPlugin`/`StudioToolPlugin` plays the RuneLite-"NavigationButton"-registration role
   (owns chrome placement). Document this relationship rather than collapsing it.

**Implemented this session** - see "Done" above for the concrete classes/methods. Remaining
follow-up: persist surface overrides across restarts, and have a real plugin actually exercise
`ownedPanel()` once one needs it.

## Notes / open questions to resolve before acting

- The "left tool rail buttons removed" ask (see above) needs the user to point at exactly
  which rail/surface in a screenshot before it's actioned, to avoid guessing wrong twice in a
  row on the same kind of request.
- The shader/ImGuizmo/JOML research dump is real engineering direction worth taking
  seriously, but represents a much larger scope (new rendering-pipeline hooks, new native
  library dependencies) than the incremental UI fixes above - should get its own plan instead
  of being folded into this backlog's quick-fix cadence.
