# OpenRune Studio Roadmap

> **Status:** single prioritized execution plan.
>
> Architecture belongs in the authoritative documents listed in docs/README.md. This file says what comes next and what completion means.

## 1. Product target

OpenRune Studio should become a professional OSRS map and content authoring environment with:

- trustworthy OSRS scene semantics and rendering;
- responsive large-scene editing;
- durable project edits independent of cache packing;
- explicit verified cache publication;
- first-class OpenRune Server project integration;
- predictable contextual UI;
- multi-region world authoring;
- reusable query/change/procedural foundations;
- progressively richer content/source tooling.

## 2. Current architecture baseline

Already established and should not be rebuilt:

- WorldDocument authored state;
- command/history/transaction editing;
- core feature modules;
- OpenRune FileStore modern cache backend;
- canonical OSRS region decoder/encoder;
- project-first launcher/loading direction;
- semantic scene separation;
- incremental 8x8 scene compilation;
- zoned GPU upload planning;
- shared native GPU geometry arena;
- CPU DDA picking;
- renderer telemetry foundations;
- OpenRune project inspection/source semantic infrastructure;
- cache-independent recovery snapshots.

Older extension-oriented names in source are migration debt. They are not future architecture.

## 3. Priority 0: finish architecture cleanup

**Status: in progress in this documentation reset.**

Goals:

- one authoritative document per concern;
- delete duplicate/outdated plans;
- remove the abandoned external extension design from product documentation;
- make Save Project versus Publish unambiguous;
- document renderer and OpenRune build flows;
- stop teaching legacy compatibility classes as new-code patterns.

Code follow-up:

- rename/remove legacy extension-oriented built-in UI classes as they are touched;
- remove dead registries/lifecycle paths once production callers are gone;
- keep core-module behavior unchanged during naming cleanup.

Completion gate:

- a new contributor can trace any major responsibility from docs/README.md to one code owner.

## 4. Priority 1: rendering trust and performance gate

**Status: active.**

Visual state is encouraging: current reviewed scenes look close to OSRS and recent manual testing has not shown z-fighting. That does not close parity work.

### 4.1 Correctness

Close the highest-value rendering parity gaps first:

- missing/null/invisible object diagnosis;
- interiors and bridge/plane semantics;
- tile paint/model material/color correctness;
- object preview framing/resolution reuse;
- representative alpha/ordering/texture cases.

Keep RENDERING_PARITY_MANIFEST.json as the detailed correctness backlog.

### 4.2 Measure the real cost

Before more major renderer features, capture repeatable CPU/GPU/memory baselines.

Required metrics:

- CPU compile/packet/upload-plan/submission time;
- GPU time;
- heap/direct/native memory;
- resident geometry/texture bytes;
- dirty/rebuilt/reused zones;
- upload bytes per frame;
- arena rebuild count;
- draw/batch counts;
- stationary, camera-movement, and active-edit FPS.

### 4.3 Remove unnecessary work

Then:

1. complete incremental world-window compilation;
2. ensure camera-only frames do not rebuild/upload static geometry;
3. bound invalidation dependencies;
4. remove repeated semantic/cache work from frame callbacks;
5. audit duplicate retained full-scene representations.

### 4.4 Reduce memory and submission cost

After the waste audit:

- compatible base-mesh reuse/instancing;
- primitive hot-path workspaces;
- zone-first visibility;
- capability-gated persistent mapped updates;
- tighter vertex/metadata packing only with parity proof.

Completion gate:

- memory categories are explainable;
- static-frame geometry upload is zero;
- small edits rebuild bounded zones;
- representative benchmarks are recorded on compatibility and higher-capability GL paths;
- correctness fixtures remain green.

## 5. Priority 2: durable Save Project model

**Status: planned, architecture now defined.**

Turn cache-independent recovery into an explicit durable project edit store.

Work:

- versioned project edit manifest;
- source identity;
- multi-region resource snapshots/journal;
- explicit Save Project / Save All;
- reopen saved edits without publishing;
- distinguish unsaved versus saved-unpublished versus published;
- reconcile/recovery behavior;
- tests for atomic writes and schema evolution.

