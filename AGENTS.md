# AGENTS.md

## Product boundary

This repository is OpenRune Server Studio.

The active product exists to inspect, develop, run, validate, and maintain OpenRune Server projects. It is not a map editor or renderer project.

Do not add terrain brushes, scene editing, camera systems, rendering pipelines, map-authoring UI, RuneLite renderer ports, desktop editor code, or generic cache-editor features to the active architecture.

If an old implementation is useful as a reference, use `archive/pre-openrune-reset-2026-09-29` without restoring its architecture wholesale.

## Read first

Before architecture-affecting work read:

1. `docs/ARCHITECTURE.md`
2. `docs/OPENRUNE.md`
3. `docs/API.md`
4. `docs/ROADMAP.md`

## Canonical responsibilities

| Concern | Owner |
| --- | --- |
| OpenRune project sessions | `:StudioService` project services |
| OpenRune project inspection | `:StudioService` OpenRune services |
| Content/GameVal indexing | `:StudioService` OpenRune services |
| Kotlin source indexing | `:StudioService` OpenRune services |
| Generated cache inspection | `:StudioService` cache services |
| Stable DTO/runtime contracts | `:Protocol` |
| Local HTTP API/security | `:StudioService` |
| Future live runtime integration | narrow OpenRune Agent + `:Protocol` |

## OpenRune authority

Studio operates on an external OpenRune Server checkout.

- project sources remain authoritative;
- generated LIVE/SERVER caches are outputs, not general editing targets;
- use the project's detected Gradle wrapper/tasks;
- preserve source provenance for generated data;
- never expose arbitrary shell execution through the API;
- never run destructive bootstrap/install behavior merely because a project was opened.

## Local security

- bind loopback by default;
- require the per-launch/session token;
- validate Host and Origin;
- scope filesystem access to an explicitly opened project root;
- use opaque project IDs after open;
- expose versioned DTOs, not PSI, Gradle, FileStore, or internal implementation objects.

## Development

New production code should be Kotlin unless a concrete interop constraint requires Java.

Prefer OpenRune's published libraries and the imported project's own build over vendored source copies.

Keep one focused PR active when practical and finish validation before expanding scope.
