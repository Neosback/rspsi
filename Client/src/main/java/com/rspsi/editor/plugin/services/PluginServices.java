package com.rspsi.editor.plugin.services;

import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.data.DecodedDataCatalog;
import com.rspsi.editor.CompositeEditCommand;
import com.rspsi.editor.DeleteObjectCommand;
import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.MoveObjectCommand;
import com.rspsi.editor.PlaceObjectCommand;
import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.SelectionChangeListener;
import com.rspsi.editor.SetTerrainHeightCommand;
import com.rspsi.editor.SetTileMaterialCommand;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.brush.BrushEngine;
import com.rspsi.editor.brush.BrushMask;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.overlay.OverlayRegistry;
import com.rspsi.editor.corpus.RegionFeatureRegistry;
import com.rspsi.editor.corpus.OsrsRegionFeatureExtractor;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.event.EditorEventBus;
import com.rspsi.editor.plugin.extension.EditorExtensionRegistry;
import com.rspsi.editor.plugin.event.SelectionChangedEvent;
import com.rspsi.editor.plugin.event.TileEditedEvent;
import com.rspsi.editor.plugin.ui.UiSurfaceContribution;
import com.rspsi.editor.terrain.CompiledTerrainTile;
import com.rspsi.editor.terrain.TerrainSceneCompiler;
import com.rspsi.editor.terrain.TerrainVertexLattice;
import com.rspsi.editor.tool.EditorTool;
import com.rspsi.editor.tool.TerrainHeightSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Stable domain-service facade exposed to EditorPlugins.
 *
 * <p>Services mutate the canonical EditorSession through undoable commands
 * and publish typed events. Plugins do not need renderer, ImGui, or cache
 * implementation access to perform normal map-editor operations.</p>
 */
public final class PluginServices {
    private static final Map<EditorPluginRegistry, PluginServices> INSTANCES = new WeakHashMap<>();

    private final EditorSession session;
    private final AssetRepository assets;
    private final DecodedDataCatalog decodedData;
    private final EditorPluginRegistry registry;
    private final EditorEventBus events;
    private final SelectionChangeListener selectionListener;
    private final BrushEngine brushEngine = new BrushEngine();
    private final OverlayRegistry overlays = new OverlayRegistry();
    private final RegionFeatureRegistry corpusFeatures = new RegionFeatureRegistry();
    private final EditorExtensionRegistry extensions = new EditorExtensionRegistry();

    private final TerrainService terrain = new TerrainServiceImpl();
    private final ObjectService objects = new ObjectServiceImpl();
    private final SelectionService selections = new SelectionServiceImpl();
    private final BrushService brushes = new BrushServiceImpl();
    private final ToolService tools = new ToolServiceImpl();
    private final UiService ui = new UiServiceImpl();
    private final CommandService commands = new CommandServiceImpl();
    private volatile java.util.function.Supplier<com.rspsi.api.Client> client = () -> null;

    private PluginServices(EditorSession session, AssetRepository assets,
                           EditorPluginRegistry registry) {
        this.session = Objects.requireNonNull(session, "session");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.decodedData = DecodedDataCatalog.fromAssets(this.assets);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.events = new EditorEventBus();
        this.corpusFeatures.register(new OsrsRegionFeatureExtractor());
        this.selectionListener = ignored ->
                events.publish(new SelectionChangedEvent(
                        session.selection().selectedCoordinates()));
        session.selection().addChangeListener(selectionListener);
    }

    /** One service bundle per plugin registry/host lifecycle. */
    public static synchronized PluginServices resolve(EditorSession session,
                                                      AssetRepository assets,
                                                      EditorPluginRegistry registry) {
        return INSTANCES.computeIfAbsent(Objects.requireNonNull(registry, "registry"),
                ignored -> new PluginServices(session, assets, registry));
    }

    /** Releases host-scoped listeners and contribution registries. */
    public static synchronized void release(EditorPluginRegistry registry) {
        if (registry == null) return;
        PluginServices services = INSTANCES.remove(registry);
        if (services != null) services.close();
    }

    private void close() {
        session.selection().removeChangeListener(selectionListener);
        events.clear();
        overlays.clear();
        extensions.clear();
        corpusFeatures.clear();
    }

    public TerrainService terrain() { return terrain; }
    public ObjectService objects() { return objects; }
    public SelectionService selections() { return selections; }
    public BrushService brushes() { return brushes; }
    public ToolService tools() { return tools; }
    public UiService ui() { return ui; }
    public CommandService commands() { return commands; }
    public EditorEventBus events() { return events; }
    public OverlayRegistry overlays() { return overlays; }
    public RegionFeatureRegistry corpusFeatures() { return corpusFeatures; }
    public DecodedDataCatalog decodedData() { return decodedData; }
    public EditorExtensionRegistry extensions() { return extensions; }

