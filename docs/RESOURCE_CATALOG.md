# RSPSi Resource Catalog

This is the intake ledger for resources that may be used by RSPSi or consulted
while implementing and validating it. It is deliberately separate from the
build dependency graph. A resource appearing here does not authorize copying
its code, bundling its assets, or making it a runtime dependency.

Last reviewed: 2026-09-17

The latest cross-resource decision is recorded in
[`RESOURCE_REVIEW_2026-09-17.md`](RESOURCE_REVIEW_2026-09-17.md). It confirms
that RSPSi has one production spine—RSPSi-owned world/editor contracts over an
OpenRune FileStore adapter—and that RuneLite, TSPS, OpenRune-Editor, Domw71,
and OpenRune-Server contribute narrowly scoped donor/oracle evidence only.

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
| OpenRune FileStore | [OpenRune/OpenRune-FileStore](https://github.com/OpenRune/OpenRune-FileStore) | `production-candidate` | OSRS filesystem, cache access, definitions, models, sprites, RSCM/GameVal, XTEA, map/location bytes, packing utilities | Apache-2.0 is declared in the published build metadata; the pinned checkout has no standalone root license file, so selected artifact/version evidence must remain attached before redistribution | Keep normal source reads read-only; explicit `CacheDelegate` output writes are covered by copied-cache integration evidence; treat upgrades as parity events |
| OpenRune RSCM/GameVal ecosystem | [OpenRune organization](https://github.com/OpenRune) | `production-candidate` | Symbolic names for object/floor/cache properties and asset search | Verify terms per selected artifact | `OpenRuneSymbolicNameProvider` now adapts already-loaded reverse tables; mapping lifecycle and selected-artifact provenance remain |
| JavaFX | [OpenJFX](https://openjfx.io/) | `production-candidate` | Current desktop UI and viewport host | Follow OpenJFX distribution terms | Retain as frontend; keep it out of editor-core |
| LWJGL/OpenGL | [LWJGL](https://www.lwjgl.org/) | `later` | Possible standalone GPU renderer backend | Verify selected version and native distribution terms | Do not start until cache/editing gates pass |
| imgui-java | [imgui-java](https://github.com/SpaiR/imgui-java) | `later` | Possible Dear ImGui frontend after editor-core is UI-neutral | MIT; verify selected version and native packaging | Keep as an option, not a current dependency |

## Donor implementations and focused ports

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| OpenRune-Server upstream | [OpenRune/OpenRune-Server](https://github.com/OpenRune/OpenRune-Server) | `donor` | Coordinate conventions, collision flags, loc constants, rotation helpers, route/reach semantics | Review the upstream license and preserve attribution | Extract a small collision/route reference package after cache seam |
| OpenRune-Server Neosback fork | [Neosback/OpenRune-Server](https://github.com/Neosback/OpenRune-Server) | `donor` | `engine`, `engine/map`, `engine/routefinder`, `or-cache`, and focused tools; cache-backed engine logic, world semantics, location-shape/layer constants, and collision behavior | BSD 2-Clause declared in the checked-out `LICENSE.md`; preserve RS Mod notices and review inherited files before adapting | Diff against upstream; inspect only world/cache/route modules, never import server/content runtime; adoption is represented by RSPSi-owned `OsrsLocShape`, `ObjectCategory`, and collision tests |
| TSPS / Elvarg TypeScript client | [RSPSApp/TSPS](https://github.com/RSPSApp/TSPS) | `scene donor` + `comparison source` | Modern terrain/loc decoding, scene construction, bridges, instances, model transforms, shaped-tile behavior, scene-to-render preparation, WebGL packet fields, and edit-mode region-pack/edit-log behavior | BSD-2-Clause declared at the pinned commit; provenance includes the xRSPS/Elvarg lineage; preserve notices and do not bundle client/server assets; the current reference worktree deletes its license file and must not be treated as license-complete | Build 13-shape × 4-rotation, scene, render-packet, and revision-240 region-pack parity cases; do not import the TypeScript runtime; RuneLite remains the client-semantic oracle |
| OpenRune-Editor concepts | [OpenRune organization](https://github.com/OpenRune) | `donor` | Tool lifecycle, brush policies, transaction grouping, history, region-stamp concepts | Verify the exact editor repository and terms | Adapt concepts into `EditorTool`, `EditorCommand`, and `WorldFragment` |
| OpenRune-Editor Neosback fork | [Neosback/OpenRune-Editor](https://github.com/Neosback/OpenRune-Editor) | `donor` | Actual map-editor workbench, terrain/object tools, brush plugins, history, region stamps, scene construction, collision, picking, WebGL renderer, and cache integration | BSD 2-Clause declared in the checked-out `LICENSE`; preserve notices and review credited assets | Inspect focused editor behavior; do not treat its browser/WebGL architecture as an independent oracle or import it into RSPSi |
| Neptune | [neptune-ps/neptune](https://github.com/neptune-ps/neptune) | `later` | CS2/RuneScript parser/compiler and diagnostics, much later | MIT declared upstream | Keep outside map-editor foundation |
| Neptune OSRS CS2 | [neptune-ps/osrs-cs2](https://gitlab.com/neptune-ps/osrs-cs2) | `later` | Future CS2 symbols/compiler inputs | Verify repository terms and revision | Revisit only after world editing is strong |
| OpenRune cache packing utilities | [OpenRune FileStore tools](https://github.com/OpenRune/OpenRune-FileStore) | `donor` | Dirty-region output and incremental packing | Covered by selected OpenRune artifact review; CS2 compiler transitive dependency is excluded from RSPSi | Keep the writable delegate explicit and continue broader output parity before changing defaults |
| OSRS Environment Exporter | [ConnorDY/OSRS-Environment-Exporter](https://github.com/ConnorDY/OSRS-Environment-Exporter) | `donor` + `license-review` | RuneLite-derived scene-region construction, terrain lighting/material preparation, object/model placement, alpha/priority renderer separation, glTF export, and headless export workflow | GPL-3.0 project; selected files retain RuneLite BSD notices; do not copy, bundle, or add as a runtime dependency without a separate distribution review | Reimplement only the needed neutral contracts and add deterministic scene/export fixtures; see [`OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md`](OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md) |

## Correctness oracles and live comparison sources

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| RuneLite client/cache | [runelite/runelite](https://github.com/runelite/runelite) | `oracle` | Current OSRS map semantics, planes, bridges, tile heights, flags, locs, minimap, model behavior | BSD-2-Clause declared upstream; do not embed its client | Establish parity fixtures and record expected semantics |
| melxin RuneLite/OpenOSRS fork | [melxin/runelite](https://github.com/melxin/runelite) | `donor` + `oracle` | Full plugin platform, PF4J/classloader lifecycle, stable API/mixin/injection layering, deobfuscation flow, cache behavior, scene/rendering study, and JShell diagnostics | Treat the fork's declared upstream terms and retained notices as reference-only; do not redistribute copied source or bundle its client/cache | Study the plugin/runtime layers and focused scene paths after the foundation gates; do not make it a production backend |
| RuneLite Developer Tools | [DevTools usage guide](https://github.com/runelite/runelite/wiki/Using-the-client-developer-tools) and [overlay source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/devtools/DevToolsOverlay.java) | `oracle` | Live tile/scene/region/chunk/flag/object inspection and debug-overlay UX | Part of RuneLite; use as an independent viewer | Recreate useful inspectors and overlays inside Studio |
| RuneLite GPU renderer | [RuneLite repository](https://github.com/runelite/runelite) | `oracle` | Face priorities, alpha, texture ordering, model visibility, scene upload behavior | BSD-2-Clause upstream; no runtime coupling | Use for renderer parity after the stable core |
| RuneLite cache-code updater | [runelite/runelite-cache-code-updater](https://github.com/runelite/runelite-cache-code-updater) | `oracle` | Revision-drift strategy and changed-assumption reporting | Verify terms before adapting code | Design an RSPSi revision-audit report |
| RuneLite Plugin Hub | [runelite/plugin-hub](https://github.com/runelite/plugin-hub) | `later` | Plugin manifest, compatibility, review, permissions, disable/broken-plugin UX | BSD-2-Clause declared upstream; ideas only for v1 | Revisit after native tools and commands stabilize |
| Model exporter / live model export workflow | [RuneLite repository](https://github.com/runelite/runelite) | `visual-reference` | Compare decoded model geometry against a live client export | Do not bundle third-party exports without provenance | Define an OBJ/mesh comparison fixture format |

## Visual, forensic, and historical references

| Resource | Link | Status | Intended use | License / distribution | Next action |
|---|---|---|---|---|---|
| Explv OSRS map tiles | [Explv/osrs_map_tiles](https://github.com/Explv/osrs_map_tiles) | `visual-reference` + `license-review` | World-map overview, region alignment, 64×64 seam spotting, visual regression | Repository license is not currently confirmed; do not bundle generated tiles | Use only as an external/manual comparison until terms are clear |
| Domw71 revision-240 editor | [Domw71/OSRS-Map-Editor-Loading-240-rev](https://github.com/Domw71/OSRS-Map-Editor-Loading-240-rev) | `donor` + `license-review` | Standalone Java map-editor workflow, revision-240 cache/archive/terrain/location compatibility, exact terrain/location save/reload, 2D/3D editing, and manual UX comparison | BSD-2-Clause declared; repository explicitly derives from RuneLite cache code and retains per-file notices; no production reuse without provenance review | Use as a RuneLite-derived integration case study and semantic round-trip comparator; do not add its copied cache module or make it a dependency |
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

## Local research checkouts

The two requested repositories are checked out outside the RSPSi Git tree so
they cannot become accidental application sources or dependencies:

| Checkout | Local location | Captured revision | Scope |
|---|---|---|---|
| Upstream OpenRune-Server | `../RSPSi-resources/OpenRune-Server` | `72e8e1a1a05c54208f64c163cae4637301397d90` | Baseline for comparing the Neosback fork; inspect only engine/map, engine/routefinder, and or-cache |
| Neosback OpenRune-Server | `../RSPSi-resources/OpenRune-Server-Neosback` | `bde85d0b0a5f7f87c1b8e9430fa81677bf443c9a` | Compare `engine`, `engine/map`, `engine/routefinder`, `or-cache`, and tools with upstream OpenRune |
| Neosback OpenRune-Editor | `../RSPSi-resources/OpenRune-Editor-Neosback` | `1e5b41055da267ca94a615a0ec9853e21296b239` | Inspect the map-editor workbench, tools/plugins, history, region stamps, scene semantics, WebGL/picking, and cache integration; not a runtime dependency |
| OpenRune FileStore | `../RSPSi-resources/OpenRune-FileStore` | `4179fc4` (3.0.2; refreshed read-only from `236e392` on 2026-09-17) | Inspect the pinned production-candidate API and OSRS filesystem/definition modules; the application still consumes the published artifact |
| RSPSApp TSPS | `../RSPSi-resources/TSPS` | `83415f76589a360eacbd0e635fe0557d06a510f0` | Inspect modern terrain, scene, model, bridge, instance, and collision behavior; donor/oracle only |
| RuneLite | `../RSPSi-resources/RuneLite` | `ced4c4aba7a3cb7cace42e1f0c25a5f79b7faef` | Inspect current OSRS coordinate, scene, DevTools, cache, and renderer semantics; independent oracle only |
| melxin RuneLite/OpenOSRS fork | `../RSPSi-resources/RuneLite-melxin` | `1ad572d7dcdbc0fb67a4a00f0c2f959d5ab25abc` | Inspect full plugin lifecycle/classloader/PF4J, API/mixin/injection layering, deobfuscation, cache, scene/rendering, and JShell patterns; architecture reference only |
| RuneLite cache-code updater | `../RSPSi-resources/runelite-cache-code-updater` | `a200d75bf779cdc76cae53e7e2cd6f23172e3535` | Study revision-drift and changed-assumption reporting; oracle/reference only |
| Domw71 revision-240 editor | `../RSPSi-resources/OSRS-Map-Editor-Loading-240-rev` | `ddc360daaf3e60414e1822c5784531e3696096af` | Forensic revision-240 comparison; license/provenance review remains required |
| OSRS Environment Exporter | `../RSPSi-resources/OSRS-Environment-Exporter` | `61d461d3bfd8217a470518924415d7de1b074b9c` | Inspect scene-region lighting, renderer upload/priority behavior, glTF export, and headless export workflow; GPL-bounded reference only |
| Explv OSRS map tiles | `../RSPSi-resources/osrs_map_tiles` | `1e3d20bfccca800c7bdec6611655670323ae10f6` | External visual world-map reference; generated tile checkout is intentionally not bundled or used as canonical data |

The sibling resource directory is intentionally not part of the RSPSi Git
repository. Refreshes should be deliberate and should update this revision
record after review.

The first detailed evidence capture is in
[`RESOURCE_INTAKE_2026-09-16.md`](RESOURCE_INTAKE_2026-09-16.md). It records
the inspected server/editor paths, license notices, RSPSi-owned replacement
APIs, and current test evidence.
