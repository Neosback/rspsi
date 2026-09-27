package com.rspsi.studio.integration

import com.rspsi.editor.integration.ServerIntegrationService
import com.rspsi.server.ServerPathKey
import com.rspsi.studio.theme.StudioFonts
import com.rspsi.studio.theme.StudioWidgets
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import java.nio.file.Files
import java.nio.file.Path

/**
 * Read-only view of the project's server integration.
 *
 * The integration is chosen when a Studio project is imported or opened; this window never
 * reconnects, disconnects, rescans or toggles content features behind the project's back.
 */
class IntegrationCenterWindow {
    fun render(integrations: ServerIntegrationService, open: ImBoolean) {
        if (!open.get()) return
        StudioWidgets.windowBackdrop("project-integration")
        ImGui.setNextWindowSize(620f, 420f, ImGuiCond.FirstUseEver)
        if (ImGui.begin("Project Integration", open, ImGuiWindowFlags.NoCollapse)) {
            if (integrations.isConnected) renderConnected(integrations) else renderNoProject()
        }
        ImGui.end()
    }

    private fun renderConnected(integrations: ServerIntegrationService) {
        val session = integrations.activeSession().orElseThrow()
        StudioWidgets.section("OpenRune-Server")
        ImGui.pushFont(StudioFonts.mono(), 0.0f)
        ImGui.textColored(OK_COLOR, "[OK] ${session.provider().name()}")
        ImGui.text("Project root: ${session.projectRoot()}")
        ImGui.popFont()

        session.projectInspection().ifPresent { inspection ->
            ImGui.dummy(1.0f, 10.0f)
            if (inspection.revision().isNotBlank()) ImGui.text("Revision ${inspection.revision()}")
            ImGui.separator()
            StudioWidgets.section("Cache roles")
            cacheRole("LIVE", inspection.path(ServerPathKey.LIVE_CACHE).orElse(null))
            cacheRole("SERVER", inspection.path(ServerPathKey.SERVER_CACHE).orElse(null))

            ImGui.dummy(1.0f, 10.0f)
            StudioWidgets.section("Bound startup services")
            val capabilities = session.activeCapabilities()
            if (capabilities.isEmpty()) {
                ImGui.textDisabled("No optional integration services are bound.")
            } else {
                capabilities.sortedBy { it.name }.forEach { ImGui.bulletText(it.description()) }
            }
            if (inspection.diagnostics().isNotEmpty()) {
                ImGui.dummy(1.0f, 8.0f)
                ImGui.textDisabled("${inspection.diagnostics().size} project diagnostic(s) available.")
            }
        }

        ImGui.dummy(1.0f, 14.0f)
        ImGui.textWrapped("The active Studio project owns this connection. Content/source indexing is not part " +
            "of startup and will be activated only by a workspace that needs it.")
        ImGui.textDisabled("To use a different server project, close this project and choose " +
            "Import OpenRune-Server from the Projects screen.")
    }

    private fun renderNoProject() {
        StudioWidgets.section("No server project imported")
        ImGui.textWrapped("Server integration is project-owned. Return to the Projects screen and choose " +
            "Import OpenRune-Server to connect a checkout.")
        ImGui.dummy(1.0f, 8.0f)
        ImGui.textDisabled("Standalone cache projects stay standalone; there is no hidden server connection.")
    }

    private fun cacheRole(label: String, path: Path?) {
        val ready = path != null && Files.isDirectory(path)
        ImGui.text("${if (ready) "[OK] " else "[--] "}$label: ${path ?: "Not detected"}")
    }

    private companion object {
        /** ABGR green for the connected marker. */
        const val OK_COLOR = 0xFF66FF66.toInt()
    }
}
