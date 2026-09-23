# AGENTS.md

Guidance for AI coding agents (and human contributors skimming for orientation) working in
this repository. See [docs/ROADMAP.md](docs/ROADMAP.md) for current direction and priorities -
this file is about how the repo is put together and how to work in it, not what to build next.

## What this project is

**OpenRune Studio** (repo name `RSPSiSuite`, root project `RSPSi`) is a from-scratch Java OSRS
map editor: a real per-triangle software renderer plus a native OpenGL renderer, a Dear ImGui
desktop shell, and its own plugin/settings architecture. It is not a RuneLite plugin and does
not embed RuneLite's client - RuneLite is present in this repo purely as **reference source**
for correctness (see below), the same way a spec document would be.

## Module layout

Two Gradle modules, declared in `settings.gradle`:

- **`Client`** - cache/definition loading, the world document model, the rendering pipeline
  (both software and OpenGL-neutral packet builders), editor tools, selection, and the public
  neutral `EditorPlugin` extension point. No ImGui/GLFW UI code lives here. Buildable and
  testable headless.
- **`Editor`** - the Dear ImGui/GLFW native desktop shell ("Studio"), including internal
  Studio projection code, all panels/HUDs/toolbars, and the OpenGL scene renderer that actually
  draws to a window.

**Public plugin boundary**: third-party and first-party feature plugins should prefer
`EditorPlugin` and neutral services. `EditorPluginContext` intentionally does not expose a raw
`DefinitionProvider`, but it does expose neutral cache-facing access through
`AssetRepository`, `PluginApi.data()`, and `DecodedDataCatalog`. Direct cache backend types,
Dear ImGui, GLFW, and OpenGL remain internal Studio implementation details. `StudioPlugin`
is a transitional/internal presentation API, not the public plugin model to teach new plugin
authors. See `docs/UI_WORKSPACE_CONTRACT.md` for where plugin UI contributions belong.

**RuneLite-shaped scene API**: `Client/src/main/java/com/rspsi/api` mirrors `net.runelite.api`
names and getters (`WorldView`, `Scene`, `Tile`, `SceneTilePaint`, `SceneTileModel`, `TileObject`
layers, `WorldPoint`/`LocalPoint`, `Perspective`), implemented by `com.rspsi.api.scene.SceneView`
over a resolved `GpuScenePacket`. It is Studio-owned, not a RuneLite dependency. Any RuneLite-style
setters must record undoable editor commands. Cite the `runescape-client`/`runelite-mixins` source
behind every value you add. See `docs/STUDIO_SEMANTIC_API.md` section 6.

## Build, test, run

```bash
./gradlew :Client:compileJava :Editor:compileJava   # compile both modules
./gradlew :Client:test :Editor:test                  # full test suite
./gradlew foundationGate                             # the repo's own CI gate (test + check + native-boundary + rendering-audit)
./gradlew :Editor:run                                 # launch the Studio desktop app
```

