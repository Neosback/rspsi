# Terraini reference notes

_Written analysis, not vendored source - see [AGENTS.md](../AGENTS.md#terraini---reference-notes-only-no-vendored-source)
for why. Everything below is a description of algorithms and techniques, in original wording,
derived from reading decompiled bytecode locally on this machine (not committed to this repo).
File/method names are cited so a detail can be re-verified against the actual decompiled
classes if needed - they are not copy-pasted source._

Terraini is a JavaFX-based OSRS map editor sharing this project's `com.jagex` cache/map/
rendering lineage (a sibling fork, not a stranger's engine). It's meaningfully more built-out
in a few specific feature areas than OpenRune Studio is today. This document is the deep
reference for those areas; `docs/ROADMAP.md` Parts 6-8 hold the prioritized "what to actually
build" summary - read that first, come here for algorithm-level detail.

---

## 1. Road/path authoring (`tools/path/`)

The single most directly useful area - OpenRune already has a matching landing spot
(`Client/src/main/java/com/rspsi/editor/generation/GenerationSchema.ROAD`, currently unused).

### 1.1 Routing - `PathPlanner` + `ObstacleRouter`

Not a global network solver. The design is **local reroute of only the blocked span**:

- `PathPlanner.plan()` treats the user's drawn/desired polyline as ground truth for everything
  that isn't blocked, and only invokes `ObstacleRouter.route(...)` for the specific span(s) that
  are.
- `ObstacleRouter.route(points, plane, environment, settings, clearanceRadius, maxSlope)` scans
  the polyline for the first/last index bracketing a blocked point or edge, expanding outward
  while endpoints stay blocked. If nothing's blocked, it returns the input unchanged (cheap
  common case).
- When rerouting is needed, it runs **A\*** on the integer tile grid, but only inside a bounding
  box = bbox(affected points) expanded by `settings.routeCorridor()` (default 10 tiles), clipped
  to the environment bounds - not the whole map.
- 8-connected movement. Priority queue ordered by `f = g + h`, heuristic
  `h = max(dx,dy) + (√2-1)*min(dx,dy)` (Chebyshev distance with a diagonal-move discount).
- Edge cost: `max(0.01, traversal_cost(tile)) * (√2 if diagonal else 1) + slope*slope_penalty + point_to_segment_distance_to_original_stroke * 0.12`.
  The last term is the key design choice: it's not "shortest detour," it's "detour that stays
  close to what the user actually drew," memoized per tile.
- Diagonal moves are rejected if either orthogonal "corner" neighbor is blocked or edge-blocked
  (standard no-corner-cutting rule).
- `blockedAt(tile)` scans a disc of radius `ceil(clearanceRadius)` (0.75 tolerance) around a
  tile; what counts as "blocking" depends on an `ObstaclePolicy` enum: `AVOID_ALL` blocks both
  decoration and solid objects, `AVOID_SOLIDS`/`AVOID_MASK`/`WARN_ONLY` block only solids,
  `IGNORE` never blocks (routing is skipped entirely). There's also a hard slope cap.
- On success: backtrack the parent-pointer map, splice the exact fractional original endpoints
  back onto the integer-grid path, then run a **greedy line-of-sight simplification pass** -
  sample every `distance/4` along a candidate shortcut, accept it if every intermediate sample
  stays in-bounds/unblocked/within one tile step of the direct line - before handing off to the
  spline compiler (1.2).
- `CostGrid` (the interface `ObstacleRouter` queries for occupancy/traversal-cost/slope/edge-
  block) is deliberately decoupled from any specific terrain store, with all-default no-op
  methods. In an OpenRune port, this is the seam: implement it against `WorldDocument` +
  `CollisionMap`/`OsrsCollisionBuilder`, which already exist.

`PathPlanner` computes the router's inputs per edge from a `RoadProfile`: width tapers per
endpoint (`node.widthScale()`), `clearanceRadius = settings.clearance() + profile.obstacleClearance() + max(0, width/2 - 0.5)`,
`maxSlope = min(profile.maximumSlope(), settings.maximumSlope())`. Rerouting only fires when
`edge.autoRoute() && profile.reroute() && policy in {AVOID_SOLIDS, AVOID_ALL}`; under
`WARN_ONLY` no rerouting happens but a conflict is still flagged if any point/edge is blocked.

### 1.2 Smoothing - `SplinePathCompiler` / `CubicBezier` / `StrokeResampler`

- A path draft is a sequence of knots (point + in/out Bezier handles). `SplinePathCompiler`
  builds one cubic Bezier segment per consecutive knot pair (wrapping for closed loops).
- `CubicBezier.sample()` estimates arc length crudely (sum of the 3 control-polygon leg
  lengths), derives a sample count `clamp(ceil(estimate/spacing), 6, 512)`, and evaluates the
  standard Bernstein cubic at **uniform parametric steps** - not true arc-length
  reparameterization. Good enough in practice because the resampling pass after (below) fixes
  up spacing anyway.
- `StrokeResampler.smoothAndResample(points, smoothness in [0,1], spacing)` is the more
  interesting piece:
  1. Dedupe near-duplicate input points.
  2. If `smoothness == 0`, skip straight to step 4.
  3. Otherwise apply a **Catmull-Rom-like centripetal spline**: for each (prev, cur, next,
     next2) window (virtual mirrored points at the ends), weight by `sqrt(distance(p,q))`
     between consecutive points (the centripetal parameterization, which avoids the cusps/loops
     a uniform Catmull-Rom can produce on tight turns), subdivided into
     `max(4, ceil(segment_length / min(spacing, 0.25)))` steps.
     **The actual smoothing strength is locally modulated by the turn angle at each vertex**:
     angles <= 30 degrees get full smoothness, angles >= 120 degrees get zero smoothness (so
     real sharp corners/intersections are preserved instead of being rounded off), blended
     between with a smoothstep curve (`3t^2 - 2t^3`). This is the detail that matters most for
     visual quality - a fixed global smoothness either rounds off every intersection or fails to
     smooth gentle curves.
  4. Final fixed arc-length resample: walk the (possibly now-smoothed) polyline, linearly
     interpolate a new point every `spacing` units, always keep the exact final point.

### 1.3 Junction shapes - `RoadShapeGrammar`

Despite the name, this is a **precomputed lookup table**, not a symbolic/rule-based grammar:

- Reduces the 8-bit "which of the 8 neighbor tiles are also road" mask (256 possible
  configurations) down to 48 canonical cases by folding away 90-degree rotations
  (`canonical`/`canonicalTurns`).
- Each canonical mask maps to a hand-authored, **weighted** list of candidate
  `(nativeOverlayShape, orientation, weight-in-parts-per-10000)` tuples - e.g. one mask has four
  candidates weighted roughly 93%/1.4%/1.4%/0.7%/0.7%/2.8%. `shapeFor(mask, randomUnit)` does
  weighted-random selection, so visually identical junction configurations don't always render
  with the exact same tile - a deliberate anti-repetition measure.
- Candidates are pre-filtered in a static initializer: only shapes whose known edge-mask is
  topologically consistent with the real neighbor bits survive before the weights are
  normalized - effectively a marching-squares-style decision table underneath the
  weighted-random dressing.

### 1.4 Tile blending / rasterization - `OverlayShapeAtlas` + the road rasterizer

This directly answers "better understanding of tile blending" for the road-painting case
specifically (see `docs/ROADMAP.md` Part 1 for the separate, already-verified underlay-color
blend used for general terrain):

- For each of OSRS's ~12 native tile shapes x 4 orientations, `OverlayShapeAtlas` precomputes a
  **supersampled boolean coverage mask** at resolution R (`PathSettings.supersample()`, 2-16,
  default 8) via point-in-triangle tests against the client's own triangulation data.
- The road rasterizer sweeps the actual road-width polygon (after the organic jitter in 1.5) and
  accumulates a real **sub-tile coverage `BitSet`** per touched tile - not a single
  in/out boolean per tile.
- Shape/orientation selection: pick whichever atlas entry minimizes Hamming distance to the
  accumulated coverage mask, **with an explicit `+2*R^2` penalty per mismatched edge "portal"
  bit** - this is specifically what keeps adjoining tiles' open/closed edges consistent with
  each other, which is the actual hard part of tile-based road rendering (a road segment doesn't
  look continuous unless neighboring tiles agree on where the road "exits" each tile).
- Each resulting tile paint also stores a scalar coverage fraction; tiles below roughly
  `0.12 + profile.edgeFalloff()*0.1` (~0.135 by default) get bucketed separately for softer edge
  treatment - i.e. there's an explicit interior-tile vs. feathered-edge-tile split driven by
  measured coverage, not a uniform treatment.

### 1.5 Organic variation

Width jitter, centerline wander, and edge noise are all seeded per network+edge UUID and
applied to the corridor geometry **before** the coverage mask is computed in 1.4 - so the
irregularity is baked into the actual swept polygon, not painted on afterward.

---

## 2. Procedural island/terrain generation (`tools/island/`)

Lower priority than the road toolkit (no existing landing spot in OpenNote's codebase the way
`GenerationSchema.ROAD` gives roads one) but a genuinely well-designed system worth understanding.

### 2.1 `IslandNoise` - the noise primitives

Ten independently-seeded named channels: RELIEF, WARP_X, WARP_Y, MASK, LOBE, RIVER, BIOME,
SCATTER, DETAIL, BENCH. Each is its own classic Perlin implementation (permutation table
shuffled via `SplittableRandom`, quintic fade `6t^5 - 15t^4 + 10t^3`, 8-direction gradients).

Two specific anti-artifact tricks applied per-octave (both worth stealing even for a simpler
generator): a **fixed random offset per octave** (not just frequency scaling - avoids every
octave sharing the same grid-aligned zero-crossings), and **rotating the sample coordinate
~28.65 degrees per octave** before evaluating (hides Perlin's inherent axis-aligned grid bias
that becomes visible when octaves are simply summed).

- `fbm(coord) = sum(noise(coord * lacunarity^i) * gain^i) / sum(gain^i)` - standard fractal
  Brownian motion, default lacunarity=2, gain=0.5.
- `ridged01`: accumulates `(1 - abs(noise))^2` per octave - produces sharp ridge lines, no
  feedback loop between octaves.
- `billow01`: accumulates `abs(noise)` per octave - produces rounded "billowy" bumps.
- Domain warping: `warpX`/`warpY` are each a 3-octave fbm of the dedicated WARP channels,
  scaled by a `warpAmount` parameter and added to the sample coordinate **before** RELIEF/MASK
  are evaluated - this is what gives the terrain its organic, non-grid-aligned large-scale
  shape rather than looking like raw layered Perlin.
- Worley/Voronoi (`patchUnit`/`patchCell`): hashes a 3x3 cell neighborhood at a given cell size,
  jitters each cell's feature point via a SplitMix64-style avalanche mixer (`mix64`), returns
  the nearest feature point's hash/distance (F1 Worley) - used for island "lobes" and scatter
  placement, not the base heightmap.

Composition in `IslandGenerator`: `relief = fbm01(RELIEF)`, `ridge = ridged01(RELIEF)`,
`final = relief + (ridge - relief) * ridgeSharpness`. Ridged noise is used as a **sharpness
dial** blended against the plain fbm result, not summed as a separate additive layer - both see
the same pre-warped coordinates, so ridges and the base relief stay spatially coherent.

### 2.2 `CoastTemplateLibrary` - authored, not purely procedural

A bundled JSON resource (`coast-templates.json`) of records, each **128 radial samples**
`r(theta)` digitized from real OSRS islands (per its own doc comment, "measured off real
rev-229 islands"), plus aspect/std-dev/max-radius/95th-percentile/family tags.
`radiusAt(theta)` linearly interpolates the two nearest samples; templates can be mirrored
cheaply. Explicitly out of scope for this representation: rings/atolls and branching
archipelagos (a single-valued radial function can't express a concave or multiply-connected
coastline).

When a template is selected, its `r(theta)` **replaces** the noise-only fallback mask term at
each angle, but still takes a LOBE-channel noise sample as a perturbation input - so the
authored outline is warped by noise rather than used rigidly. (The exact perturbation formula
lives in a helper class that stayed obfuscated in this pass - re-check
`/Users/tylercovalt/Desktop/RSPS/tools/map-and-terrain/terraini` directly if this specific
detail is needed.)

The pure-noise fallback (no template selected) is an aspect-scaled elliptical distance field,
perturbed by three additive fbm terms at different frequencies, with
`falloff = 1 - distance^falloffPower`.

### 2.3 Height quantization

No smoothing pass beyond native OSRS shape triangulation - height output is a simple per-vertex
power-curve remap plus a step/terrace quantization. Simpler than it might sound given the noise
sophistication above; the "island shape" is doing the interesting work, not the height curve.

---

## 3. Lower-priority systems (brief)

### `StampPlan` (`tools/stamp/`)

Captures a tile region into an immutable, transformable plan. `transformPoint()`: mirror ->
quarter-turn rotate -> re-center -> translate/re-plane, in that order. `heightAnchor()` finds
the nearest sample tile (by squared distance) to a chosen corner or center for height-blending
purposes. `StampConflictPolicy`/`StampHeightMode` are plain 3-value enums in this file with no
algorithm attached - the actual merge/overwrite/height-blend math lives in paste-application
code that wasn't opened in this pass. An OpenRune port would need to design that math from the
policy *names* and OSRS's own terrain-height rules, not copy anything.

### `LayerManager`/`LayerCompositor` (`layers/`)

Snapshots the untouched base region once. `recomposite()` does a **full rebuild every time**:
copies base arrays back (or blanks them if the base layer is hidden), replays each visible
layer's per-field tile changes in ascending layer order (later layers win per-field, not
per-tile - two layers can each win on different fields of the same tile), merges object
add/remove operations into an insertion-ordered map by placement key (last layer wins on
conflict). Hidden layers contribute nothing. No diff/undo machinery of its own - if ported,
would need to integrate with OpenRune's existing history system rather than bring its own.

### `RegionThumbnailService` (`ui/worldmap/`)

Insertion-order-evicting cache (not true LRU) capped at 224MB or 24,000 entries, backed by a
disk cache of raw 256x1024 ARGB PNGs keyed by a hash of the cache source path (invalidated
wholesale when the cache source changes). Two tiers - 64px and 256px, switched at a ~48px
on-screen cell size threshold - both box-downsampled from one decoded buffer. Generation renders
via a throwaway scene graph, persists atomically, times out in-flight requests after 20s, caps
concurrency at 24 simultaneous generations.
