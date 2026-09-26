package com.rspsi.editor.input

/**
 * Immutable frontend-neutral pointer event consumed by editor tools.
 *
 * Explicit record-style accessors keep the Java call surface stable while the value
 * implementation moves to Kotlin. The event intentionally carries translated input
 * only; gesture interpretation belongs to the active tool/controller layer.
 */
class PointerEvent(
    private val xValue: Float,
    private val yValue: Float,
    private val buttonValue: PointerButton?,
    private val shiftValue: Boolean,
    private val ctrlValue: Boolean,
    private val altValue: Boolean,
) {
    init {
        if (!xValue.isFinite() || !yValue.isFinite()) {
            throw IllegalArgumentException("Pointer coordinates must be finite")
        }
        if (buttonValue == null) {
            throw NullPointerException("button")
        }
    }

    fun x(): Float = xValue

    fun y(): Float = yValue

    fun button(): PointerButton = buttonValue!!

    fun shift(): Boolean = shiftValue

    fun ctrl(): Boolean = ctrlValue

    fun alt(): Boolean = altValue

    override fun equals(other: Any?): Boolean =
        other is PointerEvent &&
            java.lang.Float.compare(xValue, other.xValue) == 0 &&
            java.lang.Float.compare(yValue, other.yValue) == 0 &&
            buttonValue == other.buttonValue &&
            shiftValue == other.shiftValue &&
            ctrlValue == other.ctrlValue &&
            altValue == other.altValue

    override fun hashCode(): Int {
        var result = java.lang.Float.hashCode(xValue)
        result = 31 * result + java.lang.Float.hashCode(yValue)
        result = 31 * result + buttonValue.hashCode()
        result = 31 * result + java.lang.Boolean.hashCode(shiftValue)
        result = 31 * result + java.lang.Boolean.hashCode(ctrlValue)
        result = 31 * result + java.lang.Boolean.hashCode(altValue)
        return result
    }

    override fun toString(): String =
        "PointerEvent[" +
            "x=$xValue, " +
            "y=$yValue, " +
            "button=$buttonValue, " +
            "shift=$shiftValue, " +
            "ctrl=$ctrlValue, " +
            "alt=$altValue]"
}
