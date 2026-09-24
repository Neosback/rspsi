# OpenRune Studio Project Launcher and Dashboard Contract

> **Scope:** this document defines the application startup lifecycle, persistent Studio project model, project creation/opening flow, pre-dashboard loading gate, and the in-project Dashboard.
>
> `docs/ROADMAP.md` remains authoritative for implementation order. `docs/OPENRUNE_ECOSYSTEM_INTEGRATION.md` remains authoritative for OpenRune cache/source ownership and publishing safety. `docs/UI_WORKSPACE_CONTRACT.md` remains authoritative after a project has entered the Studio workspace shell.

## 1. Product direction

OpenRune Studio should start like a professional IDE, not like a cache utility.

The application has three top-level states:

```
APPLICATION START
      |
      v
PROJECT LAUNCHER
      |
      | user creates/opens project
      v
PROJECT LOADING
      |
      | required project/cache services verified
      v
PROJECT SHELL
      |
      v
DASHBOARD
      |
      +--> Map Studio
      +--> Interface Studio
      +--> Object/Asset Studio
      +--> later content/server workspaces
```

The Project Launcher exists **before** any Studio project is active.

The Dashboard exists **inside** an already-loaded project.

These are not the same screen and must not be implemented as the same class with conditional sections.

## 2. Current behavior to retire

The current application shell still behaves cache-first:

- `StudioApplication` reads `RSPSI_OSRS_CACHE` or `StudioPreferences.recentCache()`;
- a recent raw cache path may begin loading immediately during application construction;
- `DashboardView` owns the editable cache-path field;
- the Dashboard also owns the server-integration connection prompt;
- cache decoder diagnostics consume a large part of the Dashboard;
- workspace launch cards appear on the same screen used to select/repair the cache.

That behavior is transitional.

The target is project-first:

```
old:
app -> recent cache -> dashboard/cache form -> workspace

new:
app -> project launcher -> project selection -> loading gate -> dashboard -> workspace
```

A raw cache path is project configuration, not application identity.

## 3. Project Launcher

The launcher is a dedicated pre-project full-window surface inspired by modern IDE launchers.

### 3.1 Primary layout

Recommended structure:

```
+-------------------------------------------------------------------+
| OpenRune Studio                                                   |
|                                                                   |
| Recent Projects                         Actions                    |
|                                                                   |
| [Project A] OpenRune Server             + New Project              |
|  /projects/my-server                    Open Project...             |
|  last opened 10 min ago                 Import/Link...             |
|                                                                   |
| [Project B] Standalone Cache                                      |
|  ~/StudioProjects/world-edit                                      |
|  last opened yesterday                                            |
|                                                                   |
| [Project C] ...                                                   |
|                                                                   |
| Settings   Plugins   About                                        |
+-------------------------------------------------------------------+
```

The launcher should support:

- recent projects;
- pinned projects;
- project name;
- project type;
- project root/source summary;
- last-opened time;
- missing/moved project indication;
- remove from recent list without deleting project data;
- New Project;
- Open Existing Studio Project;
- link/import an existing OpenRune Server project;
- application-level Settings and Plugins without opening a project.

The launcher should **not** decode a cache merely to render the recent-project list.

### 3.2 Startup behavior

Default application startup opens the Project Launcher every time.

A future preference may optionally reopen the last project, but project-first startup remains the architecture and no raw `recent-cache.txt` auto-load path should remain.

Environment/CLI overrides may open a project directly for automation/development, but they should resolve into the same project-open lifecycle rather than bypass it.

## 4. Persistent project model

The existing `ProjectMetadata` and `ProjectLayout` provide useful cache identity/autosave foundations, but they are not yet a complete Studio project descriptor.

Introduce a neutral descriptor concept such as:

```
StudioProjectDescriptor
  formatVersion
  projectId
  name
  kind
  createdAt

  source
    standalone cache source
      OR
    connected server project

  target game/revision policy
  integration policy
  project data location
```

Suggested project kinds:

- `STANDALONE_OSRS_CACHE`
- `OPENRUNE_SERVER`
- later: other server/provider project kinds through the same provider model

Do not encode OpenRune-only fields directly into the generic descriptor. Provider-specific connection settings belong behind a neutral connection configuration.

### 4.1 Studio-owned project data

Studio needs a stable place for:

- autosaves;
- edit journals;
- publication provenance;
- workspace state;
- project-specific plugin/settings state;
- recent regions/assets;
- recovery information.

For a standalone Studio-owned project, this may live in the selected project directory.

