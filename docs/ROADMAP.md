# OpenRune Studio Roadmap

_Started fresh 2026-09-21. The previous 43-file `docs/` audit trail was retired - this
single document is the new source of truth for direction. It is meant to be edited in
place as work lands, not archived-and-replaced like the old one._

## How to read this

Every claim below is grounded in something checked against the actual repository or a
real reference client (RuneLite) or a real sibling map editor (Terraini), not general
knowledge. Where a claim is "confirmed," it was verified by reading the cited file.
Where it's a "target," it's a gap with a concrete next action, not a vague aspiration.

---

## Part 1 — Rendering engine: real OSRS rules

### 1.1 What's already correct (confirmed this pass)

- **Underlay blending is genuinely correct.** `FloorBlendRules.blendUnderlay` (`Client/src/main/java/com/rspsi/osrs/rules/terrain/FloorBlendRules.java`)
  implements the real client's radius-5 (11x11 tile window) weighted hue/saturation/luminance
  average - `hue = weightedHue*256/chroma`, `sat = saturation/count`, `luminance = luminance/count` -
  which matches OSRS's own `class470.method9712` (see 1.3) formula field-for-field. This was
  worth verifying rather than assuming; it holds up.
- **Tile shapes, heights, and lighting are marked `covered`** in `docs/RENDERING_PARITY_MANIFEST.json`
  (`terrain.shapes`, `terrain.heights`, `terrain.lighting`, `models.normals`, `models.alpha`,
  `models.renderModes`, `native.openglState`, `occlusion.planes`, `objects.wallOrientation`,
  `objects.ground`). Eleven of thirty-seven tracked parity entries are done. Don't re-litigate
  these without new evidence they've regressed.
- **Backface culling is deliberately off for models** (`BackfacePolicy` javadoc) because cache
  models aren't reliably wound - this was re-confirmed this cycle when the object-preview
  renderer's own culling produced a "transparent fountain" bug that the real (no-cull) native
  renderer doesn't have. Leave this alone until every model has verified winding.

### 1.2 What's not correct yet - the tracked backlog

`docs/RENDERING_PARITY_MANIFEST.json` is the live gap list: 22 "partial," 4 "deferred." Six are
P0-and-partial, each with an already-written `nextAction` - this is the actual near-term
rendering roadmap, not something to re-derive:

| id | title | next action |
|---|---|---|
| `objects.wallNormalMerge` | Wall neighbor normal merge / L-wall pair merge | Implement extended multi-region neighbor traversal for world-chunk boundary wall joins |
| `objects.wallDecorationOffsets` | Wall-decoration offsets, dual renderables, wall-width compensation | Carry both decoration renderables and wall-relative offsets before declaring parity |
| `models.textureAlpha` | Texture transparency in face-pass classification | Add golden test cases for animated texture UV offset handling |
| `textures.definitions` | Texture definitions, pixels, average-color fallback | Add real revision-240 texture and transparent-pixel fixtures |
| `native.drawRanges` | Opaque/alpha draw ranges, batching, diagnostics | Add a synthetic multi-material plan and compare command ranges/draw calls |
| `native.depthPriorityFacing` | Depth modes, face bias, priority ordering, winding/facing | Introduce a backend-neutral render-order key (model priority, face priority, depth mode, bias, facing); keep culling off until winding fixtures pass |

P1-partial items worth picking up next, once the P0s are down: `scene.apiSurface`,
`terrain.bridge`, `scene.roofs`, `objects.wallTransforms`, `objects.decorations`,
`objects.gameObjectFootprint`, `models.colors`, `models.contour`, `textures.animation`,
`occlusion.visibility`.

### 1.3 Ground-truth reference (RuneLite, verified this pass)

For anyone implementing the above, this is the actual algorithm to match, read from
`runescape-client/.../class470.java:method9712` (obfuscated) and its clean re-implementation
in `runelite-client/cache/.../MapImageDumper.java` (`BLEND = 5`):

- **Underlay blend**: separable box blur, radius 5, done with a running-sum (add the
  column/row entering the window, subtract the one leaving) rather than a re-sum per tile -
  our own `FloorBlendRules` is correct in *result* but does a naive O(25) re-scan per tile
  instead of the O(1)-amortized running sum. Worth optimizing once the incremental compiler
  (Part 2) is wired in, since that's the same "stop redoing full-window work on every tile"
  problem from two angles.
