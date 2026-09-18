# OpenRune Studio Dashboard

The long-term startup surface is a dashboard, not an editor-specific landing
screen. It gives the user a clear decision point before entering Map Editor,
Asset Studio, Interface Studio, Model Studio, Cutscene Studio, or Build &
Validation.

## Startup lifecycle

```text
Application launch
  → Studio Dashboard
  → asynchronously validate and prepare the remembered cache
  → choose Map Editor, a region, or settings
  → hand the prepared cache session to the workspace
  → enter the selected workspace
```

The dashboard must not become a second cache reader, world model, command
history, or plugin registry. It owns navigation and presentation only. The
selected workspace reuses the same project/session lifecycle as every other
workspace.

## Dashboard sections

### Workspaces

The first enabled workspace is **Map Editor**. The cards for Asset, Interface,
Model, Cutscene, and Build/Validation remain discoverable but disabled until
their contributions are registered. Disabled cards explain that the workspace
is planned; they do not pretend that unfinished functionality is available.

### Project entry

The dashboard shows:

- recent RSPSi projects;
- recent OpenRune Server connections;
- source/output cache status;
- revision and compatibility diagnostics;
- `Open Project`, `Open Cache`, and `Create Blank Project` actions.

The active JavaFX launcher presents this dashboard first. It asks the shared
OpenRune/FileStore cache-session service to validate and prepare the remembered
cache asynchronously. The selected path is persisted only after a successful
load. The Dashboard displays the cache name, revision, backend, and map
availability while loading, and shows the actionable failure when loading
cannot complete.

Map Editor remains disabled until the cache reaches `READY`. Once ready, the
prepared cache session is passed directly to Map Editor, so the editor does
not ask for the same cache again and does not report a misleading “cache
loaded” state without a modern project/session. An optional `regionX,regionY`
or numeric region-ID field supports debug launches directly into a map region;
the same modern `openRegion` route is used by later Map-menu region loads.
Leaving it empty opens a cache-ready editor with no active region. That state
identifies the loaded cache and asks the user to choose a region from the Map
menu. Invalid region input preserves the current valid session.

The legacy cache/plugin form remains available from OpenRune Content Studio
settings for compatibility and plugin management, but it is no longer the
normal startup path or the source of truth for Map Editor readiness.

### Settings

Settings are intentionally split by ownership:

**OpenRune Content Studio settings** cover the server adapter, server root,
LIVE/SERVER/raw-cache paths, staged output, GameVals, pack modules, Gradle
tasks, and optional runtime integration.

**RSPSi Studio settings** cover the JavaFX/ImGui frontend, theme, layout,
keybindings, renderer presentation, autosave, diagnostics, and accessibility.

Neither settings area is allowed to mutate the neutral project/session model
without an explicit command or project action.

## Current implementation boundary

`StudioDashboard` is a reusable JavaFX surface with callback-based actions.
It deliberately has no cache or renderer dependencies. The launcher now owns
one application-level neutral cache session and uses the dashboard as the
default visible route. Startup is split into:

1. application chrome and dashboard;
2. asynchronous cache validation/loading;
3. prepared cache-session handoff after the Map Editor action;
4. optional direct region request through `openRegion`;
5. workspace activation.

This prevents duplicate cache choosers and makes the startup experience
recoverable when a cache is missing or invalid. A failed replacement never
discards the previous valid cache session.

## UX rules

- one primary action per card;
- no fake editor tabs for unregistered workspaces;
- recent items are preferences, not project source data;
- settings do not duplicate contextual workspace controls;
- status and compatibility are visible before an editor opens;
- the dashboard follows the same compact dark shell, semantic icons, focus
  states, and keyboard navigation rules as the workspaces.
