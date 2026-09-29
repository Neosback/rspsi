# Generated cache handling

OpenRune Server Studio is not a general cache or map editor.

Cache support exists to inspect and verify outputs produced by an OpenRune Server project.

OpenRune FileStore is the canonical cache backend for the active application.

OpenRune project's `.data/cache/LIVE` and `.data/cache/SERVER` directories are generated outputs. Studio may inspect archive/index structure, report revision metadata, fingerprint outputs, compare before/after build state, and perform bounded verification.

Studio should not silently patch either generated cache as a fallback editing strategy.

When Studio gains write support, prefer authoritative source/config updates followed by the project's supported build.
