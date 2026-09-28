# Core

`Core` is the headless, dependency-light OSRS domain/codec module used by OpenRune Studio.

The first extracted slice is intentionally modern-only:

- 64x64x4 OSRS region data;
- modern unsigned-short terrain opcodes;
- landscape decode/encode;
- delta-packed location decode/encode;
- deterministic generated terrain heights;
- semantic round-trip validation.

It deliberately does **not** include:

- XTEA;
- pre-modern byte terrain codecs;
- legacy revision compatibility switches;
- Editor/renderer types;
- OpenGL/ImGui;
- Ktor;
- OpenRune FileStore objects.

The legacy `Client` codec remains in place temporarily. A later PR will adapt surviving callers to this Core model and remove the duplicate implementation once parity is proven.
