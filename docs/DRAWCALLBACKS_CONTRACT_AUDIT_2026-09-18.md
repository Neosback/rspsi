# DrawCallbacks Contract Audit — 2026-09-18

Source of truth: official RuneLite API javadoc
`net.runelite.api.hooks.DrawCallbacks`
(https://static.runelite.net/runelite-api/apidocs/net/runelite/api/hooks/DrawCallbacks.html),
cross-checked against the on-disk oracle
`RSPSi-resources/RuneLite-melxin/runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java`
and `Mat4.java`.

---

## 1. Why "we have it wrong" — the actual answer

We never implemented (or even read) the **official hooks contract**. The proposed
plan in this repo was derived from reading `GpuPlugin.java` as an *implementation*,
not from the *interface* the client calls. Reading the interface changes four
conclusions.

### Finding 1 — "GPU" is a capability bitmask, not a boolean (we missed the whole negotiation)

The javadoc defines DrawCallbacks as a **capability negotiation**, not a render loop:

```
GPU                     — GPU mode on
ZBUF                    — enable the z-buffer renderer
ZBUF_ZONE_FRUSTUM_CHECK — enable zoneInFrustum callback
HILLSKEW                — enables Model.getUnskewedModel()
NORMALS                 — requests normals be computed
UNLIT_FACE_COLORS       — enables Model.getUnlitFaceColors()
NO_VERTEX_SNAPPING      — disable vertex snapping for animations
PASS_OPAQUE / PASS_ALPHA / PRE_PASS_ALPHA — pass identifiers
RENDER_THREADS(n)       — parallel scene upload threads
```

The client only hands the plugin the *fast* data paths (unlit colors, unskewed
models, computed normals, multi-threaded zone upload) when the plugin asks for
them. **If we don't request the flag, we're rendering the degraded data path
the client gives a no-GPU plugin** — most importantly `HILLSKEW`:

> `HILLSKEW`: "GPU hillskew support. Enables the Model.getUnskewedModel() API"

This confirms the client itself **skews static object models onto the tile
heights** before the scene reaches the renderer, and hands the GPU plugin the
*unskewed* original only when it opts in. The GPU plugin then does its own
`ModelSkew` transform on the GPU (older revisions) or uses the unskewed vertices
directly.

**Our pipeline does not implement hillskew.** `ModelPacketBuilder` samples one
bilinear height at the object's footprint **center** (`sampleHeight(...)`) and
applies it as a flat `placementHeight` scalar to every vertex of the model
(`GpuUploadPlanBuilder.modelVertex` adds `placementHeight + vertex.y()`). The
correct contract: **skew the model's footprint onto the bilinear tile height
surface** — SW/SE/NW/NE corners get different y-offsets, computed per-vertex from
the model's x/z within the tile. On sloped terrain a tree is currently standing
"planted at the center height" — floating on the uphill side, sunk on the downhill
side. This is a *correctness contract* we inherited from never reading the
interface, and it is not in the current GPU plan.

### Finding 2 — The real pass model is three passes, not two

The javadoc's pass constants:

```
PASS_OPAQUE, PASS_ALPHA, PRE_PASS_ALPHA
```

And the local oracle implements:

```
drawPass(projection, scene, pass)    — called per pass by the client
drawZoneOpaque(zx, zz)               — static opaque zones
drawZoneAlpha(level, zx, zz)         — static alpha zones (per level)
drawDynamic(...)                     — dynamic entities per frame
drawTemp(...)                        — transient objects
```

Our `GpuDrawCommand.SubmissionPass` has OPAQUE/ALPHA only. `PRE_PASS_ALPHA` —
drawn *before* alpha, still depth-writing but blending — is a real RuneLite
stage (used for things like water surfaces that must occlude things behind them
while blending) that we never adopted. Not urgent for the editor, but the
interface says it exists; document rather than silently drop it.

### Finding 3 — `invalidateZone(zx, zz)` is the edit-path contract (we invented our own)

The interface has explicit invalidation:

```
invalidateZone(Scene scene, int zx, int zz)
```

called by the client when a zone's geometry changes; `GpuPlugin` responds by
re-uploading that zone (`SceneUploader.uploadZone`). The dirty-region path the
editor needs for incremental edits **is this hook**, and our `GpuUploadPlan`
regenerates the entire plan + `glBufferData` the whole scene on any tile edit.
The editor-side mirror of `invalidateZone` is the missing piece between "8×8
chunks as GPU invalidation units" (the original design doc's goal) and what the
code does today.

### Finding 4 — Framing error in the proposed plan: "uploadZone" is NOT a DrawCallbacks method

The plan's pipeline diagram lists `uploadZone` under DrawCallbacks ("Scene Load
(loadScene / uploadZone)"). **There is no `uploadZone` hook.** The interface has
`loadScene`, `invalidateZone`, and the draw hooks. `uploadZone` is an
internal method of RuneLite's `SceneUploader` class — an implementation detail
of GpuPlugin, called from `loadScene` and `invalidateZone`. The plan's claim that
"RuneLite uploads static geometry ONCE per scene/zone (loadScene)" is true, but
the *invalidation* hook `invalidateZone` was omitted from the plan's own
diagram — and that omission is exactly the piece our editor's incremental-edit
design needs. The contract is load → invalidate → draw, not just load → draw.

### Finding 5 — What the javadoc does NOT contain: reversed-Z

Nothing in `DrawCallbacks` mandates reversed-Z. The interface is projection-
agnostic — `preSceneDraw(scene, Projection entityProjection, ...)`. RuneLite's
reversed-Z (`glDepthFunc(GL_GREATER)`, `Mat4.projection = {2/w, -2/h, 0,
2n}` → `z_ndc = 2n/z`) is a **GpuPlugin implementation detail**, present in
`GpuPlugin.java:989 glDepthFunc(GL_GREATER)` and `Mat4.projection`. It is a good
idea (the proposed plan's adoption of it is sound), but it is not part of the
hooks contract; treat it as an implementation optimization to adopt, not a
compatibility requirement. That also means the parity harness (software renderer)
does not need to change when we flip the GL backend's depth direction.

### Finding 6 — `zoneInFrustum` is an opt-in callback the client consults before drawing zones

```
ZBUF_ZONE_FRUSTUM_CHECK — enable the zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY) callback
```

The client asks the renderer "is this zone visible?" before iterating its tiles.
Our per-frame CPU occlusion (`GpuCommandVisibility` → `SceneOcclusionResolver`)
is a *command*-granularity replacement for the same job. The contract point to
adopt is the **granularity**: zone-level frustum rejection before per-command
work, not the client's tile-by-tile `tileInFrustum` (which exists for the CPU
renderer). Precompute each command's AABB once at plan build time (the plan
already calls for this), and reject whole commands via zone/AABB tests in
O(1) instead of walking all vertex indices per frame.

