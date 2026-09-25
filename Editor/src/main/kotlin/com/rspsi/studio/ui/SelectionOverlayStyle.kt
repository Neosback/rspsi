package com.rspsi.studio.ui

import com.rspsi.editor.model.ObjectCategory
import java.util.EnumMap

/**
 * Mutable in-memory style settings for selection overlays.
 *
 * Shared by the settings UI and viewport overlay renderer while keeping
 * Studio-specific settings out of Client-module overlay/tool types.
 */
class SelectionOverlayStyle {
    private var objectHullEnabledValue = true
    private var showObjectInfoValue = false

    private val outlineColors = EnumMap<ObjectCategory, Int>(ObjectCategory::class.java).apply {
        putDefaults()
    }

    /** 0..255. Applied as fill alpha over the outline color's RGB. */
    private var fillAlphaValue = DEFAULT_FILL_ALPHA
    private var outlineThicknessValue = DEFAULT_OUTLINE_THICKNESS
    private var paintedEdgeValue = true

    private var tileOutlineColorValue = DEFAULT_TILE_OUTLINE_COLOR
    private var tileFillAlphaValue = DEFAULT_TILE_FILL_ALPHA

    fun objectHullEnabled(): Boolean = objectHullEnabledValue

    fun setObjectHullEnabled(value: Boolean) {
        objectHullEnabledValue = value
    }

    fun showObjectInfo(): Boolean = showObjectInfoValue

    fun setShowObjectInfo(value: Boolean) {
        showObjectInfoValue = value
    }

    fun outlineColor(category: ObjectCategory): Int =
        outlineColors[category] ?: outlineColors.getValue(ObjectCategory.UNKNOWN)

    fun setOutlineColor(category: ObjectCategory, rgba: Int) {
        outlineColors[category] = rgba
    }

    fun fillAlpha(): Int = fillAlphaValue

    fun setFillAlpha(alpha: Int) {
        fillAlphaValue = alpha.coerceIn(0, 255)
    }

    fun outlineThickness(): Float = outlineThicknessValue

    fun setOutlineThickness(thickness: Float) {
        outlineThicknessValue = thickness.coerceAtLeast(MIN_OUTLINE_THICKNESS)
    }

    fun paintedEdge(): Boolean = paintedEdgeValue

    fun setPaintedEdge(value: Boolean) {
        paintedEdgeValue = value
    }

    fun tileOutlineColor(): Int = tileOutlineColorValue

    fun setTileOutlineColor(rgba: Int) {
        tileOutlineColorValue = rgba
    }

    fun tileFillAlpha(): Int = tileFillAlphaValue

    fun setTileFillAlpha(alpha: Int) {
        tileFillAlphaValue = alpha.coerceIn(0, 255)
    }

    /** Replaces the outline color's alpha with the configured fill alpha. */
    fun fillColor(category: ObjectCategory): Int =
        (outlineColor(category) and 0xFFFFFF00.toInt()) or fillAlphaValue

    fun resetToDefaults() {
        objectHullEnabledValue = true
        showObjectInfoValue = false
        outlineColors.clear()
        outlineColors.putDefaults()
        fillAlphaValue = DEFAULT_FILL_ALPHA
        outlineThicknessValue = DEFAULT_OUTLINE_THICKNESS
        paintedEdgeValue = true
        tileOutlineColorValue = DEFAULT_TILE_OUTLINE_COLOR
        tileFillAlphaValue = DEFAULT_TILE_FILL_ALPHA
    }

    private fun EnumMap<ObjectCategory, Int>.putDefaults() {
        put(ObjectCategory.WALL, 0xF59E0BFF.toInt())
        put(ObjectCategory.WALL_DECOR, 0x22D3EEFF)
        put(ObjectCategory.GROUND, 0xF59E0BFF.toInt())
        put(ObjectCategory.GROUND_DECOR, 0xA78BFAFF.toInt())
        put(ObjectCategory.UNKNOWN, 0x94A3B8FF.toInt())
    }

    companion object {
        private const val DEFAULT_FILL_ALPHA = 128
        private const val DEFAULT_OUTLINE_THICKNESS = 2.0f
        private const val MIN_OUTLINE_THICKNESS = 0.5f
        private const val DEFAULT_TILE_OUTLINE_COLOR = 0x40E0D0FF
        private const val DEFAULT_TILE_FILL_ALPHA = 40

        private val SHARED = SelectionOverlayStyle()

        @JvmStatic
        fun shared(): SelectionOverlayStyle = SHARED
    }
}