- **Lighting**: normals come from a **height gradient**, not real surface normals -
  `dx = height[x+1][y]-height[x-1][y]`, `dy = height[x][y+1]-height[x][y-1]`,
  `len = sqrt(dx² + dy² + 65536)` (fixed dz = 256), normalized. Light vector is
  hard-coded `(-50, -50, -10)` (`|v| = sqrt(5100) ≈ 71.4`), and
  `intensity = dot(normal, light) / (|v| * 3) + 96` (96 = flat ambient). Applying it
  (`class212.method4685`): **only luminance is modulated** - `newLuminance = clamp((hsl&127)*intensity/128, 2, 126)`,
  hue/saturation pass through untouched - then the result indexes a precomputed
  65536-entry HSL->RGB palette with a gamma pass. If our own lighting ever looks
  subtly wrong, check this exact formula first (spot-check target, since the parity
  manifest already claims `terrain.lighting: covered` - this is the "grade the
  homework" reference, not a known bug).
- **Tile shapes**: `SceneTileModel`'s static `triangleTextureIndices[13][]` /
  `faceIndices[13][]` define all 13 shapes' triangulation; rotation remaps vertex
  indices mod 4/8/12/16. Shape 0 -> `SceneTilePaint` (4 corner colors, no model);
  shapes 1-12 -> `SceneTileModel` (2-4 triangles).
- **Base terrain height** (when not authored): `HeightCalc.java` - a Jagex-specific
  **value-noise** function (not Perlin), 3 octaves at frequencies 4/2/1, remapped to
  `[10, 60]`. Relevant if/when procedural terrain generation (Part 6) needs a
  game-accurate default height field to seed from.

---

## Part 2 — Performance: faster tile-painting updates

**The fix already exists and is untested-in-production, not unbuilt.**
`Client/src/main/java/com/rspsi/editor/render/compiler/IncrementalSceneCompiler.java` is a
real, working, unit-tested (`IncrementalSceneCompilerTest.java`, 69 lines, passing) "revision-driven
incremental scene compiler over canonical 8x8 zones" - its own javadoc says terrain
compilation is "bounded to dirty zones... unchanged terrain maps remain the exact immutable
instances from the previous RenderScene." **It has zero production call sites.**
`SessionSceneController` (the class actually wired to `SessionChangeListener`, i.e. the thing
that reacts to every live edit) uses `RenderSceneBuilder` - a full rebuild - unconditionally.

This is validated independently by RuneLite's own GPU plugin fork (`runelite-client/.../plugins/gpu/Zone.java`,
`GpuPlugin.invalidateZone`/`rebuild`): it partitions the world into **8x8-tile zones**, each
with its own VBO, and a per-zone `invalidate` flag drives per-tick rebuild - untouched zones
never re-upload. Our own compiler already uses the same 8x8 zone size. This is not a
coincidence worth ignoring: it's the industry-standard chunk size for exactly this problem,
and we already built the matching machinery.

**Target**: wire `IncrementalSceneCompiler` into `SessionSceneController` in place of the
unconditional `RenderSceneBuilder` call, falling back to a full rebuild only when the compiler
reports it can't determine a bounded dirty set (e.g. a cache reload). Re-run
`IncrementalSceneCompilerTest` plus a manual paint-latency check before/after. This is the
single highest-leverage performance fix available right now - it's substitution, not new
engineering.

---

## Part 3 — Plugin API refinement

### 3.1 Current state: two parallel plugin systems

- **`EditorPlugin`** (`Client/src/main/java/com/rspsi/editor/plugin/`) - cache-agnostic,
  engine-level. `EditorPluginContext` has no `DefinitionProvider`/cache access at all (checked
  this cycle while building the selection overlay - confirmed by reading the full field list
  of the context record).
- **`StudioPlugin`** (`Editor/src/main/java/com/rspsi/studio/plugin/`) - ImGui/UI-level,
  cache-aware via `StudioPanelContext.cache()`. Anything needing model geometry, object
  definitions, or textures has to live here, not in `EditorPlugin`.