Reuse SessionAutosaveStore/WorldFragment encoding concepts rather than inventing unrelated serialization.

Completion gate:

- edit multiple regions;
- Save Project;
- close;
- reopen;
- recover exact authored state;
- prove source cache bytes never changed.

## 6. Priority 3: standalone cache publication

**Status: partially implemented for some definition/output paths; map publication needs unified transaction.**

Build one explicit publication coordinator around:

- read-only source;
- staging/output cache;
- dirty resource selection;
- encode-before-write;
- canonical writable FileStore adapter;
- flush/reference table update;
- read-only reopen;
- semantic verification;
- publication provenance/baseline.

Map publication should reuse OsrsRegionEncoder and OsrsRegionSaveCoordinator inside this transaction.

Completion gate:

- source cache remains unchanged;
- multi-region output publishes atomically enough to avoid corrupting source/user work;
- failed validation does not advance publication state;
- reopened output matches authored semantics.

## 7. Priority 4: complete project-first lifecycle

**Status: foundation exists, migration incomplete.**

Finish:

- persistent Studio project descriptor;
- recent-project registry;
- launcher every normal startup;
- OpenRune import and standalone create flow;
- project loading gate;
- saved-edit restoration;
- Dashboard as project home;
- remove raw recent-cache auto-open behavior;
- project close/save state model.

Completion gate:

- app has one project-open lifecycle;
- workspaces cannot create their own project connections;
- optional content analysis does not delay normal project open.

## 8. Priority 5: connected OpenRune publication

**Status: inspect/read foundation is strong; write support remains resource-specific.**

Work in safe order:

1. keep one inspected project/build-task model;
2. expose source provenance everywhere;
3. add stale-source checking;
4. implement structured source editor for already well-defined OpenRune source resources;
5. invoke imported project's detected build;
6. reopen and verify generated output;
7. add generated SERVER verification views.

Do not enable arbitrary connected terrain/location publishing until a lossless OpenRune-consumed source/build hook exists.

Completion gate:

- supported edits are source-first and verifiable;
- unsupported resources remain saveable in Studio but clearly unpublished;
- LIVE/SERVER are never directly patched as fallback.

## 9. Priority 6: strict workspace migration

**Status: contract defined, implementation partially transitional.**

Migrate built-in tools to the contextual multi-rail model:

- Primary Tool Rail;
- Context Drawer;
- conditional Brush Shelf;
- dockable/floating Brush Settings;
- Viewport Quick Palette;
- Right Inspector;
- configurable HUD layer.

Immediate UX fixes:

- selecting a non-brush tool must hide Brush Settings;
- one shared brush state backs docked and floating UI;
- Map Studio tab labels must use the final workspace naming consistently;
- inspectors/HUDs share one semantic snapshot;
- icon use follows one host vocabulary/resource path.

Completion gate:

- every built-in tool has explicit capabilities;
- no arbitrary permanent tool panels;
- no duplicate brush/inspection state.

## 10. Priority 7: multi-region authored world

**Status: planned.**

Region boundaries are persistence boundaries, not authoring boundaries.

Build:

- multi-region loaded world window;
- absolute coordinate-first edits;
- cross-region selection;
- cross-region brushes;
- cross-region object/structure operations;
- dirty-resource tracking per region;
- save/project persistence across region switching;
- incremental renderer alignment with loaded world window.

Completion gate:

- a continuous operation across a 64x64 boundary behaves like one world and publishes correct region resources.

## 11. Priority 8: query, ChangePlan, and reusable authoring primitives

**Status: foundations exist, broader unification planned.**

Build in this order:

### Query/condition engine

Composable AND/OR/NOT conditions over tile/object/semantic facts.

### General ChangePlan

One calculation, validation, preview, commit, undo boundary for complex edits.

### WorldFragment transforms

Copy/paste/rotate/mirror across terrain, objects, planes, and structures.

### AutoTile service

Centralize OSRS overlay shape/rotation/topology logic.

### Linear feature service

Shared geometry for paths, roads, streams, fences, hedges, walls.

### Deterministic modifiers

Seeded scatter/noise/variation with previewable results.

These are shared foundations. Do not implement separate math stacks per tool.

