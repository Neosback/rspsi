/**
 * Studio scene API shaped after {@code net.runelite.api}.
 *
 * <p>Names and getters follow RuneLite's scene interfaces ({@code Tile},
 * {@code Scene}, {@code WorldView}, {@code SceneTilePaint},
 * {@code SceneTileModel}, {@code TileObject} and its layers, {@code WorldPoint},
 * {@code LocalPoint}, {@code Perspective}) so RuneLite plugin authors meet
 * familiar vocabulary. The types are Studio-owned: RuneLite is a BSD-licensed
 * reference (vendored under {@code RuneLite-melxin/}), not a dependency, and
 * the values come from the pinned client behaviour in
 * {@code runescape-client/} rather than from RuneLite's live-client
 * bookkeeping.</p>
 *
 * <p>Differences from RuneLite, all deliberate:</p>
 * <ul>
 *   <li>Views are immutable snapshots of one resolved scene. RuneLite-style
 *   setters, where offered, record undoable editor commands against the
 *   authored world instead of mutating the snapshot.</li>
 *   <li>GPU bookkeeping ({@code getBufferOffset}, {@code getUvBufferOffset},
 *   {@code getBufferLen}) is omitted.</li>
 *   <li>Editor extras sit beside the RuneLite getters: the authored plane a
 *   tile was loaded from, and the stable placed identity of an object.</li>
 * </ul>
 *
 * <p>See docs/STUDIO_SEMANTIC_API.md for the contract and roadmap.</p>
 */
package com.rspsi.api;
