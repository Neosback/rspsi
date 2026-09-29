# Roadmap

## Phase 0 - repository reset

- [x] preserve all pre-reset branch tips in `archive/pre-openrune-reset-2026-09-29`;
- [x] remove the legacy desktop/map editor stack from the active product;
- [x] remove RuneLite/rendering parity infrastructure from the active product;
- [x] remove the vendored OpenRune Server snapshot;
- [x] reduce the build to Protocol + application service;
- [ ] prune old individual branch refs after archive verification.

## Phase 1 - OpenRune project control plane

- harden project compatibility detection;
- [x] model Gradle wrapper/task capabilities and explicit task discovery;
- add bounded build/test/run operations;
- stream operation status/logs;
- add cancellation;
- add project file/output watchers;
- add fingerprints and stale-source diagnostics.

## Phase 2 - content development workflow

- deepen module and GameVal/RSCM provenance;
- deepen Kotlin source relationships;
- define versioned edit payloads;
- perform stale-source checks;
- write authoritative source atomically;
- rebuild through detected OpenRune tasks;
- verify generated output.

## Phase 3 - live OpenRune Agent

- prove a minimal read-only Agent;
- expose runtime identity/lifecycle;
- expose plugins/scripts and supported event registrations;
- validate game-thread access constraints;
- add runtime diagnostics/events.

## Phase 4 - Studio UX

Build around OpenRune Server workflows: project status, content/source explorer, symbol/provenance navigation, build/test/run controls, logs/diagnostics, runtime/plugin/event inspection, and generated-output verification.
