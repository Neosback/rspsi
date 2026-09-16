# RSPSi Reference Ecosystem

This document records research and architectural decisions. It is context for
future implementation, not a set of instructions to import or combine whole
repositories.

The enforceable design contract is in
[`ARCHITECTURE.md`](ARCHITECTURE.md); this document explains the roles of
external sources.

The actionable intake ledger—including URLs, license confidence, provenance,
and next actions—is [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md).

## Source tiers

- **A — use directly:** a small, production dependency behind an RSPSi API.
- **B — port/adapt:** a focused algorithm or concept whose behavior is useful.
- **C — oracle/reference:** independent material used to compare correctness.
- **D — later specialist:** valuable only after the editor core is stable.

## Ownership decisions

| Responsibility | RSPSi decision | Source |
|---|---|---|
| Desktop shell and current workflow | Keep and stabilize | RSPSi |
| Legacy/317/custom cache | Production adapter | Displee via `CacheStore` |
| OSRS cache and definitions | Production adapter, after parity | OpenRune FileStore |
| Collision and routefinding semantics | Focused donor | OpenRune-Server |
| Modern terrain, locs, bridges, instances, models | Focused donor | TSPS |
| Current OSRS correctness | Independent oracle | RuneLite |
| Live scene inspection and debug UX | First-class oracle | RuneLite DevTools |
| Editing tools, transactions, fragments | Focused donor | OpenRune-Editor |
| CS2 compiler/runtime | Later specialist | Neptune and TSPS |
| Cache archaeology and old revisions | Research only | OpenRS2, VoidPS, 2011Scape, LostCity |
| Revision-240 compatibility forensics | Research only | Domw71/OSRS-Map-Editor-Loading-240-rev |
| Revision drift strategy | Research only | runelite-cache-code-updater |
| World-map visual QA | Reference only; do not bundle unclear-license assets | Explv/osrs_map_tiles |
| Geometry debugging | Independent validation workflow | Model Exporter |
| Asset-browser UX | Reference only | Quill |
| Advanced rendering | Later reference | 117 HD and GPU clients |
| World model, commands, selection, renderer, UI | RSPSi-owned | Ours |

## Dependency policy

RSPSi must not become a collection of entire projects. Every external system is
isolated behind a small neutral interface, and editor code must not expose
Displee, OpenRune, RuneLite, TSPS, or server types. OpenRune FileStore is the
planned OSRS production cache; OpenRS2 and RuneLite are not second production
backends. OpenRune-Server contributes world semantics, not server runtime.

OpenRune's `dev.or2` artifacts are published through its Maven-compatible
hosting repository rather than Maven Central. The repository is declared in
the root Gradle build, and the version is pinned for repeatable compatibility
testing.

The current renderer and JavaFX UI remain the compatibility surface. The
product targets OSRS/OpenRune; non-OSRS support is quarantine-only during the
migration and is retired after parity gates pass. Renderer replacement,
Kotlin-wide migration, plugin marketplaces, Lua, CS2 tooling, network
integration, and collaborative/cloud features are intentionally later.

## Planned sequence

1. Protect existing behavior with semantic fixtures and smoke tests.
2. Complete the neutral cache/definition boundary.
3. Run the OpenRune compatibility spike without changing the default backend.
4. Move all edits through the session and command history.
5. Add terrain/object/collision improvements on that stable core.
6. Only then build a new renderer or first-party plugin ecosystem.

Resource gathering follows the same order. We collect evidence and fixtures
first, then selectively promote a resource to a pinned dependency or focused
port. We do not combine entire donor projects.

## Source-specific guidance

RSPSi remains the classic behavior and workflow oracle. TSPS and RuneLite are
the modern scene references; their browser/client render architectures are not
to be copied. OpenRune-Editor informs tool and history design, while the
canonical `WorldDocument`, `TerrainTile`, `WorldObject`, `WorldFragment`,
`EditorCommand`, `EditorTool`, selection service, and renderer interfaces belong
to RSPSi. VoidPS and 2011Scape are 667-era references and must not define modern
OSRS encoding or scene behavior.

RuneLite DevTools is promoted to the live truth viewer for tile coordinates,
scene/region/chunk relationships, flags, collision, object categories, and
loading-line context. Studio will eventually implement equivalent offline
debug overlays and inspectors rather than depending on RuneLite at runtime.
