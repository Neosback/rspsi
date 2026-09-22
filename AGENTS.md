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
  (both software and OpenGL-neutral packet builders), editor tools, selection, and the
  cache-agnostic `EditorPlugin` extension point. No ImGui/GLFW/UI code lives here. Buildable
  and testable headless.
- **`Editor`** - the Dear ImGui/GLFW native desktop shell ("Studio"), including the
  cache-aware `StudioPlugin` extension point, all panels/HUDs/toolbars, and the OpenGL scene
  renderer that actually draws to a window.

**The plugin-system split is deliberate but under-enforced**: if a plugin needs
`DefinitionProvider`/cache access (object definitions, models, textures), it has to be a
`StudioPlugin` in `Editor` - `EditorPluginContext` (`Client`) has no cache handle at all. If a
plugin is pure document/session logic that should work headless, it belongs in `Client` as an
`EditorPlugin`. When in doubt, check what `EditorPluginContext`'s record fields actually expose
before assuming a capability is available Client-side.

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
backlog, not something to re-derive from scratch.

## Reference source trees (not part of the build)

Two external codebases are vendored into this repo purely as **read-only reference material**
for verifying OSRS-accurate behavior and mining design ideas. Neither is a build dependency -
don't add them to any `settings.gradle` include or `build.gradle` sourceSet.

### `RuneLite-melxin/` - real OSRS client reference

A full RuneLite fork (based on OpenOSRS, **BSD 2-Clause licensed** - see its own
`RuneLite-melxin/README.md`), added at the repo root. This is genuine, correctly-licensed
open-source client code and is safe to read, quote, and reimplement techniques from freely.

Use it to verify or port real client behavior. Some concretely useful starting points found
this cycle (see `docs/ROADMAP.md` Part 1.3 for the full writeup):

- Terrain underlay color blending: `runelite-client/cache/.../MapImageDumper.java`
  (un-obfuscated re-implementation; the real client's is `runescape-client/.../class470.java`,
  method `method9712`).
- Tile shape/triangulation tables: `runelite-api/.../scene/SceneTileModel.java`.
- Object silhouette highlighting: `Model.getConvexHull()` (`runelite-mixins/.../RSModelMixin.java`)
  + `Jarvis.convexHull()` (`runelite-api/.../model/Jarvis.java`) - already ported into this
  project as `Client/src/main/java/com/rspsi/editor/render/ConvexHull2D.java`.
- Plugin config-from-interface pattern: `runelite-client/.../config/ConfigItem.java` +
  `ConfigManager` - the model for the declarative-settings idea in `docs/ROADMAP.md` Part 3.2.
- Zone-based incremental GPU rebuild (8x8 zones, dirty-flag invalidation): `runelite-client/.../plugins/gpu/Zone.java`,
  `GpuPlugin.invalidateZone`/`rebuild` - the validation reference for wiring in
  `Client/src/main/java/com/rspsi/editor/render/compiler/IncrementalSceneCompiler.java`
  (see `docs/ROADMAP.md` Part 2).

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
- **`StudioToolPlugin.surfaces()` defaults to all three chrome surfaces** (bottom bar, floating
  toolbar, tool rail) if unoverridden - a new tool plugin almost always wants an explicit
  override. The Left Tool Rail specifically is brush-only now (`isBrushTool()` gates it, not
  surface membership) - a non-brush tool has no reason to be there even if placed via the
  Plugin Manager's override UI.
- **Tightly-coupled small plugins should be nested inside the plugin they serve**, not given
  their own top-level file, when their only reason to exist is feeding another plugin (see
  `SelectionOverlayPlugin`'s nested `SingleObjectSelectToolPlugin`/`MultiObjectSelectToolPlugin`).
- Verify UI/rendering changes by actually launching `./gradlew :Editor:run` and looking, not
  just by compiling - this is a native GLFW/ImGui app with no UI test harness, and multiple
  "looks right in code" changes this project's history have turned out visually broken.
