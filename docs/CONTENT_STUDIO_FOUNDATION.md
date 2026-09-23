# OpenRune Studio Content Authoring Foundation Review

> **Scope:** this document records architectural prerequisites for advanced authoring.
> `docs/ROADMAP.md` is the authoritative execution order, and
> `docs/UI_WORKSPACE_CONTRACT.md` is authoritative for editor-shell/UI placement.

Reviewed against main at ce46a7b916c32b463cf7da830b3512a2c9cd4a1f after PR #44.

## Purpose

This document preserves the architectural requirements for the advanced map-authoring direction discussed for OpenRune Studio without starting any one feature prematurely.

The long-term target includes:

- an exceptionally capable tile painter and conditional tile replacer
- conditional object replacement across arbitrary areas
- advanced object copy, paste, move, rotate, mirror, and batch editing
- whole-building copy/paste across all planes, including correct rotation
- OSRS-authentic autotiling for paths, streams, shorelines, terrain transitions, and similar features
- deterministic noise, density, jitter, scatter, and masking controls
- ground-decoration and foliage scatter
- automatic fencing, gates, and linear object placement
- bridge generation and terrain-aware transitions
- biome generation and WFC-assisted authoring
- later, a cache/world-derived theme and context engine that understands why assets belong together
- a plugin system where first-party and community tools can use the same neutral editing services instead of reimplementing editor internals

This is a foundation review, not an implementation plan for one of those tools.

## Overall assessment

The current repository is capable of growing into this product without an architectural rewrite.

Several major foundations are already stronger than they initially appear:

- WorldDocument is a backend-neutral canonical authored representation.
- EditorSession centralizes selection, history, dirty state, coordinate conversion, and persistence boundaries.
- undo/redo is unified through EditorCommand, CompositeEditCommand, and transactional grouped command application.
- terrain corner editing already has a shared TerrainVertexLattice.
- brushes already have weighted samples and a neutral BrushEngine.
- fragments already represent portable terrain plus object content.
- object move, rotate, duplicate, delete, and replace commands already exist.
- generation is already modeled as non-destructive ProposedChanges that can be previewed and converted into undoable commands.
- WFC and region-corpus groundwork already exists.
- plugins already have neutral tools, settings, overlays, generators, knowledge analyzers, decoded-data access, task services, events, extension points, lifecycle cleanup, external JAR isolation, dependency resolution, and update-repository support.

The missing pieces are mainly reusable abstractions between these systems. The priority should be to add those abstractions before advanced tools are implemented so future tools become thin compositions of shared services.

## Foundation requirement 1: authored multi-region editing

This is the most important structural gap.

Today an editable EditorSession is fundamentally backed by one WorldDocument, and the normal OSRS project loader opens one 64x64 region into one session. Multi-region types exist, but they are currently used primarily for context and derived scene construction:

- WorldRegionWindow loads and stitches multiple regions.
- RegionNeighborhood resolves neighboring world tiles.
- TerrainVertexLattice already has a neighborhood mode that can synchronize shared world vertices.
- OsrsRegionSaveCoordinator.saveAll() can encode several independent region sessions before performing a batched write.

What does not yet exist is one canonical authored transaction spanning several regions.

That limitation will become visible immediately in advanced authoring. A stream, road, biome brush, replacement operation, copied building, fence, or bridge should not stop or change behavior merely because world coordinate X or Y crossed a 64-tile cache boundary.

### Target

Introduce a neutral world-edit abstraction capable of addressing loaded authored regions by absolute world coordinate while retaining region ownership for persistence.

Conceptually:

    AuthoredWorld
      region(x,y)
      tile(WorldTile)
      mutableTile(WorldTile)
      loadedBounds()
      beginEdit()
      changedRegions()

A transaction should be able to modify tiles in multiple loaded regions and commit as one undo history entry. Save logic can still encode each underlying cache region independently.

This should build on WorldRegion, WorldRegionWindow, WorldTileAddress, RegionNeighborhood, and the existing batch save coordinator rather than replacing them.

Missing or unloaded neighboring regions must remain explicit. An operation that reaches unloaded data should either request/load it through the project layer or report that part of the operation as unavailable. It should never fabricate neighboring terrain.

## Foundation requirement 2: one composable query and condition system

The repository already contains the beginnings of conditional editing:

- SelectionQuery.ObjectFilter
- SelectionQuery.TileFilter
- AttributeSelectionTool
- the older TileCondition
- selection-scoped replacement commands

These are currently narrow exact-match filters.

Advanced replacement and painting need a common predicate model that every subsystem can consume.

Examples:

    within selected polygon
    AND plane == 0
    AND overlay in [12, 15]
    AND slope < 18 degrees
    AND NOT underRoof
    AND distanceToPath < 3
    AND random(seed, coordinate) < 0.35

or:

    object.id == 11721
    AND object.type == WALL_STRAIGHT
    AND room == exterior
    AND region is inside selected area

### Target

Create a neutral, composable condition API such as:

    EditPredicate<T>
    PredicateContext
    TilePredicate
    ObjectPredicate
    SpatialPredicate

It should support deterministic composition with AND, OR, and NOT and be usable by selection, tile painting, tile replacement, object replacement, procedural generators, scatter tools, autotilers, future theme suggestions, and plugin-provided rules.

Conditions should be data-oriented where practical so they can eventually be saved as presets rather than existing only as Java lambdas.

## Foundation requirement 3: canonical fragment transforms and paste policies

WorldFragment and WorldFragmentCodec are already a strong starting point. They capture terrain and objects across all planes inside a rectangular area, and PasteFragmentCommand can translate the captured data.

The current fragment system does not yet provide the operations required for a professional structure workflow.

Missing capabilities include:

- 90, 180, and 270 degree rotation of the complete fragment
- mirroring
- configurable pivot/origin
- correct transformation of object positions and object rotations
- correct transformation of terrain overlay shapes and rotations
- correct transformation of corner heights
- preservation of all planes
- translation in world space
- destination merge and conflict policies
- terrain height adaptation

### Paste policies

A future paste should be able to choose behavior such as:

    replace all
    objects only
    terrain only
    merge objects
    replace matching categories only
    preserve destination heights
    absolute source heights
    height-offset from anchor
    fit foundation to target
    blend perimeter into destination
    skip conflicts
    report conflicts

Whole-building copy/paste should use this shared transform engine. No building tool should invent its own rotation rules.

The existing legacy CopyOptions demonstrates some historical needs, but the new model should live with WorldFragment and use current neutral editor contracts.

## Foundation requirement 4: evolve ProposedChanges into a general change plan

Generator and ProposedChanges are already pointed in the correct direction: algorithms produce a non-destructive proposal, and the proposal becomes an undoable command.

This should become the universal boundary for advanced operations, not only procedural generators.

A richer plan should carry:

- intended tile and object mutations
- absolute affected bounds
- preconditions
- conflict information
- diagnostics
- deterministic seed and provenance where applicable
- expected source state when stale-edit protection matters
- merge policy
- preview metadata
- affected underlying cache regions

Potential naming could be ChangePlan, EditPlan, or MutationPlan.

The important point is behavioral: a complex tool computes first, validates first, previews first, and only then commits through normal command history.

Conditional replacement, rotated building paste, biome generation, bridge generation, autotiling, and future theme-assisted edits should all be capable of producing the same kind of plan.

This also avoids tools directly mutating WorldDocument during their algorithms.

## Foundation requirement 5: promote autotiling into a shared OSRS topology service

The current spline path tool proves that the concept works. It already evaluates a Catmull-Rom path, rasterizes a ribbon, builds a neighbor mask, maps that mask to OSRS overlay shape and rotation, previews the result, and commits tile material changes atomically.

However, the rule table is owned by SplineBrushStyle, so autotiling is currently a path-specific implementation.

That logic needs to become a reusable OSRS-aware topology service.

### Target capabilities

The shared service should eventually handle:

- cardinal and diagonal neighborhood connectivity
- OSRS overlay shape and rotation selection
- interior and exterior corners
- material transition sets
- path edges
- stream and river banks
- shorelines
- biome boundaries
- bridge deck and approach transitions
- arbitrary painted masks
- deterministic tie-breaking
- user and plugin-defined rule sets

The service should accept topology or mask input and produce tile material decisions. It should not know about ImGui, mouse events, or one specific tool.

The current path tool can later become one caller of this service.

## Foundation requirement 6: shared linear-feature geometry

Paths, streams, fences, walls, hedges, shorelines, bridges, and similar features all begin with approximately the same problem:

1. capture a line or spline
2. rasterize or sample it
3. derive width and side information
4. classify corners and junctions
5. generate a preview
6. produce an edit plan

SplinePath already provides useful curve and ribbon groundwork.

Before separate stream, fence, road, and bridge tools are written, extract a shared neutral linear-feature model. It should preserve centerline direction, distance along path, left and right side, width, junctions, and local tangent and normal.

That allows a path generator to paint floor shapes, a stream generator to lower terrain and paint banks, a fence generator to place straight and corner posts and gates, and a bridge generator to detect crossings and create approaches without duplicating geometry code.

## Foundation requirement 7: deterministic modifier and scatter pipeline

The brush system already exposes weights, and BrushEngine already defines falloff functions. The current composite tile painter, however, treats every sampled brush tile as an equal target. Sample weights are not yet a general editing input.

There is also a legacy SimplexNoise utility, but advanced tools need a cleaner deterministic modifier model.

### Target

Create reusable modifiers such as:

    brush weight
    constant
    seeded noise
    fractal noise
    threshold
    density or probability
    distance-to-edge
    distance-to-centerline
    slope
    height
    jitter
    cluster
    mask intersection

A tool can combine these into a deterministic field.

Important requirements:

- same seed plus same inputs produces the same result
- use world coordinates so patterns do not reset at region boundaries
- preview and commit use the same sampled values
- plugin authors can contribute additional modifiers
- randomness never depends on iteration order

This is the foundation for natural terrain noise, ground decor scatter, foliage, worn paths, riverbank variation, fence variation, and biome brushes.

## Foundation requirement 8: plugin APIs should expose capabilities, not internals

The public EditorPlugin system is already sophisticated and should remain the community extension boundary.

Existing strengths include:

- versioned API
- plugin manifests
- semantic versions
- dependency resolution
- isolated managed JAR classloaders
- repository and update support
- typed settings
- tools
- overlays and HUDs
- menus, commands, and shortcuts
- inspectors
- validators
- generators
- knowledge analyzers
- region feature extractors
- decoded data access
- extension points
- automatic resource cleanup

Future foundation services should be exposed through this neutral boundary.

A plugin implementing an advanced tool should ideally ask for:

    worldEdit()
    queries()
    fragments()
    autotile()
    linearFeatures()
    modifiers()
    changePlans()
    brushes()
    assets()
    knowledge()
    generators()

rather than modifying cache types or reimplementing core algorithms.

### Correct the documented plugin boundary

The old statement that any plugin needing cache information must be a StudioPlugin is now too broad.

Public EditorPlugin code already receives neutral cache-facing access through AssetRepository, PluginApi.data(), and DecodedDataCatalog.

The correct boundary should become:

- public plugins use neutral asset, data, and editor services
- direct cache backend types remain internal
- direct Dear ImGui, GLFW, and OpenGL access remains internal Studio projection code

StudioPlugin already describes itself as an internal transitional API. Documentation and tests should consistently reflect that.

## Foundation requirement 9: enforce plugin permissions for real

PluginPermission already declares useful capabilities:

- WORLD_READ
- WORLD_EDIT
- ASSET_READ
- ASSET_CREATE
- PROJECT_READ
- PROJECT_WRITE
- CACHE_BUILD
- LIVE_CLIENT_READ
- LIVE_CLIENT_CONTROL
- NETWORK
- FILESYSTEM_PROJECT
- FILESYSTEM_EXTERNAL

At present these are principally manifest metadata. Plugins still receive a context that exposes broad session and world capabilities.

Before a public Plugin Hub is treated as a security boundary, permissions need enforcement.

That means either capability-scoped service objects or permission checks at service entry points. A plugin without WORLD_EDIT should not be able to bypass the policy by obtaining session().world() and mutating tiles directly.

This becomes more important as plugins gain powerful batch edit, file, network, project, and cache capabilities.

## Foundation requirement 10: neutral rich UI contribution model

Execution-side extensibility is currently stronger than rich UI extensibility.

Plugins can contribute settings, status, inspectors, overlays, tools, and UI-surface metadata, but a community plugin that needs a sophisticated panel should not need to become an internal StudioPlugin and call Dear ImGui directly.

A future neutral component model should support enough primitives for tool panels and inspectors, such as:

- rows, columns, and groups
- labels
- buttons
- toggles
- numeric and string fields
- combo and select controls
- asset pickers
- collapsible sections
- tables and lists
- progress
- custom preview hooks where safe

The native Studio frontend can project these neutral components into ImGui.

This would make the external plugin system genuinely easy while preserving the Client and Editor boundary.

## Foundation requirement 11: cache and world invariants with golden tests

The advanced tools discussed are highly sensitive to OSRS tile semantics. Shared infrastructure should therefore have fixtures before feature tools depend on it.

Important fixture categories include:

- every overlay shape and rotation under transform
- wall and object rotation mappings
- fragment rotation across multiple planes
- region-boundary edits
- shared terrain vertices
- path and autotile corners and junctions
- bridge flags and effective planes
- deterministic noise across region boundaries
- copy and paste round trips
- plan preview equals committed output
- encode and decode parity after large batch edits

Where possible, representative real OSRS map fragments should become golden fixtures rather than relying only on synthetic examples.

## Foundation requirement 12: project-level dirty-resource and build model

This remains an important later foundation from the post-PR #44 takeover review.

Map editing, object definitions, interfaces, scripts, GameVals, and other authored resources should eventually participate in one project-level dirty and build lifecycle.

For the map-authoring features in this document, the immediate requirement is multi-region authored edits and change plans. The generalized project build registry can follow without blocking the initial editor foundation work.

## Future system to preserve, not implement yet: Theme and Context Engine

The later Theme Engine discussion should remain part of the product direction, but it should be built after the editor primitives above are stable.

That future system should treat the official OSRS world as an empirical corpus and mine explainable relationships such as:

    ATTACHED_TO
    SAME_KIT_AS
    ADJACENT_TO
    ABOVE
    BELOW
    INSIDE_ROOM_WITH
    SAME_BUILDING
    USES_FLOOR
    USES_ROOF_WITH
    NEAR_PATH
    TRANSITIONS_TO
    SHARES_MODEL
    SHARES_TEXTURE
    SHARES_RECOLOR_FAMILY
    MORPH_OF
    MAP_ICON_OF
    LOCATED_IN_AREA
    ASSOCIATED_WITH_NPC

It should combine real map placement statistics, object shape and rotation, models and palettes, floor and material context, room and building topology, world-map semantic placements, transforms and interactions, optional server and NPC context, optional curated enrichment such as 117HD area and material metadata, and confidence and provenance for every inference.

That intelligence should then feed candidate ranking, semantic brushes, biome and WFC generation, and authoring suggestions.

The Theme Engine should consume the same world-edit, query, autotile, change-plan, fragment, and plugin services described above. It should not introduce a parallel editor architecture.

## Recommended foundation order

1. Unified authored multi-region edit model and transaction boundary.
2. General composable query and condition system.
3. Canonical fragment transform plus paste, merge, and elevation policies.
4. General previewable and validated change-plan abstraction.
5. Shared OSRS autotile and topology service.
6. Shared linear-feature and ribbon service.
7. Deterministic noise, modifier, and scatter pipeline.
8. Expose those capabilities as stable plugin services.
9. Enforce plugin permissions and improve neutral rich UI contributions.
10. Expand the project-level resource and build lifecycle.
11. Later, build the placement corpus and Theme and Context Engine.

## Architectural rule for future tools

Advanced tools should be thin.

The desired flow is:

    interaction and parameters
            |
            v
    query, geometry, or generator
            |
            v
    validated ChangePlan
            |
            v
    preview
            |
            v
    one undoable commit

A tool should not own a private copy of condition logic, region traversal, fragment rotation, autotile tables, noise generation, object transformation rules, save logic, undo stacks, or cache writes.

If that rule is maintained, Tile Painter, Replace, Stream, Fence, Bridge, Biome, Building, WFC, and later Theme tools can all remain plugins or plugin-sized features over one stable editor platform.

## Final assessment

OpenRune Studio is not missing a new architecture.

It is missing a handful of shared editor primitives that sit between the already-good low-level model and the future high-level tools.

The strongest next architectural concern is world-space multi-region authoring. After that, query and condition, fragment transform, change-plan, autotile, and procedural modifier services should be promoted into first-class neutral APIs.

Once those are in place, the ambitious authoring features in this document can be developed independently without forcing later rewrites of the editor core.
