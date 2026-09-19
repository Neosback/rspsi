package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheDecoderSummary;
import com.rspsi.cache.workspace.CacheDecoderSummary.IndexEntry;
import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Dear ImGui dashboard for cache selection, validation, and workspace activation. */
public final class DashboardView {
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final ImString cachePath = new ImString(512);
    private final ImString region = new ImString("50,50", 32);

    public DashboardView(String initialPath) {
        cachePath.set(initialPath == null ? "" : initialPath);
    }

    private void renderInternal(CacheSessionStatus status,
                                Consumer<Path> loadCache,
                                Runnable openMapEditor,
                                Runnable openInterfaceStudio,
                                Runnable openObjectStudio,
                                com.rspsi.editor.integration.ServerIntegrationService integrations,
                                Runnable openIntegrationCenter) {
        Objects.requireNonNull(status, "cache status");
        Objects.requireNonNull(loadCache, "load cache callback");
        Objects.requireNonNull(openMapEditor, "open workspace callback");

        imgui.ImGuiViewport mainViewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(mainViewport.getPosX(), mainViewport.getPosY());
        ImGui.setNextWindowSize(mainViewport.getSizeX(), mainViewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("OpenRune Studio", flags)) {
            ImGui.end();
            return;
        }

        float contentWidth = Math.min(920.0f, ImGui.getContentRegionAvailX());
        float margin = Math.max(24.0f, (ImGui.getContentRegionAvailX() - contentWidth) * 0.5f);
        ImGui.dummy(1.0f, 20.0f);
        ImGui.indent(margin);

        // Header Title
        ImGui.pushStyleColor(ImGuiCol.Text, 0.88f, 0.91f, 0.98f, 1.0f);
        ImGui.text("OPENRUNE STUDIO");
        ImGui.popStyleColor();
        ImGui.textDisabled("Cache-first editing for RuneScape worlds");
        ImGui.dummy(1.0f, 8.0f);

        // Cache Configuration Section
        ImGui.separatorText("Cache Configuration");
        ImGui.textDisabled("Specify the OSRS cache folder containing main_file_cache.dat2 and .idx files.");
        ImGui.inputTextWithHint("##cache-path", "Path to an OSRS cache directory (e.g. /path/to/cache)", cachePath);
        ImGui.sameLine();
        boolean loading = status.state() == CacheSessionState.LOADING;
        ImGui.beginDisabled(loading || cachePath.isEmpty());
        if (ImGui.button(loading ? "Loading..." : "Load cache")) {
            Path path = Path.of(cachePath.get().trim()).toAbsolutePath().normalize();
            loadCache.accept(path);
        }
        ImGui.endDisabled();

        // Path Validation Warning Cards
        renderPathWarnings(status);

        // Status / Loading / Ready panels
        renderStatus(status);

        // Server Integration Section
        renderServerIntegrationSection(integrations, openIntegrationCenter);

        ImGui.dummy(1.0f, 12.0f);
        ImGui.separatorText("Workspaces");
        ImGui.textWrapped("Open a workspace after the cache has been validated and indexed.");
        ImGui.inputTextWithHint("##region", "Region X,Y or region ID", region);
        ImGui.textDisabled("Example: 50,50 opens the Lumbridge region.");

        ImGui.spacing();
        ImGui.beginDisabled(status.state() != CacheSessionState.READY);
        if (ImGui.button("Map Editor", 130, 32)) openMapEditor.run();
        ImGui.sameLine();
        if (ImGui.button("Interface Studio", 140, 32) && openInterfaceStudio != null) {
            openInterfaceStudio.run();
        }
        ImGui.sameLine();
        if (ImGui.button("Object Studio", 130, 32) && openObjectStudio != null) {
            openObjectStudio.run();
        }
        ImGui.endDisabled();

        ImGui.dummy(1.0f, 24.0f);
        ImGui.unindent(margin);
        ImGui.end();
    }

    public void render(CacheSessionStatus status,
                       Consumer<Path> loadCache,
                       Runnable openMapEditor) {
        render(status, loadCache, openMapEditor, null, null, null, null);
    }