For a linked external OpenRune Server checkout, merely connecting the project should not force arbitrary Studio files into the server repository. Prefer a Studio-owned data directory keyed by stable project ID, for example:

```
~/.openrune-studio/projects/<project-id>/
    project.json
    autosave/
    edits/
    provenance/
    workspace.json
```

The descriptor then references the external OpenRune root.

An explicit future opt-in may allow project-local Studio metadata for portability, but linked-project discovery must remain non-destructive by default.

## 5. Recent project registry

Replace `recent-cache.txt` with a versioned recent-project registry, conceptually:

```
~/.openrune-studio/recent-projects.json
```

Each registry entry should contain only lightweight launcher metadata:

- project ID;
- project name;
- descriptor location;
- project kind;
- display/source path;
- last-opened timestamp;
- pinned state.

Do not duplicate complete mutable project configuration into the recent-project registry. The project descriptor remains authoritative.

Missing projects remain visible with a clear unavailable state until the user removes or relocates them.

## 6. First-release project creation/import flow

The first-release launcher deliberately does **not** use a multi-step project wizard.

Startup should ask for the minimum information required to establish a durable project:

### 6.1 Import OpenRune-Server

Flow:

1. user chooses **Import OpenRune-Server**;
2. Studio opens the native OS folder chooser;
3. user selects the OpenRune server checkout root;
4. Studio detects the project and asks only for the amount of access Studio may have;
5. Studio creates/reuses its private descriptor under `~/.openrune-studio/projects/`;
6. the project is remembered in Recent Projects and opened through the normal loading gate.

The user does **not** enter:

- a separate Studio project name;
- a Studio metadata directory;
- a LIVE-cache path;
- a SERVER-cache path;
- content/source roots;
- GameVal paths;
- Gradle module paths.

The checkout directory name is the initial display name. Rename/settings support can be added later
without making startup a form.

User-facing access labels are permission descriptions, not developer-role names:

- **Read only**
- **Read + write**
- **Read + write + build**
- **Full project access**

The persisted descriptor still stores granular `ProjectIntegrationCapability` values. "Full project
access" does not silently grant destructive/reset operations. Fresh-cache/reset remains explicit.

Import performs only bounded project/cache-role inspection. It must not recursively scan server
content, evaluate the Gradle project model, build semantic graphs, or run repository-wide content
indexing before the Dashboard is usable.

### 6.2 Continue without import

Flow:

1. user chooses **Continue without import**;
2. Studio opens the native OS folder chooser;
3. user selects an existing supported OSRS cache directory;
4. Studio creates/reuses a private standalone descriptor automatically;
5. the cache project appears in Recent Projects and opens through the same loading gate.

No project name is required for this flow.

### 6.3 Deferred advanced setup

The following belong in later project settings/content tooling, not first-run startup:

- custom OpenRune path overrides;
- source/content indexing;
- Kotlin PSI / Gradle semantic models;
- content graph configuration;
- server runtime controls;
- custom build-task overrides;
- project rename and portable/project-local metadata;
- bootstrap/clone/create-new-OpenRune workflows.

This keeps first launch fast and makes the access granted to Studio understandable.

## 7. Open Existing Project

Opening a Studio project should resolve a descriptor, not ask the user to re-select its cache every session.

For OpenRune projects:

```
descriptor
   |
   v
saved OpenRune connection
   |
   v
project inspection
   |
   +--> verify root exists
   +--> verify/reconcile fingerprint
   +--> resolve LIVE/SERVER
   +--> verify revision/environment
   +--> resolve source roots/build tasks
```

For standalone projects:

```
descriptor
   |
   v
saved source-cache binding
   |
   +--> verify cache path
   +--> verify cache identity/fingerprint
   +--> restore output/provenance state
```

If a source moved, the user repairs the project binding once through a relocation flow. The Dashboard should not revert to being a cache path editor.

## 8. Project loading gate

After a project is selected, the launcher disappears and a dedicated full-window loading view appears **before** the Dashboard.

The Dashboard is shown only when the minimum required project state is usable.

### 8.1 Loading stages

Use an application/project loader above the current cache loader.

Conceptually:

```
ProjectLoadService
  READ_DESCRIPTOR
  VALIDATE_PROJECT
  INSPECT_INTEGRATION
  RESOLVE_CACHE_ROLES
  OPEN_CACHE_FILESYSTEM
  VERIFY_CACHE_IDENTITY
  PREPARE_DEFINITIONS
  RESTORE_PROVENANCE
  BIND_REQUIRED_PROJECT_SERVICES
  READY
```