This split isn't necessarily wrong (engine-level extensions arguably *shouldn't* need cache
access), but it's undocumented as a deliberate boundary, which cost real time this cycle
figuring out which system a new feature belonged in. **Target**: write down the actual rule
("if it needs `DefinitionProvider`, it's a `StudioPlugin`; if it's pure document/session logic
usable headless, it's an `EditorPlugin`") somewhere a plugin author will see it before writing
code, not after.

### 3.2 What RuneLite does that's worth adopting: declarative settings

RuneLite plugins never hand-register individual settings. A plugin defines a config
*interface* (`@ConfigGroup("name")`), each getter is `@ConfigItem(keyName=..., name=...,
description=..., section=...)`, and `ConfigManager` reflects over it at runtime to both persist
values *and* auto-generate the settings panel widget for each field's type. No plugin author
ever writes `ImGui.checkbox(...)`/`ImGui.sliderFloat(...)` by hand for a setting.

Compare our own pattern, used repeatedly this cycle (`BrushSettingsHud.renderSettings`,
`SelectionOverlayPlugin.renderSettings`): every setting is a hand-written pair of "read the
field, draw the ImGui widget, write the field back" - correct, but it's boilerplate that scales
linearly with settings count and gives every plugin author a chance to get the widget-to-type
mapping subtly wrong (as happened this cycle with `sliderFloat`'s missing `int` overload and
`checkbox`'s return-value-not-out-param signature - both real compile errors this session).

**Target**: a declarative settings layer - an annotation (or a small builder DSL, given we
don't have Guice) on a plain settings class/interface that a shared renderer turns into ImGui
widgets automatically, keyed by field type (bool -> checkbox, float in a range -> slider, packed
color -> `colorEdit4`, enum -> combo). This wouldn't replace `SettingsService`/`SettingsStore`
(the persistence layer is fine) - it would replace the per-plugin `renderSettings()` boilerplate
that currently sits on top of it. Worth scoping as its own small project rather than bolting on
piecemeal.

### 3.3 What RuneLite validates about our existing shape

RuneLite plugins touch a small, fixed set of injectable UI managers (`ClientToolbar`/
`NavigationButton`, `OverlayManager`, `KeyManager`, `MenuManager`, `ConfigManager` - roughly
6-8 total). Our own `StudioPlugin` interface already has an equivalent small fixed set
(`renderOverlay`, `renderHUD`, `renderFloating`, `renderSidePanel`, `renderToolShelf`,
`renderSettings`, `renderContextDrawer` for tools). This is good validation that the *shape* of
our extension-point design matches a client with a decade of real plugin authors behind it -
the settings boilerplate (3.2) is the actual gap, not the surface area.

RuneLite also has `@PluginDependency` with cycle detection and topological sort, letting one
plugin `@Inject` another. We have nothing like this - if a future plugin genuinely needs to
depend on another plugin's state, that's worth building deliberately rather than ad hoc, but
there's no evidence yet that we need it (no current plugin depends on another).

---

## Part 4 — Layout / UI / UX flow

### 4.1 What's now established (this cycle's work, don't re-litigate)

- **Left Tool Rail is now formally the Brush Tool Rail**, not a generic tool dock. It shows
  only when a real brush tool is active, gated by an explicit `StudioToolPlugin.isBrushTool()`
  capability (not inferred from current surface placement - that was circular and let a user
  "place" a non-brush tool there from Plugin Manager settings with no effect). Only Tile
  Painter and Height Sculptor return `true`. The Plugin Manager's placement UI now hides the
  "Left Tool Rail" checkbox entirely for anything that isn't a brush tool.
- **Floating toolbar** hosts tile/object selection tools; **bottom bar** hosts everything else
  (Tile Painter, Height Sculptor, Path Builder, Object Placement).
- **Tightly-coupled plugin pieces are co-located, not scattered.** The object-select tool
  buttons live as nested classes inside `SelectionOverlayPlugin` now, since they exist only to
  feed the selection that plugin highlights - this is the pattern to keep applying: if a small
  plugin's only reason to exist is to drive a bigger one, nest it there instead of giving it its
  own top-level file.

### 4.2 Direction: shared tooling over bespoke UI

The stated goal going forward: most tools should not need to build their own brush-settings UI
or standalone tool chrome - they should reach for `BrushSettingsHud` and friends first, and
only build bespoke UI when they genuinely need workspace room beyond what the shared surfaces
offer. This isn't a rule to enforce mechanically (a tool that needs a real custom workspace -
e.g. a future path/spline editor, see Part 6 - should still get one), but new tool plugins
should be reviewed against "does this duplicate settings that already exist on a shared
surface?" before adding their own.

### 4.3 Icon audit - open, not closed

An icon-glyph audit this cycle found no static-analysis-detectable cause for reported "?"
glyphs: every codepoint constant in `StudioIcons.java` was checked against the actual shipped
`MaterialIcons-Regular.ttf`'s cmap (via `fontTools`) and all 93 resolved to real glyphs; no raw
unrouted unicode escapes exist outside `StudioIcons.java`; no literal `"?"` fallback strings
exist in settings/plugin UI code. This needs a live repro (screenshot or in-app pointer to
where it appears) to actually fix - flagging as open rather than closed.

---

## Part 5 — Selection overlay & painting system: what's still missing

Shipped this cycle: a real convex-hull object outline (ported from RuneLite's
`Model.getConvexHull()` technique), a fixed coordinate-space bug that made the whole system
render nothing (`ModelPacketBuilder`'s local-document-space anchor vs. the camera's absolute
world-space - see the fix in `SelectionOverlayPlugin`), precise single-object picking through
the previously-unused `Viewport.objectAt()` hook, and a real settings-backed plugin
(`SelectionOverlayStyle`) with per-category colors, configurable fill opacity (RuneLite's own
"transparency" trick, confirmed - its renderer has no true per-triangle tint hook either, every
"highlighted" object there is the same alpha-blended-hull-fill trick we now have), and a
"painted edge" double-stroke.

Still open, in the user's own words - "still needs work," "tones more work":

- **True per-triangle transparency**, if wanted, is a materially different and larger task:
  it would mean the native OpenGL renderer accepting a per-object tint/alpha uniform on the
  actual draw call, not a 2D overlay trick. RuneLite doesn't do this either (confirmed - its
  renderer has no hook for it), so there's no reference implementation to lean on; this would
  be original engineering against `OpenGlSceneRenderer` if pursued.
- **Tile-blending-aware painting UX** - Terraini's road rasterizer (Part 6) does real sub-tile
  coverage supersampling for edge quality; our own tile paint tool doesn't have an equivalent
  soft-edge story yet even for plain brush strokes.
- **Dev-info overlay is a first pass, not feature-complete** - shows id/name/category; RuneLite's
  Dev Tools overlay additionally shows animation IDs, distance, and per-type extras (combat
  level for NPCs, quantity for ground items) - our equivalent for objects could grow similarly
  (e.g. animation id, wall orientation, footprint dimensions) if it proves useful in practice.

---

## Part 6 — New feature: path/road generation (Terraini-informed)

Terraini's road toolkit was researched in real depth this pass (not just class names) and
gives a genuine implementable blueprint, sitting on top of OpenRune's own currently-inert
`Client/src/main/java/com/rspsi/editor/generation/` package (`Generator`, `GeneratorService`,
`GenerationSchema` - has an unused `ROAD` preset already waiting).

### 6.1 The actual algorithm (from Terraini, adaptable, not a straight port - it's decompiled
### bytecode from a commercial competitor's product for the *pieces we'd reimplement clean*,
### but the *algorithm shape* is fair game the same way a published technique is)

1. **Routing is local reroute, not global pathfinding.** Don't build a full A*/Dijkstra
   network solver. Take the user's drawn/desired polyline as ground truth; only run A* to
   patch the specific blocked span(s), inside a bounding box expanded by a small corridor
   (Terraini's default: 10 tiles) around the affected points. Cost function per edge:
   `traversal_cost(tile) * (√2 if diagonal else 1) + slope_penalty + distance_from_original_stroke * weight`
   - that last term is what keeps a reroute hugging the user's intent instead of taking an
   arbitrary shortest path. Reject diagonal moves that would cut a blocked corner. Heuristic:
   Chebyshev-with-diagonal-discount (`max(dx,dy) + (√2-1)*min(dx,dy)`).
2. **Smooth the result with a turn-angle-adaptive spline**, not a uniform one: a
   Catmull-Rom-style centripetal spline (weights `√distance` between consecutive points) whose
   *smoothing strength itself* is locally modulated by the turn angle at each vertex - sharp
   corners (>120°) get near-zero smoothing so real intersections stay crisp, gentle turns
   (<30°) get full smoothing, blended with a smoothstep in between. Then resample at fixed
   arc-length spacing for a uniform tile-placement cadence.
3. **Rasterize with real sub-tile coverage, not per-tile boolean paint.** Terraini precomputes,
   for each of OSRS's native tile shapes x 4 rotations, a supersampled coverage mask (2-16x
   supersample) via point-in-triangle tests against the actual triangulation. The swept
   road-width polygon accumulates real coverage per touched tile; shape/orientation is chosen
   by minimizing Hamming distance to that coverage mask, **with an explicit penalty for
   mismatched edge "portal" bits** so adjoining tiles' open/closed edges stay consistent (this
   is what makes junctions look continuous instead of tile-by-tile arbitrary). Below a small
   coverage threshold (~0.135), bucket separately for softer edge treatment.
