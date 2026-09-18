package com.rspsi.ui.workspace;

import com.rspsi.editor.input.EditorKeyEvent;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.editor.ui.PanelDescriptor;
import com.rspsi.editor.ui.PanelPlacement;
import com.rspsi.editor.ui.WorkspaceCatalog;
import com.rspsi.editor.ui.WorkspaceDefinition;
import com.rspsi.ui.StudioIcon;
import com.rspsi.ui.StudioIconFactory;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The controlled JavaFX Studio shell. It owns panel composition and layout,
 * while sessions, tools, plugins, and scene state remain neutral.
 */
public final class ControlledWorkspaceShell extends BorderPane implements AutoCloseable {
    private static final double GAP = 6;
    private final WorkspaceCatalog catalog;
    private final Map<String, Node> panels;
    private final TabPane workspaceTabs = new TabPane();
    private final MenuButton pluginCommands = new MenuButton("Commands");
    private final MenuButton viewMenu = new MenuButton("View");
    private final Map<String, Boolean> panelVisibility = new HashMap<>();
    private final Map<String, Stage> detachedStages = new LinkedHashMap<>();
    private final Map<String, WindowBounds> detachedLayout = new HashMap<>();
    private final WorkspaceLayoutStore layoutStore;
    private TabPane bottomTabs;
    private VBox bottomArea;
    private Node statusBar;
    private VBox centerColumn;
    private HBox commandLine;
    private Node windowControls;
    private EditorPluginHost pluginHost;
    private String activeWorkspaceId;
    private boolean drawerExpanded;

    public ControlledWorkspaceShell(WorkspaceCatalog catalog,
                                    WorkspaceDefinition workspace,
                                    Map<String, Node> panels) {
        this(catalog, workspace, panels, new JsonWorkspaceLayoutStore());
    }

