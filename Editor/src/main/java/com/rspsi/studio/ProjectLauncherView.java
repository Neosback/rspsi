package com.rspsi.studio;

import com.rspsi.project.ProjectIntegrationPreset;
import com.rspsi.project.RecentStudioProject;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.project.StudioProjectRegistry;
import com.rspsi.project.StudioProjectService;
import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pre-project launcher. Rendering this view never opens or decodes a cache.
 *
 * <p>The launcher intentionally resembles an IDE project chooser: recent projects are primary;
 * creating/linking a project is a separate action; project-specific tools do not exist here.</p>
 */
public final class ProjectLauncherView {
    private enum CreateMode { NONE, OPEN_EXISTING, OPENRUNE, STANDALONE }

    private static final DateTimeFormatter RECENT_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a");

    private final StudioProjectRegistry registry;
    private final StudioProjectService projects;

    private final ImString descriptorPath = new ImString(1024);
    private final ImString projectName = new ImString(160);
    private final ImString sourcePath = new ImString(1024);
    private final ImString projectDataPath = new ImString(1024);

    private CreateMode mode = CreateMode.NONE;
    private ProjectIntegrationPreset preset = ProjectIntegrationPreset.MANAGED_BUILD;
    private String error = "";

    public ProjectLauncherView(StudioProjectRegistry registry, StudioProjectService projects) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.projects = Objects.requireNonNull(projects, "projects");
    }

    public void render(Consumer<StudioProjectDescriptor> openProject) {
        Objects.requireNonNull(openProject, "openProject");

        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("OpenRune Studio Projects", flags)) {
            ImGui.end();
            return;
        }

        ImGui.dummy(1.0f, 20.0f);
        ImGui.indent(30.0f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.91f, 0.94f, 1.0f, 1.0f);
        ImGui.text("OPENRUNE STUDIO");
        ImGui.popStyleColor();
        ImGui.textDisabled("Choose a project to continue, or start a new content workspace.");
        ImGui.dummy(1.0f, 16.0f);

        float available = ImGui.getContentRegionAvailX();
        float actionsWidth = Math.min(360.0f, Math.max(300.0f, available * 0.30f));
        float recentWidth = Math.max(420.0f, available - actionsWidth - 20.0f);

        if (ImGui.beginTable("##project-launcher-layout", 2,
                ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableSetupColumn("Recent", 0, recentWidth);
            ImGui.tableSetupColumn("Actions", 0, actionsWidth);
            ImGui.tableNextRow();

            ImGui.tableNextColumn();
            renderRecent(openProject);

            ImGui.tableNextColumn();
            renderActions(openProject);

            ImGui.endTable();
        }

        ImGui.unindent(30.0f);
        ImGui.end();
    }

    private void renderRecent(Consumer<StudioProjectDescriptor> openProject) {
        ImGui.separatorText("Recent Projects");
        List<RecentStudioProject> recent = registry.recent();
        if (recent.isEmpty()) {
            StudioWidgets.beginCard("recent-empty", -1.0f, 130.0f);
            ImGui.text("No Studio projects yet.");
            ImGui.textDisabled("Link an OpenRune server project or create a standalone cache project.");
            StudioWidgets.endCard();
            return;
        }

        int row = 0;
        for (RecentStudioProject project : recent) {
            float height = project.available() ? 102.0f : 118.0f;
            StudioWidgets.beginCard("recent-project-" + row++, -1.0f, height);

            String kind = project.kind() == StudioProjectKind.OPENRUNE_SERVER
                    ? "OpenRune Server" : "Standalone Cache";
            ImGui.text(project.name());
            ImGui.sameLine(0.0f, 10.0f);
            int pillBg = project.kind() == StudioProjectKind.OPENRUNE_SERVER
                    ? ImGui.getColorU32(0.18f, 0.23f, 0.38f, 0.85f)
                    : ImGui.getColorU32(0.17f, 0.27f, 0.20f, 0.85f);
            int pillText = project.kind() == StudioProjectKind.OPENRUNE_SERVER
                    ? ImGui.getColorU32(0.58f, 0.70f, 1.0f, 1.0f)
                    : ImGui.getColorU32(0.45f, 0.90f, 0.58f, 1.0f);
            StudioWidgets.pill(kind, pillBg, pillText);
            if (project.pinned()) {
                ImGui.sameLine(0.0f, 8.0f);
                ImGui.textDisabled("PINNED");
            }

            ImGui.textDisabled(project.displayPath());
            ImGui.textDisabled("Last opened "
                    + RECENT_TIME.format(Instant.ofEpochMilli(project.lastOpenedEpochMillis())
                    .atZone(ZoneId.systemDefault())));

            if (!project.available()) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.67f, 0.25f, 1.0f);
                ImGui.text("Project descriptor is missing or moved.");
                ImGui.popStyleColor();
            }

            ImGui.beginDisabled(!project.available());
            if (StudioWidgets.buttonPrimary("Open##" + project.projectId(), 88.0f, 26.0f)) {
                tryOpen(project.descriptor(), openProject);
            }
            ImGui.endDisabled();

            ImGui.sameLine();
            if (StudioWidgets.buttonGhost(
                    project.pinned() ? "Unpin##" + project.projectId()
                            : "Pin##" + project.projectId(), 70.0f, 26.0f)) {
                registry.setPinned(project.projectId(), !project.pinned());
            }
            ImGui.sameLine();
            if (StudioWidgets.buttonGhost("Remove##" + project.projectId(), 78.0f, 26.0f)) {
                registry.remove(project.projectId());
            }

            StudioWidgets.endCard();
            ImGui.dummy(1.0f, 8.0f);
        }
    }

    private void renderActions(Consumer<StudioProjectDescriptor> openProject) {
        ImGui.separatorText("Start");
        if (StudioWidgets.buttonPrimary("Link OpenRune Server", -1.0f, 36.0f)) {
            setMode(CreateMode.OPENRUNE);
        }
        ImGui.dummy(1.0f, 6.0f);
        if (StudioWidgets.buttonSecondary("Standalone Cache Project", -1.0f, 34.0f)) {
            setMode(CreateMode.STANDALONE);
        }
        ImGui.dummy(1.0f, 6.0f);
        if (StudioWidgets.buttonSecondary("Open Existing Studio Project", -1.0f, 34.0f)) {
            setMode(CreateMode.OPEN_EXISTING);
        }

        ImGui.dummy(1.0f, 14.0f);
        switch (mode) {
            case OPEN_EXISTING -> renderOpenExisting(openProject);
            case OPENRUNE -> renderOpenRune(openProject);
            case STANDALONE -> renderStandalone(openProject);
            default -> {
                StudioWidgets.beginCard("launcher-hint", -1.0f, 146.0f);
                ImGui.text("OpenRune-first workflow");
                ImGui.textWrapped(
                        "Link the server checkout once. Studio will resolve LIVE/SERVER caches, "
                                + "GameVals, Gradle modules, source semantics and content graph "
                                + "during the project loading gate.");
                ImGui.dummy(1.0f, 6.0f);
                ImGui.textDisabled(
                        "Only need map/cache editing? Use Standalone Cache Project instead.");
                StudioWidgets.endCard();
            }
        }

        if (!error.isBlank()) {
            ImGui.dummy(1.0f, 10.0f);
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.40f, 0.40f, 1.0f);
            ImGui.textWrapped(error);
            ImGui.popStyleColor();
        }
    }

    private void renderOpenExisting(Consumer<StudioProjectDescriptor> openProject) {
        ImGui.separatorText("Open Existing");
        ImGui.textDisabled("Select an existing Studio project.json descriptor.");
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##descriptor-path", "/path/to/project.json", descriptorPath);
        ImGui.dummy(1.0f, 6.0f);
        ImGui.beginDisabled(descriptorPath.isEmpty());
        if (StudioWidgets.buttonPrimary("Open Project", -1.0f, 32.0f)) {
            tryOpen(Path.of(descriptorPath.get().trim()), openProject);
        }
        ImGui.endDisabled();
    }

    private void renderOpenRune(Consumer<StudioProjectDescriptor> openProject) {
        ImGui.separatorText("Link OpenRune Server");
        ImGui.textDisabled("Studio data stays outside the server repository by default.");
        commonCreateFields("Server project root", "/path/to/OpenRune-Server");

        ImGui.dummy(1.0f, 8.0f);
        ImGui.textDisabled("Integration policy");
        for (ProjectIntegrationPreset candidate : ProjectIntegrationPreset.values()) {
            boolean selected = candidate == preset;
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.24f, 0.34f, 0.62f, 1.0f);
            }
            if (ImGui.smallButton(label(candidate) + "##preset-" + candidate.name())) {
                preset = candidate;
            }
            if (selected) ImGui.popStyleColor();
            if (candidate != ProjectIntegrationPreset.DEVELOPER) ImGui.sameLine();
        }
        ImGui.textDisabled(policyDescription(preset));

        ImGui.dummy(1.0f, 8.0f);
        boolean ready = !projectName.isEmpty() && !sourcePath.isEmpty();
        ImGui.beginDisabled(!ready);
        if (StudioWidgets.buttonPrimary("Link & Open Project", -1.0f, 34.0f)) {
            try {
                StudioProjectDescriptor descriptor = projects.linkOpenRune(
                        projectName.get().trim(),
                        effectiveProjectDataPath(),
                        Path.of(sourcePath.get().trim()),
                        preset);
                error = "";
                openProject.accept(descriptor);
            } catch (Exception failure) {
                error = rootMessage(failure);
            }
        }
        ImGui.endDisabled();
    }

    private void renderStandalone(Consumer<StudioProjectDescriptor> openProject) {
        ImGui.separatorText("Standalone Cache Project");
        ImGui.textDisabled("Use Studio without a server integration.");
        commonCreateFields("OSRS cache root", "/path/to/cache");

        ImGui.dummy(1.0f, 8.0f);
        boolean ready = !projectName.isEmpty() && !sourcePath.isEmpty();
        ImGui.beginDisabled(!ready);
        if (StudioWidgets.buttonPrimary("Create & Open Project", -1.0f, 34.0f)) {
            try {
                StudioProjectDescriptor descriptor = projects.createStandalone(
                        projectName.get().trim(),
                        effectiveProjectDataPath(),
                        Path.of(sourcePath.get().trim()));
                error = "";
                openProject.accept(descriptor);
            } catch (Exception failure) {
                error = rootMessage(failure);
            }
        }
        ImGui.endDisabled();
    }

    private void commonCreateFields(String sourceLabel, String sourceHint) {
        ImGui.textDisabled("Project name");
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##project-name", "My OpenRune Project", projectName);

        ImGui.dummy(1.0f, 6.0f);
        ImGui.textDisabled(sourceLabel);
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint("##project-source", sourceHint, sourcePath);

        ImGui.dummy(1.0f, 6.0f);
        ImGui.textDisabled("Studio data location (optional)");
        ImGui.setNextItemWidth(-1.0f);
        ImGui.inputTextWithHint(
                "##project-data",
                defaultProjectDataPath(projectName.get()).toString(),
                projectDataPath);
    }

    private Path effectiveProjectDataPath() {
        String value = projectDataPath.get().trim();
        return value.isBlank() ? defaultProjectDataPath(projectName.get()) : Path.of(value);
    }

    private static Path defaultProjectDataPath(String name) {
        String value = name == null ? "" : name.trim().toLowerCase();
        String slug = value.replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) slug = "project";
        return Path.of(System.getProperty("user.home"),
                ".openrune-studio", "projects", slug);
    }

    private void tryOpen(
            Path descriptor,
            Consumer<StudioProjectDescriptor> openProject) {
        try {
            StudioProjectDescriptor project = projects.open(descriptor);
            error = "";
            openProject.accept(project);
        } catch (Exception failure) {
            error = rootMessage(failure);
        }
    }

    private void setMode(CreateMode next) {
        mode = next;
        error = "";
    }

    private static String label(ProjectIntegrationPreset preset) {
        return switch (preset) {
            case INSPECT -> "Inspect";
            case AUTHOR -> "Author";
            case MANAGED_BUILD -> "Managed Build";
            case DEVELOPER -> "Developer";
        };
    }

    private static String policyDescription(ProjectIntegrationPreset preset) {
        return switch (preset) {
            case INSPECT -> "Read-only project/source integration.";
            case AUTHOR -> "Read project content and write supported authored source.";
            case MANAGED_BUILD -> "Author content and allow supported project build tasks.";
            case DEVELOPER -> "Managed Build plus server/development controls. FreshCache stays explicit.";
        };
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }
}