## 12. Priority 9: advanced authoring tools

Only after shared foundations:

- professional Tile Painter;
- conditional tile/object replacement;
- object scatter/decoration;
- road/path;
- stream/river;
- fence/hedge/wall;
- bridge;
- building/structure stamp;
- biome generation;
- WFC-assisted layout.

Each tool remains thin over shared query/geometry/change services.

## 13. Priority 10: broader Content Studio

Expand source/semantic workflows after map/project foundations are trustworthy.

Domains:

- NPC spawn/source visualization;
- object-to-content relationships;
- GameVal/RSCM navigation;
- interface/component relationships;
- quest/content-flow graphs;
- semantic source editing where safe;
- generated SERVER verification;
- later controlled runtime observations/simulation.

Every edit must retain provenance and route to the authoritative source.

## 14. Priority 11: HD renderer foundation

Do not begin substantial effect work while baseline CPU/GPU/memory is still poorly controlled.

Once Priority 1 gates pass:

1. preserve model/terrain normals;
2. face shading/material metadata separation;
3. Studio material table;
4. shader library/includes/uniform blocks;
5. material texture sets;
6. explicit render-pass graph;
7. shadows/lights;
8. terrain materials;
9. water;
10. environments;
11. model overrides/streaming.

Use external HD renderers as behavior/data references only.

## 15. Testing strategy

Every roadmap phase adds acceptance at its owning layer.

### Semantic trust

- real-cache fixtures;
- plane/bridge cases;
- tile paint/model cases;
- object transform/model cases;
- picking.

### Project persistence

- atomic save;
- reopen;
- versioning;
- source identity mismatch;
- crash recovery.

### Cache publication

- source unchanged;
- output reopen;
- semantic round trip;
- failed transaction baseline behavior.

### OpenRune

- structural project fixture;
- role/path/task discovery;
- stale source;
- source write;
- build invocation;
- generated output verification.

### Performance

- repeatable hardware/scenario capture;
- no camera-only geometry uploads;
- bounded dirty-zone rebuild;
- memory ownership counters.

## 16. Execution discipline

1. Keep one focused PR at a time.
2. Fix the earliest incorrect layer.
3. Prefer extending canonical owners to creating wrappers.
4. Do not mix Save Project and Publish Cache semantics.
5. Do not bypass connected OpenRune source/build ownership.
6. Do not trade scene correctness for an optimization.
7. Measure performance changes with the same scenario before and after.
8. Delete superseded architecture documentation in the same PR that replaces it.
9. Mark future work as planned.
10. Leave the codebase with fewer implementation paths than before.

## 17. Next PR sequence

After this documentation reset, the recommended sequence is:

### PR 1: renderer performance baseline

- expose/capture missing CPU/GPU/memory/reuse counters;
- define repeatable benchmark scenes;
- produce baseline report;
- no speculative optimization yet.

### PR 2: upstream incremental rebuild audit

- trace edit -> dirty zones -> scene compile -> packet -> upload;
- remove any full rebuilds triggered by bounded edits;
- verify camera movement has zero geometry rebuild/upload.

### PR 3: retained-memory audit

- measure lifetime/count/bytes for scene, packet, upload, picking, texture, and native representations;
- eliminate provably redundant full-scene retention;
- add diagnostics.

### PR 4: durable project edit store

- extract reusable snapshot encoding;
- project resource manifest;
- Save Project / reopen;
- multi-region-ready identity.

### PR 5: standalone map publication transaction

- staging output;
- batch encode/write;
- reopen/verify;
- provenance/baseline.

Then return to project lifecycle/OpenRune source publication and strict workspace migration based on whichever is currently blocking end-to-end user flow.

## 18. Definition of success

OpenRune Studio reaches the intended architecture when:

- an edit has one obvious path from tool to authored state;
- Save Project never requires cache packing;
- Publish is explicit and verified;
- connected OpenRune generation remains owned by OpenRune's build;
- rendering is visually trustworthy and measurably efficient;
- multi-region authoring feels continuous;
- advanced tools reuse shared primitives;
- documentation tells a coding agent exactly what to use without offering multiple competing architectures.