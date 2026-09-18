package com.rspsi.studio.theme;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Loads the native shell's readable UI and diagnostic font set. */
public final class StudioFonts {
    private static final short[] ICON_RANGES = {
            (short) 0xf000, (short) 0xf8ff, 0
    };

    private static ImFont ui;
    private static ImFont icon;
    private static ImFont mono;

    private StudioFonts() {
    }

    /** Installs the font atlas after the Dear ImGui context has been created. */
    public static void apply(long windowHandle) {
        ImGuiIO io = ImGui.getIO();
        ImFontAtlas atlas = io.getFonts();
        atlas.clear();

        float[] scaleX = {1.0f};
        float[] scaleY = {1.0f};
        GLFW.glfwGetWindowContentScale(windowHandle, scaleX, scaleY);
        float uiScale = Math.max(1.0f, Math.max(scaleX[0], scaleY[0]));

        ImFontConfig uiConfig = new ImFontConfig();
        uiConfig.setOversampleH(2);
        uiConfig.setOversampleV(1);
        uiConfig.setPixelSnapH(true);
        byte[] roboto = resource("/font/Roboto-Regular.ttf");
        ui = atlas.addFontFromMemoryTTF(roboto, 15.0f * uiScale, uiConfig);

        ImFontConfig iconConfig = new ImFontConfig();
        iconConfig.setMergeMode(true);
        iconConfig.setPixelSnapH(true);
        iconConfig.setDstFont(ui);
        atlas.addFontFromMemoryTTF(resource("/font/fontawesome-webfont.ttf"),
                14.0f * uiScale, iconConfig, ICON_RANGES);

        ImFontConfig railIconConfig = new ImFontConfig();
        railIconConfig.setOversampleH(2);
        railIconConfig.setOversampleV(1);
        railIconConfig.setPixelSnapH(true);
        icon = atlas.addFontFromMemoryTTF(resource("/font/fontawesome-webfont.ttf"),
                18.0f * uiScale, railIconConfig, ICON_RANGES);

        ImFontConfig monoConfig = new ImFontConfig();
        monoConfig.setOversampleH(2);
        monoConfig.setOversampleV(1);
        monoConfig.setPixelSnapH(true);
        mono = atlas.addFontFromMemoryTTF(resource("/font/JetBrainsMono-Regular.ttf"),
                13.0f * uiScale, monoConfig);

        if (!atlas.build()) {
            throw new IllegalStateException("Unable to build the OpenRune Studio font atlas");
        }
        // GLFW reports Retina framebuffer scale separately from ImGui's
        // logical window coordinates. Keep the atlas sharp at 2x, but keep
        // the font metrics in logical points so dock rails do not become
        // twice as wide or clip their labels on macOS.
        ui.setScale(1.0f / uiScale);
        icon.setScale(1.0f / uiScale);
        mono.setScale(1.0f / uiScale);
        io.setFontDefault(ui);
        uiConfig.destroy();
        iconConfig.destroy();
        railIconConfig.destroy();
        monoConfig.destroy();
    }

    public static ImFont ui() {
        return Objects.requireNonNull(ui, "Studio fonts are not initialized");
    }

    public static ImFont mono() {
        return Objects.requireNonNull(mono, "Studio fonts are not initialized");
    }

    public static ImFont icon() {
        return Objects.requireNonNull(icon, "Studio fonts are not initialized");
    }

    private static byte[] resource(String path) {
        try (InputStream input = StudioFonts.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing native UI resource: " + path);
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read native UI resource: " + path, failure);
        }
    }
}
