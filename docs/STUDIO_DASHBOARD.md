# OpenRune Studio Dashboard

The long-term startup surface is a dashboard, not an editor-specific landing
screen. It gives the user a clear decision point before entering Map Editor,
Asset Studio, Interface Studio, Model Studio, Cutscene Studio, or Build &
Validation.

## Startup lifecycle

```text
Application launch
  → Studio Dashboard
  → choose recent/new project, cache, region, or settings
  → create/reuse one project/session
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

The dashboard will eventually show:

- recent RSPSi projects;
- recent OpenRune Server connections;
- source/output cache status;
- revision and compatibility diagnostics;
- `Open Project`, `Open Cache`, and `Create Blank Project` actions.

The active JavaFX launcher now presents this dashboard first. The dashboard
stores the selected cache through the existing `Settings` store and passes it
directly to Map Editor, so the editor does not immediately ask for the same
cache again. Choosing a cache also updates the remembered-cache preference
immediately, so the choice survives closing the dashboard before a workspace
is opened. An optional `regionX,regionY` or numeric region-ID field supports
debug launches directly into a map region; the primary **Open Map Editor**
action uses that field too. Leaving it empty opens the editor's normal
actionable empty state. An invalid or missing remembered cache is shown as an
explicit “no cache selected” status, but it does not block opening the Map
Editor shell. From that empty state, **Open Local Cache** selects a cache
directory and loads it directly; it does not invoke the legacy map-file
chooser. The legacy cache/plugin form remains available from OpenRune Content
Studio settings for compatibility and plugin management, but it is no longer
the startup gate.

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
It deliberately has no cache or renderer dependencies. The launcher now uses
it as the default visible route and only creates the Map Editor after the user
chooses a workspace action. Startup is split into:

1. application chrome and dashboard;
2. optional project/cache selection;
3. session creation after the Map Editor action;
4. optional direct region request;
5. workspace activation.

This prevents eager cache loading and makes the future startup experience
faster and easier to recover when no project is configured.

## UX rules

- one primary action per card;
- no fake editor tabs for unregistered workspaces;
- recent items are preferences, not project source data;
- settings do not duplicate contextual workspace controls;
- status and compatibility are visible before an editor opens;
- the dashboard follows the same compact dark shell, semantic icons, focus
  states, and keyboard navigation rules as the workspaces.
