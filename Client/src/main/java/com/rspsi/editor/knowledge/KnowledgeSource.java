package com.rspsi.editor.knowledge;

/**
 * Declares the authoritative origin and epistemic authority of a piece of world knowledge.
 *
 * <p>Separating facts by source prevents heuristic inferences or third-party presentation
 * overrides (such as 117HD) from corrupting exact cache data or deterministic OSRS scene geometry.</p>
 */
public enum KnowledgeSource {
    /** Layer 1: Exact facts extracted directly from cache definition archives and raw model geometry. */
    CACHE,

    /** Layer 2: Deterministic facts resolved by the OSRS scene graph (coordinates, footprints, instances). */
    SCENE_RESOLVER,

    /** Layer 3: Mathematically derived geometric, spatial, or topological properties (slopes, components, rooms). */
    DERIVED,

    /** Exact evaluation of formal OSRS engine rules (flag semantics, shape rules, blend algorithms). */
    RULE,

    /** Layer 4: Probabilistic, heuristic classifications with confidence scoring and evidence. */
    INFERRED,

    /** Third-party plugin-contributed semantic tags and annotations. */
    PLUGIN,

    /** Layer 5: Explicit author/user overrides and metadata tags. */
    USER,

    /** Real-time telemetry or state observed from an attached live game client. */
    LIVE_CLIENT,

    /** External HD or visual material overrides (e.g. 117HD / HDOS). */
    HD_OVERRIDE
}
