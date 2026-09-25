# OpenRune Studio API System Architecture

> **Status:** canonical architecture and API-boundary inventory.
>
> This document describes the entire OpenRune Studio API system, not only plugins. It complements:
>
> - `STUDIO_SEMANTIC_API.md` for RuneLite-shaped OSRS scene/content semantics;
> - `UI_WORKSPACE_CONTRACT.md` for the native workspace/layout contract;
> - `OPENRUNE_ECOSYSTEM_INTEGRATION.md` for OpenRune/server integration;
> - `PLUGIN_EXTENSION_SDK.md` for the public extension and map-tool model.
>
> The code remains the current implementation authority. This document distinguishes what exists today from the target public contract.

## 1. Goals

OpenRune Studio needs an API system that lets the application evolve without forcing tools, integrations, automation, or community plugins to depend on renderer, cache, UI, or project internals.

The architecture should allow all of the following without exposing Dear ImGui, GLFW, OpenGL, FileStore internals, or mutable implementation objects:

- inspect authored and resolved OSRS world state;
- build map-editing tools;
- query selection and canonical scene hits;
- perform undoable map edits;
- create previews and overlays;
- contribute panels, context drawers, HUDs, inspectors, menus, shortcuts, settings, validators, generators, and asset providers;
- consume OpenRune project/source semantics;
- use background tasks and typed events;
- integrate future simulation/content-runtime capabilities;
- remain compatible across renderer and UI rewrites.

The central rules are:

**Extensions and built-ins are peers at the supported API boundary.** A first-party module must not receive a secret richer map-editing contract merely because it ships with Studio.

**Consumers depend on stable semantics and host-owned capabilities. Internal subsystems depend on implementations.** Restricting access to implementation objects is an architectural boundary, not a reduced feature tier.

---

## 2. API layers

The Studio API is not one interface. It is a layered system.

### Layer A: authored world and editing domain

Current important types include:

- `EditorSession`
- `WorldDocument`
- `TileSnapshot`
- `WorldObject`
- `CommandHistory`
- `EditorCommand`
- concrete map-edit commands
- `SelectionModel`
- world/local coordinate types

This is the canonical authoring state and undo/redo domain.

**Current status:** mature internal foundation, but too much of it is directly reachable from the current plugin context.

**Target:** expose read views and explicit edit services/change plans to public consumers. Keep direct mutable model access internal or compatibility-only.

### Layer B: OSRS semantic API

Primary package:

- `com.rspsi.api`

Current concepts include:

- `Client`
- `WorldView`
- `Scene`
- `Tile`
- `SceneTilePaint`
- `SceneTileModel`
- `TileObject` and object-layer interfaces
- `ObjectComposition`
- `ModelData`
- `Model`
- `Perspective`
- world-map and coordinate APIs

Implementations live behind adapters such as `com.rspsi.api.scene`, `api.cache`, `api.model`, and `api.runtime`.

**Current status:** substantial and intentionally RuneLite-shaped.

**Target:** this remains the preferred semantic read API for OSRS scene/content behavior. It must not gain renderer buffer offsets, ImGui types, FileStore classes, or other implementation details.

### Layer C: plugin/application services

Current important types:

- `EditorPlugin`
- `EditorPluginContext`
- `PluginApi`
- `PluginServices`
- `EditorEventBus`
- `EditorExecutionService`
- `EditorTaskService`
- settings/notification services
- knowledge/generator/symbol/reference/spawn/simulation/integration services

`PluginServices` currently exposes neutral services for terrain, objects, selections, brushes, tools, UI metadata, and commands.

**Current status:** useful foundation, but not yet a complete public SDK.

**Target:** these become guarded, versioned capabilities. Public plugins receive only the services permitted by their declared capability set.

### Layer D: scene query, picking, and render-independent visualization

Current important types:

- `EditorSceneAccess`
- `EditorSceneSnapshot`
- `EditorSceneOverlay`
- `OverlayDraw`
- `OverlayComponent`

**Current status:** enough for overlays and some tools, but split between the older snapshot API and the newer `com.rspsi.api` semantic scene.

