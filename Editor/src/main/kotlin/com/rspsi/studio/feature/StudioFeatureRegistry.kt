package com.rspsi.studio.feature

import com.rspsi.editor.plugin.EditorPluginRegistry
import com.rspsi.editor.plugin.EditorToolRegistration
import com.rspsi.editor.plugin.ToolUiDescriptor
import com.rspsi.editor.plugin.ui.EditorUiNode
import com.rspsi.editor.plugin.ui.ToolUiContent
import com.rspsi.studio.feature.tool.HeightSculptorToolUi
import com.rspsi.studio.feature.tool.MultiSelectToolUi
import com.rspsi.studio.feature.tool.ObjectPlacementToolUi
import com.rspsi.studio.feature.tool.PathToolUi
import com.rspsi.studio.feature.tool.SingleSelectToolUi
import com.rspsi.studio.feature.tool.TilePainterToolUi
import com.rspsi.studio.theme.StudioIcons
import com.rspsi.studio.ui.SelectionOverlay
import com.rspsi.studio.ui.StudioPanel
import com.rspsi.studio.ui.StudioPanelContext
import com.rspsi.studio.ui.hud.BrushSettingsHud
import imgui.ImDrawList
import org.slf4j.LoggerFactory
import java.util.EnumSet
import java.util.Optional
import java.util.function.Consumer

/**
 * Every Studio feature and tool button, in one explicit list.
 *
 * This is the single place Studio chrome asks "which tools exist, where do their buttons
 * go, and who draws overlays and HUDs". Tool behavior comes from the core modules'
 * `EditorToolRegistration`s; a native [StudioToolUi] adds Studio chrome for some of them,
 * and any core tool without one still gets a button from its neutral UI descriptor
 * ([toolViews]). A feature can be hidden (e.g. the tile info HUD) but is never unloaded.
 */
class StudioFeatureRegistry {
    private val features = LinkedHashMap<String, StudioFeature>()
    private val enabled = HashMap<String, Boolean>()
    private var ownedPanelSink: Consumer<StudioPanel>? = null
    private var editorRegistry: EditorPluginRegistry? = null

    init {
        register(SingleSelectToolUi())
        register(MultiSelectToolUi())
        register(TilePainterToolUi())
        register(HeightSculptorToolUi())
        register(PathToolUi())
        register(ObjectPlacementToolUi())
        register(BrushSettingsHud())
        // Object selection buttons feed the selection that SelectionOverlay highlights, so
        // they are maintained together with it.
        register(SelectionOverlay())
        register(SelectionOverlay.SingleObjectSelectToolUi())
        register(SelectionOverlay.MultiObjectSelectToolUi())
    }

    /**
     * Binds the core modules' tool registry of the loaded map (or null when no map is
     * loaded), so core tools without native chrome still appear in [toolViews].
     */
    @Synchronized
    fun bindEditorRegistry(registry: EditorPluginRegistry?) {
        editorRegistry = registry
    }

    /**
     * Where tools' owned panels go: the right sidebar's panel manager. Panels already
     * registered are delivered immediately, later ones as they register.
     */
    fun setOwnedPanelSink(sink: Consumer<StudioPanel>?) {
        ownedPanelSink = sink
        if (sink != null) toolUisInOrder(includeHidden = true).forEach { it.ownedPanel().ifPresent(sink) }
    }

    /** Adds a feature. Registering an id again replaces the earlier feature. */
    @Synchronized
    fun register(feature: StudioFeature) {
        features[feature.id()] = feature
        enabled.putIfAbsent(feature.id(), true)
        LOGGER.debug("Registered Studio feature {} [{}]", feature.name(), feature.id())
        val sink = ownedPanelSink
        if (feature is StudioToolUi && sink != null) feature.ownedPanel().ifPresent(sink)
    }

    /** Whether a feature is shown. Hidden features skip every render hook and tool strip. */
    @Synchronized
    fun isEnabled(featureId: String): Boolean = enabled[featureId] ?: false

    @Synchronized
    fun setEnabled(featureId: String, value: Boolean) {
        if (featureId in features) enabled[featureId] = value
    }

    @Synchronized
    fun feature(featureId: String): Optional<StudioFeature> = Optional.ofNullable(features[featureId])

    /** Shown native tool UIs, by rail priority. */
    @Synchronized
    fun toolUis(): List<StudioToolUi> = toolUisInOrder(includeHidden = false)

    /**
     * The tool catalog Studio chrome draws: native tool UIs first claim their engine tool
     * ids, then every other core tool is projected from its neutral descriptor, all sorted
     * by rail priority then id.
     */
    @Synchronized
    fun toolViews(): List<StudioToolView> {
        val views = ArrayList<StudioToolView>()
        val represented = LinkedHashSet<String>()
        for (tool in toolUis()) {
            views += StudioToolView.fromNative(tool)
            represented += tool.toolIds()
            represented += tool.id()
        }
        editorRegistry?.toolRegistrations()?.forEach { registration ->
            if (registration.id() !in represented) views += StudioToolView.fromNeutral(registration)
        }
        views.sortWith(compareBy<StudioToolView> { it.railPriority }.thenBy { it.id })
        return views
    }

    /** The catalog entry for an engine tool id or a tool UI id. */
    @Synchronized
    fun toolView(toolId: String?): Optional<StudioToolView> {
        if (toolId == null) return Optional.empty()
        return Optional.ofNullable(toolViews().firstOrNull { toolId in it.toolIds || it.id == toolId })
    }

