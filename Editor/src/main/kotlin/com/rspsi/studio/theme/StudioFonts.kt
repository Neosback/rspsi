package com.rspsi.studio.theme

import imgui.ImFont
import imgui.ImFontConfig
import imgui.ImGui
import org.lwjgl.glfw.GLFW
import java.io.IOException

/**
 * Owns the native shell's font atlas.
 *
 * The atlas is rasterized at the GLFW content scale for sharp HiDPI text, then each
 * ImGui font is scaled back to logical UI points. That separation is important on
 * Retina/high-DPI displays: the texture stays sharp without doubling rail/panel metrics.
 */
object StudioFonts {
    private val MATERIAL_ICON_RANGES =
        shortArrayOf(0xE000.toShort(), 0xF8FF.toShort(), 0)
    private val FA_ICON_RANGES =
        shortArrayOf(0xF000.toShort(), 0xF8FF.toShort(), 0)

    const val BASE_FONT_SIZE = 17.0f

    private var ui: ImFont? = null
    private var heading: ImFont? = null
    private var display: ImFont? = null
    private var icon: ImFont? = null
    private var mono: ImFont? = null

    /**
     * Installs the full font atlas after the Dear ImGui context exists.
     *
     * Material Icons and FontAwesome are merged into the primary UI face so ordinary
     * controls can mix labels and icons without switching fonts. A standalone larger
     * icon face remains available for rails and tool buttons.
     */
    @JvmStatic
    fun apply(windowHandle: Long) {
        val io = ImGui.getIO()
        val atlas = io.fonts
        atlas.clear()

        val scaleX = floatArrayOf(1.0f)
        val scaleY = floatArrayOf(1.0f)
        GLFW.glfwGetWindowContentScale(windowHandle, scaleX, scaleY)
        val uiScale = maxOf(1.0f, scaleX[0], scaleY[0])

        val uiConfig = createConfig(oversampleH = 3, oversampleV = 3, pixelSnap = false)
        val headingConfig = createConfig(oversampleH = 3, oversampleV = 3, pixelSnap = false)
        val displayConfig = createConfig(oversampleH = 3, oversampleV = 3, pixelSnap = false)
        val materialIconConfig = ImFontConfig()
        val faConfig = ImFontConfig()
        val railIconConfig = createConfig(oversampleH = 2, oversampleV = 1, pixelSnap = true)
        val railFaConfig = ImFontConfig()
        val monoConfig = createConfig(oversampleH = 2, oversampleV = 1, pixelSnap = true)

        try {
            ui =
                atlas.addFontFromMemoryTTF(
                    resource("/font/Roboto-Regular.ttf"),
                    BASE_FONT_SIZE * uiScale,
                    uiConfig,
                )
            heading =
                atlas.addFontFromMemoryTTF(
                    resource("/font/Roboto-Regular.ttf"),
                    24.0f * uiScale,
                    headingConfig,
                )
            display =
                atlas.addFontFromMemoryTTF(
                    resource("/font/Roboto-Regular.ttf"),
                    31.0f * uiScale,
                    displayConfig,
                )

            // Merge semantic icon glyphs into the normal UI font. This lets callers use
            // StudioIcons constants inside labels without balancing ImGui font pushes.
            materialIconConfig.setMergeMode(true)
            materialIconConfig.setPixelSnapH(true)
            materialIconConfig.setDstFont(ui())
            atlas.addFontFromMemoryTTF(
                resource("/font/MaterialIcons-Regular.ttf"),
                16.0f * uiScale,
                materialIconConfig,
                MATERIAL_ICON_RANGES,
            )

            // FontAwesome remains a fallback for legacy glyphs not present in Material Icons.
            faConfig.setMergeMode(true)
            faConfig.setPixelSnapH(true)
            faConfig.setDstFont(ui())
            atlas.addFontFromMemoryTTF(
                resource("/font/fontawesome-webfont.ttf"),
                14.0f * uiScale,
                faConfig,
                FA_ICON_RANGES,
            )

            icon =
                atlas.addFontFromMemoryTTF(
                    resource("/font/MaterialIcons-Regular.ttf"),
                    22.0f * uiScale,
                    railIconConfig,
                    MATERIAL_ICON_RANGES,
                )

            railFaConfig.setMergeMode(true)
            railFaConfig.setPixelSnapH(true)
            railFaConfig.setDstFont(icon())
            atlas.addFontFromMemoryTTF(
                resource("/font/fontawesome-webfont.ttf"),
                20.0f * uiScale,
                railFaConfig,
                FA_ICON_RANGES,
            )

            mono =
                atlas.addFontFromMemoryTTF(
                    resource("/font/JetBrainsMono-Regular.ttf"),
                    14.0f * uiScale,
                    monoConfig,
                )

            if (!atlas.build()) {
                throw IllegalStateException("Unable to build the OpenRune Studio font atlas")
            }

            // GLFW reports framebuffer/content scale separately from ImGui's logical
            // coordinates. Rasterize high-resolution glyphs, but keep layout in logical points.
            val logicalScale = 1.0f / uiScale
            ui().scale = logicalScale
            heading().scale = logicalScale
            display().scale = logicalScale
            icon().scale = logicalScale
            mono().scale = logicalScale
            io.fontDefault = ui()
        } finally {
            uiConfig.destroy()
            headingConfig.destroy()
            displayConfig.destroy()
            materialIconConfig.destroy()
            faConfig.destroy()
            railIconConfig.destroy()
            railFaConfig.destroy()
            monoConfig.destroy()
        }
    }

    @JvmStatic
    fun ui(): ImFont = initialized(ui)

    @JvmStatic
    fun heading(): ImFont = initialized(heading)

    /** Large display face reserved for launch/loading/product titles. */
    @JvmStatic
    fun display(): ImFont = initialized(display)

    @JvmStatic
    fun mono(): ImFont = initialized(mono)

    @JvmStatic
    fun icon(): ImFont = initialized(icon)

    private fun initialized(font: ImFont?): ImFont =
        font ?: throw NullPointerException("Studio fonts are not initialized")

    private fun createConfig(
        oversampleH: Int,
        oversampleV: Int,
        pixelSnap: Boolean,
    ): ImFontConfig =
        ImFontConfig().apply {
            setOversampleH(oversampleH)
            setOversampleV(oversampleV)
            setPixelSnapH(pixelSnap)
        }

    private fun resource(path: String): ByteArray {
        try {
            StudioFonts::class.java.getResourceAsStream(path).use { input ->
                if (input == null) {
                    throw IllegalStateException("Missing native UI resource: $path")
                }
                return input.readAllBytes()
            }
        } catch (failure: IOException) {
            throw IllegalStateException("Unable to read native UI resource: $path", failure)
        }
    }
}
