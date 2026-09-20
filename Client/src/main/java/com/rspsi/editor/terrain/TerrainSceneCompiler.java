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
import com.rspsi.editor.model.RegionNeighborhood;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.TerrainAppearance;
import com.rspsi.editor.render.TerrainAppearanceBuilder;
import com.rspsi.editor.render.TerrainLight;
import com.rspsi.editor.render.TerrainLighting;
import com.rspsi.editor.render.TerrainPacketBuilder;
import com.rspsi.editor.render.TerrainShadowMap;
import com.rspsi.editor.render.compiler.InvalidationGraph;
import com.rspsi.editor.render.compiler.SceneZone;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative authored-terrain compiler.
 *
 * <p>The zone APIs are the incremental path: only tiles inside requested 8x8
 * zones are compiled, while radius-based underlay and normal sampling still
 * reads canonical neighboring tiles from the document.</p>
 */
public final class TerrainSceneCompiler {
    private final TerrainMeshBuilder meshBuilder = new TerrainMeshBuilder();
    private final TerrainPacketBuilder packetBuilder = new TerrainPacketBuilder();
    private final TerrainAppearanceBuilder appearanceBuilder = new TerrainAppearanceBuilder();

    public Map<TileCoordinate, CompiledTerrainTile> compile(WorldDocument document,
                                                             AssetRepository assets) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(assets, "assets");
        return compile(document, new AssetRepositoryDefinitions(assets));
    }

    public Map<TileCoordinate, CompiledTerrainTile> compile(WorldDocument document,
                                                             DefinitionProvider definitions) {
        return compile(document, definitions, LightingProfile.osrs());
    }

    public Map<TileCoordinate, CompiledTerrainTile> compile(WorldDocument document,
                                                             DefinitionProvider definitions,
                                                             LightingProfile lightingProfile) {
        Objects.requireNonNull(document, "document");
        Set<InvalidationGraph.ZoneCoordinate> zones = new LinkedHashSet<>();
        int maxZoneX = (document.width() - 1) >> 3;
        int maxZoneY = (document.length() - 1) >> 3;
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int zoneX = 0; zoneX <= maxZoneX; zoneX++) {
                for (int zoneY = 0; zoneY <= maxZoneY; zoneY++) {
                    zones.add(new InvalidationGraph.ZoneCoordinate(plane, zoneX, zoneY));
                }
            }
        }
        return compileZones(document, definitions, lightingProfile, zones);
    }

    /**
     * Compiles only the requested 8x8 zones. Neighborhood-dependent rules are
     * sampled from the full canonical document, so a zone boundary never
     * becomes a color or normal boundary.
     */
    public Map<TileCoordinate, CompiledTerrainTile> compileZones(
            WorldDocument document,
            DefinitionProvider definitions,
            LightingProfile lightingProfile,
            Set<InvalidationGraph.ZoneCoordinate> zones) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(lightingProfile, "lightingProfile");
        Objects.requireNonNull(zones, "zones");
        if (zones.isEmpty()) return Map.of();

        TerrainShadowMap shadows = TerrainShadowMap.from(document, definitions);
        Map<TileCoordinate, CompiledTerrainTile> result = new LinkedHashMap<>();
        for (InvalidationGraph.ZoneCoordinate zone : zones) {
            validateZone(document, zone);
            int startX = zone.zoneX() * SceneZone.ZONE_SIZE;
            int startY = zone.zoneY() * SceneZone.ZONE_SIZE;
            int endX = Math.min(document.width(), startX + SceneZone.ZONE_SIZE);
            int endY = Math.min(document.length(), startY + SceneZone.ZONE_SIZE);
            for (int x = startX; x < endX; x++) {
                for (int y = startY; y < endY; y++) {
                    TileCoordinate coordinate = new TileCoordinate(zone.plane(), x, y);
                    result.put(coordinate, compileTile(
                            document, definitions, lightingProfile, shadows, coordinate));
                }
            }
        }
        return Map.copyOf(result);
    }

    public Map<TileCoordinate, CompiledTerrainTile> compileZone(
            WorldDocument document,
            DefinitionProvider definitions,
            LightingProfile lightingProfile,
            InvalidationGraph.ZoneCoordinate zone) {
        return compileZones(document, definitions, lightingProfile, Set.of(zone));
    }

    /**
     * Compiles the center 64x64 region while sampling appearance, normals and
     * bridge semantics from its loaded 3x3 neighborhood.
     */
    public Map<TileCoordinate, CompiledTerrainTile> compileCenterRegion(
            RegionNeighborhood neighborhood,
            DefinitionProvider definitions,
            LightingProfile lightingProfile) {
        Objects.requireNonNull(neighborhood, "neighborhood");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(lightingProfile, "lightingProfile");
        WorldRegion center = neighborhood.center();
        WorldDocument document = center.document();
        int originX = center.regionX() * WorldRegion.REGION_SIZE;
        int originY = center.regionY() * WorldRegion.REGION_SIZE;
        Map<TileCoordinate, CompiledTerrainTile> result = new LinkedHashMap<>();

        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    int worldX = originX + x;
                    int worldY = originY + y;
                    var snapshot = document.tile(coordinate).snapshot();
                    TerrainMesh mesh = meshBuilder.build(snapshot);
                    TerrainAppearance appearance = appearanceBuilder.buildTile(
                            neighborhood, definitions, plane, worldX, worldY);
                    TerrainLight lighting = TerrainLighting.buildTile(
                            neighborhood, lightingProfile, plane, worldX, worldY);
                    var packet = packetBuilder.build(coordinate, mesh, appearance, lighting);
                    int flags = snapshot.flags();
                    int minimapHsl = appearance.overlayMinimapHsl() >= 0
                            ? appearance.overlayMinimapHsl() : appearance.underlayHsl();
                    int minimapRgb = minimapHsl >= 0
                            ? OsrsTerrainColorMath.packedHslToRgb(minimapHsl, 0.6) : 0;
                    result.put(coordinate, new CompiledTerrainTile(
                            coordinate, mesh, appearance, lighting, packet,
                            neighborhood.effectivePlane(plane, worldX, worldY),
                            OsrsTileFlags.hasBridge(flags), OsrsTileFlags.removesRoofs(flags),
                            flags, minimapRgb));
                }
            }
        }
        return Map.copyOf(result);
    }

    public CompiledTerrainTile compileTile(WorldDocument document,
                                           AssetRepository assets,
                                           TileCoordinate coordinate) {
        Objects.requireNonNull(assets, "assets");
        return compileTile(document, new AssetRepositoryDefinitions(assets),
                LightingProfile.osrs(), TerrainShadowMap.from(document,
                        new AssetRepositoryDefinitions(assets)), coordinate);
    }

    public CompiledTerrainTile compileTile(WorldDocument document,
                                           DefinitionProvider definitions,
                                           TileCoordinate coordinate) {
        Objects.requireNonNull(definitions, "definitions");
        return compileTile(document, definitions, LightingProfile.osrs(),
                TerrainShadowMap.from(document, definitions), coordinate);
    }

    private CompiledTerrainTile compileTile(WorldDocument document,
                                            DefinitionProvider definitions,
                                            LightingProfile lightingProfile,
                                            TerrainShadowMap shadows,
                                            TileCoordinate coordinate) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(lightingProfile, "lightingProfile");
        Objects.requireNonNull(coordinate, "coordinate");
        if (!document.contains(coordinate)) {
            throw new IndexOutOfBoundsException("Tile outside document: " + coordinate);
        }

        var snapshot = document.tile(coordinate).snapshot();
        TerrainMesh mesh = meshBuilder.build(snapshot);
        TerrainAppearance appearance = appearanceBuilder.buildTile(
                document, definitions, coordinate.plane(), coordinate.x(), coordinate.y());
        TerrainLight lighting = TerrainLighting.buildTile(
                document, lightingProfile, shadows,
                coordinate.plane(), coordinate.x(), coordinate.y());
        var packet = packetBuilder.build(coordinate, mesh, appearance, lighting);
        int flags = snapshot.flags();
        int minimapHsl = appearance.overlayMinimapHsl() >= 0
                ? appearance.overlayMinimapHsl() : appearance.underlayHsl();
        int minimapRgb = minimapHsl >= 0
                ? OsrsTerrainColorMath.packedHslToRgb(minimapHsl, 0.6)
                : 0;
        return new CompiledTerrainTile(
                coordinate, mesh, appearance, lighting, packet,
                document.effectivePlane(coordinate),
                OsrsTileFlags.hasBridge(flags),
                OsrsTileFlags.removesRoofs(flags),
                flags,
                minimapRgb);
    }

    private static void validateZone(WorldDocument document,
                                     InvalidationGraph.ZoneCoordinate zone) {
        Objects.requireNonNull(zone, "zone");
        int maxZoneX = (document.width() - 1) >> 3;
        int maxZoneY = (document.length() - 1) >> 3;
        if (zone.plane() < 0 || zone.plane() >= document.planes()
                || zone.zoneX() < 0 || zone.zoneX() > maxZoneX
                || zone.zoneY() < 0 || zone.zoneY() > maxZoneY) {
            throw new IndexOutOfBoundsException("Zone outside document: " + zone);
        }
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