4. **Junction shape selection is a lookup table, not a rule engine** despite the name
   ("RoadShapeGrammar") - reduce the 8-bit "which neighbors are also road" mask to 48 canonical
   cases by folding 90-degree rotations, and map each to a pre-authored, topology-filtered,
   *weighted* list of shape/orientation candidates (so identical junctions can render with
   slight variety instead of mechanical repetition).

### 6.2 Where this plugs in

`GenerationSchema.ROAD` already exists as an unused preset - this is the natural landing spot.
`CostGrid` in Terraini is an interface with pluggable occupancy/traversal-cost/slope/edge-block
methods, deliberately decoupled from any specific terrain backing store - our own
`WorldDocument`/collision data (`CollisionMap`, `OsrsCollisionBuilder`) is the natural backing
implementation. Scope as its own phase: routing engine first (testable headless, no rendering
dependency), then the spline/resample layer, then the coverage-based rasterizer last (it's the
part that touches the terrain-paint pipeline and benefits most from Part 2's incremental
compiler being wired in first, since a road stroke touching many tiles is exactly the paint
workload that should not trigger full scene rebuilds).

---

## Part 7 — Procedural terrain generation (lower priority, noted for later)

Terraini's island generator is a real, well-designed system - ten independent seeded Perlin
noise channels (fbm/ridged/billow combinations, each octave rotated ~28.65 degrees and offset
to hide grid artifacts, domain-warped by dedicated warp channels), Worley/Voronoi hashing for
island "lobes," and a library of 128-sample radial templates *digitized from real OSRS islands*
that get warped by noise rather than used rigidly. This is a substantial, self-contained
feature (island/landmass generation from a seed) rather than a natural extension of anything
we have today - noted as a real capability gap, not scheduled against Part 6's road generator,
which is the higher-value near-term target since it has an existing landing spot
(`GenerationSchema.ROAD`) and a clearer connection to "stronger editing features."

