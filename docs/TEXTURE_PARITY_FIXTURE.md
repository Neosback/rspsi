# Revision texture parity fixture

OpenRune Studio keeps real OSRS cache data outside the repository. Texture parity uses an
optional `textures.json` file inside the same external parity-fixture directory consumed by
`OsrsRevisionVerifier`.

The fixture should be produced from an independent reference implementation or independently
verified cache export. Do not generate the expected values with OpenRune Studio itself and then
treat that as independent parity evidence.

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
      "fileId": 300,
      "transparent": true,
      "averageRgb": 13398,
      "lowDetail": false,
      "animationDirection": 4,
      "animationSpeed": 9,
      "pixelCount": 16384,
      "pixelSha256": "<64-character SHA-256 hex>",
      "zeroRgbPixels": 127,
      "partialAlphaPixels": 0
    }
  ]
}
```

The numeric values above are schema examples only, not checked-in OSRS reference values.

## Pixel digest

Pixels are hashed in array order. Each packed Java `int` is written as four bytes in
big-endian order before SHA-256 is calculated. The verifier also compares:

- decoded pixel count;
- RGB-zero cutout count, where `(pixel & 0x00FFFFFF) == 0`;
- partial-alpha count, where the top byte is between 1 and 254.

The hash is the authoritative full-pixel comparison. The counts make failures easier to
diagnose, especially when a texture has cutout or alpha semantics.

## Verification

Place `textures.json` next to the other external parity files:

```text
fixture/
  fixture.properties
  terrain-semantics.json
  locations.json
  scene-geometry.json
  collision.json
  textures.json
  minimap-plane-0.png
  ...
```

Then run the existing real-cache verification path:

```bash
RSPSI_OSRS_CACHE=/path/to/cache \
RSPSI_OSRS_PARITY_FIXTURE=/path/to/fixture \
RSPSI_OSRS_REGION_X=16 \
RSPSI_OSRS_REGION_Y=33 \
RSPSI_OSRS_REVISION=240 \
./gradlew verifyOsrsRevision
```

The report includes a `texture.parity` check. A supplied texture fixture fails verification
when definition metadata, decoded pixel availability, pixel count, SHA-256, cutout count, or
partial-alpha count differs.

## Reference guidance

For revision-240 evidence, capture a small but representative set rather than every texture.
At minimum include:

1. one ordinary opaque RGB texture;
2. one texture containing RGB-zero cutout pixels;
3. one animated texture with a non-zero animation direction and speed;
4. one texture that exercises alpha-channel handling when the reference cache contains one.

Record the cache fingerprint and revision in `fixture.properties` as usual so fixture values
cannot silently be reused against a different cache.
