# Foundation completion audit — 2026-09-17

This is the current evidence-based review of the foundation before adding a
larger feature surface or replacing the frontend. It complements the progress
ledger in [`ROADMAP.md`](ROADMAP.md) and the external-cache record in
[`EXTERNAL_CACHE_VERIFICATION_2026-09-17.md`](EXTERNAL_CACHE_VERIFICATION_2026-09-17.md).

## Decision summary

The semantic foundation and JavaFX contribution host are implemented enough to
begin extracting first-party vertical features, but it is not yet fully signed
off as a production map editor. Dear ImGui does not require a second
foundation. The remaining work is parity evidence, manual acceptance, and a
native frontend host—not a new world model. The neutral ImGui projection and
shared input seam are now implemented and covered by focused tests.

## Latest verification evidence

With the pinned TSPS revision-240 cache and sprite-bearing `(50,50)` fixture,
`foundationGate` completes successfully. The run reports zero terrain,
location, authored-geometry, semantic round-trip, and shaped-minimap
differences; all four 256×256 minimap planes match exactly. The separate
revision-240 instance verifier also passes 676 packed transforms with zero
terrain or object differences. The only external parity warning in that run is
the deliberately diagnostic `CLIENT_CLIP_TYPE` collision layer; it is not
promoted to route parity.

The representative revision-240 cases `(16,33)`, `(29,72)`, `(49,49)`, and
`(50,50)` have also been run individually through `verifyOsrsRevision`; each
passes terrain, location, and authored-geometry parity. The new
`verifyOsrsRevisionMatrix` task makes that multi-case evidence repeatable from
an operator-supplied manifest.

The configured live OpenRune cache was also launched through the normal
selected OSRS plugin path. The compatibility facade reported OpenRune FileStore
for the modern cache, the corrected DAT2 mapping loaded 2,675 skeleton archives
from index 1, and the client completed map-scene startup with 272 map scenes.
The live run exposed and fixed the prior reversed animation/skeleton index
mapping and modern spot-animation opcodes. A follow-up compatibility-renderer
guard now handles missing modern textures through a bounded shaded fallback
instead of allowing a `-1` sentinel to index the legacy colour palette. This
is startup/containment evidence, not a claim of full renderer parity.

The selectable `OsrsBundle` now compiles through the controlled JavaFX
project-open path; focused tests pass for the capability-only
`OpenRuneServerAdapter` and frontend projection seam. The server adapter
describes OpenRune `or-cache` actions without making the server checkout a
runtime or cache-reader dependency. The frontend test confirms that the ImGui
adapter sees the same immutable scene snapshot and contribution IDs as the
neutral plugin host.

The local live build-240 cache was then verified read-only through the modern
FileStore path at region `(50,50)`: revision 240 and the numeric/short profile
passed, 2,937 map groups and 141,363 neutral assets were exposed, all 9
neighboring regions loaded, 120 bridge links and 21,610 world objects were
materialized, 16,384 scene meshes and 4,726 render-object projections were
built, and decode/encode/decode plus the neutral scene round trip were exact.
Independent RuneLite/TSPS fixture comparisons were not supplied for that run,
so those remain explicit `NOT_RUN` evidence rather than implied parity.

## Foundation gate decision

The Phase 4 semantic foundation gate is now **complete for the supported
revision-240 scope**. The explicit matrix passes the representative plain,
water-dominant, generated-height, wall-heavy, bridge-heavy, sprite-bearing,
boundary, and instance evidence available in the pinned external resources.
Warnings are named rather than hidden: TSPS collision output is
`CLIENT_CLIP_TYPE` diagnostic data, and the first four matrix fixtures do not
carry independent minimap or full-scene fingerprints. The instance verifier
passes 676 packed transforms with zero terrain or object differences.

This is not a claim that the entire production editor is finished. Manual
desktop acceptance, a canonical OpenRune route fixture, full 3D renderer
comparison, and the Dear ImGui frontend remain separate Phase 5/renderer
acceptance gates. They can proceed without changing the world model, command
history, asset facade, or plugin scene contracts.

## Requirement audit

