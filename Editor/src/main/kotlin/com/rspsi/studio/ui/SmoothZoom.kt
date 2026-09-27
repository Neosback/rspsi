package com.rspsi.studio.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Eased, proportional zoom for mouse wheels and trackpads.
 *
 * A fixed factor per wheel event makes trackpads (many small fractional events) jump in
 * bursts. Here each event scales the target by `exp(delta * perWheelUnit)`, so a notch and
 * a slow two-finger scroll of the same distance zoom the same amount, and [current] then
 * eases toward the target over a few frames. Easing is in log space, so zooming in and out
 * feel symmetric.
 */
class SmoothZoom(
    initial: Double,
    private val min: Double,
    private val max: Double,
    /** Log-zoom per wheel unit; 0.2 is about 22% per mouse notch. */
    private val perWheelUnit: Double = 0.2,
    /** Higher settles faster; 14 settles in about 0.2 s. */
    private val sharpness: Double = 14.0,
) {
    var current: Double = initial.coerceIn(min, max)
        private set
    var target: Double = current
        private set

    /** Adds wheel or trackpad input (ImGui `io.mouseWheel`). */
    fun wheel(delta: Float) {
        if (delta != 0f) target = (target * exp(delta * perWheelUnit)).coerceIn(min, max)
    }

    /** Multiplies the target, e.g. for zoom buttons. */
    fun multiply(factor: Double) {
        target = (target * factor).coerceIn(min, max)
    }

    /** Jumps to [value] without easing, e.g. "fit all". */
    fun set(value: Double) {
        current = value.coerceIn(min, max)
        target = current
    }

    /**
     * Moves [current] toward [target] for a frame of [deltaSeconds] and returns the ratio
     * applied (new / old), so the caller can keep the point under the cursor fixed.
     */
    fun advance(deltaSeconds: Float): Double {
        if (current == target) return 1.0
        val remaining = ln(target / current)
        val next = if (abs(remaining) < SNAP_LOG) {
            target
        } else {
            current * (target / current).pow(1.0 - exp(-sharpness * deltaSeconds.coerceIn(0f, 0.1f)))
        }
        val ratio = next / current
        current = next
        return ratio
    }

    private companion object {
        /** Within 0.2% of the target the zoom snaps, so it never creeps forever. */
        const val SNAP_LOG = 0.002
    }
}
