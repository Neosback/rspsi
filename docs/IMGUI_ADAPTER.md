# Dear ImGui adapter boundary

Status: **implemented and now the primary production frontend.**
`docs/ROADMAP.md` is the authoritative execution plan and supersedes the
"deferred until JavaFX acceptance" framing this document originally had. The
native GLFW + OpenGL 3.3 + Dear ImGui shell (`com.rspsi.studio.*`) is wired as
the application's `mainClass` and is enforced by the `verifyNativeBoundary`
Gradle check; JavaFX is the transitional/reference surface scheduled for
removal in `ROADMAP.md` Phase 9, not the other way around. The ownership
rules, frame contract, and contribution-mapping guidance below remain
accurate and are exactly what the ImGui shell must follow.

Dear ImGui does not change the OpenRune Studio foundation. It is a frontend
adapter over the same session, scene, command, asset, and plugin contracts.
ImGui must not create a second world model, coordinate system, renderer scene
graph, history stack, or plugin registry.

`docs/UI_UX_FOUNDATION.md` records the shell composition ImGui should project
rather than redefine: workspace tabs, tool rail, context toolbar, viewport
host, outliner/inspector, utility drawer, and status row (that document was
originally written against the JavaFX host and needs the same status
correction — its UX/composition content still applies). Layout persistence
must live outside project and world data and keep the same stable
contribution IDs regardless of which frontend is rendering.

The adapter also starts after OSRS bundle selection. `OsrsBundle` and its
OpenRune FileStore adapter own cache opening and revision identity; ImGui only
receives the cache-ready neutral project/session and feature contributions. A
missing cache provider is a startup-state problem, not a reason for ImGui to
decode cache archives or construct fallback scene state.

## Ownership

| Foundation owns | The ImGui adapter owns |
| --- | --- |
| `WorldDocument` and `EditorSession` | ImGui context, draw lists, textures, and docking state |
| `EditorCommand` and history | Native input polling and event translation |
| `SelectionModel` and scene coordinates | Camera widgets, focus traversal, and layout persistence |
| `RenderScene`/`EditorSceneSnapshot` semantics | GPU resource lifetime and draw submission |
| `AssetRepository` and plugin contribution IDs | Rendering settings, controls, and panel chrome |
| Plugin lifecycle and ownership cleanup | View projection rebuilds after plugin mount/unmount |

## Frame contract

Each frame follows one direction of data flow:

```text
native input
  -> EditorKeyEvent / PointerEvent
  -> EditorInputRouter (focus-aware shortcut and active-tool dispatch)
  -> EditorSession commands
  -> session/render change publication
  -> RenderScene and immutable EditorSceneSnapshot
  -> ImGui panels, viewport, overlays, and status
```

The adapter reads current state during rendering. It does not maintain a
second editable copy of tool settings or selected tiles. A setting control
calls the same `EditorSetting.setValue` binding used by the JavaFX host; a
mutation uses `EditorSession.execute`; a plugin overlay receives only the
immutable scene snapshot and neutral draw contract.

`EditorInputRouter` is the shared input seam. Frontends translate their native
events into neutral events and report whether a text editor owns focus; the
router then applies the same shortcut precedence and active-tool pointer
dispatch for JavaFX and Dear ImGui.

## Contribution mapping

The ImGui host should project the existing registry as follows:

- `EditorToolRegistration` → tool selector and active-tool lifecycle;
- `EditorToolContextRegistration` → context-bar controls rendered from
  `EditorSetting` metadata;
- `EditorInspectorRegistration` → inspector sections and immutable fields;
- `EditorAssetProviderRegistration` → providers inside the one Studio Asset
  Browser, not separate plugin browsers;
- `EditorStatusRegistration` → status-row values;
- `EditorMenuRegistration` and commands → menu and command-palette entries;
- `EditorOverlayRegistration` → viewport overlay draw calls; and
- `EditorShortcutRegistration` → deterministic scene-level shortcut dispatch.

The JavaFX reference host routes scene-level key events through
`EditorInputRouter`. The native ImGui host (`com.rspsi.studio.*`) does not yet
call it — wiring GLFW key/mouse state through the same neutral method (rather
than handling shortcuts ad hoc in the native shell) remains open follow-up
work and should happen before the native shell grows more shortcut-driven
tools.

Plugin IDs and contribution IDs are the stable identity across JavaFX and
ImGui. Frontends may differ in docking, styling, and control arrangement, but
the same command must produce the same document, selection, dirty-state, and
history result.

## Focus and performance rules

- Every interactive control has a visible focus state and a stable label.
- Scene-level shortcuts are dispatched once; focused text fields may consume
  text-editing keys before global shortcuts.
- Render from immutable snapshots or published changes; do not decode cache
  archives or rebuild the world model inside an ImGui draw callback.
- Preserve the neutral scene map order when projecting tiles, meshes, and
  overlays so repeated frames and plugin reloads remain deterministic.
- Reuse stable ImGui IDs from contribution IDs and setting IDs, not display
  labels that can change with localization.
- Rebuild frontend projections when plugin ownership changes so unloaded
  contributions cannot retain callbacks, textures, or classloader references.
- Track ImGui textures, GPU buffers, subscriptions, and native handles through
  `EditorPluginContext.track(...)`; the plugin host closes them in reverse order
  before the frontend projection is discarded.

## Dependency and acceptance gate

The neutral `Client`/editor-core packages remain free of ImGui, LWJGL,
OpenGL, JavaFX, cache-backend, and renderer implementation imports. If an
ImGui library is selected, it belongs in the frontend module only and must be
covered by a native-resource packaging decision for each supported platform.

The ImGui phase is accepted only when the same fixture-driven checks and
manual workflow pass in both frontends for:

1. tool activation and context-setting edits;
2. selection, command execution, undo/redo, and dirty state;
3. inspector and asset-provider projections;
4. scene snapshot/overlay coordinates and bridge/effective-plane display;
5. keyboard focus and shortcut precedence; and
6. plugin unload/remount without stale UI contributions.

Until that gate exists, do not weaken or fork the foundation to accelerate
either frontend — the native ImGui shell is the production path, and JavaFX
is retained only as a transitional/reference surface until `ROADMAP.md`
Phase 9 removes it.