| Requirement | Current evidence | Status |
| --- | --- | --- |
| One neutral world/session/history model | `WorldDocument`, `EditorSession`, canonical commands, selection, dirty regions, save boundaries, and rollback tests | PASS for covered behavior |
| Cache-provider startup boundary | `OsrsBundle` composes `CacheStoreFactory.openOsrs`, revision profile, neutral definitions/assets, project/session creation, and post-cache feature startup; FileStore is the backend/provider rather than an `EditorPlugin`, while 317 remains a separate compatibility path | PASS for the explicit bundle boundary; project-open lifecycle smoke and retirement of the legacy renderer facade remain |
| Understand OSRS scene phases | [`OSRS_SCENE_PIPELINE.md`](OSRS_SCENE_PIPELINE.md), [`SCENE_SEMANTICS.md`](SCENE_SEMANTICS.md), terrain/object/bridge/scene builders, geometry fixtures | PASS for documented/covered phases |
| Four-plane terrain, shapes, rotations, blending inputs, shared edges | 52-case topology matrix, codec tests, material/light contracts, boundary stitching, revision-240 terrain and geometry parity | PASS for covered semantics; full renderer material parity open |
| Regions, chunks, world coordinates, holes, neighbors, instances | World/region/chunk contracts, 9-region window verification, 676-transform revision-240 instance fixture with zero terrain/object differences | PASS for covered fixtures; broader instance editing open |
| Bridges and authored/effective planes | `WorldDocument.effectivePlane`, bridge links, bridge collision relinking, bridge-heavy `(50,50)` evidence | PASS for covered semantics |
| Object categories, orientations, footprints, appearance/config data | Neutral object views, `RenderObject`, OpenRune definition adapter, wall-heavy and bridge-heavy location/geometry parity | PASS for covered semantics; broader definition corpus open |
| Neutral 3D terrain render packet | `TerrainMeshBuilder` topology, terrain materials, and directional light baseline exist; RuneLite review shows the missing final corner/face colors, texture coordinates, flatness, overlay blending, and client-equivalent floor-light derivation | PARTIAL: topology is covered, render-ready terrain appearance is not |
| Neutral model/material render packet | FileStore/OpenRune exposes model geometry, texture triangles, render types, priorities, normals, and object transform fields; RSPSi currently exports only a reduced geometry view and does not resolve shape/type-specific transformed models into the scene | PARTIAL: adapter data is available, neutral render projection is incomplete |
| Canonical 3D scene composition and visibility | `RenderScene` contains terrain/object/bridge foundations; RuneLite review identifies missing complete tile layers, camera/frustum visibility, occluders, roof policy, depth/priority ordering, and a real 3D backend | NOT VERIFIED: current canonical JavaFX surface is a top-down preview and legacy `SceneGraph` is compatibility-only |
| TSPS scene/render-packet adjudication | Pinned TSPS review confirms the implementation details for radius-five underlay blending, final terrain HSL/UV/hidden faces, model-type selection, contouring, merged normals, bridge projection, and opaque/alpha packet fields; conflicts with mutable TSPS ownership and client collision semantics are resolved in [`TSPS_SCENE_REFERENCE.md`](TSPS_SCENE_REFERENCE.md) | PASS as a source review; implementation fixtures remain open |
| Collision semantics | OpenRune route/movement vocabulary, bridge-aware/object-derived collision, deterministic direction/size vectors, live collision construction, explicit fixture semantics | PARTIAL: TSPS `collision.json` now declares `CLIENT_CLIP_TYPE`; an independent `OPENRUNE_ROUTE` fixture is still required |
| Unified asset access for tools/plugins | `AssetRepository` searchable catalog plus typed lazy access for objects, floors, textures, models, map scenes, sequences, map elements, appearance, and collision | PASS for API; broader real-cache parity and mapping lifecycle open |
| Plugin ownership and lifecycle | Descriptor/dependency validation, stable dependency-aware discovery, owned contribution cleanup, reverse shutdown, host-owned LIFO resource cleanup, closed-registry protection, command/tool/panel/inspector/validator/overlay/shortcut/context/settings/asset-provider/status/menu registry, immutable `EditorSceneSnapshot` and per-tile `EditorSceneTileProjection`, shell-owned lifecycle, JavaFX tool/inspector/asset/status/menu/command-palette mounting, and terrain/object/selection-owned shared setting state | PASS for current API and JavaFX host surfaces; Dear ImGui and larger editor contributions remain next |
| JavaFX and Dear ImGui independence | UI-neutral import gate, renderer-neutral scene contracts, shared `EditorFrontendFrame`, `DearImGuiFrontendAdapter`, and common input router | PASS for neutral projection/input; native ImGui interactive host not built |
| Optional OpenRune-Server integration | `ServerAdapter`, `ServerProject`, `ServerBuildProvider`, and `OpenRuneServerAdapter` tests; no server dependency in the cache reader | PASS for declarative build/layout seam; runtime bridge deferred |
| External representative parity | Pinned TSPS revision-240 fixtures for plain/water, wall-heavy, generated-height, bridge-heavy; zero terrain/location/geometry differences and zero shaped-minimap pixel differences | PASS for covered evidence |
| Manual interactive acceptance | [`MANUAL_SMOKE_TEST.md`](MANUAL_SMOKE_TEST.md) exists; full run requires an unlocked macOS desktop | NOT VERIFIED |
| Sprite-bearing map-scene and full 3D renderer parity | Pinned TSPS revision-240 sprite-bearing `(50,50)` fixture now matches all four 256×256 shaped minimap planes with 0 differing pixels; compatibility renderer now contains missing-texture sentinels; no independent full 3D renderer fingerprint fixture | PARTIAL: minimap PASS and fallback containment; full renderer parity open |

