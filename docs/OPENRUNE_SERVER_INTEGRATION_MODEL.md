# OpenRune Server Integration Model

> **Status:** authoritative connected-project integration blueprint.
>
> OpenRune Studio may understand an imported OpenRune project deeply, but it does not become a competing cache-build authority.

## 1. Integration goal

A connected project should feel native:

    Studio project
      -> imported OpenRune checkout
      -> structural/source inspection
      -> read-only generated cache roles
      -> Studio authored edits
      -> supported source publication
      -> imported project's own build
      -> verified generated outputs

There is one project identity and one detected project model.

Workspaces do not reconnect or rediscover the checkout independently.

## 2. Import identity

Structural detection should establish:

- checkout root;
- project/build files;
- revision/environment metadata when available;
- LIVE/SERVER cache-role paths;
- source/resource roots;
- known GameVal/RSCM roots;
- build task availability;
- compatibility diagnostics.

Project identity must not permanently depend on one absolute external path. Relocation should update the descriptor without creating a new logical Studio project.

## 3. Compatibility is multi-dimensional

Do not reduce compatibility to one revision integer.

Track separately:

| Dimension | Question |
| --- | --- |
| cache format | can Studio's FileStore boundary read the cache? |
| game revision | do the selected codecs/semantics match this cache revision? |
| protocol/runtime | does the imported server target a compatible protocol revision? |
| source/tooling | can Studio understand the project's source/build conventions? |
| publication | does Studio have a lossless source representation for this resource? |

A project may be structurally valid even when one capability is unsupported.

Use diagnostics rather than disabling all integration because one dimension differs.

## 4. Embedded versus imported toolchain

Studio has its own pinned OpenRune FileStore dependency for its cache adapter.

The imported project has its own declared build/tool dependencies.

Do not silently change Studio dependencies at runtime to match the checkout.

Connected publication should run through the imported project's own detected build entry point so its build uses its own dependency graph.

A future isolated tooling bridge may be useful for version-specific operations, but it should not become a second in-process dependency universe.

## 5. Generated cache roles

Canonical stock paths are:

- .data/cache/LIVE
- .data/cache/SERVER

Studio policy:

### LIVE

- read-only generated client-facing cache;
- primary input for map/scene/client-definition semantics;
- never a fallback direct-write target.

### SERVER

- read-only generated server-oriented cache;
- contains/minimizes data according to OpenRune's server build;
- may contain server-enriched definitions and server map files;
- never substituted for LIVE rendering semantics.

The two roles are intentionally different.

## 6. Verified OpenRune cache lifecycle

Current OpenRune Server source implements a normal build that:

1. loads GameVals/source inputs;
2. discovers content packs and ordered cache tasks;
3. builds/updates LIVE incrementally;
4. seeds/builds SERVER from LIVE for its separate pass;
5. runs server-only pack work;
6. finalizes generated GameVals/DB/enums/code as required.

Current OpenRune tooling also keeps separate incremental state for the two cache roles.

The LIVE path uses fingerprint-oriented verification. The SERVER path uses output-oriented verification.

Therefore Studio must not patch either generated cache independently and then assume OpenRune's incremental state remains trustworthy.

## 7. Fresh install

Fresh install/bootstrap is not normal project open.

It may:

- acquire a baseline cache;
- construct/refresh generated cache directories;
- reset incremental state;
- regenerate base mapping data.

Studio must never invoke it automatically against an existing imported project.

A user-requested reset/bootstrap action must be explicit and clearly destructive.

## 8. Source-of-truth matrix

Studio writes the source OpenRune itself consumes.

