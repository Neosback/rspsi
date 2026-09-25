# Rendering System Blueprint

> **Status:** authoritative rendering architecture and performance plan.
>
> Correctness status comes from tests, fixtures, RENDERING_PARITY_MANIFEST.json, and explicitly labeled observations.

## 1. Current assessment

The native renderer is already structurally strong and visually close to OSRS in the scenes tested so far.

Current manual observation:

- no z-fighting has been observed in recent testing;
- scene appearance is very close to the expected OSRS reference in the areas reviewed.

These are observations, not proof of complete parity.

Known active problem:

- CPU utilization is still too high;
- GPU utilization is still too high for an editor at rest or during ordinary interaction;
- process/JVM/native memory remains too high;
- interactive performance still needs systematic measurement and reduction.

Performance work is therefore a first-class roadmap item, not an optional polish phase.

## 2. One renderer architecture

Do not create separate scene systems for:

- vanilla rendering;
- future HD rendering;
- picking;
- previews;
- inspectors;
- diagnostics.

All of them derive from the same authored and resolved scene truth.

High-level flow:

    WorldDocument
      -> OSRS scene resolution
      -> incremental scene compiler
      -> render scene/window
      -> GPU scene packet
      -> flat upload plan
      -> zoned incremental upload plan
      -> resident GPU arena
      -> native draw submission
      -> viewport presentation

## 3. Authored state

WorldDocument is the map truth.

Renderer code does not own terrain/object edits.

Changes arrive through editor commands/change plans and are translated into dirty region/zone state.

## 4. Scene resolution

This stage applies OSRS semantics before OpenGL.

Responsibilities include:

- terrain surface interpretation;
- object definition/display resolution;
- transforms;
- authored/effective/render plane semantics;
- bridge behavior;
- object/model placement;
- visibility semantics;
- collision/scene metadata where required;
- stable object/picking identity.

A semantic bug should be fixed here, not hidden in a shader.

## 5. Incremental scene compilation

IncrementalSceneCompiler is the canonical 8x8-zone CPU scene cache.

Properties:

- complete initial compile seeds zone state;
- ordinary edits identify dirty zones;
- unchanged zones are reused;
- topology/cache changes may invalidate all;
- zone revisions provide deterministic reuse boundaries.

The remaining performance objective is to make the entire upstream world-window path honor this incremental model consistently.

## 6. GPU scene packet

GpuScenePacketBuilder converts resolved scene state into renderer-neutral GPU submission data.

It owns ordering and scene packet semantics that native renderers must not independently reinvent.

Its incremental path should:

- retain unchanged tile/scene packet data;
- rebuild only dirty absolute world zones;
- preserve exact OSRS priority/alpha semantics;
- remain camera-independent for static geometry.

## 7. Upload planning

The flat GpuUploadPlan remains the neutral complete upload description.

IncrementalGpuZonedUploadPlanBuilder partitions/reuses upload work by world zone.

A zone is rebuilt when it is:

- dirty;
- missing;
- structurally incompatible;
- affected by a broader invalidation.

Otherwise its immutable native-ready upload is reused.

## 8. Native residency

ZoneVboManager owns logical 8x8-zone residency.

SharedGpuArena owns the shared VAO/buffer storage.

Important current architecture:

- zones retain independent allocation/revision ownership;
- resident geometry shares large native buffers;
- dirty zones update only their slices when capacity permits;
- arena rebuild is a fallback when allocation capacity/layout requires it;
- removing a scene feature should release its resident cost where appropriate.

This avoids one full set of GL objects per zone while preserving granular invalidation.

## 9. Native renderer

OpenGlSceneRenderer owns:

- native shader programs;
- buffer/texture binding;
- texture state;
- frame uniform upload;
- visibility/order workspaces;
- draw submission;
- optional GPU picking resources;
- GPU timer queries;
- GL state;
- resource teardown.

Geometry upload is gated by the camera-independent plan fingerprint.

**Invariant:** camera movement alone must not trigger static scene-buffer upload.

If that happens, treat it as a performance regression.

## 10. Viewport

NativeSceneViewport owns presentation and editor interaction around the renderer.

CPU DDA picking is the authoritative normal editor picker.

GPU ID picking is optional capability/debug infrastructure and should not force a second full scene render or synchronous readback into ordinary interaction.

The viewport also owns:

- frame dimensions;
- camera/navigation projection;
- framebuffer presentation;
- overlay projection;
- selection presentation state.

It does not own authored map semantics.

## 11. Picking architecture

Canonical flow:

    pointer
      -> viewport coordinates
      -> DDA/spatial scene query
      -> SurfaceHit / PickResult
      -> SelectionModel / tool input

Do not rebuild clickboxes independently in each tool.

Picking data should reuse scene identity and spatial indexes rather than retaining one heavyweight Java object per rendered triangle.

## 12. Correctness categories

When a visual problem appears, classify it before editing code.

### Decode/data

Wrong cache bytes, definition, texture, terrain, object, or map interpretation.

### Scene semantic

Wrong transform, model choice, bridge/plane, contouring, tile shape, visibility, or lighting input.

### Compiler/packet

Correct semantic source but stale/missing/incorrect packet.

### Native rendering

Correct packet but wrong depth, blending, texture, ordering, shader, or GL state.

Fix the earliest incorrect layer.

## 13. Visual parity policy

The renderer is not declared perfect because a representative scene looks correct.

Maintain:

- real revisioned cache fixtures;
- terrain/loc semantic tests;
- model/object resolution tests;
- bridge/interior cases;
- shaped overlay tests;
- texture parity fixtures;
- ordering/alpha tests;
- headless/native renderer acceptance where feasible.

