# Phase 0 acceptance record

This record is the evidence checklist for the active OpenRune Studio
foundation phase. It intentionally does not claim completion until the strict
external parity and native smoke evidence are supplied.

## Automated gates

| Gate | Command | Status | Evidence |
|---|---|---|---|
| Deterministic foundation | `./gradlew foundationGate` | PASS | native/settings/cache boundary checks and module tests passed on 2026-09-18 |
| Settings contract | included in `foundationGate` / `Client:check` | PASS | `Settings contract: PASS`; full editor registry coverage included |
| External parity | `./gradlew parityGate` | blocked until manifest and external fixtures are supplied | set `RSPSI_OSRS_PARITY_MANIFEST` |
| Platform matrix | deferred | queued for Phase 9 | macOS, Windows, Linux |

## Required parity manifest

Start from [`PHASE0_PARITY_MANIFEST.example.json`](PHASE0_PARITY_MANIFEST.example.json)
and create an external manifest with real paths. Every case must include:

- a cache path and fixture directory;
- region coordinates and revision;
- coverage categories;
- source/provenance information;
- the selected cache fingerprint;
- zero tolerance; and
- `requireParity: true`.

The manifest must cover plain terrain, shaped overlays, water, bridges and
effective planes, roofs, walls, alpha models, animated-object metadata,
textures, occluders, and region boundaries.

Run the strict gate with:

```text
RSPSI_OSRS_PARITY_MANIFEST=/absolute/path/to/phase0-parity-manifest.json \
./gradlew parityGate
```

The external cache and fixture directories remain outside this repository.

## Native smoke evidence

Run `./gradlew :Editor:run` on the supported development platform and record:

- date, OS, architecture, Java version, cache revision, and region;
- Dashboard startup and cache selection;
- asynchronous load phases ending in `READY`;
- Map Editor opening in the same GLFW/OpenGL/ImGui window;
- one region rendered through the viewport FBO;
- orbit, pan, zoom, and return-to-dashboard behavior;
- dirty-session Save, Discard, and Cancel behavior; and
- renderer diagnostics, missing assets, or startup failures.

Bounded macOS ARM smoke evidence on 2026-09-18: the native GLFW window
launched, loaded the configured revision-240 cache at `50,50`, displayed the
READY dashboard state, and opened Map Editor in the same window with stable
Tools, Viewport, Inspector, and bottom-panel layout. The viewport surface and
GPU submission path initialized without a fatal error; sparse scene output and
the texture-array warning remain Phase 1 renderer follow-up evidence. Dirty
Save/Discard/Cancel was not exercised because the current native slice has no
command-backed edit surface yet.

No JavaFX/AWT viewport or second cache prompt is accepted as evidence for the
native path. MSAA is now enabled by default (registry `0..8`, default `4`,
clamped to the driver's `GL_MAX_SAMPLES` by `GlFramebuffer`) as of the
camera-upload/priority renderer fix below; it is no longer clamped
unavailable.

Automated (non-interactive) renderer regression check, 2026-09-18, macOS
ARM (Apple M2), Java 21: after the camera-upload/priority-bias fix below,
`./gradlew :Editor:run` was launched in the background against the locally
configured cache and log output was captured, not a visual/interactive
session. The renderer initialized (`Native OpenGL Apple / Apple M2 / 4.1
Metal - 90.5`), decoded a full definition set (62,426 objects, 8,560
sprites, 214/214 textures), and rendered a substantial real scene
(source=989001 vertices, rendered=316996 triangles, textures decoded=23
fallback=0 unavailable=0, firstGLerror=0) with no exception and no further
error output over the following ~15+ seconds while the process stayed
alive. This is evidence that the shader compiles, the new
`GpuCommandVisibility`/upload-fingerprint path and priority-descending
opaque ordering do not crash or throw on a large real scene, and
`firstGlError` stayed 0 - it is **not** evidence about visual correctness
(no z-fighting, correct colors/shapes, cull-face winding). Orbit/pan/zoom,
Dashboard navigation, and dirty-session Save/Discard/Cancel were not
exercised in this pass; a genuine interactive smoke test per the checklist
above, ideally including a camera-drag check of the new
`geometryUploaded`/`textureUploaded`/`drawCalls` diagnostics added to the
Inspector panel, remains open.

## Exit decision

Phase 0 may be marked verified only when `foundationGate` passes, the strict
parity manifest runs successfully, the native smoke workflow is recorded, and
all remaining gaps are explicitly marked blocked or deferred in
[`ROADMAP.md`](ROADMAP.md).
