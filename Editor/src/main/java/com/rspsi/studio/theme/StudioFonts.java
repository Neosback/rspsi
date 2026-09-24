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
    private static final short[] MATERIAL_ICON_RANGES = {
            (short) 0xe000, (short) 0xf8ff, 0
    };
    private static final short[] FA_ICON_RANGES = {
            (short) 0xf000, (short) 0xf8ff, 0
    };

    public static final float BASE_FONT_SIZE = 17.0f;

    private static ImFont ui;
    private static ImFont heading;
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
        uiConfig.setOversampleH(3);
        uiConfig.setOversampleV(3);
        uiConfig.setPixelSnapH(false);
        byte[] roboto = resource("/font/Roboto-Regular.ttf");
        ui = atlas.addFontFromMemoryTTF(roboto, BASE_FONT_SIZE * uiScale, uiConfig);

        ImFontConfig headingConfig = new ImFontConfig();
        headingConfig.setOversampleH(3);
        headingConfig.setOversampleV(3);
        headingConfig.setPixelSnapH(false);
        heading = atlas.addFontFromMemoryTTF(resource("/font/Roboto-Regular.ttf"),
                23.0f * uiScale, headingConfig);

        // Merge Google Fonts Material Icons into primary UI font
        ImFontConfig materialIconConfig = new ImFontConfig();
        materialIconConfig.setMergeMode(true);
        materialIconConfig.setPixelSnapH(true);
        materialIconConfig.setDstFont(ui);
        atlas.addFontFromMemoryTTF(resource("/font/MaterialIcons-Regular.ttf"),
                16.0f * uiScale, materialIconConfig, MATERIAL_ICON_RANGES);

        // Merge FontAwesome as secondary fallback
        ImFontConfig faConfig = new ImFontConfig();
        faConfig.setMergeMode(true);
        faConfig.setPixelSnapH(true);
        faConfig.setDstFont(ui);
        atlas.addFontFromMemoryTTF(resource("/font/fontawesome-webfont.ttf"),
                14.0f * uiScale, faConfig, FA_ICON_RANGES);

        // Standalone Icon font for larger rail and tool buttons (22px)
        ImFontConfig railIconConfig = new ImFontConfig();
        railIconConfig.setOversampleH(2);
        railIconConfig.setOversampleV(1);
        railIconConfig.setPixelSnapH(true);
        icon = atlas.addFontFromMemoryTTF(resource("/font/MaterialIcons-Regular.ttf"),
                22.0f * uiScale, railIconConfig, MATERIAL_ICON_RANGES);

        // Merge FontAwesome into icon font as fallback
        ImFontConfig railFaConfig = new ImFontConfig();
        railFaConfig.setMergeMode(true);
        railFaConfig.setPixelSnapH(true);
        railFaConfig.setDstFont(icon);
        atlas.addFontFromMemoryTTF(resource("/font/fontawesome-webfont.ttf"),
                20.0f * uiScale, railFaConfig, FA_ICON_RANGES);

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
        heading.setScale(1.0f / uiScale);
        icon.setScale(1.0f / uiScale);
        mono.setScale(1.0f / uiScale);
        io.setFontDefault(ui);
        uiConfig.destroy();
        headingConfig.destroy();
        materialIconConfig.destroy();
        faConfig.destroy();
        railIconConfig.destroy();
        railFaConfig.destroy();
        monoConfig.destroy();
    }

    public static ImFont ui() {
        return Objects.requireNonNull(ui, "Studio fonts are not initialized");
    }

    public static ImFont heading() {
        return Objects.requireNonNull(heading, "Studio fonts are not initialized");
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
