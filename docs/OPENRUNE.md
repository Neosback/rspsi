# OpenRune Server integration

OpenRune Server Studio should understand an opened OpenRune project deeply enough to help develop and operate it without taking ownership away from the project.

## Project inspection

Use one structural project model. Inspection may discover:

- checkout root;
- Gradle wrapper/build files;
- source/resource roots;
- content/plugin modules;
- GameVal/RSCM roots;
- generated LIVE/SERVER cache paths;
- supported build/test/run tasks;
- compatibility diagnostics.

Custom forks should be represented through detected capabilities, not scattered path guesses.

Opening a project is passive. It must not run Gradle, load project code, or mutate files.

## Source authority

For OpenRune-owned resources, preserve provenance and edit the authoritative source form. Generated or merged output should not be edited when a supported source exists.

## Build execution

Run only bounded, application-defined operations against the imported project's detected Gradle wrapper/tasks.

Capture operation identity, timestamps, exit status, bounded logs, cancellation state, relevant output fingerprints, and post-build verification.

Never expose arbitrary shell execution through the local API.

## Generated caches

`.data/cache/LIVE` and `.data/cache/SERVER` are generated outputs. Studio may inspect them and use them for verification, not treat them as ordinary authoring workspaces.

## Runtime Agent

Static inspection does not require server code to be loaded. Live runtime visibility should come from a minimal Agent loaded by OpenRune Server and communicate through neutral `:Protocol` contracts.

See `OPENRUNE_SERVER_FOUNDATION.md` for the foundation research.