## What the proposed vertical plugin architecture changes

It changes packaging and contribution ownership, not the semantic foundation.
Features should be colocated as vertical plugins, for example:

```text
features/height/
├── HeightPlugin
├── HeightToolState
├── HeightTool
├── HeightCommands
├── HeightContextContribution
├── HeightInspector
└── HeightOverlay
```

Studio still owns the application frame, contribution hosts, workspace/layout
policy, viewport host, inspector host, lifecycle, and frontend adapters. See
[`PLUGIN_ARCHITECTURE.md`](PLUGIN_ARCHITECTURE.md) for the adopted boundary and
the Mermaid contribution-flow diagram.

The immediate code additions are intentionally small and foundation-safe:

- `AssetRepository` is now a typed lazy facade instead of descriptor search
  only;
- `EditorCommandRegistration` lets a plugin expose commands while preserving
  `EditorSession` history ownership;
- `EditorSceneSnapshot` prevents plugin scene consumers from reaching the
  mutable `WorldDocument` held by `RenderScene`.
- `EditorInputRouter` gives JavaFX and Dear ImGui the same focus-aware
  shortcut and active-tool pointer dispatch order.
- `EditorPluginContext.track(...)` gives the host ownership of plugin-created
  resources, and the closed registry prevents retained contexts from adding
  stale contributions after shutdown.
- `MinimapBuilder` now locks scene-layer ordering and TSPS-compatible
  vertical map-scene centering, with both focused regression coverage and a
  sprite-bearing external fixture at zero pixel difference.

## Next work, in order

1. Collect the manual JavaFX smoke evidence after the macOS desktop is
   unlocked, including open/edit/undo/save/reopen/recovery and controlled
   workspace focus behavior.
2. Produce a provenance-controlled collision fixture using the same canonical
   OpenRune route/movement semantics as `OsrsCollisionBuilder`, or explicitly
   keep client collision and editor/server collision as separate named layers.
   A first-party exporter probe was attempted against the pinned OpenRune-
   Server checkout. It is currently blocked by cache-layout compatibility:
   that checkout's object decoder targets config archive 55, while the pinned
   TSPS revision-240 cache exposes config archives 1–54 and 70+. No route
   parity claim is made from that probe.
3. Keep the sprite-bearing map-scene minimap gate as a verified regression
   fixture. The revision-240 `(50,50)` capture now matches all four 256×256
   shaped planes with 0 differing pixels after aligning scene-layer ordering
   and TSPS's vertical map-scene centering. Add the neutral 3D render-packet
   contracts and an independent geometry/material fixture; add a full renderer
   fingerprint only when its provenance and coordinate/plane conventions are
   explicit.
4. Expand the FileStore-backed neutral model/object views, then implement
   static revision-240 terrain and object render packets. This must cover final
   corner/face colors, model-type selection, transforms, normals, UVs,
   textures, alpha, priorities, ordered tile layers, and bridge-aware scene
   traversal before optimizing zone caches.
5. Implement one real 3D backend against those packets. The JavaFX top-down
   preview and legacy software renderer remain diagnostic/compatibility
   surfaces; neither may become a second source of scene truth.
6. Finish the native Dear ImGui adapter surfaces in the UI module. The neutral
   projection/input seam is in place; keep toolkit-specific code only at the
   final rendering boundary.
7. Continue extracting the existing catalog into vertical `Paint`, `Height`,
   `Collision`, and `Validation/Debug` feature packages; terrain, object, and
   selection registrations are now separate built-in plugins. Do not create a
   Gradle module for each feature.
8. Complete the current JavaFX shell integration, then implement Dear ImGui as
   a second consumer of the same state, commands, scene snapshots, asset
   facade, and contribution IDs.
9. Revisit third-party JAR loading, PF4J, permissions, scripting, and a Plugin
   Hub only after built-in lifecycle and ownership behavior is stable. Use the
   pinned `melxin/runelite` checkout as an architecture reference when this
   begins; do not copy its injected client or cache backend.

## Foundation stop rule

Do not start a second world model, renderer-specific scene graph, alternate
history system, or broad external-plugin runtime while any item above is
unknown. Feature work can proceed when it uses the existing canonical session,
commands, typed asset facade, immutable scene snapshot, and owned contribution
registry.
