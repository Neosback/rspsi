# OpenRune Integration

For the verified server boot/plugin/event/cache model and live-agent constraints, see [OpenRune Server Foundation Reference](OPENRUNE_SERVER_FOUNDATION.md).

## Goal

The companion provides first-class integration with OpenRune Server projects while respecting OpenRune's own build and source authority.

The bridge should understand a project deeply enough to answer:

- what project was opened;
- which cache/source capabilities are available;
- where LIVE and SERVER outputs are;
- which source owns a selected resource;
- which GameVal/RSCM symbol resolves to which entity;
- which supported build operation publishes a source change;
- whether files or generated output changed externally.

## JVM boundary

OpenRune tooling is JVM-centric. Current useful dependencies include FileStore, definition codecs, writable cache tools, source/config tooling, and the Gradle-based OpenRune Server build.

That is the main reason the local companion exists. Do not port OpenRune's JVM tooling into browser TypeScript merely to remove the bridge.

## Project inspection

Use one structural project model.

Inspection may discover:

- checkout root;
- Gradle wrapper/build files;
- source/resource roots;
- LIVE/SERVER cache paths;
- GameVal/RSCM roots;
- content/plugin packs;
- build tasks;
- compatibility diagnostics.

Custom forks should be supported through detected capabilities and bounded adapters, not scattered path guesses.

## Generated outputs

Treat:

- `.data/cache/LIVE`
- `.data/cache/SERVER`

as generated outputs owned by the OpenRune project.

Do not silently patch either as a fallback publication strategy.

## Supported publication

For a resource with an authoritative source form:

```text
browser edit
  -> bridge validates payload
  -> source authority/provenance lookup
  -> stale-source check
  -> atomic source write
  -> detected OpenRune build
  -> reopen generated output
  -> semantic verification
  -> report success
```

If no supported source form exists, report that publication capability as unavailable.

Standalone output-cache publication is a separate capability and must not be presented as OpenRune source publication.

## Source semantics

Kotlin semantic indexing and project-source inspection remain backend responsibilities.

Expose neutral facts:

- declaration identity;
- symbol/reference;
- handler/content relationship;
- source file/span;
- evidence/diagnostic confidence.

Do not expose Kotlin PSI objects through the API.

## GameVals/RSCM

Preserve provenance. A symbol is not just `name -> id`; its namespace, source, generated status, and origin matter.

Generated/merged mappings must not be edited when an authoritative originating source exists.

## Build execution

Invoke the imported project's wrapper/task discovered from project inspection.

The bridge should capture:

- operation/task identity;
- started/completed timestamps;
- exit result;
- bounded logs;
- relevant output fingerprints before/after;
- verification status.

Fresh/bootstrap installation remains explicit and destructive. It must never run automatically when a project is opened.
