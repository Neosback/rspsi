package com.rspsi.studio.project;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.project.StudioRecentProject;
import com.rspsi.server.OpenRuneServerAdapter;
import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pre-project launcher. Rendering this view performs only cheap filesystem/OpenRune marker checks;
 * it never evaluates Gradle and never opens a cache.
 */
public final class ProjectLauncherView {
    private enum Page {
        HOME,
        SOURCE,
        DETAILS
    }

    private static final DateTimeFormatter RECENT_TIME =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private Page page = Page.HOME;
    private StudioProjectKind kind = StudioProjectKind.STANDALONE_OSRS_CACHE;
    private final ImString sourcePath = new ImString(1024);
    private final ImString projectName = new ImString(160);

    private final ImBoolean symbols = new ImBoolean(true);
    private final ImBoolean declarativeContent = new ImBoolean(true);
    private final ImBoolean sourceSemantics = new ImBoolean(true);
    private final ImBoolean semanticGraph = new ImBoolean(true);
    private final ImBoolean npcSpawns = new ImBoolean(true);
    private final ImBoolean cs2Sources = new ImBoolean(true);
    private final ImBoolean allowCacheBuild = new ImBoolean(false);

    public void render(
            List<StudioRecentProject> recent,
            Consumer<StudioRecentProject> openRecent,
            Consumer<NewProjectRequest> createProject,
            Consumer<String> removeRecent) {
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("OpenRune Studio##project-launcher", flags)) {
            ImGui.end();
            return;
        }

