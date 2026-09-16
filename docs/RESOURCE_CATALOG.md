# RSPSi Resource Catalog

This is the intake ledger for resources that may be used by RSPSi or consulted
while implementing and validating it. It is deliberately separate from the
build dependency graph. A resource appearing here does not authorize copying
its code, bundling its assets, or making it a runtime dependency.

Last reviewed: 2026-09-16

## Decision vocabulary

| Status | Meaning |
|---|---|
| `production-candidate` | May become a small, pinned runtime/build dependency behind an RSPSi-owned API. |
| `donor` | Focused algorithms, constants, or design ideas may be adapted after review. |
| `oracle` | Independent correctness reference; never a production dependency for the same concern. |
| `visual-reference` | Used for screenshots, geometry, world-map, or UX comparison. |
| `later` | Valuable, but intentionally outside the stabilization foundation. |
| `license-review` | Useful reference, but provenance/license must be confirmed before reuse or distribution. |
| `do-not-depend` | Explicitly excluded from the RSPSi runtime foundation. |

License status is evidence-based. `verified` means the upstream repository or
an inspected artifact declares it. `review` means RSPSi may inspect the source
but must not redistribute code or assets until the terms are confirmed.

## Foundation and production candidates

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| RSPSi | [local repository](../) | `production-candidate` | Desktop editor, existing workflows, classic/legacy behavior, current renderer during stabilization | Project-owned code; preserve existing attribution and terms | Keep as the shipping shell while seams are introduced |
| Displee cache implementation | [Displee](https://github.com/Displee/rs-cache-library) | `production-candidate` | Legacy/317/custom cache adapter only | MIT declared upstream; preserve the license with any redistribution | Keep behind `LegacyDispleeCacheStore`; do not expose types |
| OpenRune FileStore | [OpenRune/OpenRune-FileStore](https://github.com/OpenRune/OpenRune-FileStore) | `production-candidate` | OSRS filesystem, cache access, definitions, map/location data, packing utilities | Apache-2.0 artifacts/source reviewed for the pinned 2.4.19 compatibility spike | Keep opt-in and read-only until parity fixtures pass |
| OpenRune RSCM/GameVal ecosystem | [OpenRune organization](https://github.com/OpenRune) | `production-candidate` | Symbolic names for object/floor/cache properties and asset search | Verify terms per selected artifact | Select only the small APIs needed by the asset repository |
| JavaFX | [OpenJFX](https://openjfx.io/) | `production-candidate` | Current desktop UI and viewport host | Follow OpenJFX distribution terms | Retain as frontend; keep it out of editor-core |
| LWJGL/OpenGL | [LWJGL](https://www.lwjgl.org/) | `later` | Possible standalone GPU renderer backend | Verify selected version and native distribution terms | Do not start until cache/editing gates pass |
| imgui-java | [imgui-java](https://github.com/SpaiR/imgui-java) | `later` | Possible Dear ImGui frontend after editor-core is UI-neutral | MIT; verify selected version and native packaging | Keep as an option, not a current dependency |

## Donor implementations and focused ports

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| OpenRune-Server | [OpenRune/OpenRune-Server](https://github.com/OpenRune/OpenRune-Server) | `donor` | Coordinate conventions, collision flags, loc constants, rotation helpers, route/reach semantics | ISC declared upstream; adapt only separable world logic | Extract a small collision/route reference package after cache seam |
| TSPS | [RSPSApp/TSPS](https://github.com/RSPSApp/TSPS) | `donor` + `oracle` | Modern terrain/loc decoding, scene construction, bridges, instances, model transforms, shaped-tile behavior | Review repository terms before copying code/assets | Build 13-shape × 4-rotation and modern map parity cases |
| OpenRune-Editor | [OpenRune organization](https://github.com/OpenRune) | `donor` | Tool lifecycle, brush policies, transaction grouping, history, region-stamp concepts | Verify the exact editor repository and terms | Adapt concepts into `EditorTool`, `EditorCommand`, and `WorldFragment` |
| Neptune | [neptune-ps/neptune](https://github.com/neptune-ps/neptune) | `later` | CS2/RuneScript parser/compiler and diagnostics, much later | MIT declared upstream | Keep outside map-editor foundation |
| Neptune OSRS CS2 | [neptune-ps/osrs-cs2](https://gitlab.com/neptune-ps/osrs-cs2) | `later` | Future CS2 symbols/compiler inputs | Verify repository terms and revision | Revisit only after world editing is strong |
| OpenRune cache packing utilities | [OpenRune FileStore tools](https://github.com/OpenRune/OpenRune-FileStore) | `donor` | Dirty-region output and incremental packing | Covered by selected OpenRune artifact review | Test output-cache workflow before enabling writes |

## Correctness oracles and live comparison sources

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| RuneLite client/cache | [runelite/runelite](https://github.com/runelite/runelite) | `oracle` | Current OSRS map semantics, planes, bridges, tile heights, flags, locs, minimap, model behavior | BSD-2-Clause declared upstream; do not embed its client | Establish parity fixtures and record expected semantics |
| RuneLite Developer Tools | [DevTools usage guide](https://github.com/runelite/runelite/wiki/Using-the-client-developer-tools) and [overlay source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/devtools/DevToolsOverlay.java) | `oracle` | Live tile/scene/region/chunk/flag/object inspection and debug-overlay UX | Part of RuneLite; use as an independent viewer | Recreate useful inspectors and overlays inside Studio |
| RuneLite GPU renderer | [RuneLite repository](https://github.com/runelite/runelite) | `oracle` | Face priorities, alpha, texture ordering, model visibility, scene upload behavior | BSD-2-Clause upstream; no runtime coupling | Use for renderer parity after the stable core |
| RuneLite cache-code updater | [runelite/runelite-cache-code-updater](https://github.com/runelite/runelite-cache-code-updater) | `oracle` | Revision-drift strategy and changed-assumption reporting | Verify terms before adapting code | Design an RSPSi revision-audit report |
| RuneLite Plugin Hub | [runelite/plugin-hub](https://github.com/runelite/plugin-hub) | `later` | Plugin manifest, compatibility, review, permissions, disable/broken-plugin UX | BSD-2-Clause declared upstream; ideas only for v1 | Revisit after native tools and commands stabilize |
| Model exporter / live model export workflow | [RuneLite repository](https://github.com/runelite/runelite) | `visual-reference` | Compare decoded model geometry against a live client export | Do not bundle third-party exports without provenance | Define an OBJ/mesh comparison fixture format |

## Visual, forensic, and historical references

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| Explv OSRS map tiles | [Explv/osrs_map_tiles](https://github.com/Explv/osrs_map_tiles) | `visual-reference` + `license-review` | World-map overview, region alignment, 64×64 seam spotting, visual regression | Repository license is not currently confirmed; do not bundle generated tiles | Use only as an external/manual comparison until terms are clear |
| Domw71 revision-240 editor | [Domw71/OSRS-Map-Editor-Loading-240-rev](https://github.com/Domw71/OSRS-Map-Editor-Loading-240-rev) | `license-review` | Forensic case study of revision-240 cache/archive/terrain/loc changes | License and copied RuneLite provenance require review | Diff relevant loaders against their RuneLite base; do not depend on it |
| OpenRS2 | [openrs2/openrs2](https://github.com/openrs2/openrs2) | `oracle` + `do-not-depend` | Cache archaeology, compression, XTEA, revision comparison, validation | Verify terms per module; research only | Use for historical fixtures and broken-cache diagnosis |
| tpetrychyn OSRS map editor | [tpetrychyn/osrs-map-editor](https://github.com/tpetrychyn/osrs-map-editor) | `license-review` | Kotlin/OpenGL editor, picking, terrain GPU conversion, JavaFX/OpenGL integration precedent | No clear repository license surfaced; reference only | Study architecture; copy nothing until licensed |
| VoidPS | [repository URL to verify] | `oracle` + `license-review` | 667-era validation, collision diagnostics, map dumping, navigation-tool ideas | Historical revision; verify exact repository and terms | Use only to compare historical behavior, never as OSRS authority |
| 2011Scape / RuneTek 5 client | [repository URL to verify] | `oracle` + `license-review` | Historical pathfinding, collision terminology, JS5, minimap, interfaces | Archived 667-era material; verify exact repository and terms | Consult when separating long-lived rules from OSRS behavior |
| LostCity | [repository URL to verify] | `oracle` + `license-review` | Classic terrain/location behavior and RuneScript history | Verify exact repository and terms | Keep classic behavior comparison separate from OSRS codecs |
| RuneStar | [RuneStar organization](https://github.com/runestar) | `oracle` + `license-review` | Cache names, internal symbols, CS2 research, terminology | Verify each repository before reuse | Use as supporting research; OpenRune remains primary symbolic source |
| Quill | [repository URL to verify] | `visual-reference` + `license-review` | Asset-browser search, previews, definition/property inspection UX | Verify exact project; do not assume the name identifies a specific repository | Capture UX requirements for Objects/Overlays/Underlays first |
| 117 HD | [117HD organization](https://github.com/117HD) | `later` + `license-review` | Materials, lighting, shadows, water, batching, enhanced rendering ideas | Verify terms per repository; not a foundation dependency | Revisit after faithful classic/OSRS rendering parity |

## Later specialists explicitly outside the foundation

| Resource | Role | Boundary |
|---|---|---|
| RSProx | `later` | Runtime/network/map-event observation only; no live-server connection in the editor foundation |
| Creator’s Kit | `later` | Scene, cutscene, NPC, and camera-track UX inspiration after map editing is excellent |
| RuneLite Plugin Hub ecosystem | `later` | Borrow manifest/review/permissions concepts; no public marketplace in v1 |
| TSPS CS2 VM | `later` | Future CS2 debugger and interface preview; Neptune remains the compiler candidate |

## Correctness stack

The intended comparison flow is:

```text
OSRS live client
    -> RuneLite DevTools inspection
    -> RuneLite cache/client + TSPS scene references
    -> RSPSi neutral semantic model and parity fixtures
    -> OpenRune-backed Studio implementation
```

For geometry problems, add an independent model export comparison. For
historical formats, use OpenRS2 and the 667-era references without allowing
them to define current OSRS behavior.

## Intake rules

1. Record the upstream URL, revision/commit, license evidence, and intended
   role before adding a dependency or copying an algorithm.
2. Prefer a small RSPSi-owned adapter or port over exposing an upstream type.
3. Pin production candidates and record their version in Gradle and this file.
4. Do not bundle generated map tiles, cache dumps, models, or copied source
   from a resource marked `license-review`.
5. Store only minimal fixtures needed for a reproducible test, with provenance
   and redistribution terms recorded next to the fixture.
6. When a new revision is supported, run a revision audit and update the
   compatibility evidence rather than silently changing codec assumptions.

## First resource-intake batch

This is the next focused work package; it does not add new runtime systems:

1. Freeze the OpenRune FileStore API/version evidence already used by the
   compatibility spike.
2. Capture RuneLite DevTools fields for tile coordinates, planes, bridges,
   flags, chunks, regions, and object categories.
3. Build a TSPS/RuneLite terrain reference matrix for the 52 shaped-tile
   cases and shared-edge invariants.
4. Isolate OpenRune-Server collision constants and route semantics for a
   neutral comparison test.
5. Perform the Domw71 revision-240 forensic diff without importing its cache
   implementation.
6. Add Explv map tiles only as a manually reviewed visual reference unless
   licensing becomes clear.
7. Define the first `Revision Audit` report around map, location, floor, and
   definition codecs.

The next implementation milestone after this catalog is the resource-intake
evidence and fixture harness—not a renderer rewrite or a broad dependency
import.