    public ControlledWorkspaceShell(WorkspaceCatalog catalog,
                                    WorkspaceDefinition workspace,
                                    Map<String, Node> panels,
                                    WorkspaceLayoutStore layoutStore) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.panels = Map.copyOf(Objects.requireNonNull(panels, "panels"));
        this.layoutStore = Objects.requireNonNull(layoutStore, "layoutStore");
        getStyleClass().add("controlled-workspace-shell");
        var stylesheet = getClass().getResource("/css/workspace.css");
        if (stylesheet != null) getStylesheets().add(stylesheet.toExternalForm());
        setPadding(Insets.EMPTY);
        for (var panel : catalog.panels()) panelVisibility.put(panel.id(), true);
        configureWorkspaceTabs();
        configureViewMenu();
        show(workspace);
    }

    public WorkspaceDefinition workspace(String id) {
        return catalog.workspace(id);
    }

    public Node panelNode(String id) {
        return panels.get(id);
    }

    public Node statusBar() {
        return statusBar;
    }

    public boolean dispatchKey(EditorKeyEvent event, boolean textInputFocused) {
        Objects.requireNonNull(event, "event");
        Node viewport = panelNode("viewport");
        return viewport instanceof ControlledViewportPanel controlled
                && controlled.canonicalViewport().dispatchKey(event, textInputFocused);
    }

    public void bindPluginHost(EditorPluginHost host) {
        Objects.requireNonNull(host, "host");
        if (pluginHost != null && pluginHost != host) pluginHost.close();
        pluginHost = host;
        if (panelNode("tools") instanceof AdaptiveToolPanel tools) tools.bindPluginHost(host);
        if (panelNode("assets") instanceof AssetBrowserPanel assets) assets.bindPluginHost(host);
        if (panelNode("inspector") instanceof SessionInspectorPanel inspector) inspector.bindPluginHost(host);
        if (statusBar instanceof WorkspaceStatusBar status) status.bindPluginHost(host);
        if (panelNode("command-palette") instanceof PluginCommandPalettePanel palette) palette.bindPluginHost(host);
        if (panelNode("viewport") instanceof ControlledViewportPanel viewport) viewport.canonicalViewport().bindPluginHost(host);
        rebuildPluginCommands();
    }

    public void show(String workspaceId) {
        show(catalog.workspace(workspaceId));
    }

    public void show(WorkspaceDefinition workspace) {
        Objects.requireNonNull(workspace, "workspace");
        if (activeWorkspaceId != null) saveLayout(activeWorkspaceId);
        closeDetachedPanels();
        activeWorkspaceId = workspace.id();
        loadLayout(activeWorkspaceId);
        selectWorkspaceTab(activeWorkspaceId);
        detachMountedPanels();
        detach(statusBar);
        detach(bottomArea);

        Node tools = panel("tools", catalog.panel("tools"));
        Node viewport = panel("viewport", catalog.panel("viewport"));
        Node context = panel("context-toolbar", catalog.panel("context-toolbar"));
        Node toolContext = panel("tool-context", catalog.panel("tool-context"));
        Node selector = panel("selector-strip", catalog.panel("selector-strip"));
        Node rightRail = panel("right-tool-rail", catalog.panel("right-tool-rail"));
        Node settings = panel("settings-panel", catalog.panel("settings-panel"));

        if (tools instanceof Region toolsRegion) {
            toolsRegion.setMinWidth(56);
            toolsRegion.setPrefWidth(60);
            toolsRegion.setMaxWidth(60);
        }
        if (rightRail instanceof Region railRegion) {
            railRegion.setMinWidth(44);
            railRegion.setPrefWidth(48);
            railRegion.setMaxWidth(48);
        }
        if (settings instanceof Region settingsRegion) {
            settingsRegion.setMinWidth(280);
            settingsRegion.setPrefWidth(300);
            settingsRegion.setMaxWidth(300);
        }

        centerColumn = new VBox(0, context, viewport, toolContext, selector);
        centerColumn.getStyleClass().add("workspace-center-column");
        VBox center = centerColumn;
        VBox.setVgrow(viewport, Priority.ALWAYS);

        HBox rightSidebar = new HBox(0, rightRail, settings);
        rightSidebar.getStyleClass().add("workspace-right-sidebar");
        if (rightRail instanceof Region rail) HBox.setHgrow(rail, Priority.NEVER);
        if (settings instanceof Region panel) HBox.setHgrow(panel, Priority.NEVER);

        HBox content = new HBox(tools, center, rightSidebar);
        content.getStyleClass().add("workspace-main-layout");
        HBox.setHgrow(center, Priority.ALWAYS);
        setCenter(content);
        createBottomDrawer(workspace);
        restoreDetachedPanels();
        rebuildBottom();
    }

    /** Adds the legacy menu/title controls above the workspace tabs. */
    public void setTopBar(Node top) {
        setTopBar(top, null);
    }

    /**
     * Installs the visible application chrome. The menu keeps its natural
     * width, a flexible spacer owns the unused title-bar area, and native
     * window controls are mounted as the final right-aligned element.
     */
    public void setTopBar(Node top, Node controls) {
        HBox nextCommandLine = new HBox(0);
        this.commandLine = nextCommandLine;
        this.windowControls = controls;
        nextCommandLine.getStyleClass().add("workspace-top-bar");
        nextCommandLine.setPadding(new Insets(0, 0, 0, 8));
        if (top != null) {
            detach(top);
            HBox.setHgrow(top, Priority.NEVER);
            nextCommandLine.getChildren().add(top);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        nextCommandLine.getChildren().add(spacer);
        if (top instanceof MenuBar) {
            // The modern Studio menu already owns Workspace/View actions. Keep
            // only plugin-contributed commands as a separate dynamic menu.
            nextCommandLine.getChildren().add(pluginCommands);
        } else {
            nextCommandLine.getChildren().add(viewMenu);
            nextCommandLine.getChildren().add(pluginCommands);
            nextCommandLine.getChildren().add(StudioIconFactory.button(StudioIcon.RESET_LAYOUT,
                    "Reset current workspace layout", () -> resetLayout(activeWorkspaceId)));
        }
        if (controls != null) {
            detach(controls);
            if (controls instanceof Region region) {
                region.setMinWidth(112);
                region.setPrefWidth(112);
                region.setMaxWidth(112);
                region.setMinHeight(36);
                region.setPrefHeight(36);
                region.setMaxHeight(36);
            }
            HBox.setHgrow(controls, Priority.NEVER);
            nextCommandLine.getChildren().add(controls);
        }
        VBox topStack = new VBox(nextCommandLine, workspaceTabs);
        topStack.getStyleClass().add("workspace-top-stack");
        super.setTop(topStack);
    }

    /** The visible title-bar surface used by the custom window drag helper. */
    public Node windowDragSurface() {
        return commandLine == null ? this : commandLine;
    }

    public Node windowControls() {
        return windowControls;
    }

    public void setStatusBar(Node statusBar) {
        detach(this.statusBar);
        detach(statusBar);
        this.statusBar = statusBar;
        rebuildBottom();
    }

    public void resetLayout(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) return;
        layoutStore.reset(workspaceId);
        for (var panel : catalog.panels()) panelVisibility.put(panel.id(), true);
        drawerExpanded = false;
        if (workspaceId.equals(activeWorkspaceId)) activeWorkspaceId = null;
        show(workspaceId);
    }

    public void toggleUtilityDrawer() {
        drawerExpanded = !drawerExpanded;
        rebuildBottom();
    }

    public void selectRightCategory(String categoryId) {
        Node rail = panelNode("right-tool-rail");
        if (rail instanceof RightToolRail rightToolRail) {
            rightToolRail.select(categoryId);
        }
    }

    private void configureWorkspaceTabs() {
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        workspaceTabs.getStyleClass().add("workspace-tabs");
        for (WorkspaceDefinition workspace : catalog.workspaces()) {
            Tab tab = new Tab(title(workspace.id()));
            tab.setUserData(workspace.id());
            tab.setClosable(false);
            workspaceTabs.getTabs().add(tab);
        }
        workspaceTabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == null || activeWorkspaceId == null) return;
            String id = String.valueOf(newTab.getUserData());
            if (!id.equals(activeWorkspaceId)) show(id);
        });
    }

    private void configureViewMenu() {
        viewMenu.setAccessibleText("View and panel layout");
        MenuItem reset = new MenuItem("Reset layout");
        reset.setOnAction(event -> resetLayout(activeWorkspaceId));
        viewMenu.getItems().addAll(reset, new SeparatorMenuItem());
        for (String id : List.of("assets", "history", "validation", "console", "plugins")) {
            MenuItem item = new MenuItem(title(id));
            item.setOnAction(event -> togglePanel(id));
            viewMenu.getItems().add(item);
        }
    }

    private void togglePanel(String id) {
        panelVisibility.put(id, !panelVisibility.getOrDefault(id, true));
        if (activeWorkspaceId != null) show(activeWorkspaceId);
    }

    private void createBottomDrawer(WorkspaceDefinition workspace) {
        bottomTabs = new TabPane();
        bottomTabs.getStyleClass().add("workspace-bottom-tabs");
        bottomTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        List<PanelPlacement> placements = workspace.placements().stream()
                .filter(placement -> placement.region() == DockRegion.BOTTOM)
                .sorted(Comparator.comparingInt(PanelPlacement::order)).toList();
        for (PanelPlacement placement : placements) {
            if (!panelVisibility.getOrDefault(placement.panelId(), true)) continue;
            addBottomTab(placement.panelId(), panel(placement.panelId(), catalog.panel(placement.panelId())));
        }
        bottomTabs.setPrefHeight(190);
    }

    private void addBottomTab(String id, Node node) {
        Tab tab = new Tab(title(id), node);
        tab.setClosable(false);
        tab.setUserData(id);
        MenuItem detach = new MenuItem("Detach panel");
        tab.setContextMenu(new ContextMenu(detach));
        detach.setOnAction(event -> detachPanel(id));
        bottomTabs.getTabs().add(tab);
    }

    private void restoreDetachedPanels() {
        for (String id : new ArrayList<>(detachedLayout.keySet())) {
            if (bottomTabs == null) continue;
            boolean available = bottomTabs.getTabs().stream()
                    .anyMatch(tab -> id.equals(tab.getUserData()));
            if (available) detachPanel(id);
        }
    }

    private void detachPanel(String id) {
        if (detachedStages.containsKey(id) || bottomTabs == null) return;
        Tab tab = bottomTabs.getTabs().stream()
                .filter(candidate -> id.equals(candidate.getUserData())).findFirst().orElse(null);
        if (tab == null) return;
        Node content = tab.getContent();
        bottomTabs.getTabs().remove(tab);
        VBox root = new VBox(6);
        root.setPadding(new Insets(6));
        Button dock = new Button("Dock panel");
        dock.setGraphic(StudioIconFactory.icon(StudioIcon.DETACH));
        dock.setOnAction(event -> dockPanel(id));
        root.getChildren().addAll(dock, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        Stage stage = new Stage();
        stage.setTitle("OpenRune Studio — " + title(id));
        stage.setMinWidth(320);
        stage.setMinHeight(220);
        WindowBounds savedBounds = detachedLayout.get(id);
        if (savedBounds != null) {
            stage.setX(savedBounds.x());
            stage.setY(savedBounds.y());
            stage.setWidth(savedBounds.width());
            stage.setHeight(savedBounds.height());
        }
        stage.setScene(new javafx.scene.Scene(root, 520, 320));
        stage.setOnCloseRequest(event -> {
            event.consume();
            dockPanel(id);
        });
        detachedStages.put(id, stage);
        stage.show();
    }

    private void dockPanel(String id) {
        Stage stage = detachedStages.remove(id);
        detachedLayout.remove(id);
        if (stage != null) {
            stage.setOnCloseRequest(null);
            stage.close();
        }
        if (bottomTabs == null) return;
        Node node = panelNode(id);
        if (node == null || node.getParent() != null) return;
        addBottomTab(id, node);
        rebuildBottom();
    }

    private void rebuildBottom() {
        if (bottomArea != null) detach(bottomArea);
        if (statusBar != null) detach(statusBar);
        if (centerColumn == null) return;
        bottomArea = new VBox();
        bottomArea.getStyleClass().add("workspace-bottom-area");
        HBox header = new HBox(6);
        header.getStyleClass().add("workspace-drawer-header");
        header.setPadding(new Insets(3, 8, 3, 8));
        Button toggle = new Button();
        toggle.setGraphic(StudioIconFactory.icon(drawerExpanded ? StudioIcon.CHEVRON_DOWN : StudioIcon.CHEVRON_UP));
        toggle.setAccessibleText(drawerExpanded ? "Collapse utility drawer" : "Expand utility drawer");
        toggle.setTooltip(new Tooltip("Toggle utility drawer (Ctrl+Space)"));
        toggle.setOnAction(event -> {
            drawerExpanded = !drawerExpanded;
            rebuildBottom();
        });
        header.getChildren().add(toggle);
        bottomArea.getChildren().add(header);
        if (bottomTabs != null && drawerExpanded) {
            VBox.setVgrow(bottomTabs, Priority.ALWAYS);
            bottomArea.getChildren().add(bottomTabs);
        }
        centerColumn.getChildren().add(bottomArea);
        if (statusBar != null) setBottom(statusBar);
    }

    private void loadLayout(String workspaceId) {
        WorkspaceLayout layout = layoutStore.load(workspaceId);
        detachedLayout.clear();
        if (layout == null) {
            for (var panel : catalog.panels()) panelVisibility.put(panel.id(), true);
            drawerExpanded = false;
            return;
        }
        for (PanelLayoutState panel : layout.panels()) {
            panelVisibility.put(panel.panelId(), panel.visible());
            if (panel.detached() && panel.detachedBounds() != null) {
                detachedLayout.put(panel.panelId(), panel.detachedBounds());
            }
        }
    }

    private void saveLayout(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) return;
        List<PanelLayoutState> states = new ArrayList<>();
        int order = 0;
        for (PanelDescriptor panel : catalog.panels()) {
            Stage detached = detachedStages.get(panel.id());
            WindowBounds bounds = detached == null ? detachedLayout.get(panel.id())
                    : new WindowBounds(detached.getX(), detached.getY(),
                    detached.getWidth(), detached.getHeight());
            states.add(new PanelLayoutState(panel.id(), panel.preferredRegion(),
                    panelVisibility.getOrDefault(panel.id(), true), order++, 0,
                    detached != null || bounds != null, bounds));
        }
        layoutStore.save(new WorkspaceLayout(workspaceId, WorkspaceLayout.CURRENT_VERSION, states));
    }

    private void selectWorkspaceTab(String workspaceId) {
        for (Tab tab : workspaceTabs.getTabs()) {
            if (workspaceId.equals(tab.getUserData())) {
                if (workspaceTabs.getSelectionModel().getSelectedItem() != tab) {
                    workspaceTabs.getSelectionModel().select(tab);
                }
                return;
            }
        }
    }

    private Node panel(String id, PanelDescriptor descriptor) {
        Node panel = panels.getOrDefault(id, missingPanel(id));
        if (!panel.getStyleClass().contains("workspace-panel")) panel.getStyleClass().add("workspace-panel");
        panel.setAccessibleText(title(id));
        if (panel instanceof Region region) {
            region.setMinWidth(descriptor.minimumWidth());
            region.setMinHeight(descriptor.minimumHeight());
        }
        return panel;
    }

    private static Node missingPanel(String id) {
        Label label = new Label("Panel unavailable: " + id);
        label.getStyleClass().add("workspace-missing-panel");
        return label;
    }

    private void detachMountedPanels() {
        for (Node panel : panels.values()) {
            if (panel.getParent() instanceof Pane parent) parent.getChildren().remove(panel);
        }
    }

    private void closeDetachedPanels() {
        List<Map.Entry<String, Stage>> detached = new ArrayList<>(detachedStages.entrySet());
        detachedStages.clear();
        for (Map.Entry<String, Stage> entry : detached) {
            Node content = panelNode(entry.getKey());
            detach(content);
            Stage stage = entry.getValue();
            stage.setOnCloseRequest(null);
            stage.close();
        }
    }

    private static void detach(Node node) {
        if (node != null && node.getParent() instanceof Pane parent) parent.getChildren().remove(node);
    }

    private void rebuildPluginCommands() {
        pluginCommands.getItems().clear();
        if (pluginHost == null) {
            pluginCommands.setVisible(false);
            pluginCommands.setManaged(false);
            return;
        }
        for (var registration : pluginHost.registry().menuRegistrations()) {
            MenuItem item = new MenuItem(registration.label());
            item.setDisable(!pluginHost.context().session().canEdit());
            item.setOnAction(event -> pluginHost.context().session().execute(
                    pluginHost.registry().createCommand(registration.commandId())));
            pluginCommands.getItems().add(item);
        }
        boolean available = !pluginCommands.getItems().isEmpty();
        pluginCommands.setVisible(available);
        pluginCommands.setManaged(available);
    }

    private static String title(String id) {
        if ("map".equals(id)) return "Map Editor";
        String[] words = id.split("-");
        return java.util.Arrays.stream(words).filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((first, second) -> first + " " + second).orElse(id);
    }

    @Override
    public void close() {
        saveLayout(activeWorkspaceId);
        closeDetachedPanels();
        EditorPluginHost mounted = pluginHost;
        pluginHost = null;
        if (panelNode("viewport") instanceof ControlledViewportPanel viewport) viewport.canonicalViewport().clearPluginHost();
        if (mounted != null) mounted.close();
        rebuildPluginCommands();
    }
}