**Target:** ordinary public code uses the semantic scene plus a canonical scene-query/picking service. `EditorSceneSnapshot` becomes a compatibility adapter rather than a second long-term scene model.

### Layer E: frontend projection

Current native-only types include:

- `StudioPlugin`
- `StudioToolPlugin`
- `StudioPanel`
- `StudioPanelContext`
- native HUD/rail/drawer classes

These may use Dear ImGui and renderer/frontend implementation details.

**Current status:** intentionally internal and transitional.

**Target:** native Studio projects public neutral contributions into these surfaces. Third-party plugins do not implement these interfaces directly.

### Layer F: project/server/source integration

Current services include:

- `ServerIntegrationService`
- `SymbolService`
- `ReferenceService`
- NPC spawn service
- world knowledge
- simulation
- generator services
- OpenRune project adapters

**Current status:** growing integration layer.

**Target:** project/source details are surfaced through stable neutral facts and capabilities. Kotlin PSI, Gradle internals, and server implementation classes do not become the public API.

---

## 3. Stability classes

Every API type should eventually carry one of these stability classes in documentation and generated reference material.

| Class | Meaning | Compatibility promise |
| --- | --- | --- |
| **PUBLIC_STABLE** | Supported SDK contract | Breaking changes require an API-major change or compatibility adapter |
| **PUBLIC_EXPERIMENTAL** | Public and usable, but still being proven | May change with documented migration guidance |
| **HOST_INTERNAL** | Used between Studio subsystems | No plugin compatibility promise |
| **TRANSITIONAL** | Compatibility bridge scheduled for migration/removal | Consumers should migrate to named replacement |
| **TEST_REFERENCE** | Fixture/parity/testing contract | Stable only for tests/reference tooling |

Initial classification:

- `com.rspsi.api.*`: **PUBLIC_EXPERIMENTAL** moving toward **PUBLIC_STABLE**;
- `EditorPlugin`, descriptor/manifest types, `PluginApi`: **PUBLIC_EXPERIMENTAL**;
- `StudioPlugin`, `StudioToolPlugin`, ImGui-facing panel classes: **HOST_INTERNAL / TRANSITIONAL**;
- `EditorSceneSnapshot`: **TRANSITIONAL** as the semantic API grows;
- renderer/GPU packet classes: **HOST_INTERNAL** unless explicitly documented otherwise.

A Java package being accessible on the classpath does **not** mean it is public API.

---

## 4. Current API strengths

The repository already has several foundations worth preserving.

### 4.1 Semantic scene direction is correct

The RuneLite-shaped `com.rspsi.api` layer gives plugins and first-party tools familiar OSRS concepts without making RuneLite itself a runtime dependency.

### 4.2 Canonical edits are command based

`EditorSession.execute(...)` and existing command implementations provide a strong undo/redo foundation.

### 4.3 External plugin lifecycle is real

The external runtime already supports:

- isolated JAR classloaders;
- managed manifests;
- semantic versions;
- dependency ordering;
- optional dependencies;
- installed-version selection;
- hashes;
- lifecycle rebuilding when plugins enable/disable;
- cleanup of initialization-time registry contributions.

### 4.4 Frontend-neutral overlays exist

`OverlayDraw` gives map/scene overlay primitives without raw OpenGL.

`OverlayComponent` provides a small declarative HUD tree that native Studio can render without exposing ImGui.

### 4.5 Typed events and host-owned execution exist

The event bus, background executor, debouncing, and task/progress services are useful foundations for an SDK.

### 4.6 The workspace layout is already becoming capability driven

The UI contract now explicitly distinguishes:

- primary tool rail;
- context drawer;
- brush rail/settings;
- quick palette;
- inspector;
- HUD layer.

This is the correct layout model for public map tools.

---

## 5. Current architectural gaps

These are not reasons to discard the plugin system. They are the reasons to finish the public boundary.

### 5.1 Two extension systems still exist

There is a neutral public direction:

- `EditorPlugin`
- `PluginApi`
- `EditorToolRegistration`
- `UiSurfaceContribution`

and a richer native-only direction:

- `StudioPlugin`
- `StudioToolPlugin`

