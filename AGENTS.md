# AGENTS.md

This file is the required operating guide for coding agents and contributors working in OpenRune Studio.

## Read this first

Before making architecture-affecting changes, read in this order:

1. docs/README.md
2. docs/AI_ARCHITECTURE_OVERVIEW.md
3. docs/AI_CHANGE_PLAYBOOK.md
4. the concern-specific authoritative document named in docs/README.md
5. docs/ROADMAP.md only when sequencing or future work matters

Production code plus passing tests describe what exists today. Documentation describes ownership and intended flow. A roadmap item is not proof that a feature already exists.

## One responsibility, one canonical path

Before creating a manager, registry, service, decoder, encoder, cache adapter, render setting, scene compiler, selection model, save path, project connection, or UI registration path:

1. search for the existing responsibility;
2. identify its canonical owner;
3. extend that owner when the responsibility fits;
4. add a new abstraction only when the existing owner genuinely cannot represent the new responsibility;
5. add tests that prove why the new abstraction is distinct.

Do not create a second method that is almost the same as an existing method merely because its caller is different. Prefer one neutral operation with explicit inputs over parallel convenience implementations that drift.

## Core composition

OpenRune Studio is a modular monolith.

Built-in feature composition uses:

- Client/src/main/java/com/rspsi/editor/core/CoreEditorModule.java
- Client/src/main/java/com/rspsi/editor/core/CoreEditorModules.java
- the existing CoreTerrainModule, CoreObjectModule, CorePathModule, CoreSelectionModule, CoreTilePainterModule, CoreDiagnosticsModule, and CoreUiModule

Legacy extension-oriented names may still exist in source during migration. They are compatibility debt, not the model for new code. New built-in behavior belongs in the core module/service architecture.

## Canonical ownership

| Concern | Canonical owner |
| --- | --- |
| authored map state | WorldDocument and editor session/window state |
| undoable edits | EditorCommand, CommandHistory, CommandTransaction, ChangePlan as it matures |
| selection | SelectionModel and canonical semantic hit/selection state |
| modern OSRS cache access | OpenRuneCacheStore / OpenRune FileStore |
| terrain/location encoding | OsrsRegionEncoder |
| terrain/location decoding | OsrsRegionDecoder |
| map archive access | MapService / OsrsMapService |
| standalone cache publication | explicit writable output cache only |
| project edit recovery | SessionAutosaveStore and project-owned edit state |
| OpenRune project inspection | OpenRuneServerAdapter / ServerProjectInspection path |
| OpenRune source/content integration | first-party OpenRune provider/services behind neutral integration contracts |
| scene semantics | authored world -> resolver/compiler -> semantic scene views |
| render compilation | com.rspsi.editor.render and compiler package |
| native OpenGL rendering | Editor com.rspsi.renderer.opengl |
| project startup | project descriptor -> launcher -> loading gate -> shell |
| workspace placement | UI_WORKSPACE_CONTRACT.md |
| priorities | ROADMAP.md |

If the table and a lower-level document disagree, stop and reconcile the documentation with production code rather than inventing a third interpretation.

## Editing flow

The normal editing path is:

    input
      -> active core tool
      -> command or validated ChangePlan
      -> WorldDocument
      -> dirty-region / revision tracking
      -> incremental scene compile
      -> render-neutral GPU plan
      -> native renderer

UI callbacks do not directly mutate cache files or renderer buffers.

## Save, autosave, publish

Keep these meanings separate:

**Edit/preview**
- changes canonical authored state;
- records undo/redo;
- marks project resources dirty;
- updates derived scene state.

**Save Project**
- writes Studio-owned project/edit state atomically;
- must be reopenable without publishing a cache;
- must not modify the source cache;
- must not mark an unpublished cache change as published.

SessionAutosaveStore already proves the cache-independent snapshot model. Extend that model into the durable project-edit store rather than making autosave files the only long-lived representation.

**Publish Cache**
- is explicit;
- encodes validated changed resources;
- writes an explicit output/staging cache;
- flushes reference-table changes;
- reopens and verifies output;
- only then advances the publication baseline.

Do not redefine Save Project to mean Publish Cache.

## Connected OpenRune projects

Treat .data/cache/LIVE and .data/cache/SERVER as generated outputs owned by the imported OpenRune project.

Never:

- patch LIVE or SERVER directly as a normal Studio write path;
- run FreshCache automatically on project open;
- duplicate OpenRune's incremental build database or pack ordering;
- assume LIVE and SERVER are interchangeable.

For a supported resource, edit the authoritative OpenRune source representation, perform stale-source checks, then invoke the detected project build entry point and verify generated outputs.

For arbitrary terrain/location map edits, connected-project publication remains disabled until there is an explicit lossless OpenRune-consumed source/build hook. Studio may still save the edit in its own project state and preview it without touching generated caches.

## Rendering rules

Rendering has one path. Do not build a second scene system for diagnostics, HD work, previews, or tools.

Current high-level flow:

    WorldDocument
      -> scene resolution
      -> incremental 8x8-zone compile
      -> GpuScenePacket / upload plan
      -> zoned upload plan
      -> ZoneVboManager
      -> SharedGpuArena
      -> OpenGlSceneRenderer
      -> NativeSceneViewport

Camera movement alone must not rebuild or upload static scene geometry.

Dirty edits should invalidate the smallest correct dependency set. Full rebuild remains a correctness fallback for topology, cache, revision, or renderer-contract changes.

The renderer is not considered performance-complete. Preserve or improve telemetry for CPU compile time, upload bytes, native geometry bytes, draw submission time, GPU time, heap/direct memory, dirty/reused zone counts, and frame rate under both stationary and active-edit workloads.

Do not trade OSRS semantics or editor correctness for a benchmark shortcut.

## Reference discipline

Use the right source for the question:

1. real OSRS cache fixtures for data and scene acceptance;
2. vendored RuneLite client source for OSRS scene behavior;
3. RuneLite API naming only as a vocabulary reference;
4. RuneLite GPU/client rendering source when investigating renderer behavior;
5. OpenRune FileStore for modern cache encoding, writing, reference tables, and cache tooling;
6. OpenRune Server for connected-project source/build/cache ownership;
7. Terraini and TSPS only as secondary implementation references;
8. legacy RSPSi behavior only when locked by current tests or independently validated.

Do not copy a reference project's architecture simply because one useful algorithm lives there.

## Change checklist

Before opening a PR:

1. identify the canonical owner;
2. search for an existing equivalent method/service;
3. update the smallest correct layer;
4. keep backend/UI/native types behind their boundaries;
5. add focused tests;
6. update the authoritative document if ownership or flow changed;
7. update ROADMAP.md only if priority/status changed;
8. verify no obsolete architecture document now contradicts the change.

A clean change leaves fewer possible ways to do the same thing, not more.