package com.rspsi.studio.integration;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.IntegrationProbe;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Native Dear ImGui window for managing server project integrations (e.g. OpenRune, RSMod).
 *
 * <p>Provides project folder scanning, capability negotiation, and live connection status.</p>
 */
public final class IntegrationCenterWindow {
    private final ImString projectPath = new ImString(512);
    private final Map<IntegrationCapability, ImBoolean> capabilityToggles = new HashMap<>();
    private IntegrationProbe currentProbe;
    private String statusMessage = "";

    public IntegrationCenterWindow() {
        for (IntegrationCapability cap : IntegrationCapability.values()) {
            capabilityToggles.put(cap, new ImBoolean(true));
        }
    }

    public void render(ServerIntegrationService integrations, ImBoolean open) {
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(open, "open");
        if (!open.get()) return;

        ImGui.setNextWindowSize(640, 520, imgui.flag.ImGuiCond.FirstUseEver);
        if (!ImGui.begin("Server Integration Center", open, ImGuiWindowFlags.NoCollapse)) {
            ImGui.end();
            return;
        }

        if (integrations.isConnected()) {
            renderConnectedView(integrations);
        } else {
            renderConnectView(integrations);
        }

        ImGui.end();
    }

    private void renderConnectedView(ServerIntegrationService integrations) {
        var session = integrations.activeSession().orElseThrow();
        StudioWidgets.section("Connected Server Project");

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textColored(0xFF66FF66, "[OK] Connected: " + session.provider().name());
        ImGui.text("Project Root: " + session.projectRoot().toString());
        ImGui.popFont();

        ImGui.separator();
        StudioWidgets.section("Active Capabilities");
        for (IntegrationCapability cap : session.activeCapabilities()) {
            ImGui.bulletText("[x] " + cap.description());
        }

        ImGui.dummy(1.0f, 16.0f);
        if (ImGui.button("Disconnect Server Project", 200, 30)) {
            integrations.disconnect();
            currentProbe = null;
            statusMessage = "Disconnected from server project.";
        }
    }

    private void renderConnectView(ServerIntegrationService integrations) {
        StudioWidgets.section("Connect Server Project");
        ImGui.textWrapped("Connect OpenRune Studio to a server repository to enable GameVals, RSCM mappings, content scripts, and NPC spawns.");

        ImGui.dummy(1.0f, 6.0f);
        ImGui.inputTextWithHint("##server-path", "Path to server repository root (e.g. /path/to/OpenRune-Server)", projectPath);
        ImGui.sameLine();
        if (ImGui.button("Scan Project")) {
            scanProject(integrations);
        }

        if (!statusMessage.isEmpty()) {
            ImGui.textDisabled(statusMessage);
        }

        if (currentProbe != null) {
            renderProbeDetails(integrations);
        }
    }

    private void scanProject(ServerIntegrationService integrations) {
        String raw = projectPath.get().trim();
        if (raw.isEmpty()) {
            statusMessage = "Please enter a project directory path.";
            return;
        }
        Path path = Path.of(raw).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            statusMessage = "Directory not found: " + path;
            currentProbe = null;
            return;
        }

        var probeOpt = integrations.probe(path);
        if (probeOpt.isPresent()) {
            currentProbe = probeOpt.get();
            statusMessage = "Detected: " + currentProbe.serverName();
            for (IntegrationCapability cap : IntegrationCapability.values()) {
                capabilityToggles.get(cap).set(currentProbe.supports(cap));
            }
        } else {
            currentProbe = null;
            statusMessage = "No compatible server integration provider recognized this directory layout.";
        }
    }

    private void renderProbeDetails(ServerIntegrationService integrations) {
        ImGui.separator();
        StudioWidgets.section("Detected Server: " + currentProbe.serverName());

        ImGui.textDisabled("Select features to enable:");
        if (ImGui.smallButton("Recommended")) {
            for (IntegrationCapability cap : IntegrationCapability.values()) {
                capabilityToggles.get(cap).set(currentProbe.supports(cap));
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Minimal")) {
            for (IntegrationCapability cap : IntegrationCapability.values()) {
                capabilityToggles.get(cap).set(cap == IntegrationCapability.SYMBOLS);
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Everything")) {
            for (IntegrationCapability cap : IntegrationCapability.values()) {
                capabilityToggles.get(cap).set(true);
            }
        }

        for (IntegrationCapability cap : IntegrationCapability.values()) {
            if (currentProbe.supports(cap)) {
                ImGui.checkbox(cap.description(), capabilityToggles.get(cap));
            }
        }

        ImGui.dummy(1.0f, 12.0f);
        if (ImGui.button("Connect Project", 160, 32)) {
            Set<IntegrationCapability> enabled = EnumSet.noneOf(IntegrationCapability.class);
            for (Map.Entry<IntegrationCapability, ImBoolean> entry : capabilityToggles.entrySet()) {
                if (entry.getValue().get()) {
                    enabled.add(entry.getKey());
                }
            }
            IntegrationOptions options = new IntegrationOptions(currentProbe.projectRoot(), enabled, Map.of());
            integrations.connect(currentProbe.projectRoot(), options);
        }
    }
}