Current no-z-fighting observation should remain a regression note, not an excuse to remove depth/ordering tests.

## 14. Performance telemetry contract

Performance work without measurement is incomplete.

Collect at least:

### CPU

- authored change to scene invalidation time;
- dirty-zone compile time;
- packet build time;
- upload-plan build time;
- visibility/order time;
- draw submission CPU time;
- total frame CPU time.

### GPU

- GPU frame time;
- upload occurrence and bytes;
- draw/batch count;
- rendered triangle/index counts;
- texture residency changes.

### Memory

- JVM used heap;
- JVM committed heap;
- direct-buffer use where measurable;
- render packet retained bytes/element counts;
- native geometry capacity and used bytes;
- texture array/state memory;
- per-zone residency/allocation counts;
- duplicate mesh count where measurable.

### Reuse

- dirty zones;
- rebuilt zones;
- reused zones;
- full invalidations;
- arena rebuilds;
- static-frame upload yes/no.

Metrics should be visible in diagnostics and capturable for repeatable benchmarks.

## 15. Standard performance scenarios

Keep repeatable scenarios instead of subjective feel only.

At minimum:

1. one representative region, stationary camera;
2. same region, continuous camera movement;
3. repeated small terrain brush edits;
4. repeated object placement/rotation/move;
5. multi-region window at normal target view distance;
6. interior/roof/bridge scene;
7. texture-heavy/water/animated-texture scene.

Record hardware, OS, JVM, view distance, resolution, cache revision, and build SHA.

Do not publish target numbers until baselines are measured on both the macOS compatibility path and Windows/Linux higher-capability paths.

## 16. Performance priority order

### P0: prove where cost is

Finish telemetry first. Separate CPU, GPU, heap, direct/native, upload, and retained-residency costs.

### P1: eliminate unnecessary rebuilds

- complete world-window incremental compile;
- bound invalidation dependencies;
- ensure camera-only frames do zero static geometry rebuild/upload;
- avoid repeating semantic/cache queries in frame callbacks;
- cache stable ordering/visibility structures where semantics permit.

### P2: reduce retained duplicate CPU data

Audit the lifetime of:

- RenderScene;
- RenderWindowScene;
- GpuScenePacket;
- GpuUploadPlan;
- zoned upload structures;
- model packet/geometry lists;
- texture resources;
- picking structures.

Do not retain multiple full representations longer than required merely for convenience.

Introduce ownership/lifetime diagrams and counters before deleting representations blindly.

### P3: model geometry reuse and instancing

Repeated compatible OSRS models should share immutable base geometry where lossless.

Instance data may carry:

- transform;
- orientation;
- picking identity;
- material/variant key;
- other compact per-placement state.

Fall back to unique geometry for contouring, animation, recolor/retexture, or any case where instancing changes semantics.

### P4: native upload strategy

After P0-P3:

- evaluate persistent mapped buffers on capable platforms;
- keep glBufferSubData-compatible fallback for macOS/OpenGL 3.3-4.1;
- use ring/fence ownership to avoid overwriting in-flight ranges;
- update only dirty streams within dirty zones.

### P5: tighter representation

Only after parity fixtures:

- evaluate packed positions/normals/colors/UVs;
- reduce boxed collections/hot-path allocations;
- use primitive workspaces for order/visibility;
- measure memory reduction versus decode/conversion cost.

### P6: zone-first visibility

Reject invisible zones before command-level visibility when safe, retaining exact command-level correctness fallback.

## 17. Previous optimization foundation to preserve

Recent work already established useful direction:

- lazy texture/sprite residency;
- CPU DDA picking as default;
- static framebuffer reuse;
- shared native geometry arena;
- capability-gated indirect multi-draw;
- packed picking handles/buckets;
- packed face/material metadata;
- larger ordered native batches;
- fewer state changes as batch barriers.

New work should build on these mechanisms rather than replacing them with another renderer.

## 18. Memory ownership blueprint

Target lifetime:

    cache definitions/assets
      long-lived cache session, bounded/cached

    WorldDocument
      authoritative project edit lifetime

    resolved/incremental scene zones
      project/window lifetime, replaced per dirty zone

    GPU packet/upload planning
      reuse or short-lived staging, avoid duplicate full-scene retention

    native zone allocations
      resident while scene window needs them

    frame workspaces
      reused buffers, no per-frame growth

A diagnostic should eventually explain major retained memory categories using this model.

## 19. HD rendering direction

HD work starts only after:

- current renderer parity is trustworthy;
- performance telemetry is complete;
- major rebuild/residency waste is controlled.

HD rendering must consume the same scene and residency architecture.

Ordered foundation:

1. preserve authoritative model and terrain normals;
2. separate face shading/material metadata from geometry;
3. introduce Studio-owned material IDs/table;
4. add shader library/include/uniform infrastructure;
5. generalize texture resources into material texture sets;
6. add explicit render-pass/target abstractions;
7. then add shadows, lights, terrain materials, water, environments, model overrides.

External renderer projects may be used as algorithm/data references. Do not import their live-client lifecycle into Studio.

## 20. Definition of rendering success

Rendering is ready for broader authoring when:

- representative OSRS scenes match semantic and visual fixtures;
- authored objects do not silently vanish;
- interiors/bridges/planes resolve correctly;
- picking agrees with visible semantic scene state;
- camera movement does not rebuild static geometry;
- small edits rebuild bounded zones;
- idle and interaction CPU/GPU costs are measured and controlled;
- retained memory is explainable by owned categories;
- native resources are released deterministically;
- future HD work can reuse the same authored/resolved scene without a second pipeline.