# Local application API

The API exposes OpenRune Server Studio's JVM/local capabilities to its UI or CLI without leaking internal OpenRune, Gradle, PSI, or filesystem objects.

The API is versioned under `/api/v1`.

## Security

- bind `127.0.0.1` by default;
- require a per-launch token;
- reject non-loopback Host headers and browser Origins;
- accept an arbitrary filesystem path only when explicitly opening a project;
- use an opaque project ID for later operations;
- reject resolved paths that escape the opened project root;
- never expose arbitrary shell execution.

## Current resources

```text
GET  /api/v1/status
POST /api/v1/project/open
GET  /api/v1/project/{projectId}
GET  /api/v1/project/{projectId}/gradle/tasks
POST /api/v1/project/{projectId}/content/index
POST /api/v1/project/{projectId}/content/resolve
POST /api/v1/project/{projectId}/source/index
POST /api/v1/project/{projectId}/index/refresh
GET  /api/v1/project/{projectId}/cache/live/inspect
GET  /api/v1/project/{projectId}/cache/server/inspect
```

## Gradle task discovery

A project with a detected Gradle wrapper advertises the `gradle.tasks` capability.

`GET /api/v1/project/{projectId}/gradle/tasks` is the only Gradle operation currently exposed. It explicitly invokes the opened project's wrapper with the fixed discovery command:

```text
tasks --all --console=plain --no-daemon
```

The caller cannot supply tasks or command-line arguments. Execution is scoped to the opened project root, has a fixed timeout, captures bounded stdout/stderr, and returns structured task paths, groups, and descriptions.

Opening a project remains passive and never executes Gradle.

Clients query capabilities instead of assuming features from paths or product version.

Externally consumed contracts are neutral, versioned DTOs. Do not serialize PSI nodes, Gradle model objects, OpenRune FileStore objects, Java `Path`, or server/plugin implementation instances.
