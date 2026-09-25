# AI Architecture Overview

> **Status:** authoritative whole-system mental model.
>
> Read docs/README.md first for document ownership.

## 1. Product model

OpenRune Studio is a project-first desktop authoring environment for OSRS maps and related content.

It is a modular monolith:

    Project
      -> authored state
      -> semantic resolution
      -> rendering / inspection
      -> explicit publication

There is one production path for each responsibility. The architecture is intentionally biased toward discoverability and AI-generated code safety: a contributor should be able to find the existing owner before inventing another abstraction.

## 2. Top-level modules

### Client

Client owns logic that must be testable without a native window or OpenGL context:

- modern OSRS cache adapters;
- cache definitions and neutral asset views;
- WorldDocument and authored world models;
- commands, history, transactions, selection, and ChangePlan;
- map decode/encode services;
- scene resolution and semantic views;
- render-neutral packets and incremental compilation;
- project and server integration contracts;
- OpenRune source/content analysis;
- autosave and project-edit persistence primitives;
- core feature modules.

Client must not require Dear ImGui, GLFW, or OpenGL for domain behavior.

### Editor

Editor owns native presentation:

- Project Launcher;
- project loading UI;
- Dashboard/project home;
- Map Studio and other native workspaces;
- Dear ImGui workspace shell;
- GLFW window/input integration;
- OpenGL renderer and native GPU resources;
- frontend projection of neutral tool/inspection state.

Business rules should move toward Client-owned neutral contracts rather than being implemented only in widgets.

## 3. Core feature composition

Built-in features use CoreEditorModule and CoreEditorModules.

Current core modules include terrain, tile painting, object work, path work, selection, diagnostics, and shared UI registrations.

A built-in feature should normally be:

    core module
      -> shared domain service
      -> command / ChangePlan
      -> canonical authored state

Do not create a separate lifecycle or registration system for one feature. Existing legacy extension-oriented names are migration debt only.

## 4. Canonical authored-world flow

The authoritative editing flow is:

    user input
      -> active tool/controller
      -> command or validated ChangePlan
      -> WorldDocument
      -> history + dirty resource state
      -> scene invalidation
      -> incremental scene compile
      -> semantic/render snapshots

WorldDocument is the authored map truth while the project is open.

Render packets, native buffers, inspectors, HUDs, and previews are derived state. They do not become alternate sources of truth.

## 5. Read versus write semantics

Read APIs should expose immutable snapshots or stable views.

Writes should flow through:

- EditorCommand for direct atomic operations;
- CommandTransaction for grouped atomic operations;
- ChangePlan for operations that require calculation, validation, preview, and one final commit.

Do not make a UI callback mutate tiles, objects, cache files, or native renderer state directly.

## 6. Coordinates and planes

Keep these concepts distinct:

- document-local coordinates;
- absolute OSRS world-tile coordinates;
- authored plane;
- effective/client plane;
- render plane;
- viewport/screen coordinates.

Use the existing coordinate and ScenePlaneSemantics contracts. Do not duplicate bridge or LINK_BELOW logic in tools, inspectors, picking, or rendering.

## 7. Cache architecture

### Modern OSRS

OpenRune FileStore is the production backend.

Canonical boundary:

    cache directory
      -> OpenRuneCacheStore
      -> neutral cache/definition/map services
      -> authored/semantic Studio state

Studio-owned terrain/location wire semantics remain centralized in OsrsRegionDecoder and OsrsRegionEncoder.

### Legacy compatibility

LegacyDispleeCacheStore exists only for explicit old/custom compatibility paths. It is not a second modern OSRS backend.

### Source caches are immutable inputs

A source cache opened for authoring is not the file Studio casually edits in place.

Standalone publication uses a separate explicit output cache.

Connected OpenRune projects treat LIVE and SERVER as generated, read-only products of the OpenRune build.

See CACHE_EDITING_AND_PUBLISHING.md.

## 8. Project persistence model

Studio has three separate state transitions:

### Edit / preview

Changes WorldDocument and derived scene state. No cache pack is required.

### Save Project

Persists Studio-owned edit state atomically so work can be closed and reopened without touching the source cache.

SessionAutosaveStore already demonstrates a cache-independent complete WorldDocument snapshot. The durable project-edit model should evolve from this idea, with explicit resource manifests, source identity, dirty state, and schema versioning.

### Publish / build

Creates deployable cache or project output. This is explicit and validated.

These actions must remain separate in UI language and code.

## 9. Connected OpenRune model

An imported OpenRune project owns its generated cache lifecycle.

