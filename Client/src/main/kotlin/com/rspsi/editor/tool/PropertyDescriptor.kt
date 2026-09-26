package com.rspsi.editor.tool

import java.util.Objects

/**
 * UI-neutral description of one editable tool property.
 *
 * Explicit record-style accessors preserve the Java API during migration. Keeping the
 * descriptor frontend-neutral prevents tool settings from depending on JavaFX/ImGui controls.
 */
class PropertyDescriptor(
    id: String?,
    label: String?,
    type: ValueType?,
    private val minimumValue: Int,
    private val maximumValue: Int,
) {
    enum class ValueType {
        INTEGER,
        DECIMAL,
        BOOLEAN,
        ENUM,
    }

    private val idValue = id ?: throw NullPointerException("id")
    private val labelValue = label ?: throw NullPointerException("label")
    private val typeValue = type ?: throw NullPointerException("type")

    init {
        if (minimumValue > maximumValue) {
            throw IllegalArgumentException("Property minimum cannot exceed maximum")
        }
    }

    fun id(): String = idValue

    fun label(): String = labelValue

    fun type(): ValueType = typeValue

    fun minimum(): Int = minimumValue

    fun maximum(): Int = maximumValue

    override fun equals(other: Any?): Boolean =
        other is PropertyDescriptor &&
            idValue == other.idValue &&
            labelValue == other.labelValue &&
            typeValue == other.typeValue &&
            minimumValue == other.minimumValue &&
            maximumValue == other.maximumValue

    override fun hashCode(): Int =
        Objects.hash(idValue, labelValue, typeValue, minimumValue, maximumValue)

    override fun toString(): String =
        "PropertyDescriptor[" +
            "id=$idValue, " +
            "label=$labelValue, " +
            "type=$typeValue, " +
            "minimum=$minimumValue, " +
            "maximum=$maximumValue]"
}
