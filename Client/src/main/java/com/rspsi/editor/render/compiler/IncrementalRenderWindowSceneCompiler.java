package com.rspsi.editor.render.compiler;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.RenderTextureResourceBuilder;
import com.rspsi.editor.render.RenderWindowScene;
import com.rspsi.editor.render.RenderWindowSceneBuilder;
import com.rspsi.editor.render.TerrainMaterial;
import com.rspsi.editor.render.TerrainPacketBuilder;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.terrain.CompiledTerrainTile;
import com.rspsi.editor.terrain.TerrainSceneCompiler;
import com.rspsi.osrs.rules.terrain.FloorBlendRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Incremental compiler for the stitched world-window path used by Map Studio.
 *
 * <p>This deliberately operates above the single-document {@link IncrementalSceneCompiler}.
 * A world window is first copied and edge-stitched exactly like {@link RenderWindowSceneBuilder},
 * then only terrain zones affected by underlay/overlay edits are recompiled from the same padded
 * radius-five context document. Structural mutations currently take the conservative full-build
 * fallback so incremental speed never changes bridge, object, collision, or height semantics.</p>
 */
public final class IncrementalRenderWindowSceneCompiler {
    private static final int TERRAIN_CONTEXT_BORDER = FloorBlendRules.UNDERLAY_BLEND_RADIUS;

    private final DefinitionProvider definitions;
    private final RenderWindowSceneBuilder fullBuilder;
    private final TerrainSceneCompiler terrainCompiler = new TerrainSceneCompiler();
    private final TerrainPacketBuilder packetBuilder = new TerrainPacketBuilder();

    public IncrementalRenderWindowSceneCompiler(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.fullBuilder = new RenderWindowSceneBuilder(definitions);
    }

    /**
     * Updates one previously compiled world window.
     *
     * @param previous last successfully compiled scene
     * @param source current authored window
     * @param changedTiles absolute world addresses reported by the editor session
     * @param clientCycle animation cycle used by full-build fallbacks
     */
    public UpdateResult compile(RenderWindowScene previous,
                                WorldRegionWindow source,
                                Set<WorldTileAddress> changedTiles,
                                int clientCycle) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(changedTiles, "changedTiles");
        if (clientCycle < 0) throw new IllegalArgumentException("Client cycle cannot be negative");
        if (changedTiles.isEmpty()) {
            return new UpdateResult(previous, false, 0, Set.of(), "no changes");
        }
        if (!sameTopology(previous.window(), source)) {
            return full(source, clientCycle, "window topology changed");
        }

        WorldRegionWindow prepared = source.copy();
        prepared.stitchSharedEdges();

        Set<TileCoordinate> underlayChanges = new LinkedHashSet<>();
        Set<TileCoordinate> overlayChanges = new LinkedHashSet<>();
        for (WorldTileAddress address : changedTiles) {
            TileSnapshot before = previous.window()
                    .tile(address.plane(), address.worldX(), address.worldY())
                    .orElse(null);
            TileSnapshot after = prepared
                    .tile(address.plane(), address.worldX(), address.worldY())
                    .orElse(null);
            if (before == null || after == null) {
                return full(source, clientCycle, "changed tile is outside a loaded region");
            }
            if (before.equals(after)) continue;
            if (structuralChange(before, after)) {
                return full(source, clientCycle, "structural terrain/object change");
            }

            TileCoordinate padded = paddedCoordinate(prepared, address);
            if (before.underlayId() != after.underlayId()) {
                underlayChanges.add(padded);
            }
            if (before.overlayId() != after.overlayId()
                    || before.overlayShape() != after.overlayShape()
                    || before.overlayRotation() != after.overlayRotation()) {
                overlayChanges.add(padded);
            }
        }

        if (underlayChanges.isEmpty() && overlayChanges.isEmpty()) {
            // A changedTiles notification can be broader than the actual persisted delta
            // after undo/redo coalescing. Preserve exact object identity in that case.
            return new UpdateResult(previous, false, 0, Set.of(), "no render delta");
        }

        var paddedDocument = prepared.materializePaddedWorldDocument(TERRAIN_CONTEXT_BORDER);
        Set<InvalidationGraph.ZoneCoordinate> dirtyZones = new LinkedHashSet<>();
        dirtyZones.addAll(InvalidationGraph.computeInvalidatedZones(
                underlayChanges, InvalidationGraph.InvalidationCause.UNDERLAY_EDIT,
                paddedDocument.width(), paddedDocument.length()));
        dirtyZones.addAll(InvalidationGraph.computeInvalidatedZones(
                overlayChanges, InvalidationGraph.InvalidationCause.OVERLAY_EDIT,
                paddedDocument.width(), paddedDocument.length()));

        Map<TileCoordinate, CompiledTerrainTile> compiled =
                terrainCompiler.compileZones(paddedDocument, definitions,
                        previous.lightingProfile(), dirtyZones);

        Map<WorldTileAddress, com.rspsi.editor.terrain.TerrainMesh> meshes =
                new LinkedHashMap<>(previous.terrainMeshes());
        Map<WorldTileAddress, TerrainMaterial> materials =
                new LinkedHashMap<>(previous.terrainMaterials());
        Map<WorldTileAddress, com.rspsi.editor.render.TerrainAppearance> appearances =
                new LinkedHashMap<>(previous.terrainAppearances());
        Map<WorldTileAddress, com.rspsi.editor.render.TerrainLight> lighting =
                new LinkedHashMap<>(previous.terrainLighting());
        Map<WorldTileAddress, TerrainRenderPacket> packets =
                new LinkedHashMap<>(previous.terrainPackets());