    public void render(CacheSessionStatus status,
                       Consumer<Path> loadCache,
                       Runnable openMapEditor,
                       Runnable openInterfaceStudio,
                       Runnable openObjectStudio,
                       com.rspsi.editor.integration.ServerIntegrationService integrations,
                       Runnable openIntegrationCenter) {
        renderInternal(status, loadCache, openMapEditor, openInterfaceStudio, openObjectStudio, integrations, openIntegrationCenter);
    }

    private void renderServerIntegrationSection(com.rspsi.editor.integration.ServerIntegrationService integrations,
                                                Runnable openIntegrationCenter) {
        ImGui.dummy(1.0f, 8.0f);
        ImGui.separatorText("Server Integration");
        if (integrations != null && integrations.isConnected()) {
            var session = integrations.activeSession().get();
            ImGui.textColored(0xFF66FF66, "● Connected: " + session.provider().name());
            ImGui.textDisabled("Root: " + session.projectRoot() + " (" + session.activeCapabilities().size() + " capabilities active)");
            if (openIntegrationCenter != null) {
                if (ImGui.button("Configure Integration")) openIntegrationCenter.run();
                ImGui.sameLine();
                if (ImGui.button("Disconnect")) integrations.disconnect();
            }
        } else {
            ImGui.textDisabled("No server project connected. Connect an OpenRune or custom server repository for symbols and spawns.");
            if (openIntegrationCenter != null) {
                if (ImGui.button("Connect Project...")) openIntegrationCenter.run();
            }
        }
    }

    private void renderPathWarnings(CacheSessionStatus status) {
        if (status.state() == CacheSessionState.READY || status.state() == CacheSessionState.LOADING) {
            return;
        }

        String raw = cachePath.get().trim();
        if (raw.isEmpty()) {
            renderWarningCard("CACHE PATH REQUIRED",
                    "No cache path is currently configured. Enter the file path to your OSRS cache folder above and click 'Load cache' to decode and verify all cache contents.");
            return;
        }

        try {
            Path path = Path.of(raw);
            if (!Files.exists(path)) {
                renderWarningCard("DIRECTORY NOT FOUND",
                        "The specified cache path does not exist on disk:\n" + path.toAbsolutePath().normalize());
            } else if (!Files.isDirectory(path)) {
                renderWarningCard("NOT A DIRECTORY",
                        "The specified path points to a file, not a cache directory:\n" + path.toAbsolutePath().normalize());
            } else if (!Files.exists(path.resolve("main_file_cache.dat2"))) {
                renderWarningCard("INVALID CACHE DIRECTORY",
                        "Directory exists, but 'main_file_cache.dat2' was not found in this folder.\nPlease point to the folder containing OSRS cache data files (.dat2 and .idx).");
            }
        } catch (Exception ex) {
            renderWarningCard("INVALID PATH FORMAT",
                    "The entered path string is not a valid filesystem path: " + ex.getMessage());
        }
    }

    private static void renderStatus(CacheSessionStatus status) {
        switch (status.state()) {
            case EMPTY -> {
                // Warning banner handled by renderPathWarnings above
            }
            case LOADING -> {
                ImGui.dummy(1.0f, 6.0f);
                ImGui.text("Loading cache: " + status.message());
                ImGui.progressBar((float) status.progress(), -1, 0,
                        status.phase().name().replace('_', ' '));
            }
            case READY -> status.currentSession().ifPresent(session -> {
                ImGui.dummy(1.0f, 6.0f);
                renderReadyHeader(session);
                ImGui.dummy(1.0f, 6.0f);
                renderDecoderInspection(session.decoderSummary());
            });
            case FAILED -> {
                ImGui.dummy(1.0f, 6.0f);
                String detail = status.failure() == null ? status.message() : status.failure().getMessage();
                renderErrorCard("CACHE LOAD FAILED",
                        detail == null || detail.isBlank() ? status.message() : detail);
            }
        }
    }

