# Project Launcher and Dashboard

> **Status:** authoritative startup/project lifecycle contract.

## 1. Product direction

OpenRune Studio starts like an IDE, not like a cache utility.

Top-level lifecycle:

    APPLICATION START
      -> PROJECT LAUNCHER
      -> PROJECT OPEN / CREATE
      -> PROJECT LOADING
      -> PROJECT SHELL
      -> DASHBOARD
      -> MAP / OBJECT / INTERFACE / CONTENT WORKSPACES

The launcher exists before a project is active.

The Dashboard exists inside an active project.

A raw cache path is configuration, not application identity.

## 2. Project Launcher

The launcher should be fast and require no cache decode to display.

It shows:

- recent projects;
- project name;
- project type;
- source/root summary;
- last-opened time;
- missing/moved state;
- Open;
- Remove from Recent;
- Import OpenRune Server;
- Create/continue with standalone OSRS cache.

Future pin/rename/repair features may be added without changing the core lifecycle.

## 3. Persistent project descriptor

Use one neutral project descriptor.

Conceptual fields:

    StudioProjectDescriptor
      formatVersion
      projectId
      name
      kind
      createdAt
      updatedAt
      sourceConfiguration
      revisionPolicy
      integrationPolicy
      projectDataLocation

Project kinds:

- STANDALONE_OSRS_CACHE
- OPENRUNE_SERVER

Provider-specific details belong behind source/integration configuration rather than being scattered across UI settings.

## 4. Studio-owned project data

Studio needs a stable private location for:

- descriptor;
- saved edit state;
- recovery/autosave;
- publication provenance;
- workspace layout;
- recent regions/assets;
- diagnostics/recovery metadata.

For linked external projects, default to Studio-owned metadata outside the server checkout, keyed by stable project ID.

Connecting an external project should not scatter Studio files into it.

## 5. Recent project registry

Keep a lightweight versioned recent-project registry.

Each entry contains only launcher metadata:

- project ID;
- name;
- descriptor location;
- kind;
- display/source path;
- last-opened time;
- pinned state if later supported.

The descriptor remains authoritative.

A missing project stays visible as unavailable until relocated or removed.

## 6. Import OpenRune Server

Flow:

1. user chooses Import OpenRune Server;
2. choose checkout root;
3. Studio performs structural detection;
4. show detected project identity, cache roles, revision/tool compatibility, and available access/build capabilities;
5. user selects allowed integration/control level if required;
6. Studio creates/reuses its descriptor;
7. project enters the normal loading gate.

Do not run FreshCache.

Do not rebuild generated caches merely because the project was imported.

Do not ask the user to manually browse to LIVE under a standard recognized project layout.

## 7. Standalone cache project

Flow:

1. user chooses standalone project;
2. choose supported source cache directory;
3. Studio records that cache as a read-only source;
4. create project descriptor/private project data;
5. loading gate validates required cache capabilities;
6. editor opens.

Output cache destination is chosen/configured for explicit publication, not used as the project identity.

## 8. Project loading gate

Project loading establishes the minimum state required for a usable project.

Recommended stages:

1. descriptor;
2. source/project structural inspection;
3. revision/cache identity;
4. open required read-only cache;
5. restore/validate saved Studio edits;
6. initialize required core services;
7. enter project shell.

Optional content domains load on demand.

Project open should not recursively fingerprint every source tree, decode every definition family, or build every semantic index.

## 9. Loading UI

Show one clear loading surface with:

- current stage;
- progress when measurable;
- diagnostic details on request;
- failure action;
- cancel/back when safe.

The Dashboard must not appear until the required loading gate succeeds.

## 10. Failure behavior

A loading failure should preserve the project descriptor and explain:

- failed stage;
- affected source/path;
- whether the issue is cache readability, project structure, revision compatibility, saved-edit conflict, or required capability;
- safe repair/retry action.

Do not silently replace project sources.

## 11. Dashboard role

The Dashboard is the project home, not a cache setup form.

It should answer:

- what project is open?
- what type/revision/source is it?
- is the source healthy?
- are there saved/unpublished changes?
- was the last publication/build successful?
- what workspace should I open?
- are external OpenRune changes stale relative to Studio?
- what needs attention?

Primary workspace cards:

- Map Studio;
- Object/Asset Studio;
- Interface Studio when available;
- broader content/source workspaces as they mature.

## 12. What moves out of Dashboard

Put detailed diagnostics/configuration in dedicated settings or diagnostic surfaces rather than dominating project home:

- raw decoder census;
- every archive count;
- every project source root;
- all build task details;
- deep GameVal/source analysis;
- renderer diagnostics.

Dashboard summarizes status and links to details.

## 13. Continue experience

When reopening a project, offer a clear continuation point based on stored project state:

- last workspace;
- recent regions/assets;
- saved unpublished edits;
- recovery snapshot if newer than explicit save.

Do not auto-publish or auto-build merely because the project reopened.

## 14. Project Settings

Organize settings by ownership.

### General

- name;
- project data location where supported;
- recent/continue behavior.

### Cache

- source cache identity/path;
- revision information;
- standalone output destination;
- cache diagnostics.

### OpenRune Integration

- external checkout root;
- detected cache roles;
- allowed source/build access;
- structural/revision/tool compatibility;
- relocate external root.

### Build and Publish

- publication target/status;
- detected OpenRune build command;
- last successful publication/build;
- stale-source status;
- diagnostics/logs.

### Workspace

- reset layout;
- display/UI preferences;
- project-specific workspace state.

## 15. Application state model

Keep explicit states:

- NO_PROJECT;
- OPENING_PROJECT;
- PROJECT_READY;
- PROJECT_ERROR;
- CLOSING_PROJECT.

Workspace availability derives from project state and capabilities.

Do not hide lifecycle in scattered nullable services.

## 16. Service boundaries

Project lifecycle should compose:

- recent project registry;
- descriptor store;
- source/project inspector;
- cache session service;
- project edit store;
- integration service;
- workspace manager;
- publication/build coordinator.

Each is one responsibility.

A workspace must not establish its own second project connection.

## 17. Close behavior

On project/application close:

1. check unsaved Studio edits;
2. check saved but unpublished state only for informative warning/policy, not data-loss warning;
3. offer Save Project for unsaved edit state;
4. do not force Publish Cache/Build Project;
5. preserve recovery state if close cannot complete cleanly;
6. close native/cache resources deterministically.

## 18. Acceptance

The lifecycle is correct when:

- startup does not auto-open a raw cache path outside the project lifecycle;
- launcher is fast without cache decode;
- project descriptor is stable;
- moved external projects can be relocated;
- OpenRune import is read-only until explicit source/build action;
- loading restores saved Studio edits before normal authoring;
- Dashboard appears only after required services are ready;
- optional domains do not block initial open;
- Save Project does not pack a cache;
- project close cannot silently lose unsaved edits.