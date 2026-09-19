# OpenRune Studio API Contract & Architecture Specification

This document freezes the architectural rules, service boundaries, and component ownership matrix for OpenRune Studio. All tools, procedural generators (including Wave Function Collapse), plugins, rendering pipelines, and UI panels must adhere strictly to the stable contracts defined herein.

---

## 1. Architectural Principles

### Core Principle
> **A feature should be able to become a first-party tool, third-party plugin, procedural generator, alternate workspace, or UI panel without needing special access to OpenGL, Dear ImGui, FileStore, or internal mutable map classes.**

Everything points inward toward stable, backend-neutral contracts:

```
                          OpenRune Studio
                                │
                  ┌─────────────┴─────────────┐
                  │                           │
          Application Services           Plugin Host
                  │                           │
                  ├─────────────┬─────────────┤
                  │             │             │
              World API    Command API   Render API
                  │             │             │
                  └─────────────┼─────────────┘
                                │
                         Knowledge API
                                │
                             UI API
```

---

## 2. The Five Core APIs

### 1. World API (`com.rspsi.editor.model`)
- **Purpose**: Query authored world data safely and immutably.
- **Contract**:
  - Authored world state is owned exclusively by `WorldDocument`.
  - External callers (plugins, generators, rendering passes) read immutable snapshots (`TileSnapshot`, `TileCoordinate`, `WorldObject`, `RegionProfile`).
  - Mutable `WorldDocument` instances are never directly mutated outside the Command subsystem.
  - Coordinate system is canonical tile coordinates: `plane`, `x`, `y`.

### 2. Command API (`com.rspsi.editor`)
- **Purpose**: The single, authoritative mutation pathway for all authored state changes.
- **Contract**:
  - **Mutation Flow**: `User Intent` -> `ProposedChanges` -> `Validation` -> `EditorCommand` -> `CommandHistory.execute(...)` -> `WorldDocument` mutation -> `DirtyRegion` emission -> `SceneResolver` invalidation.
  - Every modification must implement `EditorCommand` (`apply(session)`, `undo(session)`, `description()`, `changedTiles()`).
  - Atomic transactions are grouped using `CompositeEditCommand` or `CommandTransaction`.
  - Read-only sessions strictly reject all command executions.

### 3. Render API (`com.rspsi.editor.render`)
- **Purpose**: Backend-neutral scene rendering and viewport interaction.
- **Contract**:
  - Downstream consumer of `WorldDocument` and `GpuUploadPlan`.
  - Plugins, tools, and procedural previews submit draw instructions through `OverlayDraw` (`box3d`, `line3d`, `tileMarker`, `screenText`, etc.).
  - No plugin or tool may import LWJGL, GLFW, or execute raw OpenGL commands (`glDrawElements`, `glBindBuffer`, etc.) directly.
  - Managed rendering extensions register against predefined, structured render passes (`PRE_SCENE`, `POST_SCENE`, `POST_OPAQUE`, `OVERLAYS`).

### 4. UI API (`com.rspsi.studio`, `com.rspsi.editor.ui`)
- **Purpose**: Ergonomic, structured presentation layer separating UI from business logic.
- **Contract**:
  - The studio shell is divided into hard-controlled primary regions:
    - **Header Bar**: Project context, workspace tabs (`Dashboard`, `Map Editor`, `Object Studio`, `Interface Studio`), engine diagnostics.
    - **Left Tool Rail**: Tool categories and active tool switcher.
    - **Central Viewport**: OpenGL rendering surface with floating HUD cards.
    - **Right Inspector**: Tabbed inspector (`Properties`, `Asset Browser`, `Outliner`).
    - **Bottom Drawer**: Contextual drawer (`Tile Painter`, `History`, `Diagnostics`, `Tasks`, `Messages`).
    - **Status Bar**: Real-time tile, region, selection, vertex, and memory telemetry.
  - Business logic, algorithms, and generators must never import Dear ImGui (`imgui.*`). UI panels bind to neutral data models.

### 5. Knowledge API (`com.rspsi.editor.knowledge`)
- **Purpose**: Semantic classification and understanding of the game world.
- **Contract**:
  - Enriches raw OSRS tile and object primitives with high-level intent (`SemanticTag`: `ROAD`, `BUILDING`, `ROOM`, `FOREST`, `MINE`, `WATER_EDGE`, `BRIDGE`, etc.).
  - Managed by `WorldKnowledgeService` with registered deterministic `KnowledgeAnalyzer` instances.
  - Analyzers evaluate tiles and emit immutable `KnowledgeSnapshot` instances.
  - Invalidation is reactive: when `SessionChangeListener` reports dirty regions, affected semantic classifications are recalculated deterministically.

---

## 3. Wave Function Collapse (WFC) & Procedural Generation Contracts

All procedural generation (Wave Function Collapse, building synthesis, terrain erosion, road generation, dungeon carving, biome scattering) must follow strict architectural separation:

### 1. Separation of Generation and Materialization
- **Stage 1 (Semantic Generation)**: The algorithm operates purely in semantic space (e.g. "corridor", "room corner", "doorway", "natural cliff", "shallow water"). It does not hardcode OSRS object IDs or underlay/overlay IDs.
- **Stage 2 (Materialization)**: A `Theme` or `Materializer` maps semantic tags and socket constraints to specific OSRS asset IDs and tile configurations.

### 2. Non-Destructive Execution
- Generators do **not** mutate the scene directly.
- Every `Generator` implements:
  ```java
  ProposedChanges generate(GenerationRequest request, PluginContext context);
  ```
- `ProposedChanges` encapsulates:
  - Target bounding area and affected plane.
  - Proposed tile states (`TileSnapshot`).
  - Proposed object placements, moves, and deletions.
  - Validation diagnostics and conflict warnings.
- The user previews `ProposedChanges` directly in the viewport via overlays.
- Committing the generation creates an atomic `EditorCommand` pushed to `CommandHistory`, making procedural changes fully undoable and redoable.

### 3. Generation Schemas
Standardized schemas defined in `GenerationSchema`:
- `TERRAIN`: Heightmap synthesis, hydraulic erosion, thermal smoothing.
- `ROAD`: Pathfinding-based road networks, edge blending, street furniture.
- `DUNGEON`: WFC room and corridor layout generation.
- `BUILDING`: Multi-floor architectural synthesis, walls, roofs, doors.
- `BIOME`: Vegetation and rock scattering with density constraints.
- `OBJECT_DRESSING`: Detail object population based on semantic surface tags.

---

## 4. Unified Plugin Architecture & PluginContext Checklist

A plugin receives an instance of `PluginContext`, exposing all services safely through neutral boundaries:

| Method | Return Type | Responsibility |
| :--- | :--- | :--- |
| `session()` | `EditorSession` | Active session document, command execution, and history |
| `world()` | `WorldDocument` | Querying authored tiles and objects |
| `scene()` | `Optional<EditorSceneAccess>` | Viewport camera state, screen projections, picking |
| `selection()` | `SelectionModel` | Current active tile/object selection |
| `history()` | `CommandHistory` | Undo/redo stack navigation |
| `commands()` | `Consumer<EditorCommand>` | Direct command execution helper |
| `settings()` | `SettingsStore` | Raw key-value settings storage |
| `settingsService()`| `SettingsService` | Managed settings registration and categories |
| `assets()` | `AssetRepository` | Cache definitions (objects, overlays, underlays, sprites) |
| `tasks()` | `EditorTaskService` | Background asynchronous task execution and progress tracking |
| `notifications()`| `EditorNotificationService` | User alerts, warnings, and notifications |
| `knowledge()` | `WorldKnowledgeService` | Semantic tagging and region profiling queries |
| `generators()` | `GeneratorService` | Registration and invocation of procedural generators |
| `owner()` | `ContributionOwner` | Lifecycle handle for automatic teardown |

### Resource Ownership & Automatic Teardown
- When a plugin registers an overlay, menu, tool, shortcut, generator, or analyzer, it is tagged with `ContributionOwner.plugin(pluginId)`.
- When the plugin is unloaded or reloaded, the host automatically revokes and unregisters all contributions owned by that plugin. No orphaned state is ever left behind.

---

## 5. Architectural Ownership Matrix

| System Component | Owns | Must NOT Own |
| :--- | :--- | :--- |
| `WorldDocument` | Canonical authored world state | Cache encoding/decoding, OpenGL textures, Dear ImGui widgets |
| `SceneResolver` | OSRS scene semantics, bridge calculation, collision surfaces | Direct OpenGL state, UI events |
| `KnowledgeService` | Derived semantic tags, region profiles, structural classification | Authored document state, direct file I/O |
| `CommandService` | Mutation operations, undo/redo stacks, dirty chunk emission | UI rendering, GPU uploads |
| `SettingsService` | Configurable preferences, specifications, validation scopes | Direct hardware configuration, command logic |
| `ProjectService` | Project disk persistence, metadata codecs, bundle manifests | Scene rendering, command history |
| `RenderPlanner` | Geometry generation, upload batching, cull sorting | OSRS game logic, user tool state |
| `OpenGL Engine` | GPU buffers, shaders, texture bindings, framebuffers | Authoring logic, scene mutation |
| `Workspace / UI` | Layout geometry, input dispatch, Dear ImGui frames | Direct scene mutation, file format manipulation |
| `Plugins` | Contributed tools, overlays, generators, analyzers | Raw OpenGL handles, direct document mutation outside commands |

---

## 6. Compliance & Enforcement

1. **Gradle Foundation Gate**: `./gradlew foundationGate` verifies architectural boundary constraints across modules.
2. **Package Boundaries**:
   - `Client`: Owns document models, cache abstractions, neutral editor services, knowledge, procedural generation, settings, and render contracts.
   - `Editor`: Owns the Dear ImGui presentation layer, workspace views, and native desktop integration.
3. Violations of the ownership matrix (e.g. importing `org.lwjgl.*` or `imgui.*` inside `Client` or plugin business logic) are treated as build-breaking defects.
