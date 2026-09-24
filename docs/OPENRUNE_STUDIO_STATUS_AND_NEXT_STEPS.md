# OpenRune Studio Integration — Status and Remaining Plan

This document is the handoff point for the first-class OpenRune Studio work completed through the project-launcher/content-home milestone.

It records what is implemented, which safety/architecture decisions are now contractual, and which planned capabilities remain intentionally unfinished.

## Current product lifecycle

The application now follows the project-first lifecycle:

```
APPLICATION START
  -> PROJECT LAUNCHER
  -> PROJECT LOADING
  -> PROJECT SHELL
  -> DASHBOARD / CONTENT HOME
  -> Map Studio / Interface Studio / Object Studio
```

The launcher is the only pre-project home. The Dashboard is the in-project home.

Two first-class project kinds exist:

- `OPENRUNE_SERVER`
- `STANDALONE_OSRS_CACHE`

Standalone cache projects remain a supported product path. OpenRune is first-class, not mandatory.

## Completed OpenRune foundation

### 1. Authoritative project inspection

`OpenRuneServerAdapter` / `ServerProjectInspection` are the authoritative project model for:

- project detection;
- LIVE/SERVER/raw-cache/GameVal/content paths;
- saved path/task overrides;
- revision diagnostics;
- project fingerprints;
- build tasks;
- runtime-plugin inventory;
- Git state;
- capabilities.

The first-party OpenRune integration session retains the exact neutral `ServerConnection` instead of re-discovering a stock layout.

Structural OpenRune support is no longer disabled merely because the cache revision is outside the currently verified cache profile.

### 2. Real Gradle project model

A trusted/opened project evaluates its own Gradle wrapper and records a neutral model containing:

- projects/modules;
- project directories and build files;
- source sets;
- source/resource/output roots;
- project dependencies;
- applied Gradle plugin classes;
- actual task paths.

Passive project browsing does not execute Gradle.

Custom layouts and renamed modules/tasks are therefore supported without pretending every server matches stock OpenRune.

### 3. Kotlin/OpenRune semantic source index

Production Kotlin source roots are indexed with Kotlin PSI.

The neutral index records:

- class/function/call facts;
- `PluginScript`;
- `QuestScript`;
- handler registrations such as `onOpContentLoc1`;
- quest constructor metadata;
- var bindings;
- symbolic references;
- exact file/offset/line/column provenance;
- project/source-set/package metadata.

The Kotlin compatibility pin is explicit and currently matches the bundled OpenRune Kotlin 2.2.0 toolchain.

This is structural PSI evidence, not full K2/FIR cross-module type resolution.

### 4. Semantic Content Graph

Studio now has one read-only cross-source relationship graph joining:

- Kotlin PSI facts;
- GameVals/RSCM;
- TOML/JSON references;
- scripts;
- quests;
- handlers;
- vars;
- symbols;
- source/resources.

The graph preserves evidence on nodes/edges and keeps unresolved knowledge explicit.

OpenRune `obj.*` aliases canonicalize to Studio's neutral `item.*` identity while preserving provenance.

The neutral RSCM namespace set includes the important server mappings present in the bundled project, including:

- loc / npc / obj-item;
- varbit / varp / varc-varcon;
- content;
- dbtable / dbrow;
- stat;
- param;
- interface / component / clientscript;
- area / seq / spotanim;
- bas / category / controller / currency / enum;
- font / headbar / hitmark / mesanim / midi;
- projanim / queue / stalk / synth / timer;
- varn / varobj / walktrigger.

### 5. Map object -> server-content bridge

OpenRune source-controlled `[[object]]` TOML overlays are indexed structurally.

For a resolved world/map object ID, the graph can represent:

```
WorldObject.id
  -> loc.* identity
  -> authored [[object]]
  -> inherit loc.*
  -> contentGroup content.*
  -> param.*
  -> symbolic param value
  -> Kotlin handler(s)
```

This intentionally uses the authored server overlay as the semantic source of truth instead of guessing server `contentGroup` behavior from the LIVE/client object definition.

Field/block provenance is retained.

### 6. Object Content inspector

The existing Studio inspector system can now project selected map-object server semantics:

- Server symbol;
- Content group;
- Inherits;
- matching Kotlin handlers;
- server params;
- authored source;
- source provenance;
- explicit editing state.

It remains read-only until a verified edit lens exists.

### 7. Project-first launcher and loading gate

Startup no longer treats a raw cache path as the product identity.

The project launcher supports:

- recent projects;
- pin/remove;
- missing-project visibility/repair path;
- OpenRune server projects;
- standalone cache projects;
- persistent descriptors;
- explicit project integration capabilities.

Opening a project passes through a dedicated loading gate before the Dashboard/workspaces become available.

The project loading contract includes:

- descriptor read;
- project validation;
- OpenRune integration inspection;
- cache-role resolution;
- cache filesystem open;
- definition preparation;
- required project-service binding;
- ready/failure state.

### 8. Integration policy

OpenRune project descriptors persist explicit permissions and expose presets:

- Inspect;
- Author;
- Managed Build;
- Developer.

These are conveniences over granular capabilities.

`FRESH_CACHE_RESET` remains explicit and is not implied by normal presets, including Developer.

### 9. OpenRune Dashboard / Content Home

For OpenRune projects the Dashboard is now a real project/content home, not a cache picker.

It surfaces current project intelligence such as:

- Gradle module count;
- scripts and handler count;
- quests;
- authored object-content overlays;
- GameVals/symbols;
- declarative references;
- NPC spawns;
- semantic graph nodes/relationships;
- LIVE cache health;
- SERVER cache discovery;
- Kotlin source-index health;
- build tooling;
- integration policy;
- Git/project identity;
- workspace entry points;
- Integration & Build entry point.

Standalone projects receive a simpler cache-focused project home.

## Important safety contracts already established

1. Connected OpenRune LIVE/SERVER caches are generated outputs and are not normal Studio write targets.
2. Never auto-run FreshCache when connecting an existing project.
3. Connected project publication must be source-first and use the project's canonical build.
4. An authored source path or `writableSource=true` is provenance only; it does not grant mutation authority.
5. Graph connectivity is not proof of editability.
6. Kotlin PSI/compiler implementation classes do not escape the OpenRune adapter boundary.
7. Passive browsing must not execute Gradle.
8. Custom OpenRune layouts should be understood through the evaluated Gradle model and saved overrides rather than hard-coded stock paths.
9. If Studio cannot map a resource losslessly back to authored OpenRune source, integrated publishing remains disabled for that resource.

## Remaining OpenRune work

The following items are planned but intentionally not completed in the current milestone.

### A. First verified OpenRune edit lens

Implement one narrow, lossless authored-TOML edit first, such as an object overlay field:

- capture expected source fingerprint/span;
- verify the field still matches the indexed source;
- perform structured TOML mutation while preserving unrelated source;
- validate the new source;
- invoke the project's canonical Gradle build when policy permits;
- verify generated LIVE/SERVER results;
- update graph/provenance only after verification;
- support undo/revert at the project transaction layer.

Do not begin with arbitrary Kotlin rewriting.

### B. Better structured TOML/RsConfig editing

Move beyond the current structural object-overlay reader toward reusable lossless configuration adapters for:

- object/NPC packs;
- DB rows/tables;
- params;
- vars;
- GameVals source;
- other supported pack resources.

Prefer source `gamevals.toml` as the authored write target when it exists; RSCM remains primarily a mapping/resolution artifact.

### C. K2/FIR semantic enrichment

Add resolved Kotlin semantics where they materially improve correctness:

- cross-module symbol identity;
- overload/type resolution;
- handler target resolution;
- recognized DSL transformations;
- safer source-navigation/refactoring metadata.

PSI structural facts remain valid provenance and should be enriched rather than replaced.

### D. Quest/content-flow graph

Extend the graph with higher-level quest/content semantics:

- quest stages;
- requirements;
- rewards;
- var transitions;
- journal/state relationships;
- handler/event edges;
- runtime-observed branches when static analysis cannot prove them.

The UI must distinguish statically inferred relationships from runtime-observed behavior.

### E. CS2 / interface relationships

Add a real consumer before enabling compiler dependencies broadly.

Planned relationships include:

- CS2 source/compiled script;
- interface/component references;
- varp/varbit reads/writes;
- server source state relationships;
- graph navigation from UI -> script -> server content.

### F. Studio runtime bridge

Use an isolated/local OpenRune server process and a versioned Studio bridge rather than loading arbitrary server classes into the Studio JVM.

Initial runtime features:

- loaded plugin/script inventory;
- GameVals/revision/runtime compatibility;
- vars;
- instances;
- NPC state;
- handler/event trace.

Later simulation:

- isolated test player;
- actual interaction invocation;
- tick/action trace;
- XP/item/state changes;
- depletion/respawn;
- quest-state transitions.

Simulation should use a dedicated development/test runtime because arbitrary plugin hot-reload side effects are not always reversible.

### G. NPC/player/GFX/projectile semantic simulation

The content tooling can later render and inspect:

- NPC definitions/state;
- fake/test player state;
- animations;
- spot animations/GFX;
- projectiles;
- server-tick-driven previews.

This should attach to the same semantic graph/runtime bridge rather than become a separate content model.

### H. Generated SERVER-cache verification

Decode generated SERVER-cache definitions as a verification/fallback layer.

Generated data must not overwrite or erase authored-source provenance.

### I. Project creation/bootstrap enhancements

The launcher lifecycle is in place. Future project creation can add:

- clone/create OpenRune server project;
- template/version selection;
- target revision;
- integration-policy selection;
- safe initial build/bootstrap;
- richer repair/migration flow;
- project compatibility report.

The server checkout should remain a real Git/Gradle project usable from IntelliJ/CLI. Studio should not absorb OpenRune engine code into its own application.

### J. Dashboard evolution

The new Content Home is the foundation, not the final endpoint.

Future cards/actions can include:

- dirty/unpublished authored changes;
- build/publish status;
- source/index freshness;
- runtime bridge status;
- recent content objects/quests/scripts;
- build failures and generated-output verification;
- quick navigation to content facets;
- project compatibility/capability report.

Keep setup/cache selection in the launcher/settings/integration surfaces rather than turning the Dashboard back into setup UI.

## Recommended resumption order

When OpenRune work resumes, continue one PR at a time:

1. first verified TOML edit lens;
2. build + generated-output verification for that lens;
3. reusable structured declarative adapters;
4. K2 semantic enrichment;
5. quest/content-flow graph;
6. CS2/interface graph integration;
7. runtime bridge;
8. simulation and richer content authoring.

This keeps the current source-first/provenance-first architecture intact while progressively enabling low-code authoring.