For an OpenRune project, required service binding includes enough integration state to correctly establish LIVE/SERVER/source ownership before the user can edit.

Optional expensive services such as thumbnail generation, broad content search indexes, or corpus analysis should not block the Dashboard unless a workspace actually requires them. They can expose their own warming/indexing status after the project is usable.

For OpenRune specifically, the startup inspection is bounded: it checks project markers, revision,
cache-role paths and declared build availability without recursively fingerprinting LIVE/SERVER,
raw-cache, content, GameVals, plugin, or source trees. Full stale-source/content fingerprints are
computed only by workflows that require them.

### 8.2 Loading UI

The loading view should be deliberately simple and polished:

```
                 OpenRune Studio

                 My OpenRune Project

              Loading project...
       [=====================-----]

           Preparing definitions
     Revision 240.2 / LIVE cache verified

              Cancel / Back
```

Show:

- project name;
- project type/provider;
- current stage;
- real progress where measurable;
- a progress bar based on actual stages/work where possible;
- concise current detail;
- actionable failure state;
- Cancel/Back while safe.

Do not display the Dashboard behind the loading view.

Do not display a fake fine-grained percentage when the loader lacks that information. Instrument the loader so determinate stages/counts can become real over time.

### 8.3 Failure behavior

A failed project open returns a project-specific repair screen, not the old generic cache Dashboard.

Examples:

- project directory moved;
- LIVE missing;
- cache identity mismatch;
- OpenRune revision unsupported;
- server project changed materially since saved baseline;
- permissions/build task no longer available.

Offer:

- Retry;
- Locate project/cache;
- Open Project Settings;
- Back to Launcher;
- open diagnostics/details.

Never silently run `FreshCache` as repair.

## 9. In-project Dashboard overhaul

Once loading succeeds, the Dashboard becomes the **project home**, not project setup.

### 9.1 Dashboard goals

At a glance the user should know:

- what project is open;
- what kind of project it is;
- whether its cache/integration is healthy;
- what access Studio has to an imported OpenRune project;
- whether there are dirty/unpublished changes;
- what they were working on recently;
- what major workspace/action they can enter next.

The first-release Dashboard must not show zero-valued modules/scripts/quests/content-graph metrics
simply because expensive content indexing was intentionally deferred. Broader OpenRune content data
belongs to a future content workspace and is activated on demand.

### 9.2 Recommended layout

```
+------------------------------------------------------------------------+
| My Project                 OpenRune Server | Rev 240.2 | Healthy        |
| /projects/openrune-server                                               |
+------------------------------------------------------------------------+
| Continue                                                            |
| [ Continue Map Studio - Region 50,50 ]                                 |
+------------------------------------------------------------------------+
| Workspaces                                                             |
| [ Map Studio ] [ Interface Studio ] [ Object/Asset Studio ]             |
+------------------------------------------------------------------------+
| Project Status                    | Recent                               |
| LIVE cache    Ready               | Region 50,50                         |
| SERVER cache  Ready               | Region 49,50                         |
| Integration   Managed Build       | Object 1276                          |
| Changes       3 unpublished       | ...                                  |
| Build         Up to date          |                                      |
+------------------------------------------------------------------------+
| OpenRune / Project Actions                                              |
| Build Project | Publish | Reload | Project Settings | Diagnostics       |
+------------------------------------------------------------------------+
```

### 9.3 What moves off the main Dashboard

The current decoder census should not dominate the normal project home.

Detailed counts for all cache indices, decoders, audio, graphics, definitions, and archives belong in a dedicated **Cache Diagnostics** / **Project Diagnostics** view.

The Dashboard may show one compact health card:

```
Cache: Ready
Revision: 240
18/18 required decoders healthy
```

with a `View Diagnostics` action.

Likewise:

- raw cache path editing belongs in Project Settings;
- server connection setup belongs in project creation/settings;
- path repair belongs in project repair/settings;
- advanced integration capability toggles belong in Project Settings;
- destructive cache reset tools belong in explicit advanced project actions.

### 9.4 Continue experience

Persist lightweight recent project context:

- last active workspace;
- last map region/world coordinate;
- recent regions;
- recent selected asset/object where useful;
- open workspace tabs where restoration is safe.

The Dashboard's primary action should be `Continue` when meaningful.

Do not automatically build a large map scene during project load merely to support Continue. The cache/project can become READY first; workspace-specific scene loading begins when the workspace opens.

## 10. Project Settings

Project configuration needs a first-class view.

