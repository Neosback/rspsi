# GPU Divergence Review — RuneLite & TSPS vs RSPSi native renderer

**Date:** 2026-09-18
**Scope:** Why the native OpenGL path still renders wrong and lags after the
fingerprint-gating / command-visibility work landed. Every claim below was
checked against the actual sources in `RSPSi-resources/`, not from memory:

| Oracle | What was read |
|---|---|
| `RuneLite-melxin/runelite-client/.../plugins/gpu/` | `GpuPlugin.java`, `SceneUploader.java`, `Mat4.java`, `TextureManager.java`, `Zone.java`, `RegionManager.java`, `vert.glsl`, `frag.glsl`, `hsl_to_rgb.glsl` |
| `TSPS/client/render/` | `shaders/main.vert.glsl`, `shaders/main.frag.glsl`, `render/init/core.ts`, `render/frame/*`, `buffer/SceneBuffer.ts`, `buffer/VertexBuffer.ts` |
| RSPSi (this repo, working tree) | `OpenGlSceneRenderer.java`, `GpuUploadPlanBuilder.java`, `GpuDrawCommand.java`, `GpuSceneVertex.java`, `GpuCommandVisibility.java`, `SceneOcclusionResolver.java`, `GpuPriority.java`, `GpuScenePacketBuilder.java`, `RenderSettingKeys.java`, `GlFramebuffer.java`, `MapEditorView.java` |

