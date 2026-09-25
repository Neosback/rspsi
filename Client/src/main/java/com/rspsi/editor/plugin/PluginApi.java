package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.cache.data.DecodedDataCatalog;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.generation.GenerationSchema;
import com.rspsi.editor.generation.Generator;
import com.rspsi.editor.generation.GeneratorService;
import com.rspsi.editor.knowledge.KnowledgeAnalyzer;
import com.rspsi.editor.knowledge.WorldKnowledgeService;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.overlay.OverlayComponent;
import com.rspsi.editor.overlay.OverlayContribution;
import com.rspsi.editor.overlay.OverlayLayer;
import com.rspsi.editor.overlay.OverlayPosition;
import com.rspsi.editor.corpus.RegionFeatureExtractor;
import com.rspsi.editor.plugin.extension.ExtensionPoint;
import com.rspsi.editor.settings.SettingHandle;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingScope;
import com.rspsi.editor.settings.SettingSpec;
import com.rspsi.editor.settings.SettingsService;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.tool.EditorTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fluent, user-friendly, and powerful API front door for first-party and community editor plugins.
 *
 * <p>All contributions registered through {@code PluginApi} are automatically bound to
 * {@link ContributionOwner#plugin(String)}, and any closable resources or dynamic settings
 * are registered with {@link EditorPluginContext#track(AutoCloseable)} so that unload/reload
 * cleans them up automatically without leaving stale state behind.</p>
 */
public final class PluginApi {
    private final EditorPluginContext context;
    private final EditorPlugin plugin;
    private final ContributionOwner owner;

    public PluginApi(EditorPluginContext context, EditorPlugin plugin) {
        this.context = Objects.requireNonNull(context, "context");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.owner = ContributionOwner.plugin(plugin.id());
    }

    public EditorPluginContext context() {
        return context;
    }

    public EditorPlugin plugin() {
        return plugin;
    }

    public ContributionOwner owner() {
        return owner;
    }

    public EditorSession session() {
        return context.session();
    }

    public AssetRepository assets() {
        return context.assets();
    }

    /** Discoverable catalog of decoded cache families and typed providers. */
    public DecodedDataCatalog data() {
        return context.services().decodedData();
    }

    /**
     * The RuneLite-shaped client ({@code com.rspsi.api}): vars, object
     * definitions, models, map elements and the map navigator. Empty until a
     * cache is loaded; look it up when acting instead of keeping it.
     */
    public Optional<com.rspsi.api.Client> client() {
        return context.services().client();
    }

    public Optional<EditorSceneAccess> scene() {
        return context.scene();
    }

    public SettingsStore settingsStore() {
        return context.settings();
    }

    public SettingsService settings() {
        return context.settingsService();
    }

    public EditorTaskService tasks() {
        return context.tasks();
    }

    public EditorNotificationService notifications() {
        return context.notifications();
    }

    public WorldKnowledgeService knowledge() {
        return context.knowledge();
    }

    public GeneratorService generators() {
        return context.generators();
    }

    public void knowledgeAnalyzer(KnowledgeAnalyzer analyzer) {
        Objects.requireNonNull(analyzer, "analyzer");
        AutoCloseable handle = context.knowledge().registerAnalyzer(owner, analyzer);
        track(handle);
    }

    public void generator(String id, GenerationSchema schema, String displayName,
                          String description, Generator generator) {
        AutoCloseable handle = context.generators().registerGenerator(
                owner, id, schema, displayName, description, generator);
        track(handle);
    }

    public <T extends AutoCloseable> T track(T resource) {
        return context.track(resource);
    }

    // --- Fluent Setting Builder ---

    public <T> SettingBuilder<T> setting(String keyId, Class<T> type, T defaultValue) {
        return new SettingBuilder<>(this, keyId, type, defaultValue);
    }

    public SettingBuilder<Boolean> setting(String keyId, boolean defaultValue) {
        return setting(keyId, Boolean.class, defaultValue);
    }

    public SettingBuilder<Integer> setting(String keyId, int defaultValue) {
        return setting(keyId, Integer.class, defaultValue);
    }

    public SettingBuilder<Float> setting(String keyId, float defaultValue) {
        return setting(keyId, Float.class, defaultValue);
    }

    public SettingBuilder<String> setting(String keyId, String defaultValue) {
        return setting(keyId, String.class, defaultValue);
    }

    public static final class SettingBuilder<T> {
        private final PluginApi api;
        private final String id;
        private final Class<T> type;
        private final T defaultValue;
        private String category = "Plugins";
        private int order = 0;
        private String label;
        private String description = "";
        private SettingScope scope = SettingScope.GLOBAL;
        private List<T> options = List.of();
        private Comparable<?> min;
        private Comparable<?> max;

        SettingBuilder(PluginApi api, String id, Class<T> type, T defaultValue) {
            this.api = api;
            this.id = id;
            this.type = type;
            this.defaultValue = defaultValue;
            this.label = id;
        }

        public SettingBuilder<T> category(String category) {
            this.category = category;
            return this;
        }

        public SettingBuilder<T> order(int order) {
            this.order = order;
            return this;
        }

        public SettingBuilder<T> label(String label) {
            this.label = label;
            return this;
        }

        public SettingBuilder<T> description(String description) {
            this.description = description;
            return this;
        }

        public SettingBuilder<T> scope(SettingScope scope) {
            this.scope = scope;
            return this;
        }

        public SettingBuilder<T> options(List<T> options) {
            this.options = List.copyOf(options);
            return this;
        }

        @SafeVarargs
        public final SettingBuilder<T> options(T... options) {
            return options(Arrays.asList(options));
        }

        public SettingBuilder<T> range(Comparable<?> min, Comparable<?> max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public BoundSetting<T> register() {
            SettingKey<T> key = new SettingKey<>(id, type);
            Double minDouble = min instanceof Number n ? n.doubleValue() : null;
            Double maxDouble = max instanceof Number n ? n.doubleValue() : null;
            SettingSpec<T> spec = new SettingSpec<>(
                    key, defaultValue, scope, label, description, java.util.Set.of(),
                    minDouble, maxDouble, options, category, order);
            SettingHandle handle = api.settings().register(api.owner(), spec);
            api.track(handle);
            return new BoundSetting<>(api.settings(), spec, handle);
        }
    }

    // --- Fluent Tool Registration ---

    public ToolBuilder tool(String id) {
        return new ToolBuilder(this, id);
    }

    /** First-class map-tool alias; built-ins and installed extensions share this contract. */
    public ToolBuilder mapTool(String id) {
        return tool(id);
    }

    public void tool(String id, Supplier<? extends EditorTool> factory) {
        tool(id).factory(factory).register();
    }

    public static final class ToolBuilder {
        private final PluginApi api;
        private final String id;
        private String label;
        private String category = "Plugin";
        private String toolGroup;
        private String icon;
        private String shortcut;
        private int order;
        private final EnumSet<ToolUiDescriptor.ToolSurface> surfaces =
                EnumSet.of(ToolUiDescriptor.ToolSurface.BOTTOM_BAR,
                        ToolUiDescriptor.ToolSurface.FLOATING_TOOLBAR);
        private final EnumSet<ToolUiDescriptor.ToolCapability> capabilities =
                EnumSet.noneOf(ToolUiDescriptor.ToolCapability.class);
        private ToolUiDescriptor.BrushUiMode brushUiMode = ToolUiDescriptor.BrushUiMode.NONE;
        private boolean hasContextDrawerContent;
        private Supplier<? extends EditorTool> factory;

        ToolBuilder(PluginApi api, String id) {
            this.api = api;
            this.id = id;
            this.label = id;
        }

        public ToolBuilder label(String label) {
            this.label = label;
            return this;
        }

        public ToolBuilder category(String category) {
            this.category = category;
            return this;
        }

        public ToolBuilder group(String toolGroup) {
            this.toolGroup = toolGroup;
            return this;
        }

        public ToolBuilder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public ToolBuilder shortcut(String shortcut) {
            this.shortcut = shortcut;
            return this;
        }

        public ToolBuilder order(int order) {
            this.order = order;
            return this;
        }

        public ToolBuilder surfaces(ToolUiDescriptor.ToolSurface... values) {
            surfaces.clear();
            if (values != null) {
                for (ToolUiDescriptor.ToolSurface value : values) {
                    surfaces.add(Objects.requireNonNull(value, "tool surface"));
                }
            }
            return this;
        }

        public ToolBuilder capability(ToolUiDescriptor.ToolCapability capability) {
            capabilities.add(Objects.requireNonNull(capability, "tool capability"));
            return this;
        }

        public ToolBuilder capabilities(ToolUiDescriptor.ToolCapability... values) {
            if (values != null) {
                for (ToolUiDescriptor.ToolCapability value : values) {
                    capability(value);
                }
            }
            return this;
        }

        public ToolBuilder brushUi(ToolUiDescriptor.BrushUiMode mode) {
            this.brushUiMode = Objects.requireNonNull(mode, "brush UI mode");
            if (mode == ToolUiDescriptor.BrushUiMode.SHARED_SETTINGS) {
                capabilities.add(ToolUiDescriptor.ToolCapability.BRUSH_FOOTPRINT);
            }
            return this;
        }

        public ToolBuilder contextDrawer(boolean enabled) {
            this.hasContextDrawerContent = enabled;
            if (enabled) {
                capabilities.add(ToolUiDescriptor.ToolCapability.CONTEXT_DRAWER);
            } else {
                capabilities.remove(ToolUiDescriptor.ToolCapability.CONTEXT_DRAWER);
            }
            return this;
        }

        public ToolBuilder factory(Supplier<? extends EditorTool> factory) {
            this.factory = factory;
            return this;
        }

        public void register() {
            Objects.requireNonNull(factory, "tool factory");
            ToolUiDescriptor ui = new ToolUiDescriptor(
                    Set.copyOf(surfaces),
                    brushUiMode,
                    Set.copyOf(capabilities),
                    hasContextDrawerContent);
            api.context.registry().registerTool(new EditorToolRegistration(
                    id,
                    label,
                    category,
                    toolGroup,
                    icon,
                    shortcut,
                    order,
                    ui,
                    factory));
        }
    }

    /**
     * Publishes a typed inter-plugin extension and removes it automatically
     * when this plugin unloads.
     */
    public <T> void extension(ExtensionPoint<T> point, String id, int priority, T extension) {
        AutoCloseable handle = context.services().extensions().register(
                Objects.requireNonNull(point, "point"), id, priority, extension);
        track(handle);
    }

    public <T> void extension(ExtensionPoint<T> point, String id, T extension) {
        extension(point, id, 0, extension);
    }

    /** Registers a cache/region feature family for similarity, WFC and analysis. */
    public void regionFeature(RegionFeatureExtractor extractor) {
        AutoCloseable handle = context.services().corpusFeatures().register(
                Objects.requireNonNull(extractor, "extractor"));
        track(handle);
    }

    // --- Declarative HUD Overlay ---

    /**
     * Builds a frontend-neutral movable HUD contribution. This is preferred
     * over direct scene drawing for status panels, infoboxes, progress and
     * tool telemetry because every frontend can render the same component tree.
     */
    public HudBuilder hud(String id) {
        return new HudBuilder(this, id);
    }

    public static final class HudBuilder {
        private final PluginApi api;
        private final String id;
        private String label;
        private OverlayPosition position = OverlayPosition.TOP_LEFT;
        private OverlayLayer layer = OverlayLayer.HUD;
        private int priority;
        private boolean movable = true;
        private boolean enabledByDefault = true;
        private float preferredWidth = 240.0f;
        private float defaultOpacity = 0.86f;
        private Supplier<? extends OverlayComponent> content;

        HudBuilder(PluginApi api, String id) {
            this.api = api;
            this.id = id;
            this.label = id;
        }

        public HudBuilder label(String label) {
            this.label = label;
            return this;
        }

        public HudBuilder position(OverlayPosition position) {
            this.position = Objects.requireNonNull(position, "position");
            return this;
        }

        public HudBuilder layer(OverlayLayer layer) {
            this.layer = Objects.requireNonNull(layer, "layer");
            return this;
        }

        public HudBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public HudBuilder movable(boolean movable) {
            this.movable = movable;
            return this;
        }

        public HudBuilder enabledByDefault(boolean enabledByDefault) {
            this.enabledByDefault = enabledByDefault;
            return this;
        }

        public HudBuilder width(float preferredWidth) {
            this.preferredWidth = preferredWidth;
            return this;
        }

        /** Initial HUD opacity. Users may override it in Studio without changing the plugin. */
        public HudBuilder opacity(float opacity) {
            this.defaultOpacity = opacity;
            return this;
        }

        public HudBuilder content(OverlayComponent component) {
            Objects.requireNonNull(component, "component");
            this.content = () -> component;
            return this;
        }

        public HudBuilder content(Supplier<? extends OverlayComponent> content) {
            this.content = Objects.requireNonNull(content, "content");
            return this;
        }

        public void register() {
            Objects.requireNonNull(content, "HUD content");
            AutoCloseable handle = api.context.services().overlays().register(
                    new OverlayContribution(id, label, position, layer, priority,
                            movable, enabledByDefault, preferredWidth, defaultOpacity, content));
            api.track(handle);
        }
    }

    // --- Fluent Scene Overlay ---

    public void sceneOverlay(String id, String label, String category, Supplier<? extends EditorSceneOverlay> factory) {
        context.registry().registerOverlay(new EditorOverlayRegistration(id, label, category, factory));
    }

    public void sceneOverlay(String id, String label, Supplier<? extends EditorSceneOverlay> factory) {
        sceneOverlay(id, label, "General", factory);
    }

    public void sceneOverlay(String id, Supplier<? extends EditorSceneOverlay> factory) {
        sceneOverlay(id, id, "General", factory);
    }

    public void sceneOverlay(String id, BiConsumer<EditorSceneSnapshot, OverlayDraw> drawAction) {
        Objects.requireNonNull(drawAction, "drawAction");
        sceneOverlay(id, id, "General", () -> drawAction::accept);
    }

    // --- Fluent Menu and Command Builder ---

    public MenuBuilder menu(String id) {
        return new MenuBuilder(this, id);
    }

    public static final class MenuBuilder {
        private final PluginApi api;
        private final String id;
        private final List<String> path = new ArrayList<>();
        private String label;
        private String commandId;
        private int order = 0;
        private Consumer<EditorSession> action;

        MenuBuilder(PluginApi api, String id) {
            this.api = api;
            this.id = id;
            this.label = id;
        }

        public MenuBuilder path(String... pathSegments) {
            this.path.clear();
            this.path.addAll(Arrays.asList(pathSegments));
            return this;
        }

        public MenuBuilder path(List<String> pathSegments) {
            this.path.clear();
            this.path.addAll(pathSegments);
            return this;
        }

        public MenuBuilder label(String label) {
            this.label = label;
            return this;
        }

        public MenuBuilder order(int order) {
            this.order = order;
            return this;
        }

        public MenuBuilder command(String commandId) {
            this.commandId = commandId;
            return this;
        }

        public MenuBuilder action(Runnable action) {
            Objects.requireNonNull(action, "action");
            this.action = session -> action.run();
            return this;
        }

        public MenuBuilder action(Consumer<EditorSession> action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        public void register() {
            if (path.isEmpty()) {
                path.add("Plugins");
            }
            if (commandId == null && action != null) {
                String generatedCommandId = id + ".command";
                api.context.registry().registerCommand(new EditorCommandRegistration(
                        generatedCommandId, label, path.get(0), () -> new EditorCommand() {
                            @Override
                            public void apply(EditorSession session) {
                                action.accept(session);
                            }

                            @Override
                            public void undo(EditorSession session) {
                            }

                            @Override
                            public String description() {
                                return label;
                            }
                        }));
                this.commandId = generatedCommandId;
            }
            Objects.requireNonNull(commandId, "commandId or action must be provided");
            api.context.registry().registerMenu(new EditorMenuRegistration(id, path, label, commandId, order));
        }
    }

    // --- Fluent Keyboard Shortcuts ---

    public ShortcutBuilder shortcut(String id) {
        return new ShortcutBuilder(this, id);
    }

    public void shortcut(String id, String key, boolean ctrl, boolean shift, boolean alt, Runnable action) {
        shortcut(id).key(key).ctrl(ctrl).shift(shift).alt(alt).action(action).register();
    }

    public static final class ShortcutBuilder {
        private final PluginApi api;
        private final String id;
        private String label;
        private String key;
        private boolean shift;
        private boolean ctrl;
        private boolean alt;
        private boolean meta;
        private Consumer<EditorPluginContext> action;

        ShortcutBuilder(PluginApi api, String id) {
            this.api = api;
            this.id = id;
            this.label = id;
        }

        public ShortcutBuilder label(String label) {
            this.label = label;
            return this;
        }

        public ShortcutBuilder key(String key) {
            this.key = key;
            return this;
        }

        public ShortcutBuilder ctrl() {
            this.ctrl = true;
            return this;
        }

        public ShortcutBuilder ctrl(boolean ctrl) {
            this.ctrl = ctrl;
            return this;
        }

        public ShortcutBuilder shift() {
            this.shift = true;
            return this;
        }

        public ShortcutBuilder shift(boolean shift) {
            this.shift = shift;
            return this;
        }

        public ShortcutBuilder alt() {
            this.alt = true;
            return this;
        }

        public ShortcutBuilder alt(boolean alt) {
            this.alt = alt;
            return this;
        }

        public ShortcutBuilder meta() {
            this.meta = true;
            return this;
        }

        public ShortcutBuilder meta(boolean meta) {
            this.meta = meta;
            return this;
        }

        public ShortcutBuilder action(Runnable action) {
            Objects.requireNonNull(action, "action");
            this.action = ctx -> action.run();
            return this;
        }

        public ShortcutBuilder action(Consumer<EditorPluginContext> action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        public void register() {
            Objects.requireNonNull(key, "shortcut key");
            Objects.requireNonNull(action, "shortcut action");
            api.context.registry().registerShortcut(new EditorShortcutRegistration(
                    id, label != null ? label : id, key, shift, ctrl, alt, meta,
                    () -> (ctx, event) -> {
                        action.accept(ctx);
                        return true;
                    }));
        }
    }

    // --- Status Items ---

    public void statusItem(String id, String label, int order, Supplier<String> textSupplier) {
        Objects.requireNonNull(textSupplier, "textSupplier");
        context.registry().registerStatus(new EditorStatusRegistration(
                id, label, order, () -> ctx -> List.of(new EditorStatusItem(id, label, textSupplier.get()))));
    }

    public void statusItem(String id, Supplier<String> textSupplier) {
        statusItem(id, id, 0, textSupplier);
    }

    // --- Inspectors ---

    public void inspector(String id, String label, String category, Function<EditorPluginContext, List<EditorInspectorField>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        context.registry().registerInspector(new EditorInspectorRegistration(
                id, label, category, () -> supplier::apply));
    }
}
