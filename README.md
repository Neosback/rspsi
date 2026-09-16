# RSPSi

RSPSi is being stabilized into an OSRS/OpenRune-focused desktop map editor.
The existing JavaFX editor and renderer remain the compatibility surface while
the cache, world model, editing history, scene, and UI contracts move behind
small RSPSi-owned APIs.

## Start here

- [Roadmap and progress ledger](docs/ROADMAP.md)
- [Product design](docs/PRODUCT_DESIGN.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Resource catalog and provenance](docs/RESOURCE_CATALOG.md)
- [OpenRune compatibility evidence](docs/OPENRUNE_COMPATIBILITY.md)
- [Manual smoke checklist](docs/MANUAL_SMOKE_TEST.md)

## Verification

```text
./gradlew test check verifyOsrsRevision
```

To inspect an explicitly selected OSRS cache without modifying it:

```text
RSPSI_OSRS_CACHE=/path/to/cache \\
RSPSI_OSRS_REGION_X=16 RSPSI_OSRS_REGION_Y=33 RSPSI_OSRS_REVISION=240 \\
./gradlew verifyOsrsRevision
```

Real caches and external research checkouts stay outside the repository. No
external project becomes the RSPSi base, and resources marked for license
review are not bundled or added as runtime dependencies.
