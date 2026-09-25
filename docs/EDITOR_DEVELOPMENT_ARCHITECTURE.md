# Editor Development Architecture

> **Status:** authoritative internal code-placement and composition guide.

## 1. Design goal

OpenRune Studio is a modular monolith with explicit internal boundaries.

The goal is not to maximize abstraction count. The goal is to keep one obvious implementation route for each concern so humans and coding agents do not accidentally create parallel systems.

## 2. Module boundary

### Client

Put code in Client when it can be expressed without a native window or OpenGL context.

Examples:

- world/document semantics;
- editor commands and history;
- selection;
- cache adapters;
- map codecs;
- definitions/assets;
- project integration contracts;
- semantic scene resolution;
- render-neutral compilation;
- query/generation/change planning;
- autosave/edit persistence;
- core feature modules.

### Editor

Put code in Editor when it directly owns native presentation.

Examples:

- Dear ImGui rendering;
- GLFW lifecycle/input adaptation;
- OpenGL resource ownership;
- project launcher widgets;
- native workspace layout;
- viewport framebuffer/presentation;
- native texture previews.

Do not move a business rule to Editor merely because its first caller is a panel.

## 3. Built-in composition

Built-in features are compile-time core modules.

Canonical entry points:

- com.rspsi.editor.core.CoreEditorModule
- com.rspsi.editor.core.CoreEditorModules

Feature modules register or compose existing neutral services. They should remain thin.

When adding a feature:

    core module
      -> domain service
      -> command/change plan
      -> canonical state

Do not create one lifecycle wrapper per tool.

## 4. Package ownership

| Package/area | Responsibility |
| --- | --- |
| com.rspsi.editor.model | authored world data |
| com.rspsi.editor | commands, session, history, selection |
| com.rspsi.editor.change | validated multi-step changes |
| com.rspsi.editor.brush | shared brush semantics |
| com.rspsi.editor.tool | neutral tool behavior |
| com.rspsi.editor.core | built-in composition |
| com.rspsi.cache.store | cache backend adapters |
| com.rspsi.cache.map | region decode/encode/map persistence |
| com.rspsi.cache.definition | neutral definition views/edit contracts |
| com.rspsi.editor.render | semantic/render-neutral scene contracts |
| com.rspsi.editor.render.compiler | incremental scene compilation/invalidation |
| com.rspsi.editor.integration | neutral server/project integration |
| com.rspsi.server.openrune | first-party OpenRune implementation |
| com.rspsi.project | Studio project identity/layout |
| com.rspsi.editor.io | Studio-owned recovery/edit persistence |
| Editor com.rspsi.renderer.opengl | native OpenGL implementation |
| Editor com.rspsi.studio | native application/workspace composition |

If a class does two unrelated rows, split responsibilities before adding more behavior.

## 5. State ownership

Avoid mirrored mutable state.

Canonical state examples:

- WorldDocument owns authored map state.
- SelectionModel owns editor selection.
- CommandHistory owns undo/redo position.
- project descriptor owns project identity/configuration.
- project edit store owns unpublished durable Studio edits.
- renderer owns only native resource state derived from scene/upload plans.

UI fields may cache presentation values but must not become authoritative copies.

## 6. Service design

Prefer small capability-oriented services over broad grab-bag managers.

A service should have:

- one responsibility;
- explicit dependencies;
- deterministic lifecycle;
- neutral inputs/outputs where practical;
- focused tests.

Before adding a service, search for an existing owner by data type and behavior.

## 7. Method design and duplication control

Prefer:

- one method parameterized by explicit strategy/value;
- a shared private primitive used by public operations;
- one result type with enough information for all callers;
- one validation path.

Avoid:

- FooForMapStudio and FooForObjectStudio when both perform the same domain operation;
- duplicate decoder methods with tiny format differences that belong in a revision profile;
- UI-specific save methods;
- renderer-specific copies of plane/bridge calculations;
- another project path resolver inside a feature.

If two methods differ only in where the caller came from, they probably should be one method.

## 8. Commands and ChangePlan

Simple deterministic edits use EditorCommand.

Complex edits that need preview, conflict detection, boundary checks, or multi-resource validation should move toward ChangePlan:

    calculate
      -> validate
      -> preview
      -> commit atomically
      -> undo as one logical action

Generators and procedural tools produce proposed changes. They do not write directly to WorldDocument or cache files.

## 9. Cache boundaries

Backend classes stay near com.rspsi.cache.store.

Above the adapter boundary, prefer Studio-owned neutral views.

Modern OSRS code should not branch between unrelated cache libraries at random call sites.

Map wire encoding stays centralized in OsrsRegionEncoder and decoding in OsrsRegionDecoder.

## 10. Rendering boundary

Do not place cache lookup or editor mutation inside native drawing code.

The renderer consumes immutable or stable render plans.

Scene semantics should be correct before OpenGL receives them.

Native OpenGL owns resource allocation, upload, draw state, and teardown only.

## 11. OpenRune boundary

Generic project/editor code talks to neutral integration contracts.

OpenRune-specific layout, source semantics, and build discovery live in the first-party OpenRune implementation.

Do not hardcode stock OpenRune paths into unrelated tools.

## 12. UI boundary

The workspace shell decides where a UI surface belongs.

A feature supplies behavior/state. The shell projects it into the correct rail, drawer, inspector, or HUD.

Do not infer tool behavior from where a widget happened to be placed.

## 13. Migration debt

Some older source names still reflect abandoned architecture experiments.

Rules for migration debt:

- keep it working until intentionally replaced;
- do not cite it as the model for new code;
- do not add new dependencies on it;
- migrate callers toward core modules/shared services when touching the area;
- remove it once no production consumer remains.

## 14. Tests by layer

| Change | Minimum evidence |
| --- | --- |
| command/world mutation | focused unit test + undo/redo |
| codec | deterministic round trip + real-cache fixture where relevant |
| plane/scene semantic | semantic fixture |
| compiler/invalidation | dirty-zone/reuse test |
| native renderer | headless GL or renderer acceptance test |
| project persistence | atomic write/reopen/version test |
| OpenRune integration | project fixture + authority/build-role assertions |
| UI placement | workspace state/ownership test |

## 15. Review questions

Before accepting architecture-affecting code:

1. Is there already a class that owns this?
2. Did the change create a second source of truth?
3. Did it duplicate a method with slightly different naming?
4. Can the business rule be tested without native UI?
5. Did a tool or panel gain cache/native responsibilities?
6. Did Save and Publish become conflated?
7. Does a connected OpenRune path bypass source authority?
8. Does renderer code compensate for an upstream semantic bug?
9. Is future behavior clearly marked as future?
10. Can an unfamiliar coding agent find the intended owner from the docs?