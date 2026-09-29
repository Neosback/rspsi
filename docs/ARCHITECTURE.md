# Architecture

OpenRune Server Studio is a local-first application for OpenRune Server development and operations.

```text
Studio UI / CLI
      |
      | versioned local API
      v
StudioService (Kotlin/JVM)
      |
      +-- project inspection
      +-- content + GameVal/RSCM indexing
      +-- Kotlin source indexing
      +-- Gradle build/test/run control
      +-- generated cache inspection
      +-- filesystem/watch services
      |
      +---- optional narrow in-server Agent
```

The product is not a map editor. Rendering and world-authoring systems are not part of the active architecture.

## Modules

### Protocol

Neutral contracts shared by StudioService and the future runtime Agent. It must not expose Ktor, PSI, Gradle, FileStore, UI, or OpenRune implementation objects.

### StudioService

The Kotlin/JVM application service. It owns local API/security, project sessions, OpenRune project inspection, content/source indexes, generated cache inspection, and future build/watch/publication services.

## Authority model

An opened OpenRune Server checkout is authoritative. Studio may cache indexes and fingerprints, but source ownership stays with the project. Generated caches remain build outputs.

```text
source/config edit
  -> stale-source check
  -> atomic write
  -> detected OpenRune Gradle task
  -> generated-output verification
```

No fallback should silently patch generated LIVE/SERVER caches.

## Runtime model

Static project inspection and live runtime inspection are separate capabilities. A future in-server Agent should be deliberately narrow and expose neutral runtime facts such as lifecycle state, plugins/scripts, event registrations, cache/runtime identity, and diagnostics.

## Dependency direction

```text
UI / CLI -> versioned API -> StudioService -> external OpenRune libraries/project
                                |
                                +-> Protocol <- future Agent
```

No active module may depend on the archived editor/rendering stack.

Pre-reset editor work is preserved by `archive/pre-openrune-reset-2026-09-29`.
