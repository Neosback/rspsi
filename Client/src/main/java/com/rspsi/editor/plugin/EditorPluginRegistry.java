package com.rspsi.editor.plugin;

import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.assets.AssetDescriptor;
import com.rspsi.editor.ui.PanelDescriptor;
import com.rspsi.editor.ui.WorkspaceCatalog;
import com.rspsi.editor.ui.WorkspaceDefinition;
import com.rspsi.editor.validation.ValidationIssue;
import com.rspsi.editor.input.EditorKeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Neutral contribution registry for first-party editor plugins.
 *
 * <p>The registry stores behavior and layout metadata only. A frontend
 * decides how a tool or panel is rendered; a plugin never receives a
 * JavaFX/ImGui object or owns editor state.</p>
 */
public final class EditorPluginRegistry {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, EditorToolRegistration> tools = new LinkedHashMap<>();
    private final Map<String, EditorCommandRegistration> commands = new LinkedHashMap<>();
    private final Map<String, EditorToolContextRegistration> toolContexts = new LinkedHashMap<>();
    private final Map<String, EditorAssetProviderRegistration> assetProviders = new LinkedHashMap<>();
    private final Map<String, EditorStatusRegistration> statuses = new LinkedHashMap<>();
    private final Map<String, EditorMenuRegistration> menus = new LinkedHashMap<>();
    private final Map<String, EditorOverlayRegistration> overlays = new LinkedHashMap<>();
    private final Map<String, EditorInspectorRegistration> inspectors = new LinkedHashMap<>();
    private final Map<String, EditorValidatorRegistration> validators = new LinkedHashMap<>();
    private final Map<String, EditorShortcutRegistration> shortcuts = new LinkedHashMap<>();
    private final Map<String, PanelDescriptor> panels = new LinkedHashMap<>();
    private final Map<String, WorkspaceDefinition> workspaces = new LinkedHashMap<>();

    public void registerTool(String id, Supplier<? extends EditorTool> factory) {
        ensureOpen();
        String key = requireId(id, "tool");
        registerTool(key, key, "Plugin", factory);
    }

    public void registerTool(String id, String label, String category,
                             Supplier<? extends EditorTool> factory) {
        ensureOpen();
        EditorToolRegistration registration = new EditorToolRegistration(
                id, label, category, factory);
        if (tools.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor tool: " + registration.id());
        }
    }

    public EditorTool createTool(String id) {
        EditorToolRegistration registration = tools.get(requireId(id, "tool"));
        if (registration == null) {
            throw new IllegalArgumentException("Unknown editor tool: " + id);
        }
        return Objects.requireNonNull(registration.factory().get(),
                "tool factory returned null for " + registration.id());
    }