    public interface TerrainService {
        TileSnapshot tile(TileCoordinate coordinate);
        int vertexHeight(int plane, int vertexX, int vertexY);
        double sampleHeight(TileCoordinate coordinate, double x, double y);
        SurfaceNormal normal(TileCoordinate coordinate);
        CompiledTerrainTile compiled(TileCoordinate coordinate);
        void setVertexHeight(int plane, int vertexX, int vertexY, int height);
        void setMaterial(TileCoordinate coordinate, int underlayId, int overlayId,
                         int shape, int rotation);
    }

    public record SurfaceNormal(double x, double y, double z, double slope) { }

    public interface ObjectService {
        List<WorldObject> at(TileCoordinate coordinate);
        Optional<ObjectDefinitionView> definition(int id);
        void spawn(WorldObject object);
        void remove(WorldObject object);
        void move(WorldObject object, int targetX, int targetY);
        void rotate(WorldObject object, int rotation);
    }

    public interface SelectionService {
        Set<TileCoordinate> selectedTiles();
        void select(Set<TileCoordinate> coordinates);
        void clear();
    }

    public interface BrushService {
        void register(EditorBrush brush);
        List<EditorBrush> brushes();
        BrushMask sample(String brushId, int radius, TileCoordinate center);
        double falloff(BrushEngine.Falloff falloff, double normalizedDistance);
    }

    public interface ToolService {
        void register(EditorToolRegistration registration);
        void register(String id, String label, String category,
                      Supplier<? extends EditorTool> factory);
        List<EditorToolRegistration> registrations();
        EditorTool create(String id);
    }

    public interface UiService {
        void register(UiSurfaceContribution contribution);
        List<UiSurfaceContribution> surfaces();
    }

    public interface CommandService {
        void execute(EditorCommand command);
        void execute(String description, List<? extends EditorCommand> commands);
    }

    private final class TerrainServiceImpl implements TerrainService {
        @Override public TileSnapshot tile(TileCoordinate coordinate) {
            return session.world().tile(Objects.requireNonNull(coordinate, "coordinate")).snapshot();
        }

        @Override public int vertexHeight(int plane, int vertexX, int vertexY) {
            return new TerrainVertexLattice(session.world()).height(plane, vertexX, vertexY);
        }

        @Override public double sampleHeight(TileCoordinate coordinate, double x, double y) {
            return TerrainHeightSampler.sample(tile(coordinate), x, y);
        }

        @Override public SurfaceNormal normal(TileCoordinate coordinate) {
            TileSnapshot tile = tile(coordinate);
            double dx = ((tile.southEastHeight() + tile.northEastHeight())
                    - (tile.southWestHeight() + tile.northWestHeight())) * 0.5 / 128.0;
            double dz = ((tile.northWestHeight() + tile.northEastHeight())
                    - (tile.southWestHeight() + tile.southEastHeight())) * 0.5 / 128.0;
            double nx = -dx;
            double ny = 1.0;
            double nz = -dz;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double slope = Math.atan(Math.sqrt(dx * dx + dz * dz));
            return new SurfaceNormal(nx / length, ny / length, nz / length, slope);
        }

        @Override public CompiledTerrainTile compiled(TileCoordinate coordinate) {
            return new TerrainSceneCompiler().compileTile(session.world(), assets, coordinate);
        }

        @Override
        public void setVertexHeight(int plane, int vertexX, int vertexY, int height) {
            var original = session.world();
            var predicted = original.copy();
            Set<TileCoordinate> affected =
                    new TerrainVertexLattice(predicted).setHeight(plane, vertexX, vertexY, height);
            List<EditorCommand> edits = new ArrayList<>();
            for (TileCoordinate coordinate : affected) {
                TileSnapshot before = original.tile(coordinate).snapshot();
                TileSnapshot after = predicted.tile(coordinate).snapshot();
                if (!before.equals(after)) {
                    edits.add(new SetTerrainHeightCommand(coordinate, before, after,
                            before.heightSource(), after.heightSource(),
                            "Set terrain vertex height"));
                }
            }
            executeAndPublish("Set terrain vertex height", edits);
        }

        @Override
        public void setMaterial(TileCoordinate coordinate, int underlayId, int overlayId,
                                int shape, int rotation) {
            TileSnapshot before = tile(coordinate);
            TileSnapshot after = new TileSnapshot(
                    before.southWestHeight(), before.southEastHeight(),
                    before.northEastHeight(), before.northWestHeight(),
                    Math.max(0, underlayId), Math.max(0, overlayId), shape, rotation,
                    before.flags(), before.objects(), before.heightSource());
            executeAndPublish("Set terrain material", List.of(
                    new SetTileMaterialCommand(coordinate, before, after, "Set terrain material")));
        }
    }

