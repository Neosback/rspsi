# Roadmap

This roadmap tracks the transition from the native desktop application to the JVM companion for the web editor.

## Phase 0 - architecture reset

- [x] establish browser/JVM ownership boundary;
- [x] freeze new native-editor feature work;
- [x] collapse obsolete desktop architecture documentation;
- [ ] keep CI/build green while extraction starts.

## Phase 1 - headless core

Goal: prove the reusable backend works without depending on native UI/rendering.

- continue Java-to-Kotlin migration in cache/OSRS/OpenRune boundaries;
- remove accidental dependencies from reusable code into desktop/editor presentation;
- establish headless tests for cache open/inspect;
- establish region decode/encode round trips;
- establish explicit writable-output publication verification;
- establish OpenRune project inspection and build discovery tests.

Do not create new modules until the dependency seams are proven.

## Phase 2 - module extraction

Target shape, adjusted if dependency evidence suggests fewer modules:

```text
:core
:openrune
:server
```

Acceptance:

- `:core` has no desktop/OpenGL/ImGui/Ktor dependency;
- `:openrune` depends on core and OpenRune/JVM tooling, not desktop code;
- `:server` depends on headless services only;
- repository tests run without launching a desktop UI.

## Phase 3 - local bridge

Add Ktor after the headless services exist.

First slice:

- loopback-only server;
- API version/status;
- capability discovery;
- cache open/metadata;
- region read;
- side-effect-free validation;
- explicit output-cache publication;
- WebSocket event channel.

Then:

- OpenRune project open/inspection;
- source/GameVal lookup;
- build operation lifecycle;
- file/cache watchers.

## Phase 4 - web editor integration

The separate Svelte/TypeScript/WebGL2 editor consumes the bridge opportunistically.

- browser-only mode remains usable;
- bridge connection/capabilities are visible;
- cache/project operations use versioned DTOs;
- large resource transfer is profiled before choosing JSON versus binary formats;
- editor interaction/render loops never depend on bridge round trips.

## Phase 5 - retire desktop application

Remove `Editor/` and desktop-only dependencies when:

1. reusable cache/OpenRune functionality has headless coverage;
2. no surviving backend code depends on native UI/render classes;
3. the web editor covers the required authoring workflow;
4. reference/fixture assets needed for cache semantics have been preserved.

## Ongoing Kotlin migration

Keep migration incremental and reviewable.

Near-term preference:

1. small cache value/contract types;
2. map/cache services and codecs where interop is manageable;
3. OpenRune project/build value types and services;
4. semantic/source integration;
5. bridge contracts/services.

Do not spend migration effort on desktop-only classes scheduled for deletion unless required to unlock removal.
