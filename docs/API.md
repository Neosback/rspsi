# Bridge API

## Purpose

The bridge API exposes local JVM/OpenRune tooling to the browser editor without exposing internal Java/Kotlin/OpenRune object graphs.

This is an initial contract guide. Endpoints may change until the first versioned implementation lands.

## Transport

Use:

- HTTP/JSON for bounded request/response operations;
- WebSocket for project/cache change events, build progress, and logs;
- binary HTTP responses or compact binary payloads when large cache/model data makes JSON materially wasteful.

All externally consumed contracts are versioned DTOs.

Do not serialize PSI nodes, Gradle model objects, OpenRune FileStore objects, Java `Path`, or internal editor classes directly.

## Local security model

The service is local tooling.

Defaults:

- bind only to loopback;
- reject non-local bind addresses unless explicitly configured;
- restrict CORS to configured editor origins;
- use a per-launch/session credential when the browser origin is not inherently trusted;
- validate every filesystem path against an explicitly opened project/cache root;
- never provide arbitrary shell execution;
- expose allow-listed build operations discovered from the project model.

## Capability discovery

The frontend should begin with a status/capability request, conceptually:

```json
{
  "apiVersion": 1,
  "capabilities": {
    "cacheRead": true,
    "cacheWrite": true,
    "openRuneProject": true,
    "gradleBuild": true,
    "sourceIndex": true
  }
}
```

Capabilities are explicit and may depend on the currently opened project.

## Initial resource families

Expected first API families:

```text
/status
/capabilities

/cache/open
/cache/metadata
/cache/regions/{regionId}
/cache/definitions/{type}/{id}
/cache/validate
/cache/publish

/project/open
/project/inspection
/project/capabilities
/project/references/{namespace}/{name}

/build
/build/{id}
/build/{id}/cancel

/events
```

Names above are design targets, not a promise that unimplemented endpoints exist.

## Operation semantics

Read endpoints must not mutate caches or projects.

Validation must be side-effect free.

Publication is explicit and returns enough identity to verify exactly what changed.

Build invocation returns an operation ID. Progress and logs may stream over WebSocket. Completion must distinguish success, failure, cancellation, and verification failure.

## Error model

Use stable machine-readable codes plus human diagnostics.

Examples:

- `CACHE_UNSUPPORTED`
- `CACHE_READ_ONLY`
- `PROJECT_NOT_OPEN`
- `PROJECT_CHANGED_EXTERNALLY`
- `CAPABILITY_UNAVAILABLE`
- `BUILD_FAILED`
- `PUBLICATION_VERIFY_FAILED`

Do not make the browser parse exception class names or console output to determine state.