        int compiledVisibleTiles = 0;
        for (Map.Entry<TileCoordinate, CompiledTerrainTile> entry : compiled.entrySet()) {
            WorldTileAddress address = worldAddress(prepared, entry.getKey());
            if (address == null || prepared.tile(address.plane(), address.worldX(), address.worldY()).isEmpty()) {
                continue;
            }
            CompiledTerrainTile tile = entry.getValue();
            TileSnapshot authored = prepared.tile(
                    address.plane(), address.worldX(), address.worldY()).orElseThrow();

            meshes.put(address, tile.mesh());
            materials.put(address, material(authored));
            appearances.put(address, tile.appearance());
            lighting.put(address, tile.lighting());
            packets.put(address, packetBuilder.build(
                    new TileCoordinate(address.plane(), address.regionLocalX(), address.regionLocalY()),
                    tile.mesh(), tile.appearance(), tile.lighting()));
            compiledVisibleTiles++;
        }

        List<ModelRenderPacket> allModels = previous.modelPackets().values().stream()
                .flatMap(List::stream).toList();
        Map<Integer, RenderTextureResource> textures =
                RenderTextureResourceBuilder.build(definitions, previous.lightingProfile(),
                        packets.values(), allModels);

        RenderWindowScene scene = new RenderWindowScene(
                prepared,
                meshes,
                materials,
                appearances,
                lighting,
                packets,
                previous.modelPackets(),
                previous.tileFlags(),
                previous.lightingProfile(),
                previous.collision(),
                previous.objects(),
                previous.bridges(),
                textures);

        return new UpdateResult(scene, false, compiledVisibleTiles,
                Set.copyOf(dirtyZones), "incremental terrain");
    }

    private UpdateResult full(WorldRegionWindow source, int clientCycle, String reason) {
        RenderWindowScene scene = fullBuilder.build(source, clientCycle);
        return new UpdateResult(scene, true, scene.terrainPackets().size(), Set.of(), reason);
    }

    private static boolean sameTopology(WorldRegionWindow previous, WorldRegionWindow current) {
        return previous.minRegionX() == current.minRegionX()
                && previous.minRegionY() == current.minRegionY()
                && previous.regionWidth() == current.regionWidth()
                && previous.regionHeight() == current.regionHeight()
                && previous.regions().keySet().equals(current.regions().keySet());
    }

    private static boolean structuralChange(TileSnapshot before, TileSnapshot after) {
        return before.southWestHeight() != after.southWestHeight()
                || before.southEastHeight() != after.southEastHeight()
                || before.northEastHeight() != after.northEastHeight()
                || before.northWestHeight() != after.northWestHeight()
                || before.flags() != after.flags()
                || !before.objects().equals(after.objects());
    }

    private static TileCoordinate paddedCoordinate(WorldRegionWindow window,
                                                   WorldTileAddress address) {
        var world = window.worldWindow();
        return new TileCoordinate(address.plane(),
                TERRAIN_CONTEXT_BORDER + address.worldX() - world.originX(),
                TERRAIN_CONTEXT_BORDER + address.worldY() - world.originY());
    }

    private static WorldTileAddress worldAddress(WorldRegionWindow window,
                                                 TileCoordinate padded) {
        var world = window.worldWindow();
        int relativeX = padded.x() - TERRAIN_CONTEXT_BORDER;
        int relativeY = padded.y() - TERRAIN_CONTEXT_BORDER;
        if (relativeX < 0 || relativeY < 0
                || relativeX >= world.width() || relativeY >= world.length()) {
            return null;
        }
        return WorldTileAddress.of(world.originX() + relativeX,
                world.originY() + relativeY, padded.plane());
    }

    private TerrainMaterial material(TileSnapshot tile) {
        FloorDefinitionView underlay = tile.underlayId() <= 0
                ? null : definitions.underlay(tile.underlayId() - 1).orElse(null);
        FloorDefinitionView overlay = tile.overlayId() <= 0
                ? null : definitions.overlay(tile.overlayId() - 1).orElse(null);
        return new TerrainMaterial(tile.underlayId(), tile.overlayId(),
                overlay == null ? -1 : overlay.texture(),
                underlay == null ? 0 : underlay.rgb() & 0xFFFFFF,
                overlay == null ? 0 : overlay.rgb() & 0xFFFFFF);
    }

    /** Diagnostics for tests and Studio build metrics. */
    public record UpdateResult(
            RenderWindowScene scene,
            boolean fullRebuild,
            int compiledVisibleTiles,
            Set<InvalidationGraph.ZoneCoordinate> dirtyZones,
            String reason
    ) {
        public UpdateResult {
            scene = Objects.requireNonNull(scene, "scene");
            dirtyZones = Set.copyOf(Objects.requireNonNull(dirtyZones, "dirtyZones"));
            reason = Objects.requireNonNull(reason, "reason");
            if (compiledVisibleTiles < 0) {
                throw new IllegalArgumentException("Compiled tile count cannot be negative");
            }
        }
    }
}
