# Revision texture parity fixture

OpenRune Studio keeps real OSRS cache data outside the repository. Texture parity uses an
external `textures.json` file consumed by the focused
`OsrsTextureRevisionVerifier`. The same file may also be placed in the broader parity-fixture
directory for full region verification.

The fixture must be produced from an independent reference implementation or independently
verified cache export. Do not generate expected values with OpenRune Studio itself and then
treat those values as independent parity evidence.

## File format

```json
{
  "formatVersion": 1,
  "revision": 240,
  "textureSize": 128,
  "brightness": 0.6,
  "textures": [
    {
      "id": 17,
      "fileId": 463,
      "transparent": false,
      "averageRgb": 39640,
      "lowDetail": false,
      "animationDirection": 1,
      "animationSpeed": 2,
      "pixelCount": 16384,
      "pixelSha256": "<64-character SHA-256 hex>",
      "zeroRgbPixels": 15499,
      "partialAlphaPixels": 0
    }
  ]
}
```

The structure above is the accepted schema. Cutout transparency is proven from decoded pixels;
the post-233 seven-byte texture record itself has no material-alpha field.

## Pixel digest

Pixels are hashed in array order. Each packed Java `int` is written as four bytes in
big-endian order before SHA-256 is calculated. The verifier also compares:

- decoded pixel count;
- RGB-zero cutout count, where `(pixel & 0x00FFFFFF) == 0`;
- partial-alpha count, where the top byte is between 1 and 254.

The hash is the authoritative full-pixel comparison. The counts make failures easier to
diagnose, especially for cutout semantics.

## Focused verification

Run the texture-only gate so unrelated maps, collision, minimaps, or scene geometry cannot
affect this result:

```bash
RSPSI_OSRS_CACHE=/path/to/cache \
RSPSI_OSRS_TEXTURE_FIXTURE=/path/to/textures.json \
RSPSI_OSRS_REVISION=240 \
./gradlew verifyOsrsTextures
```

A mismatch in revision, definition metadata, decoded pixel availability, pixel count, SHA-256,
cutout count, or partial-alpha count fails the task.

The repository also contains the manual GitHub Actions workflow
`Revision 240 Texture Acceptance`. It downloads the pinned public reference cache, generates
the independent fixture with the vendored RuneLite cache implementation, runs
`verifyOsrsTextures`, and uploads the generated metadata/hashes as an artifact. The workflow is
manual-only because the reference cache is about 182 MiB.

## Recorded revision-240 acceptance

PR #6 recorded a passing independent acceptance run against OpenRS2 archive 2710:

- game/environment/language: oldschool / live / en;
- build: 240;
- Jagex source timestamp: 2026-09-16 10:30:13;
- RuneLite-selected texture 0: opaque, 128x128, zero RGB pixels = 0;
- RuneLite-selected texture 7: cutout, 128x128, zero RGB pixels = 6984;
- RuneLite-selected texture 17: animated direction 1 / speed 2, 128x128;
- focused RSPSi result: detected revision 240, 3 textures compared, 0 differences,
  `texture.parity: PASS`;
- GitHub Actions run: 35718110830;
- evidence artifact: `revision-240-texture-parity`.

## Reference guidance

When adding a new revision or expanding the fixture, prefer a small representative set rather
than every texture. At minimum include:

1. one ordinary opaque RGB texture;
2. one texture containing RGB-zero cutout pixels;
3. one animated texture with a non-zero animation direction and speed;
4. an alpha-bearing case if the selected reference implementation/cache actually exposes one.

Keep the cache itself external to the repository. Retain only metadata/hashes and provenance.