    /** The native tool UI for an engine tool id or a tool UI id, shown or hidden. */
    @Synchronized
    fun toolUi(toolId: String?): Optional<StudioToolUi> {
        if (toolId == null) return Optional.empty()
        return Optional.ofNullable(
            toolUisInOrder(includeHidden = true).firstOrNull { toolId in it.toolIds() || it.id() == toolId },
        )
    }

    /** Whether the active tool uses the shared Brush Settings rail. */
    @Synchronized
    fun usesSharedBrushSettings(toolId: String?): Boolean =
        toolView(toolId).map { it.brushUiMode }.orElse(StudioToolUi.BrushUiMode.NONE) ==
            StudioToolUi.BrushUiMode.SHARED_SETTINGS

    /** Draws every shown feature's viewport overlay. One failing feature never hides the rest. */
    fun renderOverlays(drawList: ImDrawList, context: StudioPanelContext) =
        forEachShown("overlay") { it.renderOverlay(drawList, context) }

    /** Draws HUDs in the HUD manager's stacking order. */
    fun renderHUDs(context: StudioPanelContext) {
        val ordered = shown().toMutableList()
        context.huds()?.let { huds -> ordered.sortBy { huds.priority(it.id()) } }
        for (feature in ordered) guarded(feature, "HUD") { feature.renderHUD(context) }
    }

    /** Draws every shown feature's floating windows. */
    fun renderFloating(context: StudioPanelContext) = forEachShown("floating") { it.renderFloating(context) }

    @Synchronized
    private fun shown(): List<StudioFeature> = features.values.filter { enabled[it.id()] == true }

    @Synchronized
    private fun toolUisInOrder(includeHidden: Boolean): List<StudioToolUi> =
        features.values.filterIsInstance<StudioToolUi>()
            .filter { includeHidden || enabled[it.id()] == true }
            .sortedBy { it.railPriority() }

    private inline fun forEachShown(hook: String, action: (StudioFeature) -> Unit) {
        for (feature in shown()) guarded(feature, hook) { action(feature) }
    }

    private inline fun guarded(feature: StudioFeature, hook: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            LOGGER.error("Studio feature {} {} failed: {}", feature.id(), hook, failure.message, failure)
        }
    }

    /**
     * One entry of the tool catalog: presentation metadata only. Engine behavior stays the
     * neutral `EditorTool` registered under [toolIds].
     */
    @JvmRecord
    data class StudioToolView(
        val id: String,
        val toolId: String,
        val toolIds: Set<String>,
        val name: String,
        val icon: String,
        val shortcut: String,
        val railPriority: Int,
        val surfaces: Set<StudioToolUi.ToolSurface>,
        val brushUiMode: StudioToolUi.BrushUiMode,
        val hasContextDrawerContent: Boolean,
        val content: ToolUiContent,
        /** The native chrome, or null for a core tool projected from its descriptor. */
        val nativeToolUi: StudioToolUi?,
    ) {
        fun isNativeProjection(): Boolean = nativeToolUi != null

        fun contextDrawerNode(): Optional<EditorUiNode> = content.contextDrawerNode()

        fun quickPaletteNode(): Optional<EditorUiNode> = content.quickPaletteNode()

        fun inspectorNode(): Optional<EditorUiNode> = content.inspectorNode()

        companion object {
            fun fromNative(tool: StudioToolUi): StudioToolView =
                StudioToolView(
                    tool.id(), tool.toolId(), tool.toolIds().toSet(), tool.name(), tool.icon(),
                    tool.shortcut(), tool.railPriority(), tool.surfaces().toSet(), tool.brushUiMode(),
                    tool.hasContextDrawerContent(), ToolUiContent.empty(), tool,
                )

            fun fromNeutral(registration: EditorToolRegistration): StudioToolView {
                val ui = registration.ui()
                val surfaces = EnumSet.noneOf(StudioToolUi.ToolSurface::class.java)
                if (ui.appearsOn(ToolUiDescriptor.ToolSurface.BOTTOM_BAR)) {
                    surfaces += StudioToolUi.ToolSurface.BOTTOM_BAR
                }
                if (ui.appearsOn(ToolUiDescriptor.ToolSurface.FLOATING_TOOLBAR)) {
                    surfaces += StudioToolUi.ToolSurface.FLOATING_TOOLBAR
                }
                val brushUi = when (ui.brushUiMode()) {
                    ToolUiDescriptor.BrushUiMode.NONE -> StudioToolUi.BrushUiMode.NONE
                    ToolUiDescriptor.BrushUiMode.SHARED_SETTINGS -> StudioToolUi.BrushUiMode.SHARED_SETTINGS
                    ToolUiDescriptor.BrushUiMode.TOOL_OWNED -> StudioToolUi.BrushUiMode.TOOL_OWNED
                }
                val iconName = registration.icon()
                val icon = if (iconName.isNullOrBlank()) StudioIcons.OBJECT else StudioIcons.byName(iconName, iconName)
                return StudioToolView(
                    registration.id(), registration.id(), setOf(registration.id()), registration.label(), icon,
                    registration.shortcut() ?: "", registration.order(), surfaces.toSet(), brushUi,
                    ui.content().hasContextDrawer(), ui.content(), null,
                )
            }
        }
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(StudioFeatureRegistry::class.java)
    }
}
