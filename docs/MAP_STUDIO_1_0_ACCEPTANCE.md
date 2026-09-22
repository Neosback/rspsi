# Map Studio 1.0 foundation acceptance

This checklist is the final acceptance boundary for PR #6. Deterministic CI and real
revision-240 texture parity are complete. One live native-winding check remains before merge.

## 1. Foundation gate

Required:

```bash
./gradlew foundationGate
```

The gate must be green at the exact PR head.

## 2. Revision-240 texture parity - complete

The external texture gate is implemented and has passed against OpenRS2 archive 2710
(oldschool/live/en build 240, Jagex source timestamp 2026-09-16 10:30:13).

Independent reference path:

- vendored RuneLite `TextureLoader` / `TextureManager` / `SpriteManager`;
- texture 0 selected as ordinary opaque;
- texture 7 selected as RGB-zero cutout;
- texture 17 selected as animated;
- 128x128 pixels at brightness 0.6;
- metadata plus full-pixel SHA-256 and semantic pixel counts exported.

RSPSi result:

- requested revision: 240;
- detected cache revision: 240;
- textures compared: 3;
- differences: 0;
- `texture.parity: PASS`;
- GitHub Actions run: 35718110830;
- artifact: `revision-240-texture-parity`.

`textures.definitions` is therefore covered. See `docs/TEXTURE_PARITY_FIXTURE.md` for the
repeatable manual workflow.

## 3. Native winding / culling parity - final remaining P0

RuneLite-melxin `Model.draw0` is the source reference:

- `edge <= 0` is stored as the culled-face flag;
- only `!culled` faces are drawn;
- therefore client-visible projected faces have `edge > 0`;
- Studio's software renderer uses the same edge-expression sign;
- converting the Y-down software viewport to OpenGL Y-up maps client-visible faces to GL_CCW.

Normal editing remains **Two Sided** until this live acceptance is complete.

In Studio Preferences, find **Native back-face culling** and compare:

1. **Two Sided**
2. **Client Front** - GL_CCW through `BackfacePolicy.nativeWinding()`
3. **Reversed Debug** - opposite winding, GL_CW

Validate at least:

- one visibly asymmetric real-cache model from multiple camera angles;
- one wall/roof/bridge-heavy view so the prior see-through-wall regression is obvious.

Terrain is intentionally excluded from this culling acceptance: `applyDrawState()` keeps
`SceneLayer.Kind.TERRAIN` two-sided even when **Client Front** is selected. Shaped-tile
winding therefore does not block this PR and should only be revisited in a separate experiment
if native terrain culling has a demonstrated benefit.

Acceptance:

- **Client Front** preserves the surfaces visible in the software/client reference;
- **Reversed Debug** demonstrates the opposite winding where expected;
- no see-through wall, missing roof, bridge, or dark-scene regression appears under
  **Client Front**.

Only after this live check should model-geometry culling become a normal default or
`native.depthPriorityFacing` move to covered. Terrain remains two-sided.

## 4. Merge rule

PR #6 is ready to leave draft when:

- exact-head Foundation is green;
- the live GL_CCW acceptance above passes;
- `docs/RENDERING_PARITY_MANIFEST.json` records the resulting native-facing evidence.

P1 rendering items and broader Map Studio features belong in the next single PR after this one
merges.
