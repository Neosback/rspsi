package com.rspsi.legacy;

import com.jagex.map.MapRegion;
import com.jagex.map.SceneGraph;
import com.jagex.map.tile.SceneTile;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SessionChangeListener;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.Objects;
import java.util.Set;

/**
 * Synchronizes the neutral terrain document with the current legacy map.
 * Objects are deliberately not imported or written until their semantics have
 * a complete neutral representation.
 */
public final class LegacyMapDocumentBridge implements SessionChangeListener, AutoCloseable {
    private final MapRegion mapRegion;
    private final SceneGraph sceneGraph;
    private final Runnable refresh;
    private EditorSession session;

    public LegacyMapDocumentBridge(MapRegion mapRegion) {
        this(mapRegion, null, mapRegion::updateTiles);
    }

    public LegacyMapDocumentBridge(MapRegion mapRegion, SceneGraph sceneGraph) {
        this(mapRegion, sceneGraph, mapRegion::updateTiles);
    }

    /** Refresh callback is injectable so synchronization can be tested without a live renderer. */
    public LegacyMapDocumentBridge(MapRegion mapRegion, SceneGraph sceneGraph, Runnable refresh) {
        this.mapRegion = Objects.requireNonNull(mapRegion, "mapRegion");
        this.sceneGraph = sceneGraph;
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    public static WorldDocument importTerrain(MapRegion mapRegion) {
        Objects.requireNonNull(mapRegion, "mapRegion");
        int width = mapRegion.underlays[0].length;
        int length = mapRegion.underlays[0][0].length;
        WorldDocument document = new WorldDocument(width, length, 4);
        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    document.tile(plane, x, y).restore(snapshotAt(mapRegion, plane, x, y));
                }
            }
        }
        return document;
    }

    public void attach(EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (this.session != null) {
            this.session.removeChangeListener(this);
        }
        this.session = session;
        session.addChangeListener(this);
    }

    @Override
    public void changed(Set<TileCoordinate> tiles) {
        if (tiles == null || tiles.isEmpty() || session == null) {
            return;
        }
        boolean changed = false;
        for (TileCoordinate coordinate : tiles) {
            if (!isValidTile(coordinate)) {
                continue;
            }
            int plane = coordinate.plane();
            int x = coordinate.x();
            int y = coordinate.y();
            int underlay = session.world().tile(coordinate).snapshot().underlayId();
            if (mapRegion.underlays[plane][x][y] != underlay) {
                mapRegion.underlays[plane][x][y] = (short) underlay;
                changed = true;
                markSceneTileDirty(plane, x, y);
            }
        }
        if (changed) {
            refresh.run();
            if (sceneGraph != null) {
                SceneGraph.minimapUpdate = true;
            }
        }
    }

    @Override
    public void close() {
        if (session != null) {
            session.removeChangeListener(this);
            session = null;
        }
    }

    private boolean isValidTile(TileCoordinate coordinate) {
        return coordinate != null && coordinate.plane() >= 0 && coordinate.plane() < 4
                && coordinate.x() >= 0 && coordinate.x() < mapRegion.underlays[0].length
                && coordinate.y() >= 0 && coordinate.y() < mapRegion.underlays[0][0].length;
    }

    private void markSceneTileDirty(int plane, int x, int y) {
        if (sceneGraph == null || plane >= sceneGraph.tiles.length
                || x >= sceneGraph.tiles[plane].length || y >= sceneGraph.tiles[plane][x].length) {
            return;
        }
        SceneTile tile = sceneGraph.tiles[plane][x][y];
        if (tile != null) {
            tile.hasUpdated = true;
        }
    }

    private static TileSnapshot snapshotAt(MapRegion mapRegion, int plane, int x, int y) {
        return new TileSnapshot(
                mapRegion.tileHeights[plane][x][y],
                mapRegion.tileHeights[plane][x + 1][y],
                mapRegion.tileHeights[plane][x + 1][y + 1],
                mapRegion.tileHeights[plane][x][y + 1],
                mapRegion.underlays[plane][x][y],
                mapRegion.overlays[plane][x][y],
                mapRegion.overlayShapes[plane][x][y],
                mapRegion.overlayOrientations[plane][x][y],
                mapRegion.tileFlags[plane][x][y],
                java.util.List.of());
    }
}
