# OpenRune FileStore capability audit — 2026-09-17

This audit answers one question with evidence: **does OpenRune FileStore have
everything RSPSi needs to be the production OSRS cache layer?** It complements
[`OPENRUNE_FILESTORE_ADOPTION.md`](OPENRUNE_FILESTORE_ADOPTION.md) (the
ownership contract) and [`RESOURCE_CATALOG.md`](RESOURCE_CATALOG.md)
(provenance). Verdicts here are per-capability and evidence-based; "covered"
always means *covered by an executable RSPSi test or verifier run*, not by a
green compile alone.

Audited artifacts:

| Artifact | Version | Evidence source |
|---|---|---|
| Published dependency in `Client/build.gradle` | `dev.or2:*:2.4.19` (pinned via root `gradle.properties`) | Gradle build; class-level inspection of the resolved jars |
| Research checkout | `../RSPSi-resources/OpenRune-FileStore` | Commit `4179fc4` (build metadata declares `3.0.2`; refreshed read-only from `236e392`) |
| OpenRune-Server Neosback `or-cache` map codecs | `../RSPSi-resources/OpenRune-Server-Neosback` | Commit `bde85d0` (map semantics donor) |

## 2.4.19 → 3.0.2 compatibility spike — 2026-09-17

The research checkout was refreshed (read-only) from commit `236e392`
(build metadata `3.0.1`) to `4179fc4` (build metadata `3.0.2`). The published
line reports `3.0.2` as `latest`/`release` in the hosting repository's
`maven-metadata.xml`.

Upstream delta inspection (`git diff 236e392..4179fc4`): the only changes are
new interface-TOML tooling under `tools/iftype` (`TomlInterfaceLoader`,
`InterfaceTomlExporter`, component DSL additions). The core modules RSPSi
consumes — `filesystem`, `filestore`, `osrs-fs`, `definition`, `osrs`,
`opcode` — are unchanged between 3.0.1 and 3.0.2.

Executable spike results (unmodified codebase, version switched via a new
`openruneFileStoreVersion` Gradle property in the root `gradle.properties`):

| Check | 3.0.2 result |
|---|---|
| clean `:Client:compileJava` + `compileTestJava` | PASS, zero source changes |
| full `test` suite (Client, Editor, OSRSPlugin), `--rerun-tasks` | PASS |
| `verifyCacheBackendBoundary` + `verifyUiNeutralCore` | PASS — no `dev.openrune` import escaped adapters |
| runtime dependency graph shape | identical to 2.4.19 for the selected module set; no new required OpenRune modules |
| reverse pin 2.4.19 full suite | PASS |

`dev.or2:opcode` was added to the explicit dependency list (it was already a
transitive companion of `definition`; the hosting-repo review promotes it to
first-class). The `tools` CS2-compiler exclusion is preserved.

**Spike verdict: 3.0.2 is deterministic-level compatible.** The production
pin remains `2.4.19` until the external parity matrix
(`verifyOsrsRevisionMatrix` plus the writable round-trip tests) is re-run
against 3.0.2 with the operator-supplied cache and fixtures, per the
runbook below. After that evidence, flipping the pin is a one-line change.

Environment note recorded for this workstation: Gradle must run with
`JAVA_HOME` pinned to Java 21/24; the shell-default Java 25 breaks Gradle
8.14.3 script compilation (class file major 69) for any build, independent of
the FileStore version.

## Version status and pin policy

The published artifact line is `2.4.19`; the research checkout is one major
step ahead at `3.0.1` build metadata. The repository `README.md` still
advertises `2.1.1`, confirming the upstream documentation lag already recorded
in the adoption contract.

**Decision: stay pinned at 2.4.19.** The audit found no capability in 3.0.1
that the map editor needs and 2.4.19 cannot provide through the existing
adapters. The 3.0.x line reorganizes modules and publishes a new major version;
adopting it now would invalidate the recorded compatibility evidence
(`verifyOsrsRevisionMatrix`, definition audits, fingerprint tests) for zero
functional gain. Known minor deltas observed during inspection:

- `ObjectType` in 3.0.x exposes the same `objectModels`/`objectTypes` pairing
  consumed by Stage 2 of the FileStore milestone; 2.4.19 exposes it identically
  (verified with `javap` against the resolved jar).
- The 3.0.x `filesystem` module separates `ReadOnlyCache` from a writable
  `Cache`; 2.4.19's `FileCache` is read-only and `CacheDelegate` (tools) is the
  writable path. RSPSi's `READ_ONLY`/`DIRECT` capability split maps cleanly
  onto both; the adapter isolates us from the rename.

## Hosting repository and dependency policy

`OpenRune/hosting` is the distribution mechanism: a Maven-compatible artifact
repository that supplies the pinned FileStore libraries at build time. It is
**not** an editor plugin, not the OSRS provider itself, not a runtime network
dependency, and never the owner of `WorldDocument`, history, selection,
rendering, or workspace state. The dependency chain is one-directional:

```text
OpenRune hosting
  → pinned Gradle dependencies
  → RSPSi FileStore adapter
  → OSRS revision-240 bundle
  → neutral Studio contracts
  → future workspaces and feature plugins
```

### Selected dependency set (dev/or2 namespace only)

| Artifact | Role |
|---|---|
| `dev.or2:filesystem` | Neutral filesystem/cache interfaces |
| `dev.or2:filestore` | Cache implementation |
| `dev.or2:osrs-fs` | OSRS cache/file mappings |
| `dev.or2:definition` | Shared definition models/decoders |
| `dev.or2:osrs` | OSRS definition codecs |
| `dev.or2:opcode` | Cache/config opcode support |
| `dev.or2:tools` | Optional; output/build adapter only (CS2 compiler excluded) |
| `dev.or2:displee` | Optional staged-output/write compatibility |

Rules confirmed with the hosting-repo review:

- **Never use the `dev.or2:all` aggregate.** It pulls in server, R718, RS3,
  and tooling dependencies Studio does not want. `all-osrs` is safer but still
  does not replace the explicit per-module dependencies above.
- The legacy `dev/openrune` publishing namespace is historical; do not add new
  dependencies from it.
- Server-only or unrelated artifacts stay outside Studio core: `filestore-server`,
  `filestore-server-osrs`, `central-*`, `openrune-central-*`, `server-utils`,
  `wiki`, `bootstrap`, `js5server`, `injector-plugin`.
- Later-integration artifacts (`me.filby:clientscript-compiler`,
  `me.filby:runescript-*`, `org.openrs2:*`, `cc.ekblad:4koma`, `dev.or2:r718`,
  `dev.or2:rs3`, `dev.or2:toml-*`) are recorded as future options. They are not
  foundation dependencies; a future CS2/interface workspace would add the
  clientscript compiler deliberately, and `toml-*` would be considered for the
  source-project format in Phase 6.
- The application must package resolved dependencies with the desktop build so
  Studio runs offline after installation; the hosting repo is a build-time
  source only. Verified: `:Editor:installDist` bundles all selected
  `dev.or2` jars (`filesystem`, `filestore`, `osrs-fs`, `osrs`, `definition`,
  `opcode`, `tools`, `displee`) plus their transitive dependencies into the
  distribution `lib/` with the launcher classpath referencing only
  `$APP_HOME/lib`, so no network access is needed at runtime.

## Capability matrix

