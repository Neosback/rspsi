# OSRS Mechanics & Blending Parity Audit — 2026-09-18

Input: the "Verify OSRS Map Mechanics" checklist (bridge flag, 105×105
heights, loc types, priorities, lighting, packed HSL, chunk palettes, texture
animation, blending). Each claim was verified against our working tree and the
oracles (`RuneLite-melxin` GPU plugin + shaders, TSPS). Scope note: per the
earlier GPU reviews, blending/rendering/GPU items got a line-by-line check;
editor-tool items got a structural check.

Legend: ✅ verified correct · 🟡 partial/risk · ❌ divergence to fix

---

## Part 1 — Blending & transparency (the core ask)

### 1.1 Face alpha byte semantics — ✅ (after checking, our convention is right)
RuneLite `vert.glsl` L84+101: `float a = float(abhsl >> 24 & 0xff) / 255.f;`
then `fColor = vec4(rgb, 1.f - a)` — **alpha byte = transparency amount**
(0 = opaque, 255 = fully transparent). Our chain matches:
- `ModelPacketBuilder` keeps `clamp(rawAlpha, 0, 255)` raw;
- `SoftwareSceneRenderer.opacity()` uses `255 - raw` for models, `128` for
  renderType 3;
- our GPU fragment shader uses `1 - vAlpha/255` for models.
The earlier TSPS review's §3.2 claim ("alpha 255 = OPAQUE, renders solid") was
**wrong** for the model path — the model classifier
(`face.alpha() != 0 → ALPHA`) is correct under the transparency-amount
convention. A face with alpha byte 255 becomes fragment alpha 0 and blends to
nothing. *(Doc corrected.)*

### 1.2 CRITICAL — alpha byte 255 (fully invisible) faces still render as a solid color when textured or when the blend equation is wrong — actually fine, but two real bugs exist nearby:
- **Fully-invisible faces are still submitted.** RuneLite's *CPU software
  renderer* skips `alpha == 255` faces outright (`Model.checkOverlapping`
  classic behavior); RuneLite GPU does too (`SceneUploader` L396: only
  `transparencies[face] != 0` go to the alpha buffer, and **fully transparent
  faces (255) are still uploaded** because the GPU relies on blending). We
  upload them too — parity OK — but they cost a draw-range slot. Optional
  micro-opt: skip `alpha == 255` at build like the classic client does.
  **Not a correctness bug.** 🟡
- **`renderType == 3` half-transparency:** our GPU shader outputs fixed
  `0.5` alpha for `vRenderType > 2.5` — matches software opacity 128 and the
  client's forced-brightness/half-alpha behavior. ✅

### 1.3 CRITICAL — blend function mismatches RuneLite for the framebuffer alpha channel
RuneLite `GpuPlugin` L986:
`glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);`
— color uses standard alpha blending, but the **destination alpha channel
accumulates** (`ONE, ONE`). We use plain
`glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)` (L575), which applies
`SRC_ALPHA` to the alpha channel too, **eroding** the FBO's alpha toward 0 in
overlapping transparent areas.

Why it matters for us specifically: our scene FBO is RGBA8 and is displayed
through `ImGui.image`. When ImGui's style alpha or a future composite uses the
texture's alpha channel, eroded alpha shows as ghosted/see-through UI patches
over water and canopy. It also makes the FBO alpha channel useless for
pick-highlight compositing.

**Fix:** `glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE,
GL_ONE)` in the alpha pass. One line, matches the oracle exactly.

### 1.4 CRITICAL — depth mask leak (re-confirmed; still unfixed)
`applyDrawState` sets `glDepthMask(false)` for the alpha pass and nothing
restores it. The next frame's `glClear(GL_DEPTH_BUFFER_BIT)` is a **no-op**
while the mask is false, so from frame 2 the depth buffer is stale until some
opaque `noDepth==0` path re-enables the mask mid-frame (which it does — the
first opaque batch with `lastNoDepth=-1` triggers `glDepthMask(true)` via the
`isAlpha` branch... **but only when `lastAlpha != isAlpha` fires** — after
`resetDrawState()` the first `applyDrawState(alpha=false)` does hit
`glDepthMask(true)`, so the leak self-heals *mid-frame* but the depth **clear
at frame start still runs masked**).

