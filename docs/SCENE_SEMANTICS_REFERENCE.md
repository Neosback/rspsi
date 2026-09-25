# Scene Semantics Reference

> **Status:** authoritative boundary between authored world state, OSRS resolution, and renderer data.

## 1. Why this layer exists

Tools, inspectors, tests, and renderers need stable OSRS concepts without depending on cache-library internals or native GPU buffers.

The architecture separates:

    authored state
      -> resolved OSRS semantics
      -> renderer-neutral scene data
      -> native rendering

This prevents a renderer rewrite from changing the meaning of a tile or object.

## 2. Reference authority

Use the source that actually answers the question.

1. Real OSRS cache fixtures prove data/placement acceptance.
2. Vendored RuneLite runescape-client source is the primary reproducible reference for client scene behavior.
3. RuneLite API types are useful naming/concept references.
4. RuneLite mixins/GPU source is used only when the question is exposure/submission/render behavior.
5. OpenRune FileStore is authoritative for modern cache decode/encode/tooling behavior.
6. OpenRune Server is authoritative for connected-project source/build ownership.
7. Secondary editors/servers are implementation cross-checks only.

Do not infer client semantics from one renderer symptom.

## 3. Authored World API

Authored state answers:

- what terrain values were authored?
- what object placement was authored?
- what plane and world coordinate owns it?
- what source/project resource does it belong to?
- is it modified relative to source/publication baselines?

WorldDocument and related authored types own this truth.

Authored objects remain discoverable even if resolution later fails.

## 4. Resolved Scene API

Resolved scene state answers:

- what surface does the client effectively display?
- what effective/render plane applies?
- is the tile simple paint or a shaped model?
- what colors, texture, shape, and rotation resolve?
- what placed object definition resolves?
- what transformed/display definition supplies appearance?
- what model/type/orientation was selected?
- was the object submitted/visible?
- why did resolution fail?

The semantic layer should preserve diagnostics rather than silently dropping unresolved state.

## 5. Render/backend data

Renderer packets may contain:

- compact vertices/indices;
- texture IDs;
- shading metadata;
- draw-order keys;
- upload fingerprints;
- zone residency data.

Those are backend implementation details.

Ordinary editor logic should not need them to answer scene questions.

## 6. Coordinate model

Always make the coordinate space explicit.

### Document-local

Coordinates inside the current WorldDocument/window.

### Absolute world tile

OSRS world coordinates independent of cache-region boundaries.

### Plane concepts

Keep distinct:

- authored plane;
- effective/client plane;
- render level;
- bridge/LINK_BELOW relationship.

Use ScenePlaneSemantics instead of repeating plane rules.

### Screen/viewport

Projection output used only for presentation/input adaptation.

Do not store screen coordinates in authored state.

## 7. Tile semantics

A tile semantic view should expose, as applicable:

- absolute world coordinate;
- authored plane;
- effective/render plane;
- raw tile settings/flags;
- underlay/overlay identity;
- overlay shape/rotation;
- corner heights;
- resolved paint/model surface;
- resolved colors/textures;
- visibility and bridge state;
- provenance/diagnostic status.

A tool should not need to decode terrain opcodes again.

## 8. Surface semantics

Canonical SurfaceHit is the bridge from picking to semantic editing.

It should be rich enough for hover HUDs, selection, inspectors, and tools to agree on:

- world tile;
- local coordinate if needed;
- authored/effective/render plane;
- sampled/interpolated surface height;
- surface kind;
- hit position;
- relevant object identity where applicable.

One pointer query should not produce competing answers for the HUD and active tool.

## 9. Object semantics

Keep three identities distinct when required:

1. authored placement identity;
2. placed/base object definition;
3. transformed/display definition used to produce current appearance.

An object semantic view should retain:

- stable authored placement ID;
- world coordinate and authored plane;
- shape/type;
- orientation;
- definition ID;
- transformed/display ID when applicable;
- model resolution status;
- scene submission/visibility status;
- collision/content facts where known;
- failure reason when not rendered.

This is especially important for missing/null/invisible object diagnostics.

## 10. Transform policy

Follow the actual OSRS client rule for the semantic stage being modeled.

Do not recursively transform merely because another child transform exists if the client performs a single transform at that point.

Tests must name the reference behavior they prove.

## 11. Tile paint and tile model semantics

Simple painted tiles and shaped model tiles are not interchangeable.

Preserve:

- shape;
- rotation;
- corner colors;
- texture;
- vertex geometry;
- face topology;
- flatness where relevant;
- minimap/color semantics separately from 3D shading where they differ.

Interior color/material bugs should be investigated at this semantic layer before native shader changes.

## 12. Model semantics

Neutral model data may expose:

- vertices/faces;
- recolor/retexture result;
- bounds;
- contour result;
- lighting inputs/results where needed for diagnostics.

GPU buffer offsets and native object IDs are not semantic model properties.

## 13. Picking semantics

CPU DDA/spatial picking should return canonical semantic identity.

The visible rendering path and picking path must agree on:

- object placement;
- plane;
- surface height;
- scene window;
- visibility restrictions.

GPU ID picking may validate or accelerate some cases but does not define authored identity.

## 14. API liveness rule

A semantic type/method is not considered established merely because it exists.

Before expanding a shared semantic contract, require:

1. a real first-party caller or acceptance verifier;
2. a focused semantic test or trusted fixture;
3. no existing neutral contract already answers the question;
4. the narrowest practical visibility;
5. documented authored-versus-resolved meaning.

This prevents speculative API growth.

## 15. Do not expose

Ordinary semantic consumers should not need:

- Dear ImGui;
- GLFW;
- OpenGL handles;
- native buffer offsets;
- GpuScenePacket internals;
- OpenRune backend-specific classes;
- Kotlin PSI nodes;
- cache archive primitives.

Adapt those at their owning boundary.

## 16. RuneLite concept mapping

Use familiar vocabulary where it improves clarity, but keep Studio-owned semantics.

Useful concept families include:

- Scene / WorldView;
- Tile;
- SceneTilePaint;
- SceneTileModel;
- GameObject / wall / decoration / ground-object layers;
- ObjectComposition;
- WorldPoint / LocalPoint;
- Perspective height/projection rules;
- ModelData / Model / bounds.

Do not copy live-client concepts that do not fit an offline authoring environment.

Actors, widgets, chat, inventory, networking, ticks, and menus belong only when a concrete simulation/content feature requires them.

## 17. Acceptance fixtures

Semantic acceptance should cover:

- Lumbridge exterior;
- Lumbridge Castle interior;
- bridge/LINK_BELOW examples;
- shaped overlays;
- textured surfaces;
- walls and wall decorations;
- multi-tile objects;
- transforms/multilocs;
- invisible/unresolved objects;
- object picking;
- sampled tile height;
- cross-region/window coordinates.

The semantic fixture should fail before native-renderer debugging begins when the resolved data itself is wrong.

## 18. Definition of success

The semantic boundary is successful when a tool or test can ask what was authored, what OSRS resolves, and why something is visible or missing without reading cache archives again or inspecting GPU buffers.