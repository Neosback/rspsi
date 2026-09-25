# Editor Development Architecture

> **Purpose:** the shortest authoritative guide for deciding where new editor code belongs.
>
> Product direction lives in `ROADMAP.md`. Public extension behavior lives in
> `PLUGIN_EXTENSION_SDK.md`. This document is about **internal code organization** and
> preventing parallel implementations.

## 1. Architecture: modular monolith

OpenRune Studio is a modular monolith.

There is one application runtime, one editor session, one extension registry, one command/history
system, and one native Studio shell. Core features are compile-time modules. External plugins are
optional extensions loaded after the core into the same neutral registry/services.

```
StudioApplication                         composition root
        |
        v
EditorPluginHost                         one runtime host
        |
        +-- CoreEditorModules             always installed, compile time
        |     +-- terrain
        |     +-- tile painter
        |     +-- path
        |     +-- objects
        |     +-- selection
        |     +-- diagnostics
        |     +-- core UI declarations
        |
        +-- external EditorPlugins        optional JAR extensions
        |
        v
EditorPluginRegistry + PluginServices    one registration/service boundary
        |
        v
EditorSession / commands / selection     canonical authored state
```

The names `EditorPluginHost`, `EditorPluginRegistry`, `EditorPluginContext`, and
`PluginServices` are legacy naming. Their runtime is shared by core modules and external
extensions. Do not create a second host/registry/service graph to avoid those names. Rename them
in a dedicated migration if/when the compatibility surface warrants it.

## 2. The rule that prevents most architecture drift

**A core OpenRune feature is not a plugin.**

If functionality ships as part of Studio, add it to an existing `CoreEditorModule` or create
one new domain module and list it exactly once in `CoreEditorModules`.

Use `EditorPlugin` only for code that can genuinely be installed/uninstalled as an external
extension artifact.

Core and extension code still use the same neutral registry and domain services. This is not a
capability distinction. It is a composition/lifecycle distinction.

## 3. Canonical paths

Before adding a manager, registry, service, model, or tool, check this table.

| Need | Canonical implementation |
| --- | --- |
| Application composition | `Editor/src/main/java/com/rspsi/studio/StudioApplication.java` |
| Core feature manifest | `Client/.../editor/core/CoreEditorModules.java` |
| Core domain registration | `Client/.../editor/core/module/Core*Module.java` |
| External extension lifecycle | `Client/.../editor/plugin/EditorPluginLifecycleManager.java` |
| Shared registrations | `Client/.../editor/plugin/EditorPluginRegistry.java` |
| Shared domain services | `Client/.../editor/plugin/services/PluginServices.java` |
| Authored world state | `EditorSession` + `WorldDocument` |
| Undoable mutation | `EditorCommand` / command classes |
| Selection | `SelectionModel` and `editor.selection` |
| Tool behavior | `editor.tool` |
| Tool pointer hit | `ToolContext.hitAt(...)` / `SurfaceHit` |
| Brush behavior | `editor.brush` + `StudioBrushManager` projection |
| Semantic OSRS API | `com.rspsi.api` |
| Render-neutral scene build | `editor.render` |
| Native OpenGL renderer | `Editor` module renderer classes only |
| Studio layout | `UI_WORKSPACE_CONTRACT.md` |
| Studio panels / rails / HUD rendering | `Editor/src/main/java/com/rspsi/studio/ui` |
| Settings | `SettingsStore`, `SettingsService`, setting keys |
| Project/open lifecycle | `CONTENT_STUDIO_ARCHITECTURE.md` |

If the thing you want already has a row, extend that system. Do not make a parallel one.

## 4. Adding a core tool

A core tool normally requires only:

1. behavior in `editor.tool`;
2. undoable commands/services if it mutates the document;
3. one registration in the correct `Core*Module`;
4. neutral UI metadata/content where possible;
5. native presentation only when the neutral UI contract cannot yet represent it;
6. tests for behavior plus the core inventory/contract tests.

Do **not** create:

- a one-tool `EditorPlugin`;
- another tool registry;
- another selection model;
- another brush engine;
- a special append in `StudioApplication`;
- a renderer fork for ordinary map edits.

## 5. Adding an external extension

External extensions implement `EditorPlugin` and register through `PluginApi` or the shared
neutral registry/services. They can use the same supported editor capabilities as core features.

Plugin lifecycle machinery exists for:

- artifact discovery;
- version/dependency checks;
- enable/disable;
- unload/reload;
- resource cleanup.

It should not be used as the internal dependency injection system for Studio itself.

## 6. Native Studio boundary

`Client` owns semantics. `Editor` owns native presentation.

Core editing behavior must not depend on:

- Dear ImGui;
- GLFW;
- OpenGL;
- native window state.

Native Studio may project a neutral tool, HUD, panel, or overlay into ImGui/OpenGL. It must not
create a second authored-world model or mutation path.

`StudioPlugin` / `StudioToolPlugin` are transitional native projection APIs. Do not add new
business logic there. New tool semantics belong in Client/core/neutral descriptors. These native
interfaces should shrink as the neutral UI contract becomes capable enough.

## 7. AI-friendly coding rules

These are repository rules, not suggestions.

### Search before create

Before adding any class ending in:

- `Manager`
- `Registry`
- `Service`
- `Controller`
- `Context`
- `Store`
- `Tool`
- `Selection`

search for the same domain concept first. Prefer extending an existing canonical type.

### One ID, one registration

Tool/panel/HUD/command IDs must have one production registration source. Tests may use fixtures,
but production code must not describe the same ID in multiple core modules/plugins.

### No compatibility aliases without an exit plan

If a migration needs an alias/wrapper, document:

- canonical replacement;
- compatibility reason;
- removal condition.

Do not leave two equally-valid APIs indefinitely.

### Composition belongs at the edge

Feature construction/registration belongs in `CoreEditorModules`, module installers, or external
plugin loading. Domain classes should not discover global implementations with ServiceLoader,
reflection, or static registries unless that mechanism is itself the documented extension point.

### Prefer data over branching

Tool/UI placement should be descriptor/capability driven. Do not add growing chains of
`if (toolId.equals(...))` when the property can live in the tool descriptor.

### Tests are architecture memory

When a bug happened because a feature was registered in the wrong place, add a boundary or
inventory test so the repository remembers the rule even when the next agent has no conversation
history.

## 8. Current migration boundary

Completed in the modular-core migration:

- core tools no longer need to be optional plugin candidates;
- one `CoreEditorModules` manifest owns core composition;
- Tile Painter and Spline Path are no longer hidden `StudioApplication` append cases;
- duplicate one-tool core plugin wrappers are retired;
- external extension dependencies keep the legacy core host IDs.

Still transitional:

- legacy `EditorPlugin*` names on the shared registry/runtime types;
- native `StudioPlugin` / `StudioToolPlugin` presentation path;
- some native drawers/settings that have not yet moved to declarative neutral UI.

These are migration targets, not reasons to introduce another system.