        switch (page) {
            case HOME -> renderHome(recent, openRecent, removeRecent);
            case SOURCE -> renderSource();
            case DETAILS -> renderDetails(createProject);
        }
        ImGui.end();
    }

    private void renderHome(
            List<StudioRecentProject> recent,
            Consumer<StudioRecentProject> openRecent,
            Consumer<String> removeRecent) {
        float margin = 36.0f;
        ImGui.dummy(1.0f, 22.0f);
        ImGui.indent(margin);

        ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.94f, 0.99f, 1.0f);
        ImGui.setWindowFontScale(1.3f);
        ImGui.text("OpenRune Studio");
        ImGui.setWindowFontScale(1.0f);
        ImGui.popStyleColor();
        ImGui.textDisabled("Projects keep cache/server identity, integration settings, and Studio state together.");
        ImGui.dummy(1.0f, 18.0f);

        ImGui.separatorText("Start a project");
        float width = Math.max(280.0f, (ImGui.getContentRegionAvailX() - 14.0f) * 0.5f);
        StudioWidgets.beginCard("new-cache-project", width, 150.0f);
        ImGui.text("Standalone OSRS Cache");
        ImGui.textDisabled("Open an existing cache directly for map/content editing.");
        ImGui.dummy(1.0f, 20.0f);
        if (StudioWidgets.buttonPrimary("Select Cache Directory", -1.0f, 34.0f)) {
            begin(StudioProjectKind.STANDALONE_OSRS_CACHE);
        }
        StudioWidgets.endCard();

        ImGui.sameLine(0.0f, 14.0f);
        StudioWidgets.beginCard("new-openrune-project", width, 150.0f);
        ImGui.text("OpenRune Server Project");
        ImGui.textDisabled("Link the server checkout; Studio resolves LIVE, source, GameVals and content.");
        ImGui.dummy(1.0f, 20.0f);
        if (StudioWidgets.buttonPrimary("Select OpenRune Project", -1.0f, 34.0f)) {
            begin(StudioProjectKind.OPENRUNE_SERVER);
        }
        StudioWidgets.endCard();

        ImGui.dummy(1.0f, 20.0f);
        ImGui.separatorText("Recent Projects");
        if (recent == null || recent.isEmpty()) {
            ImGui.textDisabled("No Studio projects yet.");
        } else {
            for (StudioRecentProject entry : recent) {
                renderRecent(entry, openRecent, removeRecent);
            }
        }

        ImGui.unindent(margin);
    }

    private void renderRecent(
            StudioRecentProject entry,
            Consumer<StudioRecentProject> openRecent,
            Consumer<String> removeRecent) {
        boolean descriptorExists = Files.isRegularFile(Path.of(entry.descriptorPath()));
        boolean sourceExists = Files.isDirectory(Path.of(entry.sourcePath()));
        boolean available = descriptorExists && sourceExists;

        StudioWidgets.beginCard("recent-" + entry.projectId(), -1.0f, 86.0f);
        ImGui.text(entry.name());
        ImGui.sameLine();
        ImGui.textDisabled("  " + entry.kind().displayName());
        if (entry.pinned()) {
            ImGui.sameLine();
            ImGui.textColored(StudioDrawColors.abgr(0xFFFBBF24), "PINNED");
        }
        ImGui.textDisabled(entry.sourcePath());

        String lastOpened = entry.lastOpenedAtEpochMillis() <= 0
                ? "Never opened"
                : "Last opened " + RECENT_TIME.format(
                        Instant.ofEpochMilli(entry.lastOpenedAtEpochMillis()));
        if (available) {
            ImGui.textDisabled(lastOpened);
        } else {
            ImGui.textColored(
                    StudioDrawColors.abgr(0xFFF87171),
                    descriptorExists ? "Source folder moved or missing" : "Project metadata missing");
        }

        ImGui.sameLine(Math.max(250.0f, ImGui.getContentRegionAvailX() - 185.0f));
        ImGui.beginDisabled(!available);
        if (StudioWidgets.buttonPrimary("Open##" + entry.projectId(), 80.0f, 28.0f)) {
            openRecent.accept(entry);
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        if (StudioWidgets.buttonGhost("Remove##" + entry.projectId(), 80.0f, 28.0f)) {
            removeRecent.accept(entry.projectId());
        }
        StudioWidgets.endCard();
        ImGui.dummy(1.0f, 7.0f);
    }

    private void renderSource() {
        float margin = 70.0f;
        ImGui.dummy(1.0f, 28.0f);
        ImGui.indent(margin);

        ImGui.textDisabled("NEW PROJECT  ·  STEP 1 OF 2");
        ImGui.setWindowFontScale(1.22f);
        ImGui.text(kind.displayName());
        ImGui.setWindowFontScale(1.0f);
        ImGui.textDisabled(kind == StudioProjectKind.OPENRUNE_SERVER
                ? "Choose the OpenRune server checkout root. Do not choose .data/cache/LIVE."
                : "Choose the OSRS cache directory containing main_file_cache.dat2.");
        ImGui.dummy(1.0f, 18.0f);

        ImGui.setNextItemWidth(Math.max(380.0f, ImGui.getContentRegionAvailX() - 130.0f));
        ImGui.inputTextWithHint("##project-source", "Project/cache directory", sourcePath);
        ImGui.sameLine();
        if (StudioWidgets.buttonSecondary("Browse...", 110.0f, 0.0f)) {
            Path initial = sourcePath.isEmpty() ? null : safePath(sourcePath.get());
            NativeFolderPicker.choose(
                    kind == StudioProjectKind.OPENRUNE_SERVER
                            ? "Select OpenRune project root"
                            : "Select OSRS cache directory",
                    initial).ifPresent(this::selectedSource);
        }

        Validation validation = validateSource();
        ImGui.dummy(1.0f, 8.0f);
        if (validation.valid()) {
            ImGui.textColored(StudioDrawColors.abgr(0xFF4ADE80), "[OK] " + validation.detail());
        } else if (!sourcePath.isEmpty()) {
            ImGui.textColored(StudioDrawColors.abgr(0xFFF87171), "[X] " + validation.detail());
        } else {
            ImGui.textDisabled("Choose a directory to continue.");
        }

        ImGui.dummy(1.0f, 24.0f);
        if (StudioWidgets.buttonGhost("Back", 90.0f, 32.0f)) page = Page.HOME;
        ImGui.sameLine();
        ImGui.beginDisabled(!validation.valid());
        if (StudioWidgets.buttonPrimary("Next", 110.0f, 32.0f)) {
            if (projectName.isEmpty()) projectName.set(defaultName());
            page = Page.DETAILS;
        }
        ImGui.endDisabled();

        ImGui.unindent(margin);
    }

    private void renderDetails(Consumer<NewProjectRequest> createProject) {
        float margin = 70.0f;
        ImGui.dummy(1.0f, 28.0f);
        ImGui.indent(margin);

        ImGui.textDisabled("NEW PROJECT  ·  STEP 2 OF 2");
        ImGui.setWindowFontScale(1.22f);
        ImGui.text("Project details");
        ImGui.setWindowFontScale(1.0f);
        ImGui.dummy(1.0f, 16.0f);

        ImGui.text("Project name");
        ImGui.setNextItemWidth(Math.min(560.0f, ImGui.getContentRegionAvailX()));
        ImGui.inputTextWithHint("##project-name", "My OpenRune Project", projectName);
        ImGui.textDisabled("Source: " + sourcePath.get());

        if (kind == StudioProjectKind.OPENRUNE_SERVER) {
            ImGui.dummy(1.0f, 16.0f);
            ImGui.separatorText("OpenRune integration");
            ImGui.textDisabled("Read/index features are safe defaults. Build execution is opt-in.");

            ImGui.checkbox("GameVals & symbols", symbols);
            ImGui.checkbox("Declarative content index", declarativeContent);
            ImGui.checkbox("Kotlin source semantics", sourceSemantics);
            ImGui.checkbox("Cross-source semantic content graph", semanticGraph);
            ImGui.checkbox("NPC spawn data", npcSpawns);
            ImGui.checkbox("CS2 source discovery", cs2Sources);
            ImGui.dummy(1.0f, 6.0f);
            ImGui.checkbox("Allow detected cache build task execution", allowCacheBuild);
            if (allowCacheBuild.get()) {
                ImGui.textColored(
                        StudioDrawColors.abgr(0xFFFBBF24),
                        "Build tasks are enabled, but FreshCache/reset is never run automatically.");
            }
        }

        ImGui.dummy(1.0f, 20.0f);
        if (StudioWidgets.buttonGhost("Back", 90.0f, 32.0f)) page = Page.SOURCE;
        ImGui.sameLine();
        boolean ready = !projectName.get().trim().isEmpty() && validateSource().valid();
        ImGui.beginDisabled(!ready);
        if (StudioWidgets.buttonPrimary("Create & Open Project", 190.0f, 32.0f)) {
            createProject.accept(new NewProjectRequest(
                    projectName.get().trim(),
                    kind,
                    Path.of(sourcePath.get()).toAbsolutePath().normalize(),
                    capabilities(),
                    Map.of("integrationMode",
                            allowCacheBuild.get() ? "MANAGED_BUILD" : "INSPECT")));
            page = Page.HOME;
        }
        ImGui.endDisabled();

        ImGui.unindent(margin);
    }

    private void begin(StudioProjectKind selectedKind) {
        kind = selectedKind;
        sourcePath.clear();
        projectName.clear();
        symbols.set(true);
        declarativeContent.set(true);
        sourceSemantics.set(true);
        semanticGraph.set(true);
        npcSpawns.set(true);
        cs2Sources.set(true);
        allowCacheBuild.set(false);
        page = Page.SOURCE;
    }

    private void selectedSource(Path path) {
        sourcePath.set(path.toString());
        if (projectName.isEmpty()) projectName.set(defaultName());
    }

    private Validation validateSource() {
        if (sourcePath.isEmpty()) return new Validation(false, "No directory selected");
        Path path = safePath(sourcePath.get());
        if (path == null) return new Validation(false, "Invalid path");
        if (!Files.isDirectory(path)) return new Validation(false, "Directory does not exist");

        if (kind == StudioProjectKind.STANDALONE_OSRS_CACHE) {
            if (!Files.isRegularFile(path.resolve("main_file_cache.dat2"))) {
                return new Validation(false, "main_file_cache.dat2 was not found");
            }
            return new Validation(true, "OSRS cache directory found");
        }

        var detection = new OpenRuneServerAdapter().detect(path);
        if (!detection.matched()) {
            return new Validation(false, "OpenRune project markers were not detected");
        }
        return new Validation(true,
                "OpenRune project detected"
                        + (detection.evidence().isEmpty()
                        ? "" : " · " + String.join(", ", detection.evidence())));
    }

    private Set<String> capabilities() {
        if (kind != StudioProjectKind.OPENRUNE_SERVER) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();

        if (symbols.get()) {
            add(result, IntegrationCapability.SYMBOLS, IntegrationCapability.GAMEVALS);
        }
        if (declarativeContent.get()) {
            add(result,
                    IntegrationCapability.CONTENT_INDEX,
                    IntegrationCapability.CONTENT_DIAGNOSTICS,
                    IntegrationCapability.LOC_REFERENCES,
                    IntegrationCapability.MAP_REFERENCES);
        }
        if (sourceSemantics.get()) {
            add(result,
                    IntegrationCapability.SOURCE_NAVIGATION,
                    IntegrationCapability.SOURCE_SEMANTICS);
        }
        if (semanticGraph.get()) add(result, IntegrationCapability.CONTENT_GRAPH);
        if (npcSpawns.get()) add(result, IntegrationCapability.NPC_SPAWNS);
        if (cs2Sources.get()) add(result, IntegrationCapability.CS2_SOURCES);
        if (allowCacheBuild.get()) add(result, IntegrationCapability.CACHE_BUILD);
        return Set.copyOf(result);
    }

    private static void add(Set<String> target, IntegrationCapability... values) {
        for (IntegrationCapability value : values) target.add(value.name());
    }

    private String defaultName() {
        Path path = safePath(sourcePath.get());
        if (path == null || path.getFileName() == null) {
            return kind == StudioProjectKind.OPENRUNE_SERVER
                    ? "OpenRune Project" : "OSRS Cache Project";
        }
        return path.getFileName().toString();
    }

    private static Path safePath(String value) {
        try {
            return Path.of(value.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record Validation(boolean valid, String detail) {
    }

    public record NewProjectRequest(
            String name,
            StudioProjectKind kind,
            Path source,
            Set<String> enabledCapabilities,
            Map<String, String> settings) {
    }
}
