# RSPSi

RSPSi is being stabilized into an OSRS/OpenRune-focused desktop map editor.
The existing JavaFX editor and renderer remain the compatibility surface while
the cache, world model, editing history, scene, and UI contracts move behind
small RSPSi-owned APIs.

The supported build/runtime baseline is Java 21 with JavaFX 21. Gradle selects
the Java 21 toolchain for all modules so a newer system JDK is not used to run
the JavaFX desktop application accidentally.

## Start here

- [Roadmap and progress ledger](docs/ROADMAP.md)
- [Product design](docs/PRODUCT_DESIGN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Resource catalog and provenance](docs/RESOURCE_CATALOG.md)
- [OpenRune compatibility evidence](docs/OPENRUNE_COMPATIBILITY.md)
- [External cache verification record](docs/EXTERNAL_CACHE_VERIFICATION_2026-09-17.md)
- [Manual smoke checklist](docs/MANUAL_SMOKE_TEST.md)
- [Scene semantics and frontend/plugin contract](docs/SCENE_SEMANTICS.md)
- [OSRS scene pipeline and plugin attachment points](docs/OSRS_SCENE_PIPELINE.md)
- [RuneLite scene reference for Studio tooling](docs/RUNELITE_SCENE_REFERENCE.md)
- [Scene rendering cross-reference: RuneLite, TSPS, and Environment Exporter](docs/SCENE_RENDERING_CROSS_REFERENCE.md)
- [OSRS Environment Exporter scene/render reference](docs/OSRS_ENVIRONMENT_EXPORTER_REFERENCE.md)
- [Vertical feature/plugin architecture](docs/PLUGIN_ARCHITECTURE.md)
- [Dear ImGui adapter boundary](docs/IMGUI_ADAPTER.md)
- [Foundation completion audit](docs/FOUNDATION_AUDIT_2026-09-17.md)
- [Ideas and future feature possibilities](docs/IDEAS.md)

## Verification

```text
./gradlew foundationGate
```

To inspect an explicitly selected OSRS cache without modifying it:

```text
RSPSI_OSRS_CACHE=/path/to/cache \\
RSPSI_OSRS_REGION_X=16 RSPSI_OSRS_REGION_Y=33 RSPSI_OSRS_REVISION=240 \\
./gradlew verifyOsrsRevision
```

Instance parity uses a separately exported reference fixture:

```text
RSPSI_OSRS_CACHE=/path/to/cache \\
RSPSI_OSRS_INSTANCE_FIXTURE=/tmp/instance.json RSPSI_OSRS_REVISION=240 \\
./gradlew verifyOsrsInstance
```

Real caches and external research checkouts stay outside the repository. No
external project becomes the RSPSi base, and resources marked for license
review are not bundled or added as runtime dependencies.

`foundationGate` is the required local/CI baseline. Without
`RSPSI_OSRS_CACHE`, it runs the deterministic fixture suite and reports real
cache parity as pending; release verification supplies an explicitly selected
cache and, when required, `RSPSI_OSRS_REQUIRE_PARITY=true`.