Categories should include:

### General

- project name;
- Studio project-data location;
- project kind (read-only display after creation unless migration exists).

### Cache

Standalone:
- source cache path;
- explicit output/publish target;
- detected revision/fingerprint.

OpenRune:
- LIVE path, detected/read-only;
- SERVER path, detected/read-only;
- overrides where the project uses a non-standard layout;
- revision/environment.

### OpenRune Integration

- integration preset;
- granular capabilities;
- source roots;
- build task;
- GameVal/symbol integration;
- content indexing;
- server runtime controls when available.

### Build and Publish

- publication status;
- source baseline/fingerprint;
- build task;
- last successful build/verification;
- advanced explicit reset/bootstrap actions.

### Plugins / Workspace

- project-scoped plugin state;
- workspace restore policy.

## 11. Application state model

Do not overload `WorkspaceManager` with pre-project application lifecycle.

Introduce a separate app/project lifecycle concept, for example:

```
ApplicationState
  LAUNCHER
  PROJECT_LOADING
  PROJECT_OPEN
```

Then, only within `PROJECT_OPEN`:

```
WorkspaceManager
  DASHBOARD
  MAP_EDITOR
  INTERFACE_STUDIO
  OBJECT_STUDIO
  ...
```

This keeps the Dashboard as a project workspace while allowing the Project Launcher and loading screen to exist cleanly outside it.

## 12. Service boundaries

Recommended neutral services:

```
StudioProjectRegistry
  recent()
  pin()
  removeRecent()

StudioProjectService
  create(...)
  open(...)
  close()
  current()

ProjectLoadService
  status()
  open(descriptor)

ProjectIntegrationBinding
  provider
  inspection
  cache roles
  permissions
  build actions
```

`OsrsCacheSessionService` remains the cache-opening implementation component, but it should be orchestrated by the project loader rather than called directly from the launcher/dashboard UI.

`ServerIntegrationService` remains the connected integration session coordinator.

The OpenRune provider should consume the converged neutral `ServerConnection` / `ServerProjectInspection` model described in `OPENRUNE_ECOSYSTEM_INTEGRATION.md`.

## 13. Migration from existing project/cache state

Do not throw away useful existing infrastructure.

Reuse:

- `ProjectMetadata` cache identity concepts;
- `ProjectLayout` autosave/edit directories;
- `ProjectMetadataStore` atomic JSON persistence pattern;
- `OsrsCacheSessionService`;
- `LoadedOsrsCacheSession`;
- definition publication provenance;
- `WorkspaceManager` once a project is open;
- `OpenRuneServerAdapter` project inspection/build-task concepts;
- `ServerIntegrationService` session bindings.

Retire or migrate:

- `StudioPreferences.recentCache()`;
- automatic recent-cache loading in `StudioApplication`;
- cache-path ownership in `DashboardView`;
- server-project connection as an ad hoc Dashboard section;
- the Dashboard as the primary cache diagnostics screen.

Existing users with a remembered cache may be offered a one-time launcher action:

`Create project from previously used cache`

rather than silently opening it.

## 14. Acceptance criteria

### Launcher

- app starts without opening/decoding a cache;
- recent projects render from lightweight metadata;
- New/Open/Link flows work without entering the Dashboard;
- missing projects can be repaired or removed;
- selecting a project enters PROJECT_LOADING.

### Project creation

- every project has a stable ID and name;
- standalone projects persist their cache binding;
- OpenRune projects persist their server-root binding and integration capabilities;
- creation does not mutate/rebuild OpenRune caches;
- invalid project/cache roots fail before descriptor commit.

### Loading

- Dashboard is never visible before required project/cache initialization succeeds;
- loading state reports current project and stage;
- failure is project-specific and recoverable;
- successful loading produces one authoritative current project session;
- OpenRune LIVE/SERVER roles are resolved before edit workspaces can open.

### Dashboard

- no raw cache selector on normal project home;
- project identity/status is prominent;
- Continue and workspace launch are prominent;
- cache diagnostics are summarized, not dumped;
- dirty/unpublished/build state is visible;
- project settings and diagnostics are reachable;
- OpenRune connected state is derived from project configuration, not a separate ad hoc connection button.

## 15. Guiding rule

The application opens **projects**.

Projects own configuration.

Loading establishes a trustworthy project runtime.

The Dashboard summarizes an already-trustworthy project.

Workspaces edit that project.

That separation should remain true even as Studio expands from map editing into interfaces, assets, scripts, server content, simulation, and broader OpenRune tooling.
