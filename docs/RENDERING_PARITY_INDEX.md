# Rendering parity index

`RENDERING_PARITY_MANIFEST.json` is the source of truth for the rendering
audit. Generate the searchable reports with:

```text
./gradlew renderingAuditReport
```

The generated files are written to:

- `build/reports/rendering/rendering-parity.md`
- `build/reports/rendering/rendering-parity.json`

The matrix deliberately distinguishes `covered`, `partial`, `blocked`, and
`deferred`. A partial or deferred row is not a failure by itself; an entry
without RuneLite, RSPSi, TSPS, evidence, and—when applicable—a reason and next
action is a failure. Strict external cache parity remains owned by
`parityGate` and is not silently inferred from this static audit.

## Review order

1. Scene levels, bridges, roofs, and visibility.
2. Wall transforms, neighbor normal merges, and wall decorations.
3. Alpha/material/texture classification and draw ranges.
4. Terrain shape, lighting, occluders, and native OpenGL state.
5. Dynamic actors, projectiles, graphics, and item layers.

The RuneLite API documentation is the semantic contract; the TSPS client is
the WebGL pipeline reference; the CPU renderer is the local visual reference.
