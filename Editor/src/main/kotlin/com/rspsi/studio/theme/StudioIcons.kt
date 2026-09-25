package com.rspsi.studio.theme

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Semantic icon registry for OpenRune Studio backed by Google Material Icons.
 *
 * Named constants are the stable application vocabulary. [byName] additionally exposes
 * the bundled Material Icons codepoint catalog for extension/tool metadata that provides
 * icon names dynamically at runtime.
 */
object StudioIcons {
    // Search & discovery
    const val SEARCH = "\ue8b6"
    const val EXPLORE = "\ue87a"
    const val NAVIGATION = "\ue55d"
    const val SPEED = "\ue9e4"

    // Navigation, viewports & shell
    const val HOME = "\ue88a"
    const val MAP = "\ue55b"
    const val VIEWPORT = "\ue8f4"
    const val VISIBILITY_OFF = "\ue8f5"
    const val SETTINGS = "\ue8b8"
    const val EXTENSION = "\ue87b"
    const val SLIDERS = "\ue429"
    const val TUNE = "\ue429"
    const val INFO = "\ue88e"
    const val PLAYER = "\ue7fd"
    const val HELP = "\ue887"
    const val PIN = "\uf10d"
    const val CLOSE = "\ue5cd"
    const val REFRESH = "\ue5d5"
    const val FULLSCREEN = "\ue5d0"
    const val FULLSCREEN_EXIT = "\ue5d1"
    const val MINIMIZE = "\ue931"
    const val OPEN_IN_NEW = "\ue89e"
    const val LOCK = "\ue897"
    const val LOCK_OPEN = "\ue898"
    const val MORE_VERT = "\ue5d4"
    const val MORE_HORIZ = "\ue5d3"
    const val CAMERA = "\ue412"
    const val CENTER_FOCUS = "\ue3dc"
    const val ZOOM_IN = "\ue8ff"
    const val ZOOM_OUT = "\ue900"

    // Selection & editing
    const val SELECT = "\ue569"
    const val TILE = "\ue3c6"
    const val AREA = "\ue162"
    const val GRID = "\ue3ec"
    const val GRID_OFF = "\ue3eb"
    const val BRUSH = "\ue3ae"
    const val PALETTE = "\ue40a"
    const val COLOR_LENS = "\ue3b7"
    const val TEXTURE = "\ue421"
    const val LAYERS = "\ue53b"
    const val LAYERS_CLEAR = "\ue53c"
    const val TERRAIN = "\ue564"
    const val HEIGHT = "\uea3b"
    const val STRAIGHTEN = "\ue41c"
    const val TRENDING_UP = "\ue8e5"
    const val WATER = "\ue798"
    const val ENVIRONMENT = "\uea63"
    const val FLAG = "\ue153"
    const val PATH = "\ue922"
    const val ROUTE = "\ueacd"
    const val POLYLINE = "\uebbb"

    // Brush shapes & geometry
    const val SHAPE_SQUARE = "\ue3c6"
    const val SHAPE_CIRCLE = "\uef4a"
    const val SHAPE_DIAMOND = "\uead5"
    const val SHAPE_FALLOFF = "\ue3e9"
    const val SHAPE_WAVE = "\ue176"

    // Height sculpting
    const val RAISE = "\ue5d8"
    const val LOWER = "\ue5db"
    const val FLATTEN = "\uf108"
    const val SMOOTH = "\ue176"
    const val TERRACE = "\uf1a9"
    const val BLEND = "\ue429"

    // Steppers & transforms
    const val ADD = "\ue145"
    const val REMOVE = "\ue15b"
    const val ROTATE_LEFT = "\ue419"
    const val ROTATE_RIGHT = "\ue41a"

    // 3D objects & hierarchy
    const val OBJECT = "\ue9fe"
    const val ADD_OBJECT = "\ue146"
    const val PREFAB = "\ue1bd"
    const val OUTLINER = "\ue8ef"
    const val LIST = "\ue241"

    // Actions, history & files
    const val HISTORY = "\ue889"
    const val UNDO = "\ue166"
    const val REDO = "\ue15a"
    const val CHECK = "\ue5ca"
    const val VALIDATE = "\ue5ca"
    const val TERMINAL = "\ueb8e"
    const val CODE = "\ue86f"
    const val BUG_REPORT = "\ue868"
    const val FOLDER = "\ue2c7"
    const val FOLDER_OPEN = "\ue2c8"
    const val SAVE = "\ue161"
    const val DELETE = "\ue872"
    const val EDIT = "\ue3c9"
    const val COPY = "\ue14d"
    const val PASTE = "\ue14f"
    const val PLAY = "\ue037"
    const val PAUSE = "\ue034"
    const val STOP = "\ue047"

    // Chevrons & directionals
    const val EXPAND_MORE = "\ue5cf"
    const val EXPAND_LESS = "\ue5ce"
    const val CHEVRON_LEFT = "\ue5cb"
    const val CHEVRON_RIGHT = "\ue5cc"
    const val ARROW_DROP_DOWN = "\ue5c5"
    const val ARROW_DROP_UP = "\ue5c7"

    private val ICONS_BY_NAME: Map<String, String> = loadNamedIcons()

    /**
     * Resolves a Google Material Icon name such as "brush" or "water_drop".
     *
     * Dynamic lookup is intentionally best-effort: if the bundled codepoint catalog cannot
     * be read, the stable constants above still work and callers receive their fallback.
     */
    @JvmStatic
    fun byName(
        name: String?,
        fallback: String,
    ): String {
        val key = normalizeName(name) ?: return fallback
        return ICONS_BY_NAME[key] ?: fallback
    }

    /** Resolves a name and falls back to [OBJECT] when it is unknown. */
    @JvmStatic
    fun byName(name: String?): String = byName(name, OBJECT)

    @JvmStatic
    fun has(name: String?): Boolean {
        val key = normalizeName(name) ?: return false
        return ICONS_BY_NAME.containsKey(key)
    }

    @JvmStatic
    fun registeredIconCount(): Int = ICONS_BY_NAME.size

    private fun normalizeName(name: String?): String? =
        name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)

    private fun loadNamedIcons(): Map<String, String> {
        val input =
            StudioIcons::class.java.getResourceAsStream(
                "/font/MaterialIcons-Regular.codepoints",
            ) ?: return emptyMap()

        // This registry is supplemental metadata, not startup-critical UI state. Malformed
        // lines are skipped so one catalog entry cannot disable all icon-name resolution.
        return runCatching {
            input.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                buildMap(capacity = 2500) {
                    lines.forEach { line ->
                        val separator = line.indexOf(' ')
                        if (separator <= 0) {
                            return@forEach
                        }

                        val name = line.substring(0, separator).trim()
                        val hex = line.substring(separator + 1).trim()
                        val codePoint = hex.toIntOrNull(16) ?: return@forEach
                        if (codePoint <= 0xFFFF) {
                            put(name, codePoint.toChar().toString())
                        }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}