---

## Part 8 — Other Terraini ideas (lower priority)

- **Stamp/prefab paste system** (`StampPlan`/`StampAnchor`/`StampConflictPolicy`/`StampHeightMode`):
  capture a region, transform it (mirror/rotate/recenter), paste elsewhere with explicit
  policies for how heights blend into existing terrain and how object conflicts resolve. Real
  gap in our own object/tile tooling (no "paste onto uneven terrain" story today) but the
  actual merge/conflict math lives in Terraini code that wasn't opened this pass - would need
  designing from the policy *names* and OSRS's own terrain-height rules, not copied.
- **Layer system** (`LayerManager`/`LayerCompositor`): Photoshop-style tile/object layers,
  each visible layer's per-field changes replayed in order onto a snapshot of the base region
  (later layers win per-field, not per-tile; hidden layers contribute nothing). No diff/undo
  machinery of its own - would need to integrate with our existing history system, not bring
  its own.
- **Region thumbnail world-map browser**: two-tier (64px/256px) box-downsampled PNG cache,
  disk-backed, capped by size/count rather than true LRU, generated via a throwaway scene
  render per region. A plausible "world overview to jump into a region" panel if our own
  navigation ever needs one - not urgent.

---

## Part 9 — Modularization: modules and plugins

Current Gradle module split is exactly two: `Client` (engine, cache, rendering-neutral) and
`Editor` (Dear ImGui/native shell, Studio plugins). The dual plugin system (Part 3.1) is the
most concrete symptom of needing clearer module boundaries - it wasn't designed as two systems
on purpose, it grew that way because cache access is Editor-side and engine logic is
Client-side, and nobody has drawn the line explicitly.

Before splitting into more Gradle modules (a real option worth considering - e.g. isolating a
`Rendering` module from `Client`, or a `PluginApi` module shared by both), the higher-value,
lower-risk step is documenting the *existing* two-module boundary's actual rule (3.1) and
seeing whether real friction remains once that's written down. Module-count changes are a
one-way door in a Gradle project of this size (build script churn, IDE reindexing, every
existing import path changes) - worth being sure the two-module split is actually the problem
before restructuring it.

---

## Explicitly deferred (not in this roadmap's near-term scope)

- True GPU-level per-object transparency (Part 5) - real engineering against the native
  renderer, no reference implementation to lean on from either RuneLite or Terraini.
- Procedural island/terrain generation (Part 7) - real feature, lower priority than the road
  generator.
- Stamp/paste and layer systems (Part 8) - real gaps, no existing landing spot in our codebase
  the way `GenerationSchema.ROAD` gives the road generator one.
- Further Gradle module splitting (Part 9) - revisit only after the two-module boundary is
  actually documented and shown to still cause friction.
- `@PluginDependency`-style plugin-to-plugin dependency graph - no current plugin needs it.