Java 21 toolchain (Gradle pins this - a newer system JDK won't get used accidentally). Always
compile **and** run the test suite for both modules after a change, even a Client-only one -
Editor consumes Client's public API surface and breaks silently otherwise.

`docs/RENDERING_PARITY_MANIFEST.json` is a live-maintained, schema-versioned list of rendering
correctness gaps against real OSRS behavior (covered/partial/deferred, with a `nextAction` per
entry). `./gradlew renderingAuditGate` validates its shape; treat it as the actual rendering
backlog, not something to re-derive from scratch. `docs/ROADMAP.md` defines product order,
`docs/PROJECT_LAUNCHER_AND_DASHBOARD.md` defines application startup/project lifecycle,
`docs/CONTENT_STUDIO_FOUNDATION.md` defines advanced-authoring prerequisites, and
`docs/UI_WORKSPACE_CONTRACT.md` defines the strict in-project editor-shell/UI contribution contract.

## Reference source trees (not part of the build)

Two external codebases are vendored into this repo purely as **read-only reference material**
for verifying OSRS-accurate behavior and mining design ideas. Neither is a build dependency -
don't add them to any `settings.gradle` include or `build.gradle` sourceSet.

### `RuneLite-melxin/` - real OSRS client reference

A full RuneLite fork (based on OpenOSRS, **BSD 2-Clause licensed** - see its own
`RuneLite-melxin/README.md`), added at the repo root. This is genuine, correctly-licensed
open-source client code and is safe to read, quote, and reimplement techniques from freely.

Use it to verify or port real client behavior. Start with `docs/RUNELITE_REFERENCE_GUIDE.md`
for the problem-to-source lookup table so the same deob/API/GPU paths are not rediscovered on
every PR. Some concretely useful starting points found this cycle (see `docs/ROADMAP.md` and
the parity manifest for current priority):

- Terrain underlay color blending: `runelite-client/cache/.../MapImageDumper.java`
  (un-obfuscated re-implementation; the real client's is `runescape-client/.../class470.java`,
  method `method9712`).
- Tile shape/triangulation tables: `runelite-api/.../scene/SceneTileModel.java`.
- Object silhouette highlighting: `Model.getConvexHull()` (`runelite-mixins/.../RSModelMixin.java`)
  + `Jarvis.convexHull()` (`runelite-api/.../model/Jarvis.java`) - already ported into this
  project as `Client/src/main/java/com/rspsi/editor/render/ConvexHull2D.java`.
- Plugin config-from-interface pattern: `runelite-client/.../config/ConfigItem.java` +
  `ConfigManager` - useful reference for neutral/declarative plugin settings UI.
- Zone-based incremental GPU rebuild (8x8 zones, dirty-flag invalidation): `runelite-client/.../plugins/gpu/Zone.java`,
  `GpuPlugin.invalidateZone`/`rebuild` - the validation reference for wiring in
  `Client/src/main/java/com/rspsi/editor/render/compiler/IncrementalSceneCompiler.java`
  (see the performance phase in `docs/ROADMAP.md`).

When citing something from here in a commit, comment, or design doc, name the exact file/method
(as above) rather than "RuneLite does X somewhere" - future readers (agents included) need to
be able to re-verify the claim without re-searching a 2000+ file tree.

### Terraini - reference notes only, no vendored source

Terraini is a sibling OSRS map editor built on the same cache/world-model lineage as this
project, but only available to us as **decompiled bytecode** (not source the original authors
published or licensed to us). Its license status is unknown. Unlike RuneLite-melxin, its
source is **not** vendored into this repo - copying decompiled third-party code, even for
internal reference, is a real copyright concern distinct from vendoring genuine open source.

Instead, `docs/TERRAINI_REFERENCE.md` holds detailed, original-wording technical notes on its
algorithms (road/path generation, procedural island noise, tile-coverage rasterization, stamp/
layer systems) - written from analysis, not copied from its decompiled output. Treat that
document as the reference; if you need to double-check a specific detail against the actual
decompiled classes, they exist locally outside this repo at
`/Users/tylercovalt/Desktop/RSPS/tools/map-and-terrain/terraini` on this machine (not committed
here) - go re-read them for verification, but bring back a written description, not a copied
file.

## Conventions worth knowing before you hit them

- **Connected OpenRune Server caches are generated artifacts, not generic Studio output directories.** In standalone mode a user may select any supported cache and publish to a separate explicit output cache. In connected OpenRune mode, `.data/cache/LIVE` is the read-only client/scene cache and `.data/cache/SERVER` is the separate read-only server cache. Do not directly patch either one and do not auto-run `FreshCache` on project open. Publish only through a supported OpenRune source representation, invoke the project's canonical `:or-cache:buildCache`, then reopen and verify both outputs. If no lossless source mapping exists for a resource, leave connected-project publishing disabled for that resource. See `docs/OPENRUNE_ECOSYSTEM_INTEGRATION.md`.
- **Local document space vs. absolute OSRS world-tile space are different coordinate systems
  and the compiler will not catch mixing them up.** `WorldTile`/`ToolContext` speak absolute
  world tiles (what the camera and picker use); `LocalTile`/`WorldDocument` speak
  document-relative tiles. `ModelPacketBuilder`'s output (`ModelRenderPacket.anchor()` and its
  vertices) is in **local** space even though it's built from a real placed object - converting
  it for anything camera/screen-facing requires `DocumentCoordinates.toWorld(...)` first. This
  exact gap silently broke the selection-overlay feature for an entire debugging session (every
  projected vertex landed ~400,000 units from the camera) before being traced down - see
  `Editor/src/main/java/com/rspsi/studio/ui/SelectionOverlayPlugin.java`'s
  `computeWorldVertices` javadoc for the fix and the full explanation.
- **Dear ImGui native color packing is ABGR (0xAABBGGRR), not the RGBA (0xRRGGBBAA) `OverlayDraw`'s
  own vocabulary uses.** Route through `StudioDrawColors`/`ViewportOverlayDraw.toImGuiColor(...)`
  rather than hand-packing a color for a native ImGui draw-list call.
- **Backface culling is deliberately disabled for models** in the native renderer
  (`BackfacePolicy` javadoc) because cache models aren't reliably wound. Don't "fix" this
  without winding-order fixtures passing first - it was tried before and broke walls/roofs/
  bridges. A renderer that *does* cull (e.g. a new software preview path) needs to emit
  double-sided geometry to match, not the other way around.
- **The current `StudioToolPlugin.surfaces()` model is transitional.** Existing code still
  supports bottom-bar/floating/left-rail placement, but new UI work must follow
  `docs/UI_WORKSPACE_CONTRACT.md`: the bottom rail activates tools, the Bottom Context Drawer
  owns deep tool content, the Left Brush Shelf owns brush/stamp dynamics, the Viewport Quick
  Palette owns near-cursor quick picks, the Right Inspector owns selected-item editing, and
  HUDs remain independently pinnable/glanceable. Do not infer tool capability from arbitrary
  surface placement.
- **Tightly-coupled small plugins should be nested inside the plugin they serve**, not given
  their own top-level file, when their only reason to exist is feeding another plugin (see
  `SelectionOverlayPlugin`'s nested `SingleObjectSelectToolPlugin`/`MultiObjectSelectToolPlugin`).
- Verify UI/rendering changes by actually launching `./gradlew :Editor:run` and looking, not
  just by compiling - this is a native GLFW/ImGui app with no UI test harness, and multiple
  "looks right in code" changes this project's history have turned out visually broken.