    public void registerCommand(EditorCommandRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "command registration");
        if (commands.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor command: " + registration.id());
        }
    }

    public EditorCommandRegistration commandRegistration(String id) {
        EditorCommandRegistration registration = commands.get(requireId(id, "command"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor command: " + id);
        return registration;
    }

    public com.rspsi.editor.EditorCommand createCommand(String id) {
        EditorCommandRegistration registration = commandRegistration(id);
        return Objects.requireNonNull(registration.factory().get(),
                "command factory returned null for " + registration.id());
    }

    public List<EditorCommandRegistration> commandRegistrations() {
        return List.copyOf(commands.values());
    }

    void removeCommand(String id) {
        commands.remove(id);
    }

    public void registerToolContext(EditorToolContextRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "tool context registration");
        if (toolContexts.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor tool context: " + registration.id());
        }
    }

    public EditorToolContextContribution createToolContext(String id) {
        EditorToolContextRegistration registration = toolContexts.get(requireId(id, "tool context"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor tool context: " + id);
        return Objects.requireNonNull(registration.factory().get(),
                "tool context factory returned null for " + registration.id());
    }

    public List<EditorToolContextRegistration> toolContextRegistrations() {
        return toolContexts.values().stream()
                .sorted(Comparator.comparingInt(EditorToolContextRegistration::order)
                        .thenComparing(EditorToolContextRegistration::id))
                .toList();
    }

    /** Returns settings from all contexts that support the active tool. */
    public List<EditorSetting> settingsForTool(EditorPluginContext context, String toolId) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(toolId, "tool id");
        List<EditorSetting> settings = new ArrayList<>();
        for (EditorToolContextRegistration registration : toolContextRegistrations()) {
            if (!registration.supports(toolId)) continue;
            List<EditorSetting> values = createToolContext(registration.id()).settings(context);
            if (values != null) settings.addAll(values);
        }
        return List.copyOf(settings);
    }

    void removeToolContext(String id) {
        toolContexts.remove(id);
    }

    public void registerAssetProvider(EditorAssetProviderRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "asset provider registration");
        if (assetProviders.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor asset provider: " + registration.id());
        }
    }

    public EditorAssetProvider createAssetProvider(String id) {
        EditorAssetProviderRegistration registration = assetProviders.get(requireId(id, "asset provider"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor asset provider: " + id);
        return Objects.requireNonNull(registration.factory().get(),
                "asset provider factory returned null for " + registration.id());
    }

    public List<EditorAssetProviderRegistration> assetProviderRegistrations() {
        return assetProviders.values().stream()
                .sorted(Comparator.comparingInt(EditorAssetProviderRegistration::order)
                        .thenComparing(EditorAssetProviderRegistration::id))
                .toList();
    }

    /** Searches plugin providers only; the base AssetRepository remains the canonical source. */
    public List<AssetDescriptor> searchProvidedAssets(EditorPluginContext context, String query) {
        Objects.requireNonNull(context, "context");
        List<AssetDescriptor> assets = new ArrayList<>();
        for (EditorAssetProviderRegistration registration : assetProviderRegistrations()) {
            List<AssetDescriptor> values = createAssetProvider(registration.id())
                    .search(context, query == null ? "" : query);
            if (values != null) assets.addAll(values);
        }
        return assets.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AssetDescriptor::type).thenComparingInt(AssetDescriptor::id))
                .toList();
    }

    void removeAssetProvider(String id) {
        assetProviders.remove(id);
    }

    public void registerStatus(EditorStatusRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "status registration");
        if (statuses.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor status contribution: " + registration.id());
        }
    }

    public EditorStatusContribution createStatus(String id) {
        EditorStatusRegistration registration = statuses.get(requireId(id, "status"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor status: " + id);
        return Objects.requireNonNull(registration.factory().get(),
                "status factory returned null for " + registration.id());
    }

    public List<EditorStatusRegistration> statusRegistrations() {
        return statuses.values().stream()
                .sorted(Comparator.comparingInt(EditorStatusRegistration::order)
                        .thenComparing(EditorStatusRegistration::id))
                .toList();
    }

    public List<EditorStatusItem> statusItems(EditorPluginContext context) {
        Objects.requireNonNull(context, "context");
        List<EditorStatusItem> items = new ArrayList<>();
        for (EditorStatusRegistration registration : statusRegistrations()) {
            List<EditorStatusItem> values = createStatus(registration.id()).items(context);
            if (values != null) items.addAll(values);
        }
        return List.copyOf(items);
    }

    void removeStatus(String id) {
        statuses.remove(id);
    }

    public void registerMenu(EditorMenuRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "menu registration");
        if (menus.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor menu contribution: " + registration.id());
        }
    }

    public List<EditorMenuRegistration> menuRegistrations() {
        return menus.values().stream()
                .sorted(Comparator.comparingInt(EditorMenuRegistration::order)
                        .thenComparing(EditorMenuRegistration::id))
                .toList();
    }

    /** Validates shell references after every plugin has had a chance to register commands. */
    public void validateReferences() {
        for (EditorMenuRegistration menu : menus.values()) {
            if (!commands.containsKey(menu.commandId())) {
                throw new IllegalArgumentException("Menu contribution references unknown command: "
                        + menu.commandId());
            }
        }
    }

    void removeMenu(String id) {
        menus.remove(id);
    }

    public void registerPanel(PanelDescriptor panel) {
        ensureOpen();
        Objects.requireNonNull(panel, "panel");
        if (panels.putIfAbsent(panel.id(), panel) != null) {
            throw new IllegalArgumentException("Duplicate editor panel: " + panel.id());
        }
    }

    public void registerWorkspace(WorkspaceDefinition workspace) {
        ensureOpen();
        Objects.requireNonNull(workspace, "workspace");
        if (workspaces.putIfAbsent(workspace.id(), workspace) != null) {
            throw new IllegalArgumentException("Duplicate editor workspace: " + workspace.id());
        }
    }

    public List<String> toolIds() {
        return List.copyOf(tools.keySet());
    }

    void removeTool(String id) {
        tools.remove(id);
    }

    public List<EditorToolRegistration> toolRegistrations() {
        return List.copyOf(tools.values());
    }

    public void registerOverlay(EditorOverlayRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "overlay registration");
        if (overlays.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor overlay: " + registration.id());
        }
    }

    public EditorSceneOverlay createOverlay(String id) {
        EditorOverlayRegistration registration = overlays.get(requireId(id, "overlay"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor overlay: " + id);
        return Objects.requireNonNull(registration.factory().get(),
                "overlay factory returned null for " + registration.id());
    }

    public List<EditorOverlayRegistration> overlayRegistrations() {
        return List.copyOf(overlays.values());
    }

    void removeOverlay(String id) {
        overlays.remove(id);
    }

    public void registerInspector(EditorInspectorRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "inspector registration");
        if (inspectors.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor inspector: " + registration.id());
        }
    }

    public EditorInspector createInspector(String id) {
        EditorInspectorRegistration registration = inspectors.get(requireId(id, "inspector"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor inspector: " + id);
        return Objects.requireNonNull(registration.factory().get(),
                "inspector factory returned null for " + registration.id());
    }

    public List<EditorInspectorRegistration> inspectorRegistrations() {
        return List.copyOf(inspectors.values());
    }

    void removeInspector(String id) {
        inspectors.remove(id);
    }

    public void registerValidator(EditorValidatorRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "validator registration");
        if (validators.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor validator: " + registration.id());
        }
    }

    public EditorValidator createValidator(String id) {
        EditorValidatorRegistration registration = validators.get(requireId(id, "validator"));
        if (registration == null) throw new IllegalArgumentException("Unknown editor validator: " + id);
        return Objects.requireNonNull(registration.factory().get(),
                "validator factory returned null for " + registration.id());
    }

    public List<EditorValidatorRegistration> validatorRegistrations() {
        return List.copyOf(validators.values());
    }

    void removeValidator(String id) {
        validators.remove(id);
    }

    public void registerShortcut(EditorShortcutRegistration registration) {
        ensureOpen();
        Objects.requireNonNull(registration, "shortcut registration");
        if (shortcuts.putIfAbsent(registration.id(), registration) != null) {
            throw new IllegalArgumentException("Duplicate editor shortcut: " + registration.id());
        }
    }

    public List<EditorShortcutRegistration> shortcutRegistrations() {
        return List.copyOf(shortcuts.values());
    }

    void removeShortcut(String id) {
        shortcuts.remove(id);
    }

    /** Dispatches one translated key event in deterministic registration order. */
    public boolean dispatchShortcut(EditorPluginContext context, EditorKeyEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(event, "event");
        for (EditorShortcutRegistration registration : shortcuts.values()) {
            if (registration.matches(event)
                    && createShortcut(registration.id()).handle(context, event)) {
                return true;
            }
        }
        return false;
    }

    public EditorShortcut createShortcut(String id) {
        EditorShortcutRegistration registration = shortcuts.get(requireId(id, "shortcut"));
        if (registration == null) {
            throw new IllegalArgumentException("Unknown editor shortcut: " + id);
        }
        return Objects.requireNonNull(registration.factory().get(),
                "shortcut factory returned null for " + registration.id());
    }

    public List<EditorInspectorField> inspect(EditorPluginContext context) {
        Objects.requireNonNull(context, "context");
        List<EditorInspectorField> fields = new ArrayList<>();
        for (EditorInspectorRegistration registration : inspectors.values()) {
            List<EditorInspectorField> contribution = createInspector(registration.id()).inspect(context);
            fields.addAll(contribution == null ? List.of() : contribution);
        }
        return List.copyOf(fields);
    }

    public List<ValidationIssue> validate(EditorPluginContext context) {
        Objects.requireNonNull(context, "context");
        List<ValidationIssue> issues = new ArrayList<>();
        for (EditorValidatorRegistration registration : validators.values()) {
            List<ValidationIssue> contribution =
                    createValidator(registration.id()).validate(context);
            issues.addAll(contribution == null ? List.of() : contribution);
        }
        return List.copyOf(issues);
    }

    public List<PanelDescriptor> panels() {
        return List.copyOf(panels.values());
    }

    public List<WorkspaceDefinition> workspaces() {
        return List.copyOf(workspaces.values());
    }

    void removePanel(String id) {
        panels.remove(id);
    }

    void removeWorkspace(String id) {
        workspaces.remove(id);
    }

    /** Combines contributions with the host catalog and re-runs validation. */
    public WorkspaceCatalog catalog(WorkspaceCatalog hostCatalog) {
        Objects.requireNonNull(hostCatalog, "hostCatalog");
        List<PanelDescriptor> allPanels = new ArrayList<>(hostCatalog.panels());
        allPanels.addAll(panels.values());
        List<WorkspaceDefinition> allWorkspaces = new ArrayList<>(hostCatalog.workspaces());
        allWorkspaces.addAll(workspaces.values());
        return new WorkspaceCatalog(allPanels, allWorkspaces);
    }

    private static String requireId(String id, String kind) {
        String value = Objects.requireNonNull(id, kind + " id").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(kind + " id cannot be empty");
        }
        return value;
    }

    /** Prevents retained plugin contexts from adding stale frontend contributions. */
    void close() {
        closed.set(true);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Editor plugin registry is closed");
        }
    }
}
