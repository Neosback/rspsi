# OpenRune Studio Feature-Freeze Readiness

This checklist defines the point where the project can stop doing foundational rewrites and spend most development time on map/content features, UX polish, adapters, and tuning.

The rule is simple: **do not call the foundation stable until every P0 gate is green in CI and the smoke matrix passes against a real OSRS cache plus the OpenRune reference project.**

## Status legend

- **DONE**: implemented on `feature/next-gen-map-studio`.
- **IN PROGRESS**: implemented partially or actively being migrated.
- **BLOCKER**: must be completed before feature-freeze.
- **POST-FREEZE**: valuable, but may land after the foundation is stable.

## P0 feature-freeze gates

| Gate | Status | Exit condition |
|---|---|---|
| Coordinate spaces | IN PROGRESS | Viewport/navigation use `WorldTile`; document mutation uses `LocalTile`; no world-to-local `floorMod`; tests cover non-zero region origins. |
| EditorSession boundary | DONE / VERIFY | Session owns document, selection, history and `WorldWindow`; Studio passes the loaded region window into it. |
| Map editing correctness | BLOCKER | Paint, sculpt, select, object move/rotate/duplicate/delete, context menu, drag/drop and undo/redo pass the smoke matrix. |
| Picking | IN PROGRESS | One public picking service/result contract. DDA or GPU strategy is replaceable without tool changes. Legacy picker path removed. |
| Terrain semantics | IN PROGRESS | Shared-vertex height edits, provenance, overlay shape 0..11, flags, bridges and exact topology are covered by golden tests. |
| Incremental scene rebuild | DONE / VERIFY | 8x8 zone revisions rebuild only affected zones plus required blend/seam neighborhoods. |
| Plugin boundary | IN PROGRESS | Public plugins compile against neutral SPI only; no ImGui/JavaFX renderer types leak into public contracts; contract test kit exists. |
| UI surface model | DONE / VERIFY | Shell owns right/bottom rails, managed panels and viewport HUD placement; plugin contributions cannot collide arbitrarily. |
| Hotkeys | BLOCKER | Central registry owns action id, default chord, override, suppression and generated help. No raw editor hotkeys remain in `MapEditorView`. |
| Navigation | BLOCKER | One `NavigationService` owns jump-to-world-tile/region and back/forward history; minimap/world-map/search reuse it. |
| Workspace state | IN PROGRESS | Layout, HUD visibility/offsets and per-tool surface overrides share one schema-versioned store. |
| Background work | BLOCKER | Definitions, thumbnails, minimap bakes and generators use one `EditorExecutors` policy with cancellation/UI pump. |
| CI parity corpus | IN PROGRESS | Every `osrs/rules/**` rule that changes authored/rendered behavior has a checked-in fixture/golden test. |
| Server content contract | IN PROGRESS | Data-only manifests, capability negotiation, tolerant readers, diagnostics, adapter registry and reference fixture pass CI. |
| Round-trip content writing | BLOCKER | Spawn/area writers preserve comments/custom keys/order, produce reviewable diffs, and never rewrite unrelated data. |
| Diagnostics Center | BLOCKER | Parse/version/capability/adapter errors are visible and actionable from one panel. |
| Staging/export safety | BLOCKER | Map/cache writes stage first, show semantic diff, validate, then export. Base cache is never mutated silently. |
| Real-cache smoke matrix | BLOCKER | Known regions open/edit/save/reopen on current supported OSRS cache without semantic drift. |

## Coordinate-space completion checklist

The desired compiler-enforced flow is:

```
viewport / navigation / server content
            WorldTile
               |
        DocumentCoordinates
               |
            LocalTile
               |
          WorldDocument
```

Before this gate is closed:

1. `Viewport.tileAt()` and all scene pick results return `WorldTile`.
2. `WorldDocument` public mutation/read APIs use `LocalTile` for new code.
3. Legacy `TileCoordinate` remains only behind explicitly deprecated compatibility seams until command/selection contracts migrate.
4. No editing tool performs modulo wrapping to enter a region.
5. Non-zero-origin tests use at least region (50,50), proving that absolute 3200+ coordinates cannot index a 64x64 document accidentally.
6. Viewport overlays stay world-space; selection/history/commands stay document-local.

## Map editor smoke matrix

Run these against an actual loaded region with a non-zero world origin:

| Operation | Must verify |
|---|---|
| Tile Painter | Underlay, overlay, shape, rotation and flags apply to the hovered world tile and survive save/reload. |
| Brush radius/stroke | Preview mask equals committed mask; no wrap across opposite document edge. |
| Raise/lower | Shared corners remain continuous across adjacent tiles. |
| Smooth/blend/terrace/ramp/flatten | No coordinate crash, seam, provenance loss or out-of-document mutation. |
| Box/lasso select | World-space marquee maps to correct local selection. |
| Place/move/duplicate/rotate/delete object | Correct local object coordinates, overlay stays at absolute world position. |
| Context menu | Eyedropper, quick height, flag toggles and object actions hit the clicked tile. |
| Drag/drop object | Drop world pick converts once through session coordinates. |
| Plane restriction | Visible lower/upper planes cannot steal picks from active edit plane. |
| Undo/redo | One user gesture creates one atomic history entry and restores exact prior authored state. |
| Save/reopen | Terrain provenance, objects, shapes, rotations, flags and heights round-trip. |

## Content-integration contract

The production rule is **data-only discovery**.

Studio may parse:

- `content-manifest.toml`
- declared TOML/JSON resources
- cache archives/definitions
- GameVals/symbol dumps
- optional server-produced collision/runtime dumps

Studio must not depend on parsing Kotlin source or loading compiled server content classes.

Required before content editing is called stable:

1. Manifest: plugin id, manifest/schema version, capabilities, authors/homepage and resource paths.
2. Capability negotiation with schema versions.
3. Pluggable project-layout resolvers.
4. Canonical editor models for spawns, areas, drops, skill nodes and other modeled content.
5. Lenient readers that report imported/skipped records and unknown keys.
6. Round-trip-preserving writers.
7. Generic TOML/JSON inspector for unmodeled declarative artifacts.
8. Adapter registry for project dialects.
9. Conformance corpus containing the same authored concept represented in multiple supported variants.
10. Reference OpenRune project exercising every documented contract.
11. Optional live channel stays additive. Disk/cache workflows must work without the server running.
12. Server-truth collision diff overlay.
13. Federated symbols with source/confidence labels.
14. Diagnostics Center aggregates every parse/capability/version issue.

## Plugin/SPI freeze gate

Before third-party plugin development is encouraged, freeze a neutral `editor-spi` contract:

- lifecycle and contribution ownership
- command/history mutation boundary
- immutable scene snapshots
- brush registration and capability lookup
- managed UI surface metadata/content contract
- managed HUD contribution contract
- settings namespace
- hotkey/action registration
- task/executor API
- diagnostics API
- integration adapter API

Add an abstract contract test kit that external and internal plugins can run against.

## Performance gates

These are foundation-level, not cosmetic optimization:

- zone-revision dirty tracking for terrain/object edits
- DDA or GPU-ID picking without whole-scene brute-force hover scans
- persistent/reused VBOs for selection/brush overlays
- bounded allocations in frame loops
- one executor policy for asset work
- asynchronous GPU readback/ring-buffer path if/when the ID buffer becomes the primary picker
- static scene buffers are re-uploaded only for relevant dirty zones, not camera movement

## UI/UX freeze gate

The shell stays structured instead of arbitrary:

```
top menu / workspace tabs
left tool rail | central viewport + managed HUDs | right tool rail + right panel
                     bottom tool rail + bottom drawer
```

A tool may move between allowed shell regions, but its icon + contextual panel move as one contribution. Viewport HUDs are positioned by the host, not by independent plugins choosing absolute coordinates.

Before freeze:

- central keybinding registry
- generated per-tool shortcut/help sheet
- single workspace state store
- single navigation service
- command palette uses the same actions/navigation/symbol sources
- standard property-grid components
- non-blocking notifications
- dashboard exposes recent cache/project state, validation health and quick entry to workspaces

## Definition of "coast and focus on features"

The foundation is ready when:

1. CI is green on `main` and the feature branch.
2. The P0 gates above are closed.
3. The real-cache smoke matrix passes.
4. The OpenRune reference project passes the content self-check with no errors.
5. A third-party sample plugin can add one tool, one HUD, one panel, one brush and one content adapter without importing Studio/ImGui internals.
6. Adding a normal map/content feature no longer requires modifying `MapEditorView`, renderer internals, coordinate conversion code or integration discovery core.

After that point, new work should mostly be feature modules, rules/adapters, UI contributions, fixtures and performance tuning rather than architectural surgery.