    private static void renderReadyHeader(LoadedOsrsCacheSession session) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.22f, 0.12f, 0.65f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.25f, 0.85f, 0.40f, 0.90f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12.0f, 10.0f);

        if (ImGui.beginChild("##ready-header", 0.0f, 74.0f, true, ImGuiWindowFlags.NoScrollbar)) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.35f, 0.95f, 0.50f, 1.0f);
            ImGui.textUnformatted("[OK] CACHE READY & VERIFIED  ·  " + session.backendName());
            ImGui.popStyleColor();
            ImGui.text("Revision " + session.identity().revision()
                    + "  ·  " + fmt(session.mapCount()) + " map groups  ·  FileStore decoders operational");
            ImGui.textDisabled(session.path().toString());
            ImGui.endChild();
        }

        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private static void renderDecoderInspection(CacheDecoderSummary summary) {
        if (summary == null) return;

        ImGui.separatorText("FileStore Cache Inspection & Decoder Verification");

        // Health Status Badge Row
        if (summary.allDecodersPassed()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.35f, 0.95f, 0.50f, 1.0f);
            ImGui.textUnformatted("[OK] All 18 FileStore Decoders Verified Operational [100% OK]");
            ImGui.popStyleColor();
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.82f, 0.25f, 1.0f);
            ImGui.textUnformatted("[!] Cache Read with " + summary.failures().size() + " Decoder Diagnostic Note(s)");
            ImGui.popStyleColor();
        }
        ImGui.textDisabled("Total Decoded Definitions: " + fmt(summary.totalDefinitionsCount())
                + "  ·  Total Audio Assets: " + fmt(summary.totalAudioCount())
                + "  ·  Total Archives: " + fmt(summary.totalArchives())
                + " across " + summary.totalIndices() + " indices");

        ImGui.dummy(1.0f, 6.0f);

        // 2-Column Categorized Metric Grid
        if (ImGui.beginTable("##decoder-grid", 2, ImGuiTableFlags.SizingStretchSame)) {
            ImGui.tableNextRow();

            // Column 1: Audio & Sound Decoders
            ImGui.tableNextColumn();
            renderStatCard("AUDIO & SOUND DECODERS", 0.35f, 0.75f, 0.95f, 155.0f, () -> {
                statRow("Sound Effects (Synth/Wave):", summary.soundEffects());
                statRow("Vorbis Audio Samples:", summary.vorbisSounds());
                statRow("Music Tracks (MIDI):", summary.musicTracks());
                statRow("Music Jingles:", summary.musicJingles());
                statRow("Music Patches (SoundFont):", summary.musicPatches());
            });

            // Column 2: Visual Media & Graphics
            ImGui.tableNextColumn();
            renderStatCard("VISUAL MEDIA & GRAPHICS", 0.95f, 0.65f, 0.35f, 155.0f, () -> {
                statRow("Sprites (Index 8):", fmt(summary.spriteGroups()) + " groups (" + fmt(summary.totalSubSprites()) + " frames)");
                statRow("3D Models (Index 7):", summary.models());
                statRow("Textures & Materials:", summary.textures());
                statRow("Minimap Map Scenes:", summary.mapScenes());
                statRow("Fonts (Index 13):", summary.fonts());
            });

            ImGui.tableNextRow();

            // Column 1: World & Environment
            ImGui.tableNextColumn();
            renderStatCard("WORLD & ENVIRONMENT", 0.45f, 0.90f, 0.55f, 260.0f, () -> {
                statRow("Maps & Regions (Index 5):", summary.maps());
                statRow("Underlay Floor Types:", summary.underlays());
                statRow("Overlay Floor Types:", summary.overlays());
                statRow("World Map Areas (Index 19):", summary.worldMapAreas());
            });

            // Column 2: Gameplay Definitions & Logic
            ImGui.tableNextColumn();
            renderStatCard("GAME DEFINITIONS & LOGIC", 0.85f, 0.55f, 0.95f, 260.0f, () -> {
                statRow("Objects / Scenery:", summary.objects());
                statRow("Items & Equipment:", summary.items());
                statRow("NPCs / Monsters:", summary.npcs());
                statRow("Sequences (Animations):", summary.sequences());
                statRow("Spot Animations (GFX):", summary.spotAnims());
                statRow("Identity Kits (Appearance):", summary.identityKits());
                statRow("Inventories:", summary.inventories());
                statRow("VarBits & Variables:", summary.varbits());
                statRow("Enums & Data Structs:", fmt(summary.enums()) + " / " + fmt(summary.structs()));
                statRow("Interfaces & CS2 Scripts:", fmt(summary.interfaces()) + " / " + fmt(summary.clientScripts()));
                statRow("DB Tables (Index 21):", summary.dbTables());
            });

            ImGui.endTable();
        }

        // Collapsible All 25 Indices Breakdown Table
        ImGui.dummy(1.0f, 4.0f);
        if (ImGui.collapsingHeader("Raw Cache Indices Breakdown (" + summary.totalIndices() + " indices, " + fmt(summary.totalArchives()) + " archives)")) {
            if (ImGui.beginTable("##raw-indices-table", 3,
                    ImGuiTableFlags.RowBg | ImGuiTableFlags.BordersInnerH | ImGuiTableFlags.BordersOuter)) {
                ImGui.tableSetupColumn("Index ID", 0, 80.0f);
                ImGui.tableSetupColumn("Category / Name", 0, 320.0f);
                ImGui.tableSetupColumn("Archives", 0, 120.0f);
                ImGui.tableHeadersRow();

                for (IndexEntry entry : summary.indices()) {
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    ImGui.text(String.valueOf(entry.id()));
                    ImGui.tableNextColumn();
                    ImGui.text(entry.name());
                    ImGui.tableNextColumn();
                    if (entry.archiveCount() > 0) {
                        ImGui.text(fmt(entry.archiveCount()));
                    } else {
                        ImGui.textDisabled("0");
                    }
                }
                ImGui.endTable();
            }
        }
    }

    private static void renderStatCard(String title, float r, float g, float b, float height, Runnable rows) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.12f, 0.14f, 0.18f, 0.80f);
        ImGui.pushStyleColor(ImGuiCol.Border, r * 0.7f, g * 0.7f, b * 0.7f, 0.50f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 5.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 10.0f, 8.0f);

        if (ImGui.beginChild("##card-" + title, 0.0f, height, true, ImGuiWindowFlags.NoScrollbar)) {
            ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, 1.0f);
            ImGui.text(title);
            ImGui.popStyleColor();
            ImGui.separator();
            rows.run();
            ImGui.endChild();
        }

        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private static void statRow(String label, int count) {
        statRow(label, fmt(count));
    }

    private static void statRow(String label, String value) {
        ImGui.textUnformatted(label);
        float textWidth = ImGui.calcTextSize(value).x;
        float alignX = Math.max(ImGui.getCursorPosX() + 10.0f, ImGui.getContentRegionAvailX() - textWidth - 6.0f);
        ImGui.sameLine(alignX);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.96f, 0.98f, 1.0f);
        ImGui.textUnformatted(value);
        ImGui.popStyleColor();
    }

    private static void renderWarningCard(String title, String message) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.24f, 0.18f, 0.05f, 0.75f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.95f, 0.72f, 0.15f, 0.95f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12.0f, 10.0f);

        if (ImGui.beginChild("##warning-card", 0.0f, 76.0f, true, ImGuiWindowFlags.NoScrollbar)) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.82f, 0.25f, 1.0f);
            ImGui.textUnformatted("[!] " + title);
            ImGui.popStyleColor();
            ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.92f, 0.92f, 1.0f);
            ImGui.textWrapped(message);
            ImGui.popStyleColor();
            ImGui.endChild();
        }

        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private static void renderErrorCard(String title, String message) {
        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.26f, 0.08f, 0.08f, 0.75f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.95f, 0.25f, 0.25f, 0.95f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 6.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 1.5f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 12.0f, 10.0f);

        if (ImGui.beginChild("##error-card", 0.0f, 76.0f, true, ImGuiWindowFlags.NoScrollbar)) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.35f, 0.35f, 1.0f);
            ImGui.textUnformatted("[X] " + title);
            ImGui.popStyleColor();
            ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.92f, 0.92f, 1.0f);
            ImGui.textWrapped(message);
            ImGui.popStyleColor();
            ImGui.endChild();
        }

        ImGui.popStyleVar(3);
        ImGui.popStyleColor(2);
    }

    private static String fmt(int value) {
        return NUMBER_FORMAT.format(value);
    }

    public boolean pointsToDirectory() {
        String raw = cachePath.get().trim();
        if (raw.isEmpty()) return false;
        try {
            return Files.isDirectory(Path.of(raw));
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean pointsToValidCache() {
        String raw = cachePath.get().trim();
        if (raw.isEmpty()) return false;
        try {
            Path path = Path.of(raw);
            return Files.isDirectory(path) && Files.exists(path.resolve("main_file_cache.dat2"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public String regionText() {
        return region.get().trim();
    }
}