| Resource | Authoritative source | Generated output | Studio policy |
| --- | --- | --- | --- |
| client terrain/location map archives | no general stock text source confirmed | LIVE map files 0/1 | save in Studio; connected publish disabled until supported source/build hook exists |
| NPC map spawns | .data/raw-cache/map/npcs/*.toml | SERVER map file 5 | structured source edit + normal build |
| ground-object map spawns | .data/raw-cache/map/objs/*.toml | SERVER map file 6 | structured source edit + normal build |
| map areas | .data/raw-cache/map/area/*.toml | SERVER map file 7 | source polygon edit + normal build |
| server definition overlays | .data/raw-cache/server/**/*.toml and module config sources | SERVER definitions | schema-aware source edit + normal build |
| loc/NPC examines | .data/raw-cache/examines/*.csv | SERVER data | edit source |
| module custom GameVals | originating module gamevals.toml | merged mappings | edit origin + merge/build |
| cache-derived base GameVals | generated/dumped base data | mapping providers | inspect, do not hand-edit as ordinary authored source |
| merged/generated mappings | .data/gamevals and generated outputs | provider inputs | write only when provenance says the file is authoritative |
| interfaces/components | cache + source + mappings depending on resource | LIVE/SERVER/runtime | use actual source authority, do not assume one text format |
| teleports/content behavior | often Kotlin source and/or params | runtime behavior | semantic inspection first, edit only through supported source form |

Generated outputs are never fallback source.

## 9. GameVal/RSCM provenance

Treat symbols as source-aware entries, not a flat name-to-ID map.

Conceptual entry:

    GameValEntry
      namespace
      name
      id
      sourceFile
      sourceKind
      generated
      generation

OpenRune merges several mapping sources and may know which file supplied an entry.

Studio should preserve that provenance so edits target the origin rather than blindly modifying a merged output.

## 10. Project metadata hints

When the project provides tooling metadata describing mapping/source roots, use it as a discovery hint.

Do not hardcode only one stock directory shape if the inspected project supplies explicit configuration.

Stock conventions remain defaults, not proof that every fork is identical.

## 11. Map Studio integration

Map Studio can present one coherent world while preserving multiple source authorities.

Potential overlays:

- LIVE terrain and locations;
- OpenRune NPC spawns;
- OpenRune ground-object spawns;
- OpenRune area polygons;
- source-derived content relationships;
- later runtime observations.

Each selectable entity must retain provenance.

Example:

    NPC spawn
      -> semantic identity
      -> source file/span
      -> related definition/content
      -> edit source
      -> validate
      -> build
      -> reload SERVER

The visible world can be unified without pretending every entity saves to the same place.

## 12. Terrain/location publication gap

This is intentionally explicit.

Studio has a canonical terrain/location decoder and encoder and can save arbitrary map edits into its own project state.

What is not yet established is a general stock OpenRune source form for arbitrary client terrain/location archives that the standard build consumes as authored source.

Until that exists or Studio integrates an explicit supported build hook:

- connected map edits remain saveable inside Studio;
- preview/rendering uses the Studio authored state;
- standalone output-cache publication can be offered;
- direct connected LIVE patching is prohibited.

Do not solve this gap by bypassing OpenRune's build ownership.

## 13. Server-enriched definitions

OpenRune's SERVER build combines client definitions with server-specific source data for multiple domains.

Studio may inspect the result and show authored provenance.

It should not copy the server's pack ordering/merge implementation into Studio.

Generated SERVER decoding is valuable as verification:

    source edit
      -> OpenRune build
      -> decode SERVER result
      -> compare expected semantic effect

The generated result verifies source publication, it does not replace source authority.

## 14. Source semantics

Studio can extract neutral facts from Kotlin/config source for navigation and low-code workflows.

Keep source-parser/compiler implementation details private to the OpenRune integration layer.

Neutral consumers receive facts such as:

- declaration identity;
- symbolic reference;
- handler/content relationship;
- source span;
- evidence kind;
- confidence/diagnostic state.

Do not expose parser AST/PSI nodes as general Studio domain types.

## 15. Structural project model

One inspected-project model should own:

- project directories;
- build files;
- source sets/resources;
- task paths;
- cache-role paths;
- explicit overrides;
- source roots;
- compatibility diagnostics.

Do not create separate stock-layout and UI-specific project graphs that can disagree.

Expensive build-model evaluation belongs to an explicit trusted workflow after structural project open, not the launcher list.

## 16. Stale-source protection

Before publishing source changes:

1. capture baseline source identity/fingerprint during inspection/load;
2. compare again immediately before write;
3. if changed externally, abort;
4. present reconcile/reload options;
5. never silently overwrite external edits.

After a successful source write/build, update baselines only after verification.

## 17. Build invocation

Use the imported project's detected wrapper/task path.

Do not hardcode one module name when project inspection has discovered the actual task.

Studio should capture:

- command/task;
- start/end status;
- exit result;
- relevant log;
- generated cache identity before/after;
- verification result.

Build failure is not publication success.

## 18. Synchronization model

Watch separate generations for:

- source tree/resources;
- GameVal/mapping data;
- LIVE;
- SERVER.

A LIVE change may stale cache-backed Map/Object workspaces.

A SERVER change may stale server-semantic views without requiring the map renderer to discard correct LIVE state.

If Studio has unsaved edits, external generated-cache changes require explicit reconcile behavior.

## 19. Running-server bridge

A runtime bridge is later work.

If implemented, it should expose intentionally bounded development information such as:

- server state;
- tick/time;
- loaded semantic entities;
- selected runtime observations;
- controlled reload/build operations where safe.

Do not make the editor depend on an always-running server for ordinary map authoring.

## 20. Fork compatibility

Support capabilities, not brand-name guesses.

A fork may vary:

- module names;
- task paths;
- source roots;
- cache paths via explicit overrides;
- available source formats;
- build dependency versions.

When Studio cannot prove a capability, report it as unavailable rather than guessing paths.

Fork-specific knowledge belongs in bounded profiles/adapters, not accumulated path heuristics throughout the application.

## 21. Publication flow

Supported connected resource:

    Studio authored state
      -> source authority lookup
      -> stale check
      -> validate
      -> atomic source write
      -> imported project build
      -> generated LIVE/SERVER changes
      -> reopen
      -> semantic verification
      -> publication baseline

Unsupported resource:

    Studio authored state
      -> Save Project
      -> preview
      -> remain unpublished
      -> optional standalone output route

## 22. Acceptance

Connected OpenRune support is trustworthy when:

1. import identifies cache roles without modifying them;
2. project open never runs fresh install implicitly;
3. LIVE is used for client scene semantics;
4. SERVER is used only for server-oriented semantics;
5. source provenance is retained;
6. stale external source blocks overwrite;
7. arbitrary terrain/location changes do not patch LIVE as fallback;
8. supported source edits invoke the detected project build;
9. failed build does not advance publication baseline;
10. successful build reopens/verifies generated output;
11. custom layouts use the same inspected-project model with explicit diagnostics.