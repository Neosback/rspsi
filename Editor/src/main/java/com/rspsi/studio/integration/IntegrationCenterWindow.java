package com.rspsi.studio.integration;

import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.server.ServerPathKey;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.nio.file.Files;
import java.util.Objects;

/**
 * Read-only project-owned integration status.
 *
 * <p>Server integration is selected when a Studio project is imported/opened.
 * This window intentionally does not reconnect, disconnect, rescan, or toggle
 * content features behind the active project's back.</p>
 */
public final class IntegrationCenterWindow {

    public void render(ServerIntegrationService integrations, ImBoolean open) {
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(open, "open");
        if (!open.get()) return;

        ImGui.setNextWindowSize(620, 420, imgui.flag.ImGuiCond.FirstUseEver);
        if (!ImGui.begin("Project Integration", open, ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }

        if (integrations.isConnected()) {
            renderConnectedView(integrations);
        } else {
            renderNoProjectView();
        }

        ImGui.end();
    }

    private static void renderConnectedView(ServerIntegrationService integrations) {
        var session = integrations.activeSession().orElseThrow();
        StudioWidgets.section("OpenRune-Server");

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textColored(0xFF66FF66, "[OK] " + session.provider().name());
        ImGui.text("Project root: " + session.projectRoot());
        ImGui.popFont();

        session.projectInspection().ifPresent(inspection -> {
            ImGui.dummy(1.0f, 10.0f);
            if (!inspection.revision().isBlank()) {
                ImGui.text("Revision " + inspection.revision());
            }

            ImGui.separator();
            StudioWidgets.section("Cache roles");
            inspection.path(ServerPathKey.LIVE_CACHE).ifPresentOrElse(
                    path -> statusLine("LIVE", path.toString(), Files.isDirectory(path)),
                    () -> statusLine("LIVE", "Not detected", false));
            inspection.path(ServerPathKey.SERVER_CACHE).ifPresentOrElse(
                    path -> statusLine("SERVER", path.toString(), Files.isDirectory(path)),
                    () -> statusLine("SERVER", "Not detected", false));

            ImGui.dummy(1.0f, 10.0f);
            StudioWidgets.section("Bound startup services");
            if (session.activeCapabilities().isEmpty()) {
                ImGui.textDisabled("No optional integration services are bound.");
            } else {
                session.activeCapabilities().stream()
                        .sorted(java.util.Comparator.comparing(Enum::name))
                        .forEach(capability -> ImGui.bulletText(capability.description()));
            }

            if (!inspection.diagnostics().isEmpty()) {
                ImGui.dummy(1.0f, 8.0f);
                ImGui.textDisabled(inspection.diagnostics().size()
                        + " project diagnostic(s) available.");
            }
        });

        ImGui.dummy(1.0f, 14.0f);
        ImGui.textWrapped(
                "The active Studio project owns this connection. Content/source indexing is "
                        + "not part of startup and will be activated only by a workspace that "
                        + "needs it.");
        ImGui.textDisabled(
                "To use a different server project, close this project and choose "
                        + "Import OpenRune-Server from the Projects screen.");
    }

    private static void renderNoProjectView() {
        StudioWidgets.section("No server project imported");
        ImGui.textWrapped(
                "Server integration is project-owned. Return to the Projects screen and choose "
                        + "Import OpenRune-Server to connect a checkout.");
        ImGui.dummy(1.0f, 8.0f);
        ImGui.textDisabled(
                "Standalone cache projects stay standalone; there is no hidden server connection.");
    }

    private static void statusLine(String label, String value, boolean ready) {
        ImGui.text((ready ? "[OK] " : "[--] ") + label + ": " + value);
    }
}
