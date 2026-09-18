# Rendering reference triangulation

This note records the rendering decisions checked against the three local
reference implementations before changing the native OpenGL path.

## Texture alpha

The raw modern texture record is not a reliable native alpha classification
field:

- RuneLite-melxin's `runescape-client/src/main/java/Texture.java` decodes the
  fifth byte as `isLowDetail`.
- RuneLite-melxin's GPU texture upload converts decoded indexed-sprite pixels
  with `rgb == 0` to alpha zero in
  `runelite-client/src/main/java/net/runelite/client/plugins/gpu/TextureManager.java`.
- TSPS derives `isTransparent` from the texture loader's decoded sprite
  contract in `client/rs/texture/DatTextureLoader.ts` and
  `client/rs/texture/SpriteTextureLoader.ts`; its fragment shader then applies
  an alpha cutoff.
- OSRS-Environment-Exporter follows the same pixel rule in
  `src/main/kotlin/controllers/worldRenderer/TextureManager.kt`.

RSPSi therefore keeps model-face alpha and render type as the primary alpha
pass inputs, and only adds a textured face to that pass when its decoded pixel
resource contains the OSRS cutout sentinel. The texture record's low-detail
byte is retained as metadata and never turns a normal opaque wall or object
face into a blended draw.

## Submission and state

- RuneLite's native GPU path separates face-alpha geometry and orders it by
  priority/depth; ordinary textured faces remain in the opaque geometry path
  while their zero-alpha texture pixels are discarded by the shader.
- TSPS uses separate opaque/transparent scene ranges, keeps texture alpha as a
  shader cutoff, and renders the transparent range after the opaque range.
- OSRS-Environment-Exporter packs face alpha and priority into the vertex
  stream and uploads model geometry without converting every textured model to
  a blended surface.

The native baseline follows the conservative combination: decoded cutouts are
discarded in the shader, face alpha remains blend-sorted, depth writes are
reset before and after every frame, and all textured draws keep a valid
`sampler2DArray` binding.

## Current evidence

With the revision-240 cache at `/Users/tylercovalt/Desktop/LIVE`, the native
smoke run now reports non-zero geometry and texture coverage with
`firstGLerror=0`. The corrected texture classification reduced native draw
submissions from 1,061 to 240 while preserving the populated scene.