The native system understands context drawers, brush ownership, tool surfaces, owned panels, viewport HUDs, and native overlays more completely than the neutral system.

**Impact:** an external plugin can add engine behavior but cannot yet become a first-class Map Studio tool with the same ergonomics as built-ins without crossing into internal APIs.

**Required change:** one neutral map-tool descriptor must describe both engine behavior and host presentation. Native Studio becomes an adapter/projection of that descriptor.

### 5.2 Public tool registration is underpowered

`EditorToolRegistration` already has:

- id;
- label;
- category;
- tool group;
- icon;
- shortcut;
- order;
- factory.

But `PluginApi.ToolBuilder` currently exposes only:

- id;
- label;
- category;
- factory.

It also cannot declare:

- brush UI ownership;
- tool capabilities;
- context-drawer content;
- quick-palette content;
- inspector sections;
- tool HUDs;
- selection mode;
- input policy;
- replacement/augmentation behavior.

This should be replaced by the map-tool contribution model in `PLUGIN_EXTENSION_SDK.md`.

### 5.3 UI contribution metadata has no general content contract

`UiSurfaceContribution` and `EditorPanelRegistration` describe placement, but ordinary public plugins still lack a sufficiently rich, frontend-neutral interactive component tree for drawers, panels, inspectors, and quick palettes.

`OverlayComponent` proves the pattern but is intentionally HUD-oriented and mostly read-only.

**Required change:** a host-owned declarative UI model for normal plugin surfaces.

### 5.4 Direct mutable domain access is too broad

`PluginContext` currently exposes `EditorSession`, `WorldDocument`, `SelectionModel`, command history, and `AssetRepository` directly.

That is powerful but creates several problems:

- plugins can bypass permission intent;
- plugins can bypass future validation/change-plan rules;
- plugins can accidentally couple to mutable internal representation;
- threading requirements are unclear;
- renderer invalidation/persistence assumptions can be bypassed if direct mutation is possible.

**Required change:** public read views plus guarded edit/change-plan services. Direct session/world access becomes internal or compatibility-only.

### 5.5 Plugin permissions are currently declarations, not an enforced boundary

`PluginPermission` and manifest permission sets exist, but the context currently exposes the same service graph regardless of declared permissions.

This means `WORLD_READ`, `WORLD_EDIT`, `ASSET_READ`, and similar values are not yet runtime authorization gates.

**Required change:** build each plugin a capability-filtered context/service facade and reject unauthorized operations deterministically.

Important security limitation:

**In-process Java plugins are not a security sandbox.**

Even after Studio API permissions are enforced, arbitrary Java bytecode can call JDK filesystem/network APIs unless the plugin is isolated out of process or into a stronger sandbox. Studio permissions should therefore be described as **host capability enforcement**, not protection against malicious bytecode.

Plugin Hub trust must additionally rely on provenance, hashes/signatures, review/reputation, clear permission disclosure, and potentially a separate-process/WASM model for truly untrusted extensions.

### 5.6 The classloader boundary is too broad

`IsolatedPluginClassLoader` currently treats all `com.rspsi.*` classes as parent-first.

That makes current development convenient, but it means external plugins can compile against many host internals if they know the class names.

**Required change:**

- publish explicit API/SDK artifacts;
- parent-first only approved API packages and shared dependencies;
- reject or hide internal host packages from external plugins;
- move internal implementation packages behind a deliberate boundary.

### 5.7 Dynamic contribution cleanup is incomplete

`EditorPluginHost` captures registry contributions before/after `initialize()` and removes the difference on unload. That correctly cleans contributions created during initialization.

However, registry contribution methods generally do not return tracked registration handles. A plugin that adds registry contributions later in response to an event can escape that initialization snapshot.

**Required change:** every public registration returns an `AutoCloseable`/contribution handle, and `PluginApi` automatically tracks it. Direct mutable access to the registry should not be the ordinary public path.

### 5.8 Contribution IDs are globally collision prone

Many builders accept raw IDs. Multiple plugins can choose the same tool, menu, status, task, setting, or debounce ID.

**Required change:** plugin-facing builders automatically namespace IDs by plugin identity unless a host-global ID is explicitly requested for a documented extension point.