| # | Editor need | Neutral contract | FileStore 2.4.19 evidence | Verdict |
|---|---|---|---|---|
| 1 | Open DAT2 cache, enumerate indices/archives/files | `CacheStore`, `CacheIndexView`, `CacheArchiveView` | `filesystem` module (`Cache`, `FileCache`); used by every loader | **covered** |
| 2 | XTEA-encrypted map reads | `CacheStore.read(index, archive, file)` | `Cache.data(..., xtea)`; map payloads for rev < 237 decrypt through the same call | **covered** |
| 3 | Revision-aware terrain/location decode + encode | RSPSi-owned `OsrsRegionDecoder`/`OsrsRegionEncoder` | FileStore does **not** provide map codecs in 2.4.19; OpenRune-Server `or-cache` does. RSPSi owns these by design (adoption contract) | **covered by design** — not a FileStore gap |
| 4 | Named (`mX_Y`) and packed numeric map groups | `MapIndexTable`, `OsrsMapService` | `Cache.archiveId(index, name)` + numeric enumeration; verified live on revision-6 named and build-240 numeric | **covered** |
| 5 | Object definitions (incl. model-type pairing, collision, appearance) | `ObjectDefinitionView`, `ObjectCollisionView`, `ObjectAppearanceView` via `OpenRuneDefinitionProvider` | `osrs` module `ObjectCodec` decodes opcodes 1–249; `ObjectType.objectModels`/`objectTypes` pairing is available | **covered**; model-type pairing now surfaced to the editor (this milestone, Stage 2) |
| 6 | Underlay/overlay definitions incl. blend inputs | `FloorDefinitionView` | `UnderlayType`/`OverlayType` expose raw + weighted hue, saturation, lightness, hue multiplier | **covered** |
| 7 | Textures: definitions, pixels, animation metadata | `TextureDefinitionView`, `texturePixels(...)` | `TextureType.load(sprites)` at 128px/brightness 0.6 | **covered with a frozen contract** — brightness/size is pinned to 0.6/128 (see §Texture contract) |
| 8 | Models: geometry, normals, texture triangles, render types | `ModelGeometryView` (lazy) | `ModelDecoder` + `ModelType.computeNormals()`; geometry verified live (model 0) and metadata for 60k+ IDs | **covered** |
| 9 | Sprites and map-scene sprites | `MapSceneSpriteView` | `SpriteDecoder`; graphics-defaults + named `mapscene` fallback verified on build 240 (127 placed sprites resolved) | **covered** |
| 10 | Sequences + map elements (lazy) | `SequenceDefinitionView`, `MapElementDefinitionView` | 2.4.19 exposes raw config bytes; RSPSi-owned lazy decoders cover rev-226+ skeletal opcodes | **covered**; decode failures now diagnosable (Stage 2) |
| 11 | RSCM/GameVal symbolic names | `SymbolicNameProvider` | OpenRune RSCM tables adapted through `OpenRuneSymbolicNameProvider`; mapping-file lifecycle open | **covered (adapter-work)** — lifecycle tracking remains in ROADMAP |
| 12 | Writable output cache | `CacheStore.write` + `CacheWriteMode.DIRECT` | `tools` module `CacheDelegate(write/update)`; verified by copied-cache round-trip tests | **covered** (Stage 4 of this milestone extends batch coverage) |
| 13 | Cache identity/fingerprint for project binding | `CacheStore.metadata(revision)` | Canonical CRC fingerprint over index reference tables | **covered** |
| 14 | Incremental packing / dirty-unit builds | *(future build pipeline)* | `tools.incremental` (`IncrementalBuild`, `PackUnit`, `RecordingCache`, fingerprint verification) exists in 2.4.19 and is used by `PackMaps`/`PackModels` | **upstream-candidate** — deliberately unused until Phase 6 (source-first build); RSPSi's own `DirtyRegion` owns editor-level invalidation |
| 15 | Item/NPC/interface/CS2/DB-table codecs | *(future content workspaces)* | `osrs` module ships `ItemCodec`, `NPCCodec`, `ComponentDecoder`, `DBRowCodec`, CS2 building blocks | **available, not needed for map editor** — recorded for the Studio content workspace phase; same `DefinitionProvider` facade will be extended |
| 16 | World-map tooling | *(future World Map workspace)* | `tools.worldmap` + `PackWorldMap`/`DumpWorldMap` | **available, not needed for map editor** |
| 17 | JS5 file serving | out of scope | `tools` JS5 server exists | **not-needed-for-map-editor** (Studio is offline; server integration is adapter-level) |
| 18 | 718/RS3 caches | out of scope | `r718`, `rs3` modules exist | **not-needed-for-map-editor** (OSRS-only product scope) |

### Summary verdict

**FileStore 2.4.19 has everything the map editor needs.** Zero rows require an
upgrade to close. Row 3 (map codecs) is intentionally RSPSi-owned; rows 14–17
are future-phase capabilities that already exist in the pinned artifact and
require only adapter work when their phase begins. The one true adapter debt
found (model-type pairing not surfaced through the neutral view) is fixed in
this milestone, not an upstream gap.

