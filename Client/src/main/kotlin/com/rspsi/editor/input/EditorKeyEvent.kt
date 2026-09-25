package com.rspsi.editor.input

import java.util.Objects

/**
 * Immutable keyboard input translated from JavaFX, Dear ImGui, GLFW, or another frontend.
 *
 * This remains a frontend-neutral value object. The explicit record-style accessors are
 * intentional: most of Client is still Java, so callers keep using key(), pressed(), etc.
 * while the implementation moves to Kotlin.
 */
class EditorKeyEvent(
    key: String?,
    private val pressedValue: Boolean,
    private val repeatValue: Boolean,
    private val shiftValue: Boolean,
    private val ctrlValue: Boolean,
    private val altValue: Boolean,
    private val metaValue: Boolean,
) {
    private val keyValue: String =
        key
            ?.trim()
            ?: throw NullPointerException("key")

    init {
        if (keyValue.isEmpty()) {
            throw IllegalArgumentException("key cannot be empty")
        }
    }

    fun key(): String = keyValue

    fun pressed(): Boolean = pressedValue

    fun repeat(): Boolean = repeatValue

    fun shift(): Boolean = shiftValue

    fun ctrl(): Boolean = ctrlValue

    fun alt(): Boolean = altValue

    fun meta(): Boolean = metaValue

    override fun equals(other: Any?): Boolean =
        other is EditorKeyEvent &&
            keyValue == other.keyValue &&
            pressedValue == other.pressedValue &&
            repeatValue == other.repeatValue &&
            shiftValue == other.shiftValue &&
            ctrlValue == other.ctrlValue &&
            altValue == other.altValue &&
            metaValue == other.metaValue

    override fun hashCode(): Int =
        Objects.hash(
            keyValue,
            pressedValue,
            repeatValue,
            shiftValue,
            ctrlValue,
            altValue,
            metaValue,
        )

    override fun toString(): String =
        "EditorKeyEvent[" +
            "key=$keyValue, " +
            "pressed=$pressedValue, " +
            "repeat=$repeatValue, " +
            "shift=$shiftValue, " +
            "ctrl=$ctrlValue, " +
            "alt=$altValue, " +
            "meta=$metaValue]"
}
