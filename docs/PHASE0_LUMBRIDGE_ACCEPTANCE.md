# Phase 0 Lumbridge Object-Resolution Acceptance

> **Purpose:** reproducible real-cache acceptance for the Phase 0 missing/null/invisible-object work.
>
> This is an execution recipe, not a bundled fixture. Third-party/Jagex cache bytes remain external to the repository.

## Pinned reference

The current pinned acceptance cache is:

- OpenRS2 cache ID: `2710`
- Scope: `runescape`
- Game: `oldschool`
- Environment: `live`
- Language: `en`
- OSRS build/revision: `240`
- Source: Jagex via OpenRS2
- Source timestamp: `2026-09-16T10:30:13Z`
- Region under test: `50,50` (world tiles 3200..3263 x 3200..3263)

OpenRS2 cache page:

    https://archive.openrs2.org/caches/runescape/2710

Do not commit the downloaded cache.

## Why the count is not hardcoded

The acceptance must never encode a guessed statement such as "there are 6 bushes" or "there are 8 bushes."

The selected cache is the source of authored placements. The verifier decodes every location in region 50,50, then the object-resolution audit accounts for every placement through:

    authored placement
      -> placed definition
      -> default editor transform (when present)
      -> display definition
      -> loc-shape/model selection
      -> model geometry availability
      -> neutral scene projection
      -> ModelRenderPacket submission

Successful transformed placements are printed as `object transform:` lines. Problems are printed as `object resolution:` lines.

A separate external `locations.json` fixture can additionally prove the decoded location list against an independently exported reference scene, but it is not required to derive the count from the cache itself.

## Execution

Download/extract the OpenRS2 disk cache so the path points at its `cache` directory, then run:

    RSPSI_OSRS_CACHE=/absolute/path/to/cache \
    RSPSI_OSRS_REGION_X=50 \
    RSPSI_OSRS_REGION_Y=50 \
    RSPSI_OSRS_REVISION=240 \
    ./gradlew verifyOsrsRevision

The verifier must report the `object.resolution` check.

## Acceptance

Phase 0 object-resolution acceptance for this region requires:

1. the cache opens as OSRS revision 240;
2. region 50,50 terrain and locations decode;
3. every authored object receives an `ObjectSceneResolutionAudit.Entry`;
4. no selected model references missing geometry;
5. no geometry-ready object silently fails to produce a model packet;
6. no canonical authored object disappears from the neutral scene projection;
7. transformed objects retain the placed `SceneObjectIdentity` while their visible model resolves through the display definition;
8. client sentinel/blank names are presented as safe ID-based labels rather than literal `null`;
9. every warning is reviewed as an intentional state-dependent/no-model case rather than ignored;
10. the actual authored Lumbridge bush/multiloc placements are counted from the decoded region and all relevant placements are accounted for by transform/submission diagnostics.

Camera-dependent occlusion is not part of this static audit. That is validated through scene visibility/picking fixtures and live Studio validation because it depends on view state.

## Optional independent location fixture

When a provenance-controlled RuneLite/OpenRune/other independent export is available, place `locations.json` in the external parity fixture directory and run through the normal strict parity manifest.

`OsrsLocationSemanticFixture` compares the complete canonical placement list, including ID, type, rotation, plane, X, and Y. Do not create a special bush-only location decoder when the complete location fixture already exists.

## Evidence recording

For a release/merge validation, record in the PR:

- cache ID and fingerprint
- revision
- region
- `object.resolution` status
- authored/submitted/transformed/warning/failure counts
- transformed lines relevant to the observed Lumbridge objects
- any warning/failure diagnostic lines
- whether independent `locations.json` parity was also supplied