Net effect: depth clearing depends on draw composition — a frame that starts
with an alpha command (e.g. camera inside a canopy) or a `noDepth` command
first will clear depth with the mask still off. **Fix:** `glDepthMask(true)`
at the top of `draw()` before `glClear`. One line.

### 1.5 Terrain alpha — 🟡 inverted vs RuneLite (but our own choice, document it)
RuneLite uploads **tile-paint vertices with alpha 0 and models with
transparency-amount alpha**; tile alpha is only non-zero for overlay
visibility tricks (`getUnderlayFront` in TSPS-like clients). Our
`TerrainPacketBuilder` emits `alpha=255` for every terrain face, and our GPU
shader renders terrain as `alpha = vAlpha/255` = **1.0** (opaque) — correct
result today, but the *semantic* is inverted vs the model path (255 = opaque
for terrain, 255 = transparent for models). The software renderer's
`opacity()` (`command.layer() == TERRAIN → raw`) has the same split. This is
internally consistent and produces correct pixels; it only becomes a trap if
any future code treats terrain alpha as a transparency amount. **Action:
document the split in `TerrainRenderFace`'s javadoc; no code change.**

### 1.6 Textured-face transparency — ✅ fixed earlier
Alpha-0 texel discard on the texture array matches RuneLite
(`textureColor0.a < 1 → discard`). Indexed-texture transparency convention is
correct.

