package com.rspsi.cache.map;

import com.openrune.studio.core.osrs.map.ModernOsrsRegionCodec;
import com.openrune.studio.core.osrs.map.OsrsObjectPlacement;
import com.openrune.studio.core.osrs.map.OsrsRegionData;
import com.openrune.studio.core.osrs.map.OsrsTileData;
import com.openrune.studio.core.osrs.map.TerrainHeightSourceKind;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * Temporary compatibility adapter between the retiring Client world model and the headless Core model.
 *
 * <p>New backend/map code should use Core directly. This class exists only while legacy editor
 * sessions still consume {@link WorldDocument}.</p>
 */
public final class CoreOsrsRegionAdapter {
    private CoreOsrsRegionAdapter() {
    }

    public static WorldDocument decodeTerrain(
            byte[] landscape,
            int regionX,
            int regionY,
            IntBinaryOperator baseHeightProvider) {
        Objects.requireNonNull(baseHeightProvider, "baseHeightProvider");
        return toClient(
                ModernOsrsRegionCodec.INSTANCE.decodeTerrain(
                        landscape,
                        regionX,
                        regionY,
                        (x, y) -> baseHeightProvider.applyAsInt(x, y))
        ).document();
    }

    public static OsrsRegionData toCore(WorldDocument document, int regionX, int regionY) {
        Objects.requireNonNull(document, "document");
        requireRegion(document);

        List<OsrsTileData> tiles = new ArrayList<>(OsrsRegionData.TILE_COUNT);
        for (int plane = 0; plane < OsrsRegionData.PLANES; plane++) {
            for (int x = 0; x < OsrsRegionData.REGION_SIZE; x++) {
                for (int y = 0; y < OsrsRegionData.REGION_SIZE; y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    List<OsrsObjectPlacement> objects = tile.objects().stream()
                            .map(CoreOsrsRegionAdapter::toCoreObject)
                            .toList();
                    tiles.add(new OsrsTileData(
                            tile.southWestHeight(),
                            tile.southEastHeight(),
                            tile.northEastHeight(),
                            tile.northWestHeight(),
                            tile.underlayId(),
                            tile.overlayId(),
                            tile.overlayShape(),
                            tile.overlayRotation(),
                            tile.flags(),
                            objects,
                            toCoreHeightSource(tile.heightSource())));
                }
            }
        }
        return new OsrsRegionData(regionX, regionY, tiles);
    }

    public static WorldRegion toClient(OsrsRegionData region) {
        Objects.requireNonNull(region, "region");
        WorldDocument document = new WorldDocument(
                OsrsRegionData.REGION_SIZE,
                OsrsRegionData.REGION_SIZE,
                OsrsRegionData.PLANES);

        for (int plane = 0; plane < OsrsRegionData.PLANES; plane++) {
            for (int x = 0; x < OsrsRegionData.REGION_SIZE; x++) {
                for (int y = 0; y < OsrsRegionData.REGION_SIZE; y++) {
                    OsrsTileData tile = region.tile(plane, x, y);
                    List<WorldObject> objects = tile.getObjects().stream()
                            .map(CoreOsrsRegionAdapter::toClientObject)
                            .toList();
                    com.rspsi.editor.model.TerrainHeightSource heightSource =
                            toClientHeightSource(tile.getHeightSource());
                    document.tile(plane, x, y).restore(new TileSnapshot(
                            tile.getSouthWestHeight(),
                            tile.getSouthEastHeight(),
                            tile.getNorthEastHeight(),
                            tile.getNorthWestHeight(),
                            tile.getUnderlayId(),
                            tile.getOverlayId(),
                            tile.getOverlayShape(),
                            tile.getOverlayRotation(),
                            tile.getFlags(),
                            objects,
                            heightSource), heightSource);
                }
            }
        }

        return new WorldRegion(region.getRegionX(), region.getRegionY(), document);
    }

    private static OsrsObjectPlacement toCoreObject(WorldObject object) {
        return new OsrsObjectPlacement(
                object.id(), object.type(), object.rotation(),
                object.plane(), object.x(), object.y());
    }

    private static WorldObject toClientObject(OsrsObjectPlacement object) {
        return new WorldObject(
                object.getId(), object.getType(), object.getRotation(),
                object.getPlane(), object.getX(), object.getY());
    }

    private static com.openrune.studio.core.osrs.map.TerrainHeightSource toCoreHeightSource(
            com.rspsi.editor.model.TerrainHeightSource source) {
        if (source == null || !source.known() || source.authored()) {
            return com.openrune.studio.core.osrs.map.TerrainHeightSource.Companion.unknown();
        }
        if (source.generated()) {
            return com.openrune.studio.core.osrs.map.TerrainHeightSource.Companion.generated();
        }
        return com.openrune.studio.core.osrs.map.TerrainHeightSource.Companion.explicit(
                source.explicitValue());
    }

    private static com.rspsi.editor.model.TerrainHeightSource toClientHeightSource(
            com.openrune.studio.core.osrs.map.TerrainHeightSource source) {
        if (source.getKind() == TerrainHeightSourceKind.GENERATED) {
            return com.rspsi.editor.model.TerrainHeightSource.generatedSource();
        }
        if (source.getKind() == TerrainHeightSourceKind.EXPLICIT) {
            return com.rspsi.editor.model.TerrainHeightSource.explicitSource(source.getExplicitValue());
        }
        return com.rspsi.editor.model.TerrainHeightSource.unknown();
    }

    private static void requireRegion(WorldDocument document) {
        if (document.width() != OsrsRegionData.REGION_SIZE
                || document.length() != OsrsRegionData.REGION_SIZE
                || document.planes() != OsrsRegionData.PLANES) {
            throw new IllegalArgumentException("OSRS region must be exactly 64x64x4");
        }
    }
}