---

## 2. Verdict on the proposed plan's items (in light of the real contract)

| Plan item | Verdict |
| --- | --- |
| Adopt reversed-Z (`GL_GREATER`, clear depth 0) | ✅ Sound — RuneLite implementation detail, not contract, but correct fix for depth precision. Keep software renderer on conventional Z; parity is at the *image* level. |
| Relax `canMerge` single-tile constraint | ✅ Sound, **with one correction** (below). |
| Precomputed command AABBs | ✅ Sound — matches `zoneInFrustum`'s granularity; removes the per-frame `CommandBounds.of` index walks. |
| Fingerprint cost: avoid giant string concat | ✅ Sound — but see §4; the current code hashes a megabyte-scale string. |
| `fract()` unconditional in fragment shader | ⚠️ **Do not copy blindly.** RuneLite does not do this. RuneLite's approach: terrain UVs are already modular (`0..256/256.0` in `SceneUploader`), and animated offsets use `fract` in frag shader — but *static* sampling uses clamp-to-edge policy, which `SoftwareSceneRenderer.sample()` reimplements as `clampUv` for model UVs. The seam artifact the plan targets is real, but the correct fix is **REPEAT wrap mode on the texture array + build-time modular UVs**, matching `SoftwareSceneRenderer.wrapUv` for animated UVs. |
| Winding audit of shaped-tile rotations | ✅ Sound — prerequisite for enabling `GL_CULL_FACE` (both oracles cull; we don't). |
| `TerrainMeshBuilder` winding tests exist | ✅ Verified present: `TerrainMeshBuilderTest`, `TerrainSharedEdgeInvariantTest`, `TerrainMeshGoldenTest` all exist under `Client/src/test/java/com/rspsi/editor/terrain/`. |

**Correction to the merge relaxation:** the plan says picking should derive the
clicked tile from the ray-hit point rather than command tile attribution. But
`GpuPlanPicker.pick()` currently returns
`best.command.tile()` — if commands merge across tiles, every merged pick
returns the *first* tile of the merged range. The plan's ray-point derivation
(`(int) hitX / 128, (int) hitZ / 128`) is not a "will do later" item; it is a
**required same-PR change** to `GpuPlanPicker`, otherwise every tool click on a
merged terrain region returns the wrong tile after merging lands. (The hit
point is already available in `pick()` — the intersection math computes it; it
just currently discards the hit point and reports `command.tile()`.)

---

## 3. Contract gaps to add to the plan

1. **Hillskew placement** (contract: `HILLSKEW` flag): replace the flat
   `placementHeight` scalar with per-vertex bilinear skew onto the tile height
   surface. `ModelPacketBuilder.sampleHeight` already has the right sampler —
   extend it to a per-vertex skew during packet build. This is the biggest
   *visual correctness* item the plan missed: trees on slopes currently
   float/sink by up to a full tile-height delta.
   Wait — before implementing, verify against RuneLite's `ModelSkew.java`
   exactly what "skew" means for multi-tile footprints (per-vertex x/z
   interpolation vs. 4-corner bilinear at tile granularity).
2. **`invalidateZone`-shaped edit path** (contract): editor-side mirror — on a
   tile edit, rebuild only the affected 8×8 zone's geometry rather than the
   whole plan. This is the missing contract piece for the "8×8 chunks as GPU
   invalidation units" goal from the original architecture review, and it
   blocks the FPS fix for *edits* (not just camera movement).
3. **`PRE_PASS_ALPHA`** (contract): document as a known, intentionally-unhandled
   pass in `GpuDrawCommand.SubmissionPass`; not needed for editor parity now.

---

## 4. Practical notes for whoever implements

- The merge relaxation changes `GpuDrawCommand` semantics: `tile` becomes
  "first tile of range", not "the tile" — update the record's javadoc, and make
  `GpuPlanPicker` return hit-point-derived tile + objectId (hit point is already
  computed in `intersect`; return it in `PickResult` or a `Hit`-level record).
- `CommandBounds.of()` is called from three places (software renderer alpha
  sort, native renderer alpha sort, occlusion). With precomputed AABBs on the
  command, all three call sites take the precomputed value; `CommandBounds.of`
  can be deleted or kept as a debug cross-check.
- Reversed-Z flip touches: `depthA/depthB` uniforms in `OpenGlSceneRenderer`,
  `glDepthFunc`, `glClearDepth(0)`, and the bias sign convention
  (`projected.z -= (uFaceBias/128) * projected.w` was tuned for conventional Z
  — check the sign against RuneLite's `screenPos.z += float(bias)/128.0` under
  reversed Z before copying).
- The "renderer consumes unknown setting" contract check should grow a rule:
  nothing in production reads `RenderConfig.activePlane` into the native
  backend... (verify before asserting; do not ship this claim unverified).
- `GpuUploadPlanBuilder.fingerprint()` builds a Java string of the full vertex
  list via `StringBuilder.append(List)` — megabyte-scale allocations per plan
  build. Replacing with a streaming hash over primitive buffers is worth doing
  in the same pass as the AABB work.

## 5. Bottom line

The reason "we have it wrong": **we read the implementation, not the interface.**
The interface is the negotiation: capability flags (`HILLSKEW`, `NORMALS`,
`UNLIT_FACE_COLORS`, `ZBUF`, render threads), explicit zone invalidation
(`invalidateZone`), optional frustum callbacks, and a three-pass model
(`PRE_PASS_ALPHA`). We implemented a two-pass, scene-wide-VBO renderer with no
hillskew, no zone invalidation, and invented `uploadZone` as a contract step.
The proposed plan fixes depth (reversed-Z), batching (merge relaxation +
AABBs), and seams (REPEAT + modular UVs) — all sound. Add hillskew and the
`invalidateZone` edit path to the plan, fix the picker before relaxing merging,
and don't copy RuneLite's `fract()`-in-shader approach for static terrain.
