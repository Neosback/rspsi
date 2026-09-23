# OpenRune Ecosystem Integration Architecture

## Purpose

OpenRune Studio should use the OpenRune ecosystem as a content toolchain, not treat
OpenRune-FileStore as only a byte reader. This document records the integration
decisions discovered while auditing the OpenRune organization before writable
definition editing.

The core rule is:

> Studio owns editor-neutral state, transactions, UX, validation and undo/redo.
> OpenRune owns OSRS codecs, definition builders, cache packing primitives and
> OpenRune-native source/reference formats.

Backend-specific types must remain behind `com.rspsi.cache.store` compatibility
adapters.

## Audited OpenRune repositories

### OpenRune-FileStore

Directly useful modules and concepts:

- immutable definition models such as `ObjectType`
- mutable builders such as `ObjectTypeBuilder` and `ObjectType.toBuilder()`
- `DefinitionCodec` / `BuilderDefinitionCodec`
- OSRS codecs such as `ObjectCodec`, `NPCCodec`, `ItemCodec`
- shared parameter support through `Parameterized` / `MutableParameterized`
- opcode 249 string, int and long parameter encoding
- opcode-definition framework:
  - `DefinitionOpcode`
  - `DefinitionOpcodeProperty`
  - `OpcodeList`
  - `OpcodeDefinitionCodec`
- `CacheDelegate` for explicit writable output caches
- packing/tooling for maps, models, sounds, MIDI, configs, DB tables, interfaces,
  CS2, gamevals and world-map data
- incremental build concepts such as `CacheTarget`
- gameval/reference tooling such as `GameValReferenceIndex`
- object DSL helpers
- RsConfig/TOML annotations and serializers used by definition types

### OpenRune-Server

Useful as the server/content half of Studio rather than a cache replacement.

Relevant findings:

- server-only definition codecs coexist with client/cache definitions
- gameval symbol sets cover objects, params, varbits, varps, enums, db rows/tables,
  client scripts, areas and other content families
- server/content definitions create a future bridge from a cache object to its
  gameplay behavior

Long-term Studio views should be able to show cache definition data and server
content bindings together without coupling the cache editor to the server runtime.

### openrune-toml-parser

This is more than a generic TOML parser. Relevant pieces include:

- `RsConfig`
- `RsTableHeaderBehavior`
- `TomlField`
- serializer/deserializer infrastructure
- constant replacement
- tokenized replacement
- RS table/post-decode support

This provides a route for a future source view next to visual editors. Studio
should not create a competing OpenRune project/config language.

### OpenRune-Developer-Tools / OpenRune-IntelliJ-Tools

These repositories should be checked before implementing developer-facing source,
symbol, project or authoring UX. Studio can provide a graphical workflow while
remaining compatible with OpenRune's existing development conventions.

### Other ecosystem repositories

`OpenRune-FileStore-Server`, `js5server`, bootstrap/launcher and central-server
projects are integration references for later live-server, cache-serving and
project workflows. They are not dependencies of the core editor today.

## Existing RSPSi/OpenRune integration we must reuse

The repository already contains important foundations:

- `OpenRuneCacheStore.open(...)` is the canonical read-only OSRS path.
- `OpenRuneCacheStore.openWritable(...)` wraps OpenRune `CacheDelegate` and is
  deliberately opt-in.
- `CacheStore` is the neutral byte-oriented boundary.
- source and output cache paths are intentionally separated.
- `OpenRuneWritableRoundTripTest` proves explicit output-cache map writes survive
  flush, close and reopen.
- `OpenRuneDefinitionProvider` reduces OpenRune definition types to neutral
  RSPSi-owned views.
- `DefinitionProvider.objectRaw(...)` exposes read-only raw object metadata
  without leaking OpenRune classes.

Definition editing must extend these boundaries, not bypass them.

## Definition-edit architecture

Target flow:

```text
Studio UI / plugin
       |
       v
neutral DefinitionEditTransaction
       |
       v
DefinitionProvider preview/apply capability
       |
       v
OpenRune adapter
  ObjectType.toBuilder()
       |
       v
ObjectTypeBuilder mutations
       |
       v
ObjectCodec.encode(...)
       |
       v
decode encoded bytes again
       |
       v
semantic round-trip validation
       |
       +---- preview only (current phase)
       |
       +---- explicit dirty/output-cache commit (future phase)
```

The source cache must remain read-only. A future save operation must target an
explicitly selected output/cache project and use the existing writable
`CacheStore` path.

## Transaction model

Definition edits should be represented as backend-neutral typed mutations instead
of UI controls calling OpenRune builders directly.

Initial mutation families:

- set scalar field
- set opcode-249 parameter
- remove opcode-249 parameter

The value system must preserve at least:

- string
- int
- long
- boolean

Object support should start with safe scalar fields and params, then expand to
lists/actions/transforms after round-trip fixtures exist for each family.

## Undo/redo

RSPSi already has `CommandHistory`, `EditorCommand` and atomic command helpers
for map editing. Definition edits should eventually participate in the same
user-visible history model rather than creating a second undo system.

The definition transaction introduced before UI editing is therefore an immutable
description of intent. Applying it to project state can later be wrapped by an
`EditorCommand`.

## Parameters

Opcode 249 is not object-specific. OpenRune models parameterized definitions through
shared `Parameterized` / `MutableParameterized` contracts.

Studio should build one reusable parameter editor that can later serve objects,
NPCs, items, structs and any other parameterized definition family.

OpenRune supports opcode-249 values of:

- int
- string
- long

Param IDs are unsigned-medium values and must remain in `0..0xFFFFFF`.

## Gamevals and symbolic references

Numeric IDs should remain canonical, but Studio should resolve gameval/symbolic
names where available.

Examples:

```text
Object       1276  rocks_copper
Animation    8321  mining_pickaxe_swing
Param         451  mining_level
```

The asset browser, definition editors, content bindings and source editor should
share the same symbolic-name/reference service.

## Source + visual editing

Long-term authoring should support two coordinated representations:

```text
Visual editor <-> neutral content model <-> RsConfig/TOML source
                         |
                         v
                  OpenRune codecs
                         |
                         v
                    output cache
```

Do not build a second proprietary source format where OpenRune's RsConfig/TOML
model already fits the requirement.

## Incremental build/save direction

A save should not blindly repack the full cache. The desired pipeline is:

```text
edited definitions/resources
        |
        v
dirty registry
        |
        v
family codec/packer
        |
        v
incremental cache target
        |
        v
reference/gameval updates when required
        |
        v
explicit output cache/project
```

OpenRune's existing packers, `CacheTarget`, gameval references and cache delegate
should be evaluated before implementing each corresponding Studio subsystem.

## Client definition + server content convergence

A professional content studio should eventually allow a selected world object to
surface both cache/client data and gameplay bindings.

Example:

```text
Rocks [1276]

CACHE
  models
  size
  actions
  animation
  params

SERVER
  Mining
    ore: copper
    level: 1
    respawn: 4 ticks

REFERENCES
  map placements
  scripts
  gamevals
```

This should be implemented through modules/adapters, not by making the map renderer
depend directly on OpenRune-Server.

## Guardrails

1. No `dev.openrune.*` types in neutral editor APIs.
2. The source cache remains read-only.
3. Preview/validation happens before output-cache mutation.
4. Every writable definition family requires encode/decode round-trip fixtures.
5. Complex fields are not enabled for editing until their codec semantics are
   covered by tests.
6. Reuse OpenRune builders/codecs/packers instead of manually emitting opcodes.
7. Reuse RsConfig/gamevals rather than inventing competing source/symbol formats.
8. Definition edits eventually join the existing RSPSi undo/redo history.
