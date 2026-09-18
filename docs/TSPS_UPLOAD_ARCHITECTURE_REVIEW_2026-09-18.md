# TSPS WebGL2 Upload Architecture Review — 2026-09-18 (rev 2)

Reference: `RSPSi-resources/TSPS/client/render/**` (TypeScript / WebGL2 /
PicoGL, a production OSRS client renderer). Re-verified against the *current*
RSPSi working tree (`OpenGlSceneRenderer.java`, `GpuUploadPlanBuilder.java`,
`GpuCommandVisibility.java`, `SceneOcclusionResolver.java`), which has landed
several earlier recommendations: reversed-Z (`GL_GREATER` + `glClearDepth(0)`),
multi-draw batching (`drawBatches` + `glMultiDrawElements`), per-face
transparency classification, cached command bounds, and upload instrumentation.

This revision lists only **verified, remaining** deltas. Each item names the
exact TSPS mechanism and the exact line(s) in our tree.

---

## 1. TSPS's architecture in one paragraph

The scene is uploaded as **one packed-uint vertex/index buffer per map square**
(64×64 tiles), built once, indexed by exact packed identity so shared vertices
dedupe. Faces are classified **per face** into pre-bucketed draw lists
(opaque/alpha × LOD × interact); a bucket is a list of `(offset, count)`
ranges into that square's index buffer. Per frame, each visible square
submits each bucket with **one `multiDrawArrays`** — zero uniform updates
inside a pass. All per-model/per-instance state (position, level, priority,
interact id, contour-ground mode, roof-cull plane) lives in an `RGBA16UI`
**metadata texture** fetched by `gl_DrawID` in the vertex shader. Object
placement on terrain is computed **in the vertex shader** from a tile-corner
height-map texture (both diagonals, `max` of the two) — the CPU never bakes
heights into geometry. Roof culling is per-instance in the shader via
degenerate `gl_Position`. Texture animation is material metadata, not face
data. There is no CPU occlusion and no per-frame re-encoding of anything.

## 2. Verified current-state comparison

| Topic | TSPS | RSPSi (current tree) | Verdict |
| :-- | :-- | :-- | :-- |
| Depth convention | conventional Z, `LEQUAL` | reversed Z, `GL_GREATER`, `glClearDepth(0)` | ✅ deliberate divergence, fine |
| Draw calls | 1 multi-draw per bucket per square | `drawBatches` groups contiguous same-state runs → multi-draw | ✅ mechanism present, but see §3.3 |
| Transparency split | per face, at build | per face (`GpuUploadPlanBuilder` L99–100) | ✅ fixed |
| Per-frame uploads | none after load | gated on camera-independent fingerprint | ✅ fixed |
| CPU occlusion | none | `GpuCommandVisibility`, budget-capped | ✅ acceptable, see §3.6 |
| Frame state hygiene | PicoGL re-applies state; present pass resets | **depth mask never restored after alpha pass** | ❌ **critical** (§3.1) |
| Edit invalidation | rebuild only the changed map square | scene-wide fingerprint → full VBO re-upload per edit | ❌ (§3.4) |
| Placement | GPU height-map skew | CPU `placementHeight` scalar baked per model | ❌ (§3.5) |
| Culling | render-distance per square + shader roof planes | none (no frustum test at all) | ❌ (§3.6) |
| Textures | never deleted; per-layer updates; deferred mips; mip+anisotropy | array deleted+recreated wholesale; `GL_NEAREST`+clamp; no mips | ❌ (§3.7) |
| Vertex format | 12 bytes packed uint | 48 bytes, 12 floats | ⚠ later (§3.8) |

## 3. What we still do wrong (prioritized)

### 3.1 CRITICAL — `glDepthMask(false)` leaks across frames; the depth clear is a no-op

`applyDrawState` (`OpenGlSceneRenderer.java` ~L574–577) sets
`glDepthMask(false)` when entering the alpha pass. The alpha pass is the
**last** pass of the frame, and nothing restores the mask afterward. The next
frame begins with `glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)`
(~L261) — but **depth writes are still disabled**, so the depth clear silently
does nothing from frame 2 onward. Every subsequent opaque draw depth-tests
against the *previous* frame's depth buffer.

With a static camera this is mostly invisible (same depths), but any camera
movement produces ghosting, holes, and faces popping — the classic "renders
wrong sometimes" symptom. TSPS avoids this by re-establishing state per pass
through PicoGL; RuneLite's `GpuPlugin` sets `glDepthMask(true)` every frame.

**Fix (one line):** at the top of `draw()`, before `glClear`:
`glDepthMask(true);` — and/or restore it at the end of `draw()`.

### 3.2 HIGH — fully-invisible faces are classified OPAQUE (rendered solid)

`GpuUploadPlanBuilder` L41–42:
`face.alpha() == 255 ? OPAQUE : ALPHA`. In the OSRS convention, face
alpha 255 is the *fully transparent / invisible* endpoint (RuneLite's
`SceneUploader` **skips** `alpha == 255` faces entirely; alpha 0 is opaque).
As written, an invisible face goes into the opaque pass where blending is
disabled — its fragment alpha is ignored and it renders **solid**. The
fragment shader's `1 - vAlpha/255` model-alpha handling confirms alpha is
being interpreted as a transparency amount, which contradicts the L41
classification.

**Fix:** skip `alpha == 255` faces at build (match RuneLite), or verify
`ModelPacketBuilder`'s normalization and make classification, shader, and
builder agree on one convention. Add a golden fixture with an invisible-face
model.

