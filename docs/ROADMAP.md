# Roadmap

This roadmap tracks the transition from the native desktop application to the JVM companion for the web editor.

## Phase 0 - architecture reset

- [x] establish browser/JVM ownership boundary;
- [x] freeze new native-editor feature work;
- [x] collapse obsolete desktop architecture documentation;
- [ ] keep CI/build green while extraction starts.

## Current foundation - Companion and protocol

The loopback Companion and neutral Protocol module now exist earlier than the original phase ordering anticipated. Security/project sessions and project-scoped indexing are foundation work, not deferred transport polish.

## Phase 1 - headless core

Goal: prove the reusable backend works without depending on native UI/rendering.

- continue Java-to-Kotlin migration in cache/OSRS/OpenRune boundaries;
- remove accidental dependencies from reusable code into desktop/editor presentation;
- establish headless tests for cache open/inspect;
- [x] establish modern headless region decode/encode round trips in `:Core`;
- [x] cross-check the modern Core codec against the existing Client codec in both directions;
- [x] route production modern Client region load/save through Core using a temporary model adapter;\n- delete the duplicate Client codec and retire pre-modern terrain plumbing;
- establish explicit writable-output publication verification;
- establish OpenRune project inspection and build discovery tests.

The Protocol and Companion seams are proven. Modern region decode/encode now belongs in `:Core`; the next blocker is adapting surviving Client/Companion publication callers to that model and removing the duplicate legacy codec. XTEA and pre-modern terrain compatibility are intentionally outside the new Core scope.

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

## Phase 3 - local Companion expansion

Ktor already exists as the loopback Companion transport. Continue hardening and expanding it over headless services rather than treating transport as a future phase.

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
- project-scoped cached content/source snapshots;
- dedicated single-thread PSI/index execution;
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


## Foundation checkpoint - OpenRune runtime model

Before expanding the source browser or adding invasive runtime instrumentation:

- [x] document verified OpenRune boot, Guice, plugin, event, cache, GameVal, pack, and tooling architecture;
- [x] document external-plugin classloader/reload constraints;
- [x] separate Companion static/project authority from future Agent runtime authority;
- [x] add neutral runtime identity/lifecycle/plugin contracts;
- [ ] prove a minimal read-only OpenRune Studio Agent;
- [ ] inventory runtime event registrations through supported APIs or a narrow adapter;
- [ ] validate game-thread access rules before any live mutation;
- [ ] evaluate Byte Buddy only for trace features that cannot be implemented through supported registries.
