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

Bounded macOS ARM smoke evidence on 2026-09-19:
- Environment: macOS ARM (Apple M2), Java 21 (Temurin), Native OpenGL 4.1 Metal - 90.5.
- Dashboard & Cache Validation:
  - Unconfigured state displays prominent amber warning card (`CACHE PATH REQUIRED`) with real-time directory validation.
  - Configured state points to `/Users/tylercovalt/Desktop/LIVE` (revision 240 reference cache).
  - Background asynchronous load finishes cleanly in ~300ms, displaying the green `[OK] CACHE READY & VERIFIED` status.
  - Comprehensive inspection of all 18 FileStore decoders passes with 100% operational verification:
    - 133,527 definitions (62,426 objects, 33,978 items, 16,345 NPCs, 14,490 sequences, 4,018 spotanims, 9,783 CS2 scripts, 969 UI interfaces, 19,087 varbits, etc.).
    - 14,061 audio entries (12,096 sound effects, 882 music tracks, 581 vorbis samples, 315 jingles, 187 patches).
    - Visual media (8,560 sprite groups with 14,606 sub-sprites, 61,866 models, textures, materials).
    - 117,200 raw archives across all 25 raw indices verified healthy.
- Native Map Editor Viewport & FBO:
  - Opens in the same native GLFW/OpenGL/ImGui window with Dear ImGui docking enabled.
  - DockBuilder initializes default `Tools | Viewport | Inspector` layout with bottom utility drawer.
  - Viewport renders into `GlFramebuffer` with MSAA 4x enabled (samples clamped to driver `GL_MAX_SAMPLES`).
  - Viewport panel displays live GPU render statistics: triangles rendered, draw calls, textures decoded/fallback, first GL error (0), and upload status (`geometryUploaded: NO`, `textureUploaded: NO` on steady camera).
  - Camera orbit, pan, and zoom operate smoothly with zero geometry or texture rebuilds.
  - Captured high-resolution screenshot evidence: `dashboard_verified.png` and `dashboard_warning.png`.

## Exit decision

Phase 0 may be marked verified only when `foundationGate` passes, the strict
parity manifest runs successfully, the native smoke workflow is recorded, and
all remaining gaps are explicitly marked blocked or deferred in
[`ROADMAP.md`](ROADMAP.md).
