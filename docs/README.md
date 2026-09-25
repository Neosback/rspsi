# OpenRune Studio Documentation Map

This directory is intentionally small in concept even when individual references are detailed. Each architectural concern has one primary owner.

## Authority order

When documents disagree, use this order:

1. production code and passing tests for current behavior;
2. AI_ARCHITECTURE_OVERVIEW.md for the whole-system model;
3. AI_CHANGE_PLAYBOOK.md for the official implementation route;
4. the concern-specific authoritative document below;
5. ROADMAP.md for future sequencing;
6. reference and acceptance documents for evidence and historical context.

A future plan never overrides current code. A reference project never overrides Studio's own architecture.

## Active architecture documents

| Document | Owns |
| --- | --- |
| AI_ARCHITECTURE_OVERVIEW.md | system boundaries, source of truth, major flows |
| AI_CHANGE_PLAYBOOK.md | deterministic change routes and anti-duplication rules |
| EDITOR_DEVELOPMENT_ARCHITECTURE.md | package placement, module composition, service boundaries |
| CACHE_EDITING_AND_PUBLISHING.md | edit state, save, autosave, standalone publish, OpenRune build handoff |
| RENDERING_SYSTEM.md | current render pipeline, ownership, performance work, parity gates |
| SCENE_SEMANTICS_REFERENCE.md | authored versus resolved scene semantics and RuneLite mapping |
| OPENRUNE_SERVER_INTEGRATION_MODEL.md | connected OpenRune project discovery, source authority, build and generated caches |
| UI_WORKSPACE_CONTRACT.md | rails, drawer, inspectors, HUDs, input and workspace state |
| PROJECT_LAUNCHER_AND_DASHBOARD.md | launcher, project descriptor, loading gate, project home |
| ROADMAP.md | prioritized execution order only |

## Reference and acceptance documents

These provide evidence or focused acceptance criteria. They do not create alternate architecture.

- OPENRUNE_MAVEN_CATALOG.md
- RUNELITE_REFERENCE_GUIDE.md
- TERRAINI_REFERENCE.md
- PHASE0_LUMBRIDGE_ACCEPTANCE.md
- MAP_STUDIO_1_0_ACCEPTANCE.md
- TEXTURE_PARITY_FIXTURE.md
- RENDERING_PARITY_MANIFEST.json
- PHASE0_PARITY_MANIFEST.example.json

## Status vocabulary

Use these words precisely:

- **Implemented**: production path exists and has relevant tests.
- **Observed**: seen in current manual testing but not proven across the acceptance matrix.
- **Planned**: approved direction, not current behavior.
- **Reference**: useful external or historical evidence, not a production dependency by itself.
- **Migration debt**: existing code that is supported temporarily but must not be copied for new work.

Do not write a planned design in present tense.

## Architectural compression rule

Documentation should reduce choices.

For every major responsibility, an agent should be able to answer:

- What owns this?
- What data is authoritative?
- What is the normal call flow?
- Where may I extend it?
- What must I not duplicate?
- What test proves the change?
- Is this current behavior or future work?

If a document cannot answer one of those questions and only repeats another document, merge the useful facts into the authoritative owner and remove the duplicate.

## Retired directions

Built-in features use core modules and shared services. Existing source names from earlier architecture experiments are migration debt until removed or renamed.

The following old document families were consolidated into the active documents above:

- separate content-studio foundation/status plans;
- separate system API and extension SDK plans;
- duplicate cache/OpenRune ownership documents;
- duplicate rendering future plans.

This avoids multiple documents giving slightly different instructions for the same responsibility.