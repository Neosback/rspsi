package com.rspsi.editor.render

/**
 * Which planes a frame draws, evaluated per draw command at render time.
 *
 * The client keeps every plane of the scene loaded and only chooses what to draw each frame.
 * Studio does the same: the GPU plan holds all planes and this filter applies
 * [SceneVisibilityPolicy.maxPlaneExclusive] to each command, so changing the active plane
 * or toggling all-heights never rebuilds or re-uploads geometry.
 *
 * Mirrors Terraini's VisiblePlaneWindow.maxPlaneExclusive contract:
 * - allHeightsVisible=true  → every plane passes
 * - allHeightsVisible=false → only authored plane < maxPlaneExclusive(4) passes,
 *   plus bridge decks authored one plane above but drawn below
 *   (Terraforge GL32ForwardSceneRenderer re-emits plane-1 bridge tiles while viewing plane 0).
 */
@JvmRecord
data class ScenePlaneFilter(
    val currentHeight: Int,
    val allHeightsVisible: Boolean,
    val hideBridgeUpperGeometry: Boolean,
) {
    /** Compute maxPlaneExclusive for [planes] total planes (Terraini VisiblePlaneWindow logic). */
    fun maxPlaneExclusive(planes: Int): Int {
        if (allHeightsVisible) return planes
        return (currentHeight.coerceIn(0, planes - 1)) + 1
    }

    /** True when every command passes (all heights on, bridge geometry not hidden). */
    fun isAll(): Boolean = allHeightsVisible && !hideBridgeUpperGeometry

    /**
     * Whether [command] is drawn. [contract] is the scene window's contract (scene minimum
     * plane); null skips that check, as for plans without a window.
     */
    fun includes(command: GpuDrawCommand, contract: SceneContract?): Boolean {
        val authored = command.tile().plane
        val effective = command.scenePlane()

        // Scene minimum check (mirrors SceneVisibilityPolicy.includesSceneMinimum)
        if (!allHeightsVisible && contract != null && !contract.rendersScenePlane(effective)) {
            return false
        }

        // Authored-plane gate via maxPlaneExclusive, with a bridge-deck exception:
        // a command drawn below its authored plane (scenePlane < authored) is a
        // bridge link; it passes when its drawn plane is visible even if its
        // authored plane is clipped. This keeps the plane-1 bridge deck visible
        // while viewing plane 0 with all-heights off.
        val maxPlane = maxPlaneExclusive(4)
        val bridgeDeck = effective < authored && effective < maxPlane
        if (authored >= maxPlane && !bridgeDeck) return false

        // Bridge upper geometry filter
        return !hideBridgeUpperGeometry || effective >= authored
    }

    companion object {
        @JvmField
        val ALL = ScenePlaneFilter(0, true, false)
    }
}
