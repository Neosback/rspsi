package com.rspsi.editor.tool.state

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single authoritative mutable state container for the composite Tile Painter.
 *
 * Studio controls and the neutral painting tool share this object rather than copying
 * settings between UI and engine code each frame. Setters intentionally notify on every
 * accepted assignment, even when the value is unchanged, because listeners treat calls
 * as state-change signals rather than relying on property-diff semantics.
 */
class TilePainterState {
    fun interface Listener {
        fun changed(state: TilePainterState)
    }

    // Copy-on-write preserves the original listener behavior while allowing listeners to
    // be added or removed safely during notification without locking the painting path.
    private val listeners = CopyOnWriteArrayList<Listener?>()

    private var applyUnderlayValue = false
    private var underlayIdValue = 0
    private var applyOverlayValue = true
    private var overlayIdValue = 1
    private var applyShapeValue = false
    private var shapeValue = 0
    private var applyRotationValue = false
    private var rotationValue = 0
    private var applyFlagsValue = false
    private var flagsValue = 0
    private var applyHeightValue = false
    private var heightValue = 0
    private var brushIdValue = "square"
    private var brushRadiusValue = 0

    fun applyUnderlay(): Boolean = applyUnderlayValue

    fun underlayId(): Int = underlayIdValue

    fun applyOverlay(): Boolean = applyOverlayValue

    fun overlayId(): Int = overlayIdValue

    fun applyShape(): Boolean = applyShapeValue

    fun shape(): Int = shapeValue

    fun applyRotation(): Boolean = applyRotationValue

    fun rotation(): Int = rotationValue

    fun applyFlags(): Boolean = applyFlagsValue

    fun flags(): Int = flagsValue

    fun applyHeight(): Boolean = applyHeightValue

    fun height(): Int = heightValue

    fun brushId(): String = brushIdValue

    fun brushRadius(): Int = brushRadiusValue

    fun setApplyUnderlay(value: Boolean) {
        applyUnderlayValue = value
        fire()
    }

    fun setUnderlayId(value: Int) {
        underlayIdValue = value.coerceAtLeast(0)
        fire()
    }

    fun setApplyOverlay(value: Boolean) {
        applyOverlayValue = value
        fire()
    }

    fun setOverlayId(value: Int) {
        overlayIdValue = value.coerceAtLeast(0)
        fire()
    }

    fun setApplyShape(value: Boolean) {
        applyShapeValue = value
        fire()
    }

    fun setShape(value: Int) {
        require(value in 0..11) { "shape must be 0..11" }
        shapeValue = value
        fire()
    }

    fun setApplyRotation(value: Boolean) {
        applyRotationValue = value
        fire()
    }

    fun setRotation(value: Int) {
        require(value in 0..3) { "rotation must be 0..3" }
        rotationValue = value
        fire()
    }

    fun setApplyFlags(value: Boolean) {
        applyFlagsValue = value
        fire()
    }

    fun setFlags(value: Int) {
        flagsValue = value
        fire()
    }

    fun setApplyHeight(value: Boolean) {
        applyHeightValue = value
        fire()
    }

    fun setHeight(value: Int) {
        heightValue = value
        fire()
    }

    fun setBrushId(value: String?) {
        if (value.isNullOrBlank()) {
            throw IllegalArgumentException("brush id cannot be blank")
        }

        // Brush IDs are validated here but not normalized. The brush registry owns identity,
        // so silently trimming/casing an ID during migration could change third-party lookup.
        brushIdValue = value
        fire()
    }

    fun setBrushRadius(value: Int) {
        require(value in 0..64) { "brush radius must be 0..64" }
        brushRadiusValue = value
        fire()
    }

    fun addListener(listener: Listener?) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener?) {
        listeners.remove(listener)
    }

    private fun fire() {
        for (listener in listeners) {
            // CopyOnWriteArrayList permits null just like the original Java implementation.
            // Preserve that edge case: a null registration fails when notification is attempted.
            listener!!.changed(this)
        }
    }
}
