# AI Change Playbook

> **Purpose:** deterministic implementation routes for common changes.
>
> Use this document to answer: where should this change go, what existing path should it reuse, and what must not be duplicated?

## 1. Before changing code

For every non-trivial change:

1. identify the responsibility;
2. read its owner in AI_ARCHITECTURE_OVERVIEW.md;
3. search the repository for the existing service/class/method;
4. inspect tests for the current contract;
5. change the canonical owner;
6. add or update focused tests;
7. update the authoritative document only if the contract changed.

Do not start by creating a new manager, registry, wrapper, or helper.

## 2. Add or change a built-in map tool

Route:

    CoreEditorModule
      -> existing tool/controller
      -> shared domain service
      -> command or ChangePlan
      -> WorldDocument

Use existing brush, selection, query, object, terrain, and command services.

A tool-specific UI may adapt state, but it must not create its own edit history, cache writer, scene resolver, or selection truth.

## 3. Add or change a brush operation

Use:

- EditorBrush / BrushEngine;
- BrushCapability and BrushAwareTool;
- existing brush masks/sampling;
- commands or ChangePlan for committed edits.

Shared brush geometry belongs in the brush subsystem. Do not embed another circle/square/falloff implementation inside a tool.

## 4. Add or change selection or picking

Use the canonical semantic hit/selection path.

Start with:

- SurfaceHit for scene-semantic hit information;
- DdaScenePicker / PickingSpatialIndex for authoritative editor picking;
- SelectionModel for selected authored state.

Do not reconstruct picking from raw GPU buffers or make a panel-specific selection copy.

## 5. Add a property editor or inspector

Read from immutable semantic/authored snapshots.

Writes route through commands/change plans.

The inspector does not own the domain object and should be disposable without losing edits.

## 6. Change terrain/location decode or encode

There is one Studio-owned map wire pair:

- OsrsRegionDecoder
- OsrsRegionEncoder

Extend those semantics and their fixtures.

Do not create a second map codec for a specific workspace.

## 7. Add modern cache access

Start with OpenRune FileStore through OpenRuneCacheStore.

Expose backend-neutral data above the cache boundary.

Before adding a new decoder or writer:

1. inspect existing OpenRune definitions/tools;
2. inspect current Studio adapters;
3. confirm the capability is genuinely absent;
4. add the smallest adapter exception;
5. add real-cache or deterministic fixture coverage.

LegacyDispleeCacheStore is not a fallback modern backend.

## 8. Save editor work without packing a cache

Use Studio-owned project persistence.

The expected path is:

    WorldDocument + project resource identity
      -> durable edit snapshot/journal
      -> atomic Studio-owned file
      -> reopen/recovery

SessionAutosaveStore is the existing proof of concept and recovery implementation.

Do not call OsrsRegionSaveCoordinator merely because the user chose Save Project. That coordinator encodes/writes cache region payloads and belongs to explicit cache publication.

## 9. Publish a standalone cache

Expected path:

    read-only source cache
      -> staging/output cache
      -> encode validated dirty resources
      -> write through canonical writable adapter
      -> flush reference tables
      -> close
      -> reopen read-only
      -> semantic/byte validation
      -> publish/advance baseline

Never write through the ordinary source-cache session.

For map regions, OsrsRegionSaveCoordinator already enforces encode-before-write and mark-saved-after-flush behavior. Use it only inside an explicit output-cache transaction with correct project publication semantics.

## 10. Publish to a connected OpenRune project

First determine the source authority for the resource in OPENRUNE_SERVER_INTEGRATION_MODEL.md.

If there is a lossless supported source representation:

    Studio edit state
      -> stale-source check
      -> transactional source write
      -> imported project's detected build command
      -> OpenRune incremental LIVE build
      -> OpenRune SERVER build/finalization
      -> reopen/verify

If there is no supported source representation, stop at Studio project state. Do not patch LIVE or SERVER.

## 11. Change project startup/loading

Route through:

- persistent Studio project descriptor;
- recent-project registry;
- ProjectOpenCoordinator;
- project loading state;
- cache/project capability validation;
- shell activation after required gates pass.

Project open should not recursively analyze every source tree or eagerly decode every content domain.

Optional domains activate when their workspace needs them.

## 12. Change OpenRune project detection

Extend the one structural inspection graph.

Do not add a second path resolver in a workspace or UI panel.

Structural detection, cache-role discovery, task discovery, source roots, and overrides belong to the neutral inspected-project model.

## 13. Change rendering semantics

First classify the bug.

### Authored/cache semantic problem

Examples:

- wrong terrain opcode interpretation;
- missing object placement;
- wrong transform;
- wrong bridge/effective plane.

Fix decode/authored/semantic resolution first.

### Scene compiler problem

Examples:

- correct authored data but incorrect resolved packets;
- wrong zone invalidation;
- stale derived scene after edits.

Fix resolver/compiler/invalidation.

### Native renderer problem

Examples:

- correct packet but wrong OpenGL output;
- draw ordering;
- texture state;
- depth state;
- buffer upload/submission.

Fix the native renderer.

Do not compensate for an upstream semantic bug in a shader.

## 14. Change renderer performance

Measure first.

Use existing telemetry and add missing counters at the owner closest to the cost.

Check:

- CPU scene compile time;
- packet/upload-plan build time;
- dirty and reused zone counts;
- bytes uploaded this frame;
- total resident native geometry bytes;
- draw call/batch count;
- submission CPU time;
- GPU time;
- JVM used/committed heap;
- direct/native buffer usage;
- stationary FPS;
- camera-movement FPS;
- active-edit FPS.

A camera move that triggers geometry upload is a regression unless a documented renderer contract changed.

Optimization priority is:

1. eliminate unnecessary work;
2. reduce retained duplicate data;
3. improve residency/update strategy;
4. improve submission/batching;
5. compact representation only after parity proves it safe.

## 15. Add HD rendering work

Do not create a second authored scene.

HD work consumes the same semantic scene, geometry identity, zone invalidation, picking identity, and animation state as the current renderer.

Performance and vanilla parity gates come first. Then add renderer-neutral material/shader/pass infrastructure.

See RENDERING_SYSTEM.md.

## 16. Add a manager/service/registry

Before doing so, answer all four:

1. What responsibility cannot the existing owner represent?
2. Why is this not a method on the existing service?
3. Which callers need this abstraction?
4. How does its lifecycle differ from the existing owner?

If the answer is only naming convenience, do not add it.

## 17. Common failure patterns

### Could not find the method, so added one

Search by behavior and data type, not only guessed method name.

### UI needed data, so it decoded it

Move decoding/query behavior to the canonical domain service.

### Tool needed save, so it wrote cache bytes

Tools change authored state. Publication owns cache writes.

### Renderer needed one field, so it queried cache

Renderer consumes prepared semantic/render data. Cache access belongs upstream.

### OpenRune data was nearby, so LIVE was patched

LIVE/SERVER are generated project outputs. Use source-first publication or remain unpublished.

### Performance was slow, so quality was disabled

Profile and fix work/residency first. Keep a deliberate user-facing quality setting separate from performance correctness.

## 18. Definition of a clean change

A clean change:

- has one obvious owner;
- reuses existing semantics;
- introduces no parallel state;
- is testable at the lowest practical layer;
- keeps UI/cache/native boundaries intact;
- preserves save/publish separation;
- updates documentation when a contract changes;
- leaves the next contributor with fewer ambiguous choices.