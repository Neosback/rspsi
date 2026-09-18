# Smart Map Tools and Transformation Engine

This is the future smart-tool backlog for OpenRune Studio. It records ideas
that become possible once the editor has verified OSRS terrain semantics,
location definitions, collision, chunk/region coordinates, bridges, and
command transactions.

These are proposals, not current implementation commitments. The first
priority remains the canonical OSRS foundation and source/build pipeline.

## Product principle

Smart tools should understand RuneScape data, not paint an unrelated editor
mesh. Every generated result must resolve to ordinary OSRS-compatible terrain,
locations, flags, heights, and derived collision/scene data.

The common flow is:

```text
selection / stroke / spline / shape
                ↓
       deterministic rule or generator
                ↓
          proposed changes
                ↓
       constraints and validation
                ↓
          ghost/diff preview
                ↓
             commit
                ↓
       one grouped undoable command
```

Nothing mutates the document while a generator is calculating or previewing.
Generators produce a proposed change set; the session validates and commits it
atomically.

## Shared primitives

The tools should be built on a small number of reusable services rather than
thirty unrelated implementations:

| Primitive | Responsibility |
|---|---|
| Adjacency and topology rules | Neighbour masks, edge/corner cases, overlay shape and rotation selection |
| Selection masks | Tiles, vertices, objects, regions, chunks, paths, splines, and derived perimeters |
| Height-field operations | Shared corner edits, interpolation, falloff, smoothing, terraces, slopes, and edge preservation |
| Location semantics | Type/layer, orientation, footprint, definition, contouring, collision, and wall rules |
| Spatial occupancy | Object overlap, blocked tiles, region boundaries, plane relationships, and placement constraints |
| Coordinate transforms | World/region/chunk/local mapping, rotations, mirrors, planes, and instance templates |
| Deterministic generation | Seeded randomness, stable ordering, repeatable output, and reproducible previews |
| Proposed change model | Terrain, height, flag, object, collision, metadata, and source-level mutations before commit |
| Constraint solver | Reject, warn, or repair invalid seams, footprints, slopes, collision, and unsupported data |
| Preview/diff layer | Current versus proposed state, ghost geometry, changed tiles, and validation messages |

The `Map Transformation Engine` is a future orchestration layer over these
primitives. It is not a second `WorldDocument`, renderer, collision system, or
history implementation.

## Tool catalog

### First smart-tool wave

These have the highest value and build directly on the foundation primitives:

| Tool | User action | Deterministic result |
|---|---|---|
| Autotile brush | Paint a material/path | Selects overlay, underlay, shape, and rotation from neighbours |
| Semantic copy/rotate | Copy a selection and rotate/mirror it | Transforms tiles, heights, objects, orientations, footprints, collision, and plane relationships |
| Smart wall/fence | Drag a line or boundary | Places straight pieces, corners, ends, orientations, decorations, and collision |
| Path/road tool | Draw a grid path or spline | Rasterizes width, junctions, caps, borders, materials, and optional dressing |
| Plateau/slope tools | Select or connect elevations | Produces shared-corner heights with configurable hard, smooth, terraced, or stepped transitions |
| Collision-aware placement | Place or move an object | Shows footprint and rejects or warns about invalid overlap, clipping, and boundaries |
| Prefab/stamp system | Save and place a structure | Reuses terrain, locations, heights, flags, collision inputs, and transforms |
| Region seam fixer | Inspect neighbouring regions | Detects and proposes repairs for height, material, path, wall, river, and object seams |
| Semantic selection | Query map content | Selects by object/category/definition/type/size/plane/material/elevation/rule |

### Second smart-tool wave

| Tool | Purpose |
|---|---|
| Building/room generator | Walls, corners, floors, doors, roof/render flags, windows, and collision from a footprint |
| Biome brush | Deterministic terrain/material/object presets with density and spacing rules |
| Scatter brush | Seeded Poisson-like placement constrained by terrain, slope, footprint, and collision |
| Edge/border generator | Extracts a selection perimeter for walls, fences, hedges, cliffs, curbs, or shorelines |
| Staircase generator | Connects elevations with stairs, landings, terrain, and collision |
| Bridge generator | Builds deck, supports, approaches, flags, effective-plane behavior, and collision |
| River/waterway generator | Rasterizes a channel, grades banks, paints water, autotiles shores, and optionally scatters bankside assets |
| Coastline generator | Creates land/water transitions, beach bands, slopes, and shoreline topology |
| Smart object replacement | Replaces definitions while checking type, footprint, orientation, contouring, and collision |
| Rule-based find/replace | Applies semantic transformations to matching tiles or locations |
| Cleanup inspector/autofix | Finds terrain spikes, orphan walls, bad orientations, isolated material anomalies, overlaps, and broken collision |

### Later specialist tools

| Tool | Purpose |
|---|---|
| Chunk composer | Arranges and rotates 8×8 rooms into instance-template layouts |
| Naturalize/erosion | Adds bounded deterministic variation while respecting roads, buildings, water, and selection edges |
| Fit object to terrain | Aligns objects or prepares terrain beneath a footprint |
| Pathfinding validator | Tests reachability for actor sizes, line-of-walk, projectile LOS, and blocked areas |
| Door/state placement | Replaces a wall section, places a door, updates collision, and previews known varbit/varp states |
| Content-aware placement | Uses future content metadata for trees, mining rocks, doors, and interactables |
| Reference trace | Aligns an image or world-map reference to tile coordinates for manual tracing |
| Macro/recipe system | Runs several smart transformations as one reviewed transaction |
| Live minimap validator | Regenerates and compares OSRS-style minimap output during review |
| Multi-location state preview | Previews definition or varbit/varp-driven object states |

## Autotiling rule model

Autotiling should operate on semantic material rules rather than hard-coded
IDs. A material preset may define:

- underlay and overlay candidates;
- allowed shape/rotation combinations;
- neighbour connectivity rules;
- edge and corner transition policies;
- height or slope constraints;
- optional decorative locations;
- deterministic variation seed;
- validation behavior when no legal tile exists.

For a path-like material, the four-neighbour mask can select straight,
corner, T-junction, intersection, and end-cap topology. The implementation
must still validate all selected shapes against the canonical 13-topology × 4
rotation mesh rules and shared-edge invariants.

## Semantic generators

Generators should be expressed as domain operations, not UI event handlers. A
road generator might emit:

```text
TerrainMutation[]
HeightMutation[]
LocationMutation[]
CollisionMutation[]
```

The editor previews the aggregate, runs constraints, and commits it through one
`EditorCommand` or composite transaction. This enables atomic undo/redo,
autosave, semantic source diffs, and future macro recipes without adding a
second mutation path.

## Determinism and safety requirements

Every smart tool must:

1. use a stable seed when randomness is enabled;
2. produce the same proposed changes for the same document, selection, inputs,
   revision, and seed;
3. expose a preview before commit for multi-tile or multi-object changes;
4. report unsupported definitions, missing models, invalid footprints, and
   cross-region effects instead of guessing;
5. preserve shared corner heights and valid shape/rotation combinations;
6. derive collision from canonical object/terrain semantics rather than asking
   users to repaint collision manually;
7. remain bounded by the selected area unless the user explicitly includes
   neighbouring context;
8. produce a semantic change report suitable for source review;
9. commit as one grouped command and support exact undo;
10. leave the base cache untouched.

## Suggested implementation order

After the foundation and source/build gates pass:

1. semantic selection masks and proposed-change previews;
2. autotile rules and material presets;
3. semantic copy/rotate/mirror and prefab stamps;
4. smart wall/fence and collision-aware placement;
5. path/road generation;
6. plateau, slope, staircase, and terrain-fit tools;
7. region seam fixer and cleanup diagnostics;
8. deterministic scatter and biome tools;
9. building/room, river/coastline, and bridge generators;
10. chunk composition, recipes/macros, and content-aware tools.

This order maximizes reuse of the same adjacency, height, location, collision,
transform, preview, and transaction infrastructure.

## Scope guardrails

Smart tools are not permission to begin procedural world generation, AI map
authoring, a proprietary geometry format, or a full game/content editor. They
remain optional first-party workflow plugins over the verified OSRS model. A
tool that cannot explain its generated terrain/locations/collision in the
canonical model belongs in research until its semantics and fixtures are
clear.