### 5.9 Threading and editor-thread rules are incomplete

The event bus supports caller-thread and background delivery, but the SDK does not yet have a clear editor-thread dispatcher/mutation rule.

**Required change:**

- define which APIs are snapshot/read-safe on background threads;
- require authored-world edits on the editor thread;
- add `EditorThreadService`/dispatcher;
- require background jobs to return immutable results and post mutations back through the host.

### 5.10 API versioning is too coarse

`EditorPluginApi.CURRENT_VERSION` is currently a single integer and managed manifests require equality.

**Required change:** version the SDK with a major/minor compatibility model and capability negotiation. A plugin should be able to say that it requires a minimum API/capability version without every additive SDK release becoming incompatible.

### 5.11 Manifest and descriptor metadata should converge

The in-memory descriptor and external manifest overlap but are not a perfect one-to-one contract.

**Required change:** define one canonical plugin metadata model and derive runtime/internal views from it.

---

## 6. Target package/artifact boundary

A future build split should make public intent physically obvious.

Recommended artifacts:

### `studio-semantic-api`

Contains stable, renderer-independent semantic types:

- `com.rspsi.api.*`
- coordinates;
- scene/tile/object/model interfaces;
- immutable query/result values.

No UI toolkit, renderer backend, cache backend, or mutable editor implementation.

### `studio-plugin-sdk`

Contains:

- plugin lifecycle interfaces;
- manifest/metadata;
- capability declarations;
- service interfaces;
- contribution descriptors;
- declarative UI model;
- tool/input/selection contracts;
- event types;
- settings contracts;
- testing helpers.

Depends on `studio-semantic-api`.

### `studio-core`

Contains:

- `EditorSession`;
- world model implementations;
- command implementations;
- cache adapters;
- scene compilation;
- persistence;
- integration implementations.

Not a plugin dependency.

### `studio-native`

Contains:

- Dear ImGui;
- GLFW/OpenGL;
- `StudioPlugin` compatibility projection;
- native panels/rails/drawers/HUD windows;
- renderer implementation.

Not a public plugin dependency.

This split is a target. It does not require an immediate Gradle multi-module rewrite to begin enforcing package rules.

---

## 7. Read APIs versus edit APIs

Public API design should intentionally separate observation from mutation.

### Read path

Preferred flow:

```
Semantic Client/Scene
        +
WorldReadService
        +
SceneQueryService
        +
AssetReadService
```

Read APIs should return immutable views/snapshots or stable semantic interfaces.

### Edit path

Preferred flow:

```
plugin/tool
   |
   v
EditService / ChangePlanBuilder
   |
   +-- validate
   +-- preview
   +-- changed-region calculation
   +-- persistence impact
   +-- conflict checks
   |
   v
commit()
   |
   v
one undoable transaction
```

A complex plugin should not need to hand-build dozens of unrelated commands merely to make one logical authoring operation.

The current `PluginServices.TerrainService`, `ObjectService`, and `CommandService` are useful primitives and can evolve into this model.

---

## 8. Scene-query API direction

A map editor extension needs more than a tile-at-cursor helper.

The stable query surface should include:

- canonical `SurfaceHit`;
- hovered tile/object;
- picked semantic object identity;
- authored/effective/render plane;
- hit position and sampled height;
- tile/object queries by world area;
- collision queries;
- visibility/rendered status;
- model/footprint bounds where semantically meaningful.

The same result should drive:

- hover HUDs;
- click selection;
- inspector context;
- plugin tools;
- overlay previews.

Do not let each plugin reconstruct picking from renderer buffers.

---

## 9. Selection API direction

The current plugin selection service is tile-oriented. The public SDK should support semantic targets.

Recommended model:

```
SelectionTarget
  TileTarget
  ObjectTarget
  VertexTarget
  AreaTarget
  PathPointTarget
  FragmentTarget
```

A selection snapshot should preserve stable semantic identity across scene rebuilds whenever possible.

Plugins may contribute new selection tooling, but the host owns the canonical active selection state.

---

## 10. Rendering extension policy

Ordinary map-editing plugins should **not** require renderer changes.

If a plugin edits canonical properties already understood by Studio, such as:

- terrain heights;
- underlays/overlays;
- tile shape/rotation;
- tile flags;
- object placement/removal/move/rotation;

the normal scene rebuild should render the result automatically.

For previews and diagnostics, plugins should use renderer-neutral overlay primitives.

A plugin only needs a deeper render extension when it introduces visual semantics that cannot be represented by the canonical world model.

The future render-extension boundary should emit neutral render data such as:

- lines/polygons;
- ghost models;
- model instances;
- temporary terrain meshes;
- gizmos;
- debug geometry;
- materials/textures through host-owned handles.

Raw OpenGL IDs, shader ownership, VAOs/VBOs, or direct command-buffer access should remain internal by default.

A privileged native renderer extension API may exist later for trusted extensions, but it should be separate from the normal Plugin SDK and versioned independently.

---

## 11. Integration API policy

OpenRune/server/source integrations should follow the same rule as scene APIs:

**semantic facts cross the boundary; implementation objects do not.**

Examples:

Good public values:

- source symbol identity;
- handler relationship;
- source location/provenance;
- project module/source-set identity;
- spawn definition;
- semantic graph edge;
- simulation state snapshot.

Internal-only values:

- Kotlin PSI nodes;
- Gradle Project objects;
- FileStore implementation handles;
- compiler sessions;
- native renderer objects.

---

## 12. API documentation requirements

Every public capability should document:

1. stability class;
2. required permission/capability;
3. thread on which callbacks execute;
4. ownership/lifetime;
5. mutability;
6. unload behavior;
7. persistence/undo implications;
8. performance expectations;
9. whether the value is authored, resolved, rendered, simulated, or source-derived;
10. compatibility/version introduced.

Generated Javadocs alone are not enough. Architectural semantics belong in the docs tree.

---

## 13. Testing requirements

The API system should have contract tests independent of native Studio rendering.

Required categories:

### Semantic API contract tests

Validate scene/tile/object/model behavior against real or trusted fixtures.

### Plugin lifecycle tests

Validate:

- enable/disable;
- dependency order;
- reload;
- dynamic contribution cleanup;
- task/subscription cleanup;
- duplicate IDs;
- API compatibility failures.

### Permission/capability tests

Validate every guarded service both allows and denies expected operations.

### Tool SDK tests

A headless sample plugin should prove it can:

- register a map tool;
- receive input;
- edit terrain/objects;
- create one undo transaction;
- contribute drawer/brush/HUD/inspector UI;
- draw a preview;
- unload cleanly.

### Frontend projection tests

Native Studio tests should verify that neutral descriptors land in the correct workspace slots and obey the layout contract.

### Compatibility fixtures

Keep sample plugins compiled against previous supported SDK versions and load them in CI.

---

## 14. Near-term implementation order

The highest-value sequence is:

1. define the neutral map-tool/UI descriptor;
2. make native built-in tools project through the same descriptor;
3. add a general declarative UI component model;
4. add scene-query/`SurfaceHit` and semantic selection services;
5. add edit transaction/change-plan APIs;
6. make all registrations return lifecycle handles;
7. namespace plugin contribution IDs;
8. add editor-thread dispatch rules/services;
9. enforce Studio capability permissions at service boundaries;
10. narrow the external classloader/API package boundary;
11. split/publish semantic API and Plugin SDK artifacts;
12. add major/minor API compatibility and capability negotiation;
13. migrate representative built-ins to dogfood the public SDK;
14. ship a complete example map-tool plugin and contract-test kit;
15. only then call the external plugin API stable.

---

## 15. Decision

OpenRune Studio should keep an extension/plugin system, but enabled extensions should be treated as first-class editor modules.

The problem is not that "plugin" is the wrong concept. The current problem is that the public extension boundary is less expressive than the native built-in tool boundary.

The target is:

**one shared extension SDK for built-ins and installed modules, many extension types, one host-owned workspace/layout system, and renderer-independent semantic/editing services.**

A developer should be able to build a new map editor tool with the same supported editing authority as an equivalent built-in tool, without needing Studio source changes, direct ImGui access, or a renderer fork.
