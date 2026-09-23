# RSPSi

RSPSi is being stabilized into an OSRS/OpenRune-focused desktop map editor.
The existing JavaFX editor and renderer remain the compatibility surface while
the cache, world model, editing history, scene, and UI contracts move behind
small RSPSi-owned APIs.

The supported build/runtime baseline is Java 21 with JavaFX 21. Gradle selects
the Java 21 toolchain for all modules so a newer system JDK is not used to run
the JavaFX desktop application accidentally.

## Start here

- [Agent/contributor orientation - module layout, build commands, conventions](AGENTS.md)
- [Roadmap and current priorities](docs/ROADMAP.md)
- [Project launcher, startup lifecycle, and Dashboard contract](docs/PROJECT_LAUNCHER_AND_DASHBOARD.md)
- [RuneLite/deob source lookup guide](docs/RUNELITE_REFERENCE_GUIDE.md)
- [Rendering parity tracker (machine-readable gap list)](docs/RENDERING_PARITY_MANIFEST.json)
- [Terraini reference notes (algorithms, not vendored source)](docs/TERRAINI_REFERENCE.md)

The `docs/` folder was reset to a single living roadmap on 2026-09-21 - the previous
audit-trail documents were retired rather than kept as a growing pile of point-in-time
snapshots. `docs/ROADMAP.md` is meant to be edited in place as work lands.

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

Real caches stay outside the repository. `RuneLite-melxin/` at the repo root is a genuine,
BSD 2-Clause-licensed reference source tree (see `AGENTS.md`) kept for verifying OSRS-accurate
behavior - it is not a build dependency and no external project becomes the RSPSi base.
Resources with unclear licensing (e.g. decompiled third-party output) are deliberately kept
out of the repository even for reference - see `docs/TERRAINI_REFERENCE.md` for how that's
handled instead (written notes, not vendored files).

`foundationGate` is the required local/CI baseline. Without
`RSPSI_OSRS_CACHE`, it runs the deterministic fixture suite and reports real
cache parity as pending; release verification supplies an explicitly selected
cache and, when required, `RSPSI_OSRS_REQUIRE_PARITY=true`.
