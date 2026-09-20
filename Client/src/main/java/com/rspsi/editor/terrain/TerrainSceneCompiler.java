package com.rspsi.editor.terrain;

import com.rspsi.cache.AssetCategory;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.MapElementDefinitionView;
import com.rspsi.cache.definition.MapSceneSpriteView;
import com.rspsi.cache.definition.ModelDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.TerrainAppearance;
import com.rspsi.editor.render.TerrainAppearanceBuilder;
import com.rspsi.editor.render.TerrainLight;
import com.rspsi.editor.render.TerrainLighting;
import com.rspsi.editor.render.TerrainShadowMap;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.TerrainPacketBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative authored-terrain compiler. The same compiled tile can be used
 * by the 3D renderer, palette preview, hover ghost, minimap and diagnostics.
 */
public final class TerrainSceneCompiler {
    private final TerrainMeshBuilder meshBuilder = new TerrainMeshBuilder();
    private final TerrainPacketBuilder packetBuilder = new TerrainPacketBuilder();

    public Map<TileCoordinate, CompiledTerrainTile> compile(WorldDocument document,
                                                             AssetRepository assets) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(assets, "assets");
        return compile(document, new AssetRepositoryDefinitions(assets));
    }

    public Map<TileCoordinate, CompiledTerrainTile> compile(WorldDocument document,
                                                             DefinitionProvider definitions) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Map<TileCoordinate, TerrainAppearance> appearances =
                new TerrainAppearanceBuilder().build(document, definitions);
        TerrainShadowMap shadows = TerrainShadowMap.from(document, definitions);
        Map<TileCoordinate, TerrainLight> lighting =
                TerrainLighting.build(document, LightingProfile.osrs(), shadows);

        Map<TileCoordinate, CompiledTerrainTile> result = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    var snapshot = document.tile(coordinate).snapshot();
                    TerrainMesh mesh = meshBuilder.build(snapshot);
                    TerrainAppearance appearance = appearances.get(coordinate);
                    var packet = packetBuilder.build(coordinate, mesh, appearance, lighting.get(coordinate));
                    int flags = snapshot.flags();
                    int minimapHsl = appearance.overlayMinimapHsl() >= 0
                            ? appearance.overlayMinimapHsl() : appearance.underlayHsl();
                    int minimapRgb = minimapHsl >= 0
                            ? OsrsTerrainColorMath.packedHslToRgb(minimapHsl, 0.6)
                            : 0;
                    result.put(coordinate, new CompiledTerrainTile(
                            coordinate, mesh, appearance, lighting.get(coordinate), packet,
                            document.effectivePlane(coordinate),
                            OsrsTileFlags.hasBridge(flags),
                            OsrsTileFlags.removesRoofs(flags),
                            flags,
                            minimapRgb));
                }
            }
        }
        return Map.copyOf(result);
    }

    public CompiledTerrainTile compileTile(WorldDocument document,
                                           AssetRepository assets,
                                           TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        CompiledTerrainTile tile = compile(document, assets).get(coordinate);
        if (tile == null) throw new IndexOutOfBoundsException("Tile outside document: " + coordinate);
        return tile;
    }

    public CompiledTerrainTile compileTile(WorldDocument document,
                                           DefinitionProvider definitions,
                                           TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        CompiledTerrainTile tile = compile(document, definitions).get(coordinate);
        if (tile == null) throw new IndexOutOfBoundsException("Tile outside document: " + coordinate);
        return tile;
    }

    /** Adapter that keeps the compiler on the public AssetRepository boundary. */
    private record AssetRepositoryDefinitions(AssetRepository assets) implements DefinitionProvider {
        @Override public Optional<ObjectDefinitionView> object(int id) { return assets.object(id); }
        @Override public Optional<FloorDefinitionView> underlay(int id) { return assets.underlay(id); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return assets.overlay(id); }
        @Override public Optional<TextureDefinitionView> texture(int id) { return assets.texture(id); }
        @Override public Optional<ModelDefinitionView> model(int id) { return assets.model(id); }
        @Override public Optional<ModelGeometryView> modelGeometry(int id) { return assets.modelGeometry(id); }
        @Override public Optional<MapSceneSpriteView> mapScene(int id) { return assets.mapScene(id); }
        @Override public Optional<SequenceDefinitionView> sequence(int id) { return assets.sequence(id); }
        @Override public Optional<MapElementDefinitionView> mapElement(int id) { return assets.mapElement(id); }
        @Override public Optional<ObjectCollisionView> objectCollision(int id) { return assets.objectCollision(id); }
        @Override public Optional<ObjectAppearanceView> objectAppearance(int id) { return assets.objectAppearance(id); }
        @Override public Optional<int[]> texturePixels(int id, double brightness, int size) {
            return assets.texturePixels(id, brightness, size);
        }
        @Override public List<Integer> objectIds() { return assets.ids(AssetCategory.OBJECTS); }
        @Override public List<Integer> underlayIds() { return assets.ids(AssetCategory.UNDERLAYS); }
        @Override public List<Integer> overlayIds() { return assets.ids(AssetCategory.OVERLAYS); }
        @Override public List<Integer> textureIds() { return assets.ids(AssetCategory.TEXTURES); }
        @Override public List<Integer> modelIds() { return assets.ids(AssetCategory.MODELS); }
        @Override public List<Integer> mapSceneIds() { return assets.ids(AssetCategory.MAP_SCENES); }
        @Override public List<Integer> sequenceIds() { return assets.ids(AssetCategory.SEQUENCES); }
        @Override public List<Integer> mapElementIds() { return assets.ids(AssetCategory.MAP_ELEMENTS); }
    }
}
