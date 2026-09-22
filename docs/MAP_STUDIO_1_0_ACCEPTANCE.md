# Map Studio 1.0 foundation acceptance

This checklist is the final acceptance boundary for PR #6. It deliberately separates
deterministic CI coverage from the two external/live checks that cannot be proven by the
headless Foundation gate alone.

## 1. Foundation gate

Required:

```bash
./gradlew foundationGate
```

The gate must be green at the exact PR head.

## 2. Revision-240 texture parity

Code-side support is complete. The remaining evidence is one independently sourced build-240
cache/fixture run.

Use `docs/TEXTURE_PARITY_FIXTURE.md` and an external `textures.json` containing a small
representative set:

- ordinary opaque texture;
- RGB-zero cutout texture;
- animated texture;
- alpha-bearing texture if the selected reference cache contains one.

Recommended public cache provenance for the acceptance run:

- OpenRS2 archive cache 2710
- game: oldschool
- environment: live
- language: en
- build: 240
- Jagex source timestamp: 2026-09-16 10:30:13

The cache itself must remain outside this repository. Commit only fixture metadata/hashes if
the project chooses to retain the evidence.

Acceptance:

- `texture.parity` is PASS;
- definition metadata matches;
- 128x128 pixel decode is available for the selected texture set;
- SHA-256, RGB-zero cutout count, and partial-alpha count match the independent fixture.

## 3. Native winding / culling parity

RuneLite-melxin `Model.draw0` is the source reference:

- `edge <= 0` is stored as the culled-face flag;
- only `!culled` faces are drawn;
- therefore client-visible projected faces have `edge > 0`;
- Studio's software renderer uses the same edge-expression sign;
- converting the Y-down software viewport to OpenGL Y-up maps client-visible faces to GL_CCW.

The normal editor default remains `TWO_SIDED`.

For the live acceptance check, use the viewport setting **Native back-face culling** and compare:

1. `TWO_SIDED`
2. `CLIENT_FRONT` (GL_CCW through `BackfacePolicy.nativeWinding()`)
3. `REVERSED_DEBUG` (opposite winding, GL_CW)

Validate at least:

- one visibly asymmetric real-cache model from multiple camera angles;
- one shaped terrain tile;
- one wall/roof-heavy view so the prior see-through-wall regression is obvious.

Acceptance:

- `CLIENT_FRONT` preserves the surfaces visible in the software/client reference;
- `REVERSED_DEBUG` visibly demonstrates the opposite winding where expected;
- no see-through wall, missing roof, bridge, or dark-scene regression appears under
  `CLIENT_FRONT`.

Only after this check should native culling become a normal default or the
`native.depthPriorityFacing` manifest item move to covered.

## 4. Merge rule

PR #6 is ready to leave draft when:

- exact-head Foundation is green;
- revision-240 texture parity passes;
- live GL_CCW validation passes;
- `docs/RENDERING_PARITY_MANIFEST.json` is updated with the resulting evidence.

P1 rendering items and broader Map Studio features belong in the next single PR, not this one.
