# OpenRune Studio

OpenRune Studio is a Java/Kotlin desktop environment for authoring Old School RuneScape maps and content. The repository is organized as a modular monolith with one canonical world model, one command/change path, one scene-semantics path, one native rendering path, and one project integration model.

## Current state

The editor can load modern OSRS caches through OpenRune FileStore, build and render editable world state, and integrate with OpenRune Server projects.

The current renderer is visually close to the OSRS reference in the scenes tested so far. No z-fighting has been observed in current manual testing, but that is an observation, not a parity claim. Rendering correctness remains fixture-driven, and CPU, GPU, and memory cost are still active engineering problems.

## Modules

- **Client** owns cache adapters, authored-world state, commands, scene semantics, render-neutral compilation, project integration contracts, and testable domain logic.
- **Editor** owns Dear ImGui, GLFW, OpenGL, the project launcher, native workspace shell, and presentation.

Built-in functionality is composed through CoreEditorModule and CoreEditorModules. New work should extend the existing core module and service graph instead of adding parallel managers, registries, save paths, rendering paths, or project connections.

## Save and publish are different

Normal authoring changes update the canonical in-memory world and durable Studio project state. They do not need to repack a cache.

- **Save Project** persists Studio-owned edit state and recovery data.
- **Publish Cache** explicitly encodes validated changes into an output cache.
- **Build OpenRune Project** writes only supported authoritative OpenRune source and then invokes the imported project's own cache build.

Source caches and connected OpenRune LIVE/SERVER caches are not ordinary mutable workspaces.

## Build, test, run

Use the Gradle wrapper from the repository root.

    ./gradlew test
    ./gradlew :Editor:run

Platform-specific and opt-in real-cache verification is documented in the acceptance and rendering references under docs.

## Documentation

Start with **docs/README.md**.

The most important documents are:

- docs/AI_ARCHITECTURE_OVERVIEW.md
- docs/AI_CHANGE_PLAYBOOK.md
- docs/EDITOR_DEVELOPMENT_ARCHITECTURE.md
- docs/CACHE_EDITING_AND_PUBLISHING.md
- docs/RENDERING_SYSTEM.md
- docs/OPENRUNE_SERVER_INTEGRATION_MODEL.md
- docs/UI_WORKSPACE_CONTRACT.md
- docs/PROJECT_LAUNCHER_AND_DASHBOARD.md
- docs/ROADMAP.md

Reference and acceptance documents are listed in docs/README.md.

## Reference sources

The repository contains vendored reference source trees for reproducible comparison:

- RuneLite-melxin for OSRS client, scene, cache, and renderer behavior.
- OpenRune-Server-main for the OpenRune project/cache/content build contract.

Those trees are references, not alternate application architectures. OpenRune Studio owns its own domain contracts and uses the external projects only where their behavior or data format is authoritative.

## License

See LICENSE and any third-party notices or source-level license headers for vendored/reference code.