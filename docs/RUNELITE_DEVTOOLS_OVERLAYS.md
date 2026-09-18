# RuneLite DevTools and Scene Overlay Reference

RuneLite's DevTools and overlay systems are useful examples of how to expose
scene facts without changing the scene. RSPSi will adopt the semantic ideas,
not the Java2D implementation or a second world model.

TSPS's tile/object/walkability overlays and the Environment Exporter's
headless scene/export path add complementary validation ideas. They are
summarized in [`SCENE_RENDERING_CROSS_REFERENCE.md`](SCENE_RENDERING_CROSS_REFERENCE.md);
overlay implementations still consume RSPSi snapshots and never open a
research checkout or cache directly.

## Two different overlay families

RuneLite has both persistent user intent and temporary diagnostics. Studio
keeps them separate:

```text
Persisted project data
└── UserTileMarker
    ├── tile/world anchor
    ├── label/note
    ├── semantic color
    └── visibility/category

Ephemeral session data
└── DiagnosticTileAnnotation
    ├── scene snapshot identity
    ├── tile/object/region target
    ├── text and diagnostic kind
    ├── severity/color
    └── temporary geometry hints
```

User markers survive save/reopen. IDs, flags, bridge state, collision, LOS,
camera, hover, loading lines, and object-inspection output are regenerated from
the current snapshot and are never silently saved as map edits.

## RuneLite overlay lifecycle

The reference flow is:

```text
Plugin startUp
  → create overlay instances
  → register with OverlayManager
  → OverlayRenderer orders by layer/position/priority
  → render(Graphics2D) after the selected client scene/interface layer
Plugin shutDown
  → unregister overlays and release resources
```

RuneLite's `Overlay`, `OverlayManager`, `OverlayRenderer`, and `OverlayUtil`
are Java AWT/Java2D APIs. Studio's neutral equivalent is `EditorSceneOverlay`
plus immutable `EditorSceneSnapshot`/`DebugOverlaySnapshot`; JavaFX and ImGui
adapt those values to their own draw APIs.

An overlay may draw a projected polygon, label, line, minimap marker, or
diagnostic panel. It may not write to `WorldDocument`, `RenderScene`, collision
flags, or cache data.

## DevTools groups to support

The Studio `RendererDiagnostics` contribution should expose grouped toggles:

- tile, chunk, region, and loaded-window boundaries;
- world, scene, region, chunk, and plane coordinates;
- terrain heights, underlay/overlay IDs, shape, and rotation;
- object category, ID, definition/model ID, shape, orientation, and footprint;
- bridge, visibility-below, and roof flags;
- collision movement/projectile flags;
- route, reach, and line-of-sight previews;
- camera and projection information;
- occluder bounds and scene layer order; and
- hovered-tile tooltip data.

The existing `DebugOverlayMode`, `DebugOverlayBuilder`, and
`DebugOverlaySnapshot` are the first implementation seam. Additional groups
must extend those neutral snapshots rather than query the cache or scene graph
from a frontend callback.

## RuneLite color and style defaults

RuneLite commonly uses translucent black fills, solid colored borders, black
text shadows, and semantic colors. The DevTools reference constants include:

| Semantic use | RGB default |
|---|---:|
| red | `221,44,0` |
| green | `0,200,83` |
| orange | `255,109,0` |
| yellow | `255,214,0` |
| cyan | `0,184,212` |
| blue | `41,98,255` |
| deep purple | `98,0,234` |
| purple | `170,0,255` |
| gray | `158,158,158` |

Ground markers use yellow by default, a black fill near alpha 50, and a
two-pixel border. Tile indicators use cyan/gray variants, collision and LOS
use distinct semantic colors, and labels use a black offset shadow.

These values are defaults in a renderer-neutral semantic palette. Frontends
may convert them to JavaFX paint, ImGui draw-list colors, or GPU overlay
vertices. A theme change must not change authored scene data or scene
fingerprints.

## Tile projection and picking

RuneLite projects a world tile with the current camera/perspective and tests
mouse containment against the projected tile polygon. Studio follows the same
rule:

1. resolve a neutral `TileCoordinate`/`WorldTileAddress` from the snapshot;
2. project it through the active frontend camera;
3. test the pointer against the projected polygon;
4. publish a neutral selection or diagnostic annotation; and
5. let the frontend draw the result.

The projected polygon is never used as the authoritative tile geometry. Picking
must work against the same scene coordinate and effective-plane semantics in
JavaFX and ImGui.

## Renderer diagnostics plugin boundary

DevTools functionality belongs in one grouped first-party feature contribution
named `renderer-debug`/`Renderer Diagnostics`. It may register:

- grouped settings and toggle commands;
- scene overlays;
- inspectors and hover tooltips;
- camera/scene status widgets; and
- validators for missing assets, bridge inconsistencies, seams, and unsupported
  model capabilities.

It must not become a cache loader, renderer backend, or second plugin registry.
Its lifecycle is owned by the existing `EditorPluginHost`, so unloading the
feature removes overlays, commands, settings, and resources together.

## Verification

The overlay contract is complete when:

- persisted markers survive save/reopen;
- diagnostics disappear when the feature/session closes;
- colors and alpha are stable across frontends;
- labels resolve the same world/scene/region coordinates;
- bridge, roof, collision, and object IDs agree with the immutable snapshot;
- JavaFX and ImGui produce equivalent selection and annotation targets; and
- no overlay directly opens FileStore or mutates authored data.
