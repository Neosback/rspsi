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
native path. MSAA remains explicitly unavailable until its later FBO gate.

## Exit decision

Phase 0 may be marked verified only when `foundationGate` passes, the strict
parity manifest runs successfully, the native smoke workflow is recorded, and
all remaining gaps are explicitly marked blocked or deferred in
[`ROADMAP.md`](ROADMAP.md).
