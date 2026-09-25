# AI Change Playbook

> **Purpose:** deterministic implementation routes for common OpenRune Studio changes.
>
> Read `AI_ARCHITECTURE_OVERVIEW.md` first. This file answers: **where do I change this without
> creating a second system?**

# 1. Before editing anything

Always do this first:

1. identify the responsibility, not just the filename named in the task;
2. search for the stable ID, setting key, tool ID, panel ID, service interface, or command involved;
3. read the relevant canonical owner from `AI_ARCHITECTURE_OVERVIEW.md`;
4. search for tests that already encode the behavior;
5. change the canonical owner;
6. remove obsolete parallel paths instead of leaving both;
7. add/update architecture memory tests when the bug was caused by ambiguity.

If two different production systems appear to own the same responsibility, stop expanding either
one and resolve the ownership first.

# 2. Add or change a core map tool

Use this route:

```
editor.tool behavior
      |
commands/services
      |
Core*Module registration
      |
neutral ToolUiDescriptor / ToolUiContent
      |
native Studio projection
```

Checklist:

- behavior is headless/testable in Client;
- tool ID is unique;
- edits are undoable;
- pointer queries use `ToolContext.hitAt(...)`;
- brush behavior is declared, not inferred from UI placement;
- tool is registered once in the appropriate core module;
- Context Drawer/Quick Palette/Inspector/HUD are described through neutral APIs where supported;
- no one-tool `EditorPlugin`;
- no hardcoded fallback button in Studio UI.

# 3. Add or change a brush tool

Use:

- `EditorBrush`;
- `BrushEngine`;
- `BrushCapability`;
- `StudioBrushManager` only as native projection/state for the shared brush UX.

Declare one of:

- `NONE`;
- `SHARED_SETTINGS`;
- `TOOL_OWNED`.

Do not decide Brush Rail visibility by checking tool IDs.

# 4. Add a right-side inspector/settings surface

Use a `StudioPanel` or neutral inspector contribution that resolves to `DockRegion.RIGHT`.

Right-side content should be one of:

- inspection;
- exact property editing;
- region/world intelligence;
- simulation/player context;
- map/render settings;
- theme/context intelligence.

If the user should later place the inspected asset, provide an explicit action that hands a
semantic asset/preset to a bottom authoring tool.

Do not make the inspector itself a hidden placement system.

# 5. Add a bottom authoring workflow

The bottom area is one Primary Tool Rail plus one active Context Drawer.

The tool should own the drawer through its descriptor/projection.

Examples:

- material/tile catalog;
- object catalog;
- generator settings;
- path rules;
- biome parameters.

Do not add generic History/Tasks/Notifications/Diagnostics drawer modes.

# 6. Add a floating picker

Use the floating tool rail / Quick Palette only for:

- selection/picking;
- compact contextual choices;
- quick switching related to the active operation.

Do not create a second copy of the primary authoring rail.

# 7. Add a renderer setting

Do all steps in one change:

1. define typed key in `RenderSettingKeys`;
2. register it in the settings registry;
3. give it a consumer in `SettingConsumerCatalog`;
4. compile it in `RenderConfigCompiler`;
5. store it in immutable `RenderConfig` when renderer-facing;
6. consume the compiled value in the viewport/renderer;
7. expose UI through the same key;
8. add a compiler/contract test.

Do not read the same renderer preference directly from `SettingsStore` in several native classes.

If a setting is not supported by the current renderer, show it disabled with an explanation instead
of wiring a no-op checkbox.

# 8. Change rendering behavior

First classify the bug.

## Semantic/scene problem

Examples:

- wrong tile color;
- wrong object/model;
- wrong bridge/render plane;
- missing object;
- wrong texture/definition.

Fix scene/cache semantics before native OpenGL.

## Packet/compiler problem

Fix the render-neutral compiler/packet builder.

## Native renderer problem

Only then change OpenGL/native renderer state.

Use `RENDERING_PARITY_MANIFEST.json` as the rendering backlog and record semantic evidence before
claiming pixel parity.

Do not compensate for bad semantic data with renderer-specific magic constants.

# 9. Add a modern OSRS cache capability

Start with OpenRune FileStore.

Allowed architecture:

```
OpenRune backend type
      |
one adapter in cache/store
      |
Studio neutral interface/value
      |
rest of application
```

If FileStore already exposes the capability, use it.

If FileStore lacks it:

1. prove the missing capability;
2. add the narrowest adapter-boundary exception;
3. document why;
4. add a real-cache or deterministic regression test;
5. state the condition under which the exception can be removed.

Do not introduce Displee as a modern OSRS fallback.

# 10. Change terrain/location decoding

There is one canonical pair:

- `OsrsRegionDecoder`;
- `OsrsRegionEncoder`.

Extend and test those.

Do not add another region codec in a tool, project loader, renderer, or server integration.

# 11. Change definitions/assets

Preferred path:

```
FileStore definitions
      |
OpenRuneDefinitionProvider
      |
Studio DefinitionProvider / AssetRepository
      |
tools / inspectors / render compiler
```

Backend-specific types should be reduced to Studio-owned neutral types near the cache boundary.

# 12. Add an OpenRune Server feature

OpenRune Server is core integration.

Use:

- `ServerIntegrationService`;
- `OpenRuneServerProvider`;
- `OpenRuneServerAdapter`;
- symbols/references/spawns/source semantic providers.

Do not implement it as `EditorPlugin`.

Do not add another project-path/cache-role/build-task discovery graph.

For connected publishing, preserve source ownership: write supported source artifacts, invoke the
canonical OpenRune build, then reopen/verify generated cache roles.

# 13. Add an external extension feature

Only use `EditorPlugin` when the feature is genuinely installable/removable as an external
artifact.

Use the published neutral SDK.

Do not import:

- Dear ImGui;
- GLFW;
- OpenGL internals;
- FileStore backend classes;
- native Studio implementation classes.

If an external extension cannot implement an ordinary editing feature without an internal import,
improve the shared SDK rather than documenting an internal dependency.

# 14. Change project startup/loading

Use the project/application lifecycle described in:

- `PROJECT_LAUNCHER_AND_DASHBOARD.md`;
- `CONTENT_STUDIO_ARCHITECTURE.md`.

Do not load all definitions/source indexes eagerly at application startup.

Project opening establishes identity and required cache/project capability. Heavy domains are
workspace-demanded/lazy.

# 15. Add a manager/service/registry

Assume the answer is **no** until proven otherwise.

Search:

- `PluginServices`;
- `EditorPluginRegistry`;
- `EditorSession`;
- existing domain services;
- Studio panel/HUD managers;
- project/session services.

A new service is appropriate only when it owns a cohesive lifecycle/state boundary not already
owned elsewhere.

Never create `NewThingManager` only because the correct existing type has a legacy name.

# 16. Verification matrix

| Change | Minimum verification |
| --- | --- |
| Client/domain logic | Client tests + Editor compile/tests |
| tool behavior | headless tool test + undo/selection assertions |
| renderer setting | compiler test + consumer/boundary test |
| native UI placement | workspace ownership test + live Studio launch |
| OpenGL/rendering | foundationGate + live visual validation + parity evidence |
| cache decoder/write | deterministic fixture + opt-in real-cache test where applicable |
| FileStore adapter | backend boundary test + neutral consumer test |
| OpenRune project integration | project fixture/provider tests + source/cache-role assertions |
| external extension contract | lifecycle/unload/permission + neutral API test |
| architecture change | boundary/inventory test that fails if old path returns |

# 17. Common failure patterns

## "I could not find the method, so I added one"

Search by concept and caller first. The API may live on a service/descriptor rather than the class
you expected.

## "This panel needed a control, so I read settings directly"

If it changes renderer behavior, route it through a typed setting key and compiled config.

## "This tool needed a button, so I added it to a toolbar"

Register the tool descriptor. The host decides where the button belongs.

## "This decoder did not expose exactly what I wanted, so I wrote another decoder"

Extend the one canonical decoder or add a narrow backend adapter exception with tests.

## "The plugin API name sounded right for a core feature"

Core product code is a `CoreEditorModule`, not an `EditorPlugin`.

## "The UI allowed moving a panel, so I allowed every region"

Workspace regions express workflow. Use the strict ownership contract.

# 18. Definition of a clean change

A clean change should make the repository **more obvious** afterward:

- fewer valid places to implement the same responsibility;
- stable IDs defined once;
- comments explain non-obvious constraints, not line-by-line syntax;
- public/core contracts are explicit;
- obsolete paths are removed;
- tests remember architectural decisions;
- an agent starting from this repository can find the correct path without prior chat context.