**Already correct in the working tree** (verified, do not redo): camera moves
no longer re-upload geometry (`plan.fingerprint()` is camera-independent);
occlusion is a `BitSet` over merged commands, not a per-frame geometry
rebuild; priority no longer synthesizes depth (bias-only, matching
RuneLite's `screenPos.z += float(bias)/128.0`); upload/draw-call counters
exist in `Statistics`; MSAA setting is wired through to the multisample FBO.

**Update 2026-09-18 (later same day):** P1, P2, P3, and C2 below are now
implemented and verified (full test suite, `foundationGate`, and a
non-interactive `:Editor:run` smoke test against a real cache with zero GL
errors). C0 was investigated and retracted - it does not describe a real bug
in this codebase (see the C0 section). **P0 (the vertex-format redesign +
`glMultiDrawElements` submission) is still the single biggest remaining lag
source and was deliberately not attempted in this pass** - it is large
enough, and touches enough shader/vertex-layout surface I cannot visually
verify, that it deserves its own pass with visual confirmation of the
current fixes first. C1 (culling) also remains open pending a visual
winding check. See the per-section status notes below for exactly what
changed and what's still open.

---

## 1. What the two oracles actually do

### RuneLite (closest to our GL 3.3 target)

* **Vertex format:** two ints per vertex (`GpuIntBuffer.put22224/put2222`).
  Word A packs `x,y,z` (11 bits each, zone-local) + 16-bit HSL; word A's top
  byte is `alpha<<24 | bias<<16`. Word B packs `textureId+1, u*256, v*256`.
  Texture id, UV, alpha, bias are **per-vertex attributes**, not per-draw
  state (`SceneUploader.java` ~L672-695).
* **Buffers:** one static VBO per 8×8-tile zone, uploaded once at scene
  build; a frame draws pre-recorded VAO ranges. Camera movement = change the
  projection matrix, touch zero buffers (`Zone.java`, `RegionManager.java`).
* **Draw shape:** opaque pass = one `vao.draw()` per live zone, plus the
  "priority opaque" VAO drawn twice (once with `glDepthMask(false)`, once
  with `glColorMask(false,false,false,false)`) to emulate the client's
  priority ordering without sorting. Alpha pass = per-zone ranges over a
  dynamically re-sorted buffer produced by a compute shader
  (`FacePrioritySorter`, `comp.glsl`) — CPU does **no** per-frame sort.
* **Depth:** reversed-Z. `Mat4.projection` yields `gl_Position.z/w = 2n/z`;
  `glClearDepth(0)`, `glDepthFunc(GL_GREATER)` (`GpuPlugin.java` L902/989).
  Face bias is a **constant NDC offset** (`bias/128`), so ordering is
  distance-independent.
* **State per frame:** `glEnable(GL_CULL_FACE)` (default front = CW by
  client winding), `glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
  GL_ONE, GL_ONE)`, depth test on. Nothing per triangle.
* **Textures:** `GL_TEXTURE_2D_ARRAY`, `glTexStorage3D` with 8 mip levels,
  `GL_NEAREST` mag / `GL_NEAREST_MIPMAP_LINEAR` min (nearest base + mips) +
  optional anisotropy (`TextureManager.java` L57-114). Indexed-color texel 0
  is uploaded with **alpha = 0**; `frag.glsl` does
  `if (textureColor0.a < 1.f) discard;`. Per-layer updates via
  `glTexSubImage3D`, mipmaps regenerated after updates.
* **Animation:** texture scroll = `fUv + tick * anim * 1/128` in the vertex
  shader from a uniform array; no CPU work.

### TSPS (WebGL2/PicoGL, second independent implementation)

* One shared vertex buffer for the whole scene; per-map-square draw calls
  (optionally a single `WEBGL_multi_draw`). Per-model placement/plane/
  priority/contourGround metadata lives in a **data texture** fetched in the
  vertex shader via `gl_InstanceID` (`main.vert.glsl` `decodeModelInfo`).
* Roof culling is a vertex-shader early-out that emits degenerate vertices
  for models above `u_roofPlaneLimit` — no CPU rebuild.
* **Conventional depth** (`depthFunc(LEQUAL)`, `init/core.ts` L260),
  `CULL_FACE` enabled for the scene pass (disabled for actors/overlays).
* Alpha: per-face cutoff from material data (`v_alphaCutOff`,
  `main.frag.glsl`), plus a dedicated water path. Priority appears only as
  tiny view-space epsilon layering, never as a global depth hack.
* Same shape as RuneLite: **static geometry, dynamic per-frame decision**;
  the CPU never rebuilds vertex data after load.

The common denominator: **per-vertex material encoding + static buffers +
tiny per-frame CPU + draw counts measured in single digits.**

---

## 2. Remaining RSPSi divergences — performance (the lag)

### P0 — Draw-call and per-command uniform explosion  **[OPEN — not attempted]**

`OpenGlSceneRenderer.drawCommand()` issues, per command: ~9 `glUniform*`
calls (incl. `uFaceBias`, `uTextureLayer`, `uTextured`, `uTerrain`,
`uTextureOffset`, `uTextureAvailable`, `uTextureScale`), a
`glBindTexture`, and a `glDrawElements`. `GpuDrawCommand.canMerge` requires
equal `tile, layer, pass, textureId, priority, depthBias, objectId` and
contiguity, so real scenes (per-object commands, per-texture splits,
per-priority splits) produce **hundreds to thousands of draw calls per
frame**, each with ~10 GL calls of state churn — on macOS's OpenGL driver
this alone pins a modern CPU. This is now the dominant cost after the
re-upload fix.

**Fix (RuneLite-shaped, no GL 4.x needed):**
1. Move `textureId`, `depthBias` (and priority is already there) into
   per-vertex attributes, exactly like RuneLite's packed layout. Layer index
   becomes a shader-side coordinate (`fTextureId` in RuneLite), removing
   `uTextureLayer`/`uTextured`/`uTextureAvailable` per-draw state.
2. In `GpuUploadPlanBuilder`, **order the index buffer** by
   `(pass, textureId, priority, depthBias)` at build time; merging then
   produces long contiguous ranges instead of scattered single-object runs.
3. Issue all opaque ranges with one `glMultiDrawElements` (GL 1.4 core —
   safe for the GL 3.3 target), rebuilding the tiny count/offset arrays
   per frame from the visibility `BitSet` (CPU-only, no GPU upload).
   Same for alpha. Draw calls per frame collapse to ~2–4.
4. Bind the texture array once per frame; move the remaining truly-global
   uniforms out of the loop.

`GpuCommandVisibility`/`Statistics` stay as-is; only the submission loop and
vertex layout change. Parity tests are unaffected (same triangles, same
order within a pass).

### P1 — Per-triangle CPU occlusion every frame  **[DONE]**

`GpuCommandVisibility.of` → `SceneOcclusionResolver.occludesCommand` walks
**every triangle of every command × every occluder** on the CPU per frame.
RuneLite never does this: the client's occluder system rejects at
tile/occluder granularity (`Scene.occlude` builds an active-occluder list,
then per-tile corner tests); TSPS culls roofs in the vertex shader.

**Fix:** precompute a conservative **AABB per command** at plan build
(the command already knows its tile and geometry); per frame test the 8
AABB corners against occluder planes — O(commands × occluders), roughly
100× less work, still conservative (may draw a few extra commands). Keep
`occludesTriangle` untouched for `SoftwareSceneRenderer`.

### P2 — Alpha sorting recomputes per-vertex depths every frame  **[DONE]**

`averageDepth()` walks all alpha vertices per frame, and merged commands
make per-command average depth a poor painter's key anyway. Short term:
derive the sort key from the command AABB (P1's data) — center or max
corner depth. Long term (RuneLite's answer): per-face alpha ordering is a
GPU/compute problem; for an editor, AABB-depth with back-to-front
submission is acceptable and already better than source order.

### P3 — Texture array churn + no mipmaps  **[DONE — mipmaps; texture-array-churn-on-single-edit part still open]**

`uploadTextureArray` deletes and **fully reallocates** the array (all
layers) whenever any texture changes, and samples with `GL_NEAREST`/no
mips. Consequences: shimmering/aliasing in motion (reads as "rendering is
broken"), and full re-uploads on single-texture edits.

**Fix (mirror `TextureManager`):** allocate once with mip levels
(`glTexStorage3D` needs GL 4.2 — with GL 3.3 use `glTexImage3D` per level,
or keep level 0 only + `glGenerateMipmap` after allocation which generates
the chain), set `GL_TEXTURE_MIN_FILTER = GL_NEAREST_MIPMAP_LINEAR`, keep
`GL_NEAREST` mag, update changed layers with `glTexSubImage3D` and regenerate
mips for those layers. Cache textures are 128×128 — the mip chain is trivial
memory.

---

## 3. Remaining RSPSi divergences — correctness (the "renders wrong")

### C0 — RETRACTED: not a bug in this codebase

**Correction (checked against the actual working-tree code, not from
memory):** this finding does not hold. `GpuUploadPlanBuilder.appendModels()`
already classifies **per face**, independently for each pass call:
```java
boolean transparent = face.alpha() != 0 || face.renderType() == 3;
if ((pass == GpuDrawCommand.SubmissionPass.ALPHA) != transparent) continue;
```
so a model with a mix of opaque and alpha faces already emits its opaque
faces into the OPAQUE pass (full depth write) and only its genuinely alpha
faces into the ALPHA pass (`glDepthMask(false)`) — exactly what RuneLite and
TSPS do. `GpuScenePacketBuilder.hasTransparentGeometry()` /
`SceneLayer.opaqueModelIndices()`/`transparentModelIndices()` are a
*different*, model-level partition; traced every consumer
(`SceneLayer`'s own Javadoc, `RenderConfig.filterTile()`, which only remaps
the index lists after visibility filtering and never branches on them) and
found no code path where this partition affects depth-mask or pass
assignment - it exists to preserve OSRS-style category submission order for
inspectors/compatibility renderers, per `SceneLayer`'s own doc comment.
No self-overlapping-model depth bug was found; no fix applied.

### C1 — No back-face culling  **[OPEN — needs a visual winding check before enabling; not attempted]**

RuneLite culls (`GL_CULL_FACE`, client CW winding); TSPS culls the scene
pass. RSPSi disables it (`OpenGlSceneRenderer.initialize`), so interior
faces render — invisible with opaque depth-testing but clearly wrong on
alpha surfaces (double-blend through roof/water back faces) and it doubles
fragment work.

**Fix:** verify winding once against a known asymmetric object plus the
FBO's vertical UV flip, then `glFrontFace(GL_CW); glEnable(GL_CULL_FACE);`
(`BackfacePolicy.nativeWinding()` already documents the intended CW
convention). Gate with a render setting (default on) so a bad model family
can be diagnosed.

### C2 — Indexed-texture transparency discards black texels  **[DONE]**

`frag` discards when `r==0 && g==0 && b==0`, and the upload forces
`alpha = 0xFF`. RuneLite instead uploads palette index 0 with **alpha 0**
and discards on `textureColor0.a < 1.0`. Legitimately black texels
(currently discarded → holes) and future partial-alpha textures require the
alpha-channel convention.

**Fix:** in `uploadTextureArray`, write `a = (rgb == 0) ? 0 : 0xFF` per the
indexed convention, and change the fragment test to `if (texel.a < 1.0)
discard;` removing the rgb test.

### C3 — Minor, record-only

* **[OPEN, deliberately skipped]** `fract()` on animated UVs in the fragment
  shader creates a derivative discontinuity at the wrap seam (mip shimmer
  line). The suggested fix (move to the vertex shader) needs more thought
  than "just relocate it": per-vertex `fract()` before interpolation would
  wrap each vertex's UV independently, and a triangle whose vertices straddle
  the wrap boundary (e.g. u=0.99 and u=1.01→0.01) would then interpolate
  *backward* through the whole 0..1 range instead of continuing forward past
  1.0 - a worse seam than today's, not a better one. Left as-is pending a
  correct design (e.g. add the offset per-vertex without wrapping, keep
  `fract` in the fragment shader) that can actually be visually verified.
* **[DONE]** Textured-face lightness uses `/128.0`; RuneLite uses `/127.0`
  (`frag.glsl`). Cosmetic constant; align when touching the shader anyway.
* **[DEFERRED, as originally noted]** Depth curve is conventional
  (`GL_LESS`, near=1/far=200000). Precision at editor distances is adequate
  (≈0.3 units at 100k view units on 24-bit depth); RuneLite's reversed-Z is
  better but is a coordinated matrix+func+clear+shader change — deliberately
  not mixed into this pass.

---

## 4. Execution order

1. **P0 + C0 together** (vertex layout/index reordering is shared) — this is
   the lag fix and the biggest visual fix.
2. **P1** (command AABBs, reused by P2).
3. **P3** (mipmaps + per-layer texture updates) — pure visual quality.
4. **C1** (culling) after a one-object winding verification.
5. **C2/C3** in the same shader pass as whichever change touches `frag`.

Verify after each step with `Statistics.drawCalls`/`geometryUploaded`
(flat draw calls, zero uploads during camera drag) and the existing parity
suite; regenerate goldens only with visual confirmation per
`RENDERING_SYSTEM_AUDIT_2026-09-18.md`'s rule.

---

## 5. Things checked and found NOT divergent

Documented so nobody re-litigates them:

* Face-bias-only depth (no priority-derived depth offset) — matches RuneLite
  `vert.glsl` exactly, including the constant-NDC-offset scaling.
* `RsFaceOrderPlanner` alpha ordering exists and is correct in kind;
  RuneLite's compute-shader sorter is a scale optimization, not semantics.
* Terrain lighting/HSL decode (`packedHslToRgb`, `noperspective` HSL
  interpolation, smooth-banding toggle) matches RuneLite's
  `hsl_to_rgb.glsl` approach.
* Fog model (rounded-square scene-edge distance) matches RuneLite's
  `vert.glsl` fog within constant differences.
* Texture animation cadence (client-cycle / 20 ms) matches
  `uniTick & 127` semantics closely enough for editor purposes.
* Reversed-Z: deliberate, compensated difference — see C3.
* RuneLite's `Zone`/`RegionManager` streaming infrastructure: correctly
  *not* adopted; RSPSi's bounded editor region doesn't need it. The
  per-zone *upload shape* (static buffers, range draws) is what we should
  borrow, and P0 does that without the streaming machinery.