## Texture pixel contract

`DefinitionProvider.texturePixels(id, brightness, textureSize)` is frozen to
the client-compatible path FileStore exposes: **brightness `0.6`, size `128`**.
Other requested values return `Optional.empty()` rather than silently
returning different pixels than asked for. The 3D renderer work must consume
the 0.6/128 decode; if a future renderer needs another gamma, that is an
RSPSi-owned color utility concern, not an adapter change.

## Decode-failure diagnostics

Silent `catch (RuntimeException ignored)` blocks in
`OpenRuneDefinitionProvider` previously made a *corrupt* definition
indistinguishable from an *absent* one. The provider now records decode
failures and exposes them through `RevisionAudit.auditDefinitions`, so a
verifier run names which definition IDs failed to decode and why. Absent
families remain `WARN`; indexed-but-undecodable remains `FAIL` with the first
failing ID and exception message attached.

## Upgrade runbook (when 3.x is adopted, not before)

1. Update the pin in `Client/build.gradle` and record the new artifact hashes
   in `RESOURCE_CATALOG.md`.
2. Re-run the full local suite: `./gradlew foundationGate` must be green.
3. Re-run external parity: `verifyOsrsRevisionMatrix` with the operator
   manifest; all fixtures must report zero differences.
4. Re-run definition audits on a real cache; family counts must match the
   pre-upgrade run modulo upstream definition changes (which must be
   explained, not hand-waved).
5. Re-run the writable round-trip tests (`RSPSI_OSRS_WRITABLE_CACHE`) for both
   Displee-staged and OpenRune-direct output paths.
6. Re-check `verifyCacheBackendBoundary` / `verifyUiNeutralCore`: any new
   package renames must be absorbed inside the adapters only.
7. Update the version table at the top of this file and the adoption
   contract's pinned-version note in the same commit.

## Milestone outcomes driven by this audit (2026-09-17)

- **Read-path adapter gaps closed.** `ObjectDefinitionView` now carries the
  `modelTypes` ↔ `models` pairing validated in the OpenRune decoder, surfaced
  through `ObjectDefinitionSummary`/`ObjectInspectorSnapshot`; decode failures
  in `OpenRuneDefinitionProvider` are counted and reported through
  `RevisionAudit.auditDefinitions`; every store reports `backendName()`.
- **Legacy loader retirement.** The external `OSRSPlugin` service-loader
  plugin is removed. Its ten renderer loaders live in
  `com.rspsi.compat.osrs.OsrsCompatibilityLoaders` inside the Client
  compatibility boundary, still reading exclusively through the neutral
  `CacheIndexView`/`CacheArchiveView` seam; a build gate blocks renderer
  imports of the retired plugin package.
- **Native write parity.** `OsrsRegionEncoder` now fails fast on height delta
  byte 1 (the client decodes it as 0 — writing it silently erased an 8-unit
  height step), re-encodes shape-without-overlay-id tiles, and caps overlay
  ids at 254 legacy / 32767 modern to match decoder masks. A deterministic
  fresh-cache `CacheDelegate` round trip (`OpenRuneWritableRoundTripTest`)
  proves byte-level and semantic save→reopen equality through the production
  `OpenRuneCacheStore` with no external cache.
- **Plugin enable/disable.** `EditorPluginStateStore` persists user disable
  intent to `~/.rspsi/plugins.json`; `EditorPluginLifecycleManager` gates
  host initialization on it with transitive dependency cascade, rebuilds and
  rebinds the host on every toggle, and the JavaFX shell exposes a Plugins
  panel/workspace preset driven by the neutral manager.

## Out of scope for FileStore (confirmed boundaries)

Confirmed against this audit and unchanged from the adoption contract: FileStore
must not absorb the world model, map/location semantics, collision policy,
scene construction, commands/history, or any editor/plugin contract. The
OpenRune-Server route fixture remains blocked by that server checkout's
config-archive-55 expectation — a server-adapter compatibility task, not a
FileStore capability gap.