Normal flow:

    OpenRune source/config
      -> OpenRune project's detected cache build
      -> .data/cache/LIVE
      -> .data/cache/SERVER
      -> Studio reopens and verifies generated outputs

Studio may edit only a supported authoritative source representation.

If no lossless OpenRune-consumed source representation exists, Studio keeps the resource editable in its own project state but does not silently patch generated OpenRune caches.

FreshCache is bootstrap/reset tooling, not normal project-open behavior.

See OPENRUNE_SERVER_INTEGRATION_MODEL.md.

## 10. Scene semantics

Studio separates:

    authored world
      -> OSRS resolution rules
      -> resolved scene semantics
      -> renderer-neutral packets
      -> native rendering

Authored and resolved identity are both retained. An authored object that fails to become visible should remain diagnosable instead of disappearing from the API.

The semantic layer owns concepts such as:

- tile paint versus shaped tile model;
- authored/effective/render plane;
- object definition and transformed/display definition;
- model/type resolution;
- collision and visibility diagnostics;
- canonical SurfaceHit.

See SCENE_SEMANTICS_REFERENCE.md.

## 11. Rendering architecture

Rendering is a derived consumer of authored and resolved state.

Current high-level path:

    WorldDocument
      -> scene resolver/compiler
      -> IncrementalSceneCompiler
      -> RenderScene / RenderWindowScene
      -> GpuScenePacketBuilder
      -> GPU upload plan
      -> incremental zoned upload plan
      -> ZoneVboManager
      -> SharedGpuArena
      -> OpenGlSceneRenderer
      -> NativeSceneViewport

The canonical invalidation unit is an 8x8 world zone where applicable.

Camera movement is presentation state. It must not force static world geometry to be rebuilt or re-uploaded.

The current renderer appears visually close to OSRS in tested scenes and no z-fighting has been observed in current manual testing. Neither observation is a proof of parity. Keep real-cache fixtures and renderer telemetry active.

See RENDERING_SYSTEM.md.

## 12. Workspace architecture

After project load, the main editing shell is a strict contextual multi-rail workspace.

Core concepts:

- Primary Tool Rail: choose editing mode;
- Context Drawer: tool-specific workflow and content choices;
- Brush Shelf: shared brush mechanics only when the active tool needs them;
- Viewport Quick Palette: compact near-cursor switching;
- Right Inspector: exact selection/property/settings work;
- HUD Layer: glanceable scene/tool diagnostics.

Visibility is derived from active tool capabilities and selection/context state, not from arbitrary panel placement.

See UI_WORKSPACE_CONTRACT.md.

## 13. Project lifecycle

Application lifecycle:

    Application start
      -> Project Launcher
      -> project descriptor selection/import
      -> Project Loading
      -> Project Shell
      -> Dashboard
      -> workspace

A raw cache path is project configuration, not application identity.

Project opening should establish identity and required cache/project capability without eagerly decoding every optional domain.

See PROJECT_LAUNCHER_AND_DASHBOARD.md.

## 14. Source of truth by concern

| Concern | Source of truth |
| --- | --- |
| current implemented behavior | production code + tests |
| authored map | WorldDocument / active project edit state |
| edit history | command history / transaction state |
| modern OSRS cache | OpenRune FileStore through OpenRuneCacheStore |
| map wire format | OsrsRegionDecoder / OsrsRegionEncoder |
| OpenRune project layout/build | OpenRuneServerAdapter + inspected project model |
| connected OpenRune generated client cache | LIVE, read-only |
| connected OpenRune generated server cache | SERVER, read-only |
| semantic scene | Studio semantic/resolution layer |
| renderer correctness backlog | RENDERING_PARITY_MANIFEST.json |
| native rendering | OpenGlSceneRenderer + zone/shared-arena path |
| UI placement | UI_WORKSPACE_CONTRACT.md |
| product order | ROADMAP.md |

## 15. Architecture invariants

1. One canonical owner per responsibility.
2. Built-in functionality uses core modules and shared services.
3. Source caches are never implicit mutable working files.
4. Save Project and Publish Cache are different operations.
5. Connected OpenRune LIVE/SERVER are generated outputs, not fallback write targets.
6. WorldDocument remains authored truth while editing.
7. Scene semantics are derived once and reused.
8. Rendering never becomes authored state.
9. A UI surface does not own business rules merely because it displays them.
10. A new abstraction must remove ambiguity, not create another way to do the same thing.
11. Planned behavior is labeled planned.
12. Correctness and performance claims require measurement or fixtures.