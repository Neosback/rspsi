package com.rspsi.studio.plugin.builtin.tool

import com.rspsi.studio.plugin.StudioToolPlugin
import com.rspsi.studio.theme.StudioDrawColors
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.StudioPanelContext
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImInt
import java.util.EnumSet

/**
 * Native Studio projection for object placement and manipulation.
 *
 * Engine-side object editing remains owned by the neutral editor tool. This projection
 * contributes Studio metadata, preferences, and compact context-drawer controls only.
 */
class ObjectPlacementToolPlugin : StudioToolPlugin {
    private val defaultRotation = ImInt(0)
    private val snapToTileCenter = ImBoolean(true)
    private val autoIncrementRotation = ImBoolean(false)

    override fun id(): String = ID

    override fun name(): String = "Object Spawner"

    override fun description(): String =
        "Spawn, translate, rotate, duplicate, and remove interactive 3D map objects and scenery models."

    override fun version(): String = "1.0.0"

    override fun author(): String = "OpenRune Team"

    override fun icon(): String = StudioIcons.OBJECT

    override fun toolId(): String = ENGINE_TOOL_ID

    override fun shortcut(): String = "O"

    override fun railPriority(): Int = 50

    override fun category(): String = "Objects"

    /**
     * Object placement is a bottom-bar activity. The floating toolbar is reserved for
     * selection modes, while the docked left rail is reserved for shared brush settings.
     */
    override fun surfaces(): MutableSet<StudioToolPlugin.ToolSurface> =
        EnumSet.of(StudioToolPlugin.ToolSurface.BOTTOM_BAR)

    override fun isConfigurable(): Boolean = true

    override fun renderSettings(context: StudioPanelContext?) {
        ImGui.textColored(
            PREFERENCES_COLOR,
            "${StudioIcons.TUNE}  Object Tool Preferences",
        )
        ImGui.separator()

        ImGui.sliderInt("Default Spawn Rotation", defaultRotation.data, 0, ROTATION_COUNT - 1)
        ImGui.checkbox("Snap to Tile Center", snapToTileCenter)
        ImGui.checkbox("Auto-Increment Rotation on Stamp", autoIncrementRotation)

        ImGui.spacing()
        if (ImGui.button("${StudioIcons.REFRESH}  Reset Object Tool Defaults")) {
            resetDefaults()
        }
    }

    override fun renderContextDrawer(context: StudioPanelContext?) {
        ImGui.textColored(
            PREFERENCES_COLOR,
            "${StudioIcons.OBJECT}  Object Placement Controls",
        )
        ImGui.sameLine(0.0f, 12.0f)
        ImGui.textDisabled(
            "Click in the 3D viewport to spawn or manipulate objects. " +
                "Use the Outliner or Object Viewer for full definitions.",
        )

        ImGui.spacing()
        if (
            ImGui.button(
                "${StudioIcons.ROTATE_RIGHT} Rotate CW (+90°)##rot-cw",
                150.0f,
                22.0f,
            )
        ) {
            rotateBy(1)
        }

        ImGui.sameLine(0.0f, 8.0f)
        if (
            ImGui.button(
                "${StudioIcons.ROTATE_LEFT} Rotate CCW (-90°)##rot-ccw",
                150.0f,
                22.0f,
            )
        ) {
            rotateBy(ROTATION_COUNT - 1)
        }

        ImGui.sameLine(0.0f, 12.0f)
        ImGui.textColored(
            ACTIVE_ROTATION_COLOR,
            "Active Rotation: ${defaultRotation.get() * DEGREES_PER_ROTATION}° " +
                "(${defaultRotation.get()})",
        )
    }

    private fun resetDefaults() {
        defaultRotation.set(0)
        snapToTileCenter.set(true)
        autoIncrementRotation.set(false)
    }

    /**
     * Rotation is stored in OSRS quarter-turn units (0..3), not degrees.
     * Keeping wrapping here prevents the two drawer buttons from drifting apart semantically.
     */
    private fun rotateBy(delta: Int) {
        defaultRotation.set((defaultRotation.get() + delta) % ROTATION_COUNT)
    }

    companion object {
        const val ID = "studio.tool.objects"
        const val ENGINE_TOOL_ID = "object.place"

        private const val ROTATION_COUNT = 4
        private const val DEGREES_PER_ROTATION = 90

        private val PREFERENCES_COLOR = StudioDrawColors.abgr(0xFF38BDF8.toInt())
        private val ACTIVE_ROTATION_COLOR = StudioDrawColors.abgr(0xFFD49B35.toInt())
    }
}