### 1.7 Missing: `PRE_PASS_ALPHA` (third RuneLite pass) — 🟡 accepted divergence
RuneLite's `DrawCallbacks` defines `PASS_OPAQUE / PASS_ALPHA /
PRE_PASS_ALPHA` (pre-pass draws alpha geometry without color writes to
prime depth for large canopies). We model two passes. Fine for the editor's
bounded scenes; record as a deliberate omission.

---

## Part 2 — Lighting & color

### 2.1 Lighting constants — ✅ exact
`LightingProfile.osrs()`: light vector **(-50, -10, -50)**, ambient
`96`, intensity factor `768`, `ModelPacketBuilder` uses `ambient = 64 +
appearance.ambient()`, `contrast = 768 + appearance.contrast()`, and
`intensity = max(1, (magnitude * contrast) >> 8)`. This matches the deob
client (`ObjectComposition.getModelData`: ambient+64, contrast+768, light
-50,-10,-50) exactly. The doc's claim #5 "fixed sun vector" is implemented
correctly.

### 2.2 Packed HSL 6/3/7 — ✅
`packedHslToRgb` in the fragment shader unpacks `(packed >> 10) & 63`,
`(packed >> 7) & 7`, `packed & 127` with the correct `+0.0078125` hue and
`+0.0625` saturation offsets, and `pow(rgb, 0.6)` gamma — matching
`JagexColor`/`hsl_to_rgb.glsl`. Terrain underlay/overlay HSL decode verified
previously (RsHslColorMath tests). The doc's claim #6 is correctly implemented
end-to-end.

### 2.3 Terrain corner lighting — ✅
`TerrainLighting` derives per-corner light from neighbor heights (slope
normals) with edge fallback to ambient — the doc's "normal from surrounding
vertex heights" is implemented in the parity-correct integer domain.

---

## Part 3 — Terrain mechanics

### 3.1 Bridge flag / render levels — 🟡 modeled, not yet client-exact
Good news: `SceneVisibilityPolicy` already distinguishes
`AUTHORED_PLANE` vs `EFFECTIVE_PLANE` (bridge-resolved), plus
`hideBridgeUpperGeometry` — the doc's recommended
`sourcePlane / renderLevel / collisionPlane / bridgeLink` shape exists in
v0 form. Gap: **the GPU plan builder never applies the policy** —
`GpuUploadPlanBuilder.build()` iterates every tile layer unconditionally, so
the effective-plane view still draws all planes today; the policy is only
consulted by the software path. **Fix (P1):** thread
`SceneVisibilityPolicy` into `GpuUploadPlanBuilder` (or filter `SceneTileSnapshot`s
before build) so the GL viewport honors bridge/effective-plane selection.

### 3.2 105×105 shared height samples — ✅ (verify at doc level)
Terrain topology is built from a shared corner-height grid
(`TerrainMeshBuilder` produces `TerrainFace`s referencing shared
`TerrainVertex`es; neighbor continuity tests exist). Sculpting operates on the
height grid, not per-tile copies — the doc's #2 requirement holds. (Full
editor-UX checks — brush falloff etc. — are roadmap Phase 5, not audited
here.)

### 3.3 Underlay/overlay/shape/rotation — ✅ structurally
`TerrainAppearance` carries `underlayHsl`, `overlayHsl`, `shape`,
`rotation`, `textureId`, `overlayHidden` (the `-2` hole sentinel), and
`TerrainPacketBuilder` emits underlay faces (material 0) + overlay faces
(material 1) with shape+1 scene shape — matching the doc's #9-#10
(SceneTilePaint vs SceneTileModel split). RenderType-2 (material) faces remain
**skipped in the GPU path** (known divergence from the GPU divergence review —
still open).

### 3.4 Chunk palettes (8×8 dynamic regions) — ❌ not started
No instance-template representation exists yet. This is roadmap Phase 5+
(Chunk Composer); nothing to fix now, listed for completeness against the
doc's #7.

---

## Part 4 — Objects

### 4.1 The "4-slot rule" — ✅ correctly NOT implemented
The doc's original claim is wrong (and its own ChatGPT correction says so);
our `SceneTileSnapshot` layers keep arbitrary model lists per tile with no
slot clobbering, matching RuneLite's `GameObject[]` reality. No change.

### 4.2 Multi-tile footprints — 🟡 data exists, placement logic unverified
`ObjectDefinition` sizeX/sizeY and orientation-swapped footprints are decoded
by the FileStore/OpenRune provider, but the editor's placement validation
(overlap/collision-aware placement) is Phase 5 scope. No renderer impact.

### 4.3 `contouredGround` / HILLSKEW — ❌ still scalar
`ModelPacketBuilder` still bakes one `placementHeight` scalar; per the
DrawCallbacks contract audit and TSPS review this is the known placement
divergence (P1: GPU height-map skew). Re-confirms the doc's #25.

### 4.4 Priorities 0–11 — ✅
`face.priority()` clamped 0..255, sorted high-first in the opaque pass,
alpha pass ordered via `RsFaceOrderPlanner` (RuneLite parity). The doc's
"0–10" correction (actually 12 groups) matches what we already implement.

---

## Part 5 — Texture animation

### 5.1 Direction table — ✅ exact
`TextureAnimation.offset`: 1 = −V, 2 = −U, 3 = +V, 4 = +U, displacement
`speed * cycle / size` — matches RuneLite's `Texture` animate directions and
TSPS's `textureAnimations` unit. UV-scroll in the shader (instead of the
client's pixel-buffer rotation) is the correct GPU-native approach the doc
itself endorses.

### 5.2 Tick source — ✅
`clientCycle()` = 20 ms steps (client tick), not the 600 ms server tick.

---

## Fix list (priority order)

| # | Fix | Where | Cost |
| :-- | :-- | :-- | :-- |
| 1 | `glDepthMask(true)` before `glClear` (1.4) | `OpenGlSceneRenderer.draw()` | 1 line |
| 2 | `glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE)` (1.3) | `applyDrawState` | 1 line |
| 3 | Thread `SceneVisibilityPolicy` into the GPU plan build (3.1) | `GpuUploadPlanBuilder` / caller | small |
| 4 | Skip `alpha==255` model faces at build (1.2 optional) | `GpuUploadPlanBuilder` | tiny |
| 5 | Document terrain-vs-model alpha convention (1.5) | `TerrainRenderFace` javadoc | trivial |
| 6 | (carried) GPU height-map skew (4.3), renderType==2 faces, texture-array hygiene, chunked buffers | earlier reviews | P1 |

Items 1–2 are the two blending/GPU bugs this pass adds to the known list —
both one-liners, both verified against the RuneLite oracle line-by-line.
Everything else in the checklist was either already correct (lighting, HSL,
animation directions, priorities, object-slot reality) or is a documented
deliberate divergence (two-pass alpha, conventional terrain alpha, packed
vertex format pending chunking).