    private final class ObjectServiceImpl implements ObjectService {
        @Override public List<WorldObject> at(TileCoordinate coordinate) {
            return session.world().tile(coordinate).objects();
        }

        @Override public Optional<ObjectDefinitionView> definition(int id) {
            return assets.object(id);
        }

        @Override public void spawn(WorldObject object) {
            executeAndPublish("Place object", List.of(new PlaceObjectCommand(object)));
        }

        @Override public void remove(WorldObject object) {
            executeAndPublish("Delete object", List.of(new DeleteObjectCommand(object)));
        }

        @Override public void move(WorldObject object, int targetX, int targetY) {
            executeAndPublish("Move object", List.of(new MoveObjectCommand(object, targetX, targetY)));
        }

        @Override public void rotate(WorldObject object, int rotation) {
            executeAndPublish("Rotate object", List.of(new RotateObjectCommand(object, rotation)));
        }
    }

    private final class SelectionServiceImpl implements SelectionService {
        @Override public Set<TileCoordinate> selectedTiles() {
            return session.selection().selectedCoordinates();
        }

        @Override public void select(Set<TileCoordinate> coordinates) {
            session.selection().selectTiles(coordinates);
        }

        @Override public void clear() {
            session.selection().clear();
        }
    }

    private final class BrushServiceImpl implements BrushService {
        @Override public void register(EditorBrush brush) { brushEngine.register(brush); }
        @Override public List<EditorBrush> brushes() { return brushEngine.brushes(); }

        @Override
        public BrushMask sample(String brushId, int radius, TileCoordinate center) {
            LocalTile local = LocalTile.from(Objects.requireNonNull(center, "center"));
            return brushEngine.sample(brushEngine.brush(brushId), radius,
                    session.coordinates().toWorld(local), session.world(), session.window());
        }

        @Override
        public double falloff(BrushEngine.Falloff falloff, double normalizedDistance) {
            return BrushEngine.applyFalloff(falloff, normalizedDistance);
        }
    }

    private final class ToolServiceImpl implements ToolService {
        @Override public void register(EditorToolRegistration registration) {
            registry.registerTool(registration);
        }

        @Override
        public void register(String id, String label, String category,
                             Supplier<? extends EditorTool> factory) {
            registry.registerTool(new EditorToolRegistration(id, label, category, factory));
        }

        @Override public List<EditorToolRegistration> registrations() {
            return registry.toolRegistrations();
        }

        @Override public EditorTool create(String id) { return registry.createTool(id); }
    }

    private final class UiServiceImpl implements UiService {
        @Override public void register(UiSurfaceContribution contribution) {
            registry.registerUiSurface(contribution);
        }

        @Override public List<UiSurfaceContribution> surfaces() {
            return registry.uiSurfaceContributions();
        }
    }

    private final class CommandServiceImpl implements CommandService {
        @Override public void execute(EditorCommand command) {
            Objects.requireNonNull(command, "command");
            session.execute(command);
            publish(command.description(), command.changedTiles());
        }

        @Override
        public void execute(String description, List<? extends EditorCommand> commands) {
            executeAndPublish(description, commands);
        }
    }

    private void executeAndPublish(String description, List<? extends EditorCommand> edits) {
        if (edits == null || edits.isEmpty()) return;
        List<EditorCommand> commands = new ArrayList<>(edits);
        EditorCommand command = commands.size() == 1
                ? commands.get(0)
                : new CompositeEditCommand(description, commands);
        session.execute(command);
        publish(description, command.changedTiles());
    }

    private void publish(String description, Set<TileCoordinate> tiles) {
        if (tiles != null && !tiles.isEmpty()) {
            events.publish(new TileEditedEvent(tiles, description));
        }
    }

    /**
     * The RuneLite-shaped {@link com.rspsi.api.Client} for the loaded cache:
     * player vars, object definitions, models, map elements and the map
     * navigator. Empty until the host binds one (no cache loaded yet), so call
     * it when acting rather than caching it during plugin initialization.
     */
    public java.util.Optional<com.rspsi.api.Client> client() {
        return java.util.Optional.ofNullable(client.get());
    }

    /** Host-side: binds the client supplier plugins see through {@link #client()}. */
    public void bindClient(java.util.function.Supplier<com.rspsi.api.Client> supplier) {
        client = Objects.requireNonNull(supplier, "supplier");
    }
}