### 3.3 HIGH — priority-major sort fragments the state batching we just built

`opaqueOrder` sorts `priority DESC, then drawStateKey`. Commands with
identical state but different priorities are **not adjacent**, so
`drawBatches`'s contiguous-run grouping breaks at every priority boundary:
state flips back and forth, `applyDrawState` re-issues uniform/GL-state
changes per fragment, and the batch count balloons toward per-command again.

Both oracles solve this at **build/upload time**, not per frame: RuneLite
orders faces by priority inside `SceneUploader` (per-frame zone draws are
state-identical and need no ordering); TSPS includes priority in its
build-time bucket key.

**Fix:** make `GpuUploadPlanBuilder` emit commands already grouped by
`(state, priority)` so the per-frame pass is a straight iteration over
pre-sorted ranges (no comparator, no fragmentation). Opaque coplanar
resolution then falls out of upload order exactly like RuneLite, and the
face-bias byte handles the rest.

### 3.4 HIGH — scene-wide VBO: every edit re-uploads everything

`plan.fingerprint()` changes on any single-tile edit → `uploadGeometry`
re-uploads the **entire** vertex/index buffer, and the first frame after each
edit also rebuilds the whole `BOUNDS_CACHE` (a full walk over every index of
every command on the UI thread — a visible hitch per edit). TSPS's
`WebGLMapSquare` owns per-map-square VAO/VBO/ranges/metadata and rebuilds
**only the changed square**; scene-wide state (program, texture array) lives
on the host.

**Fix (P1, architectural):** chunk the plan by 8×8 (or 64×64) with per-chunk
buffers, fingerprints, and bounds; the renderer keeps a map of resident chunks
and re-uploads only dirty ones. Compute `CommandBounds` **during build** and
store it on `GpuDrawCommand` (the builder already has the vertices in hand) —
this deletes the static bounds cache and its edit hitch immediately, even
before chunking lands.

### 3.5 MEDIUM — CPU-baked placement vs TSPS's GPU height-map skew

We bake one bilinear height at the model footprint center into every vertex
(`placementHeight`) — objects float/sink on slopes and **any terrain edit must
rebuild every object packet**. TSPS uploads a tile-corner height texture and
skews in the vertex shader (`height-map.glsl`, both diagonals, `max` of the
two; `contourGround` mode selects per-vertex vs center sampling). Terrain
edits then cost **zero** object-buffer work.

**Fix (P1):** height texture + per-instance placement metadata; delete CPU
skew. Also the correct answer to the `DrawCallbacks.HILLSKEW` contract gap.

### 3.6 MEDIUM — no frustum/distance culling at all

Our only per-frame rejection is the budget-capped occluder test
(`GpuCommandVisibility`: ≤10k commands and ≤250k tests — beyond that,
**everything draws**). TSPS culls whole map squares by render distance before
any draw and culls roofs per-instance in the shader; RuneLite has
`zoneInFrustum`. For large regions we rasterize 100% of geometry every frame.

**Fix (P1):** cheap per-command AABB-vs-frustum test using the (already
cached / soon build-time) bounds — no shader work, same granularity as the
visibility bitset. Optional later: roof-plane culling via metadata in the
vertex shader, the TSPS way.

### 3.7 MEDIUM — texture array lifecycle and sampling

`uploadTextureArray` **deletes and recreates the whole array** whenever any
texture changes, re-uploading every layer from CPU pixels. TSPS never deletes
the array: per-layer `texSubImage3D`, white base layer, and **deferred,
amortized** mipmap regeneration (quiet-period timer) because
`generateMipmap` on a large array stalls. We also run `GL_NEAREST` +
`CLAMP_TO_EDGE` with no mips — correct for parity, but TSPS's default-quality
path (mip + linear + anisotropy) is what kills motion shimmer, and our
`fract()`-based UV wrap prevents tile seams under that mode too.

**Fix (P1/P2):** keep the array; add/replace layers in place; regenerate mips
on a defer timer; expose nearest-vs-mip as a render setting (vanilla look vs
quality), matching TSPS's selectable filter modes.

### 3.8 LOWER — packed vertex format (4× bandwidth), dead code, allocs

- Our 48-byte float vertices vs TSPS's 12 packed bytes (position 15-bit, HSL,
  alpha, priority, texture id packed) — adopt **with** chunking, not before
  (packed positions want per-chunk local space + base offset).
- `drawCommand()` is now dead (no callers) — delete.
- `occludesAllCorners` allocates `new float[]{...}` per corner per
  command per occluder every frame — unroll the 8 corners; the visibility
  loop runs this hundreds of thousands of times per frame when enabled.

## 4. Bottom line

The big structural fixes from the earlier reviews are **in** (reversed-Z,
multi-draw, per-face split, upload gating). What separates us from TSPS now
is no longer "the upload strategy is wrong" — it is four concrete things:
**one leaked GL state bit that breaks depth after the first alpha frame
(§3.1)**, an **inverted invisible-face classification (§3.2)**, **build-time
ordering/grouping so batching doesn't fragment (§3.3)**, and **scene-wide
granularity (edit re-upload, bounds hitch, no culling — §3.4–3.6)**. Fix 3.1
and 3.2 today (small diffs), 3.3 with the next builder change, and chunking
(3.4) as the next architectural step — it is also the precondition for the
packed vertex format and for edit-latency parity with TSPS.
