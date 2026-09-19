package com.rspsi.editor.knowledge.scene;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Layer 2 Scene Facts: Deterministic scene resolver output over a {@link WorldDocument}.
 *
 * <p>Resolves effective planes (accounting for bridges), tile center elevations,
 * object spatial lookups, and footprint bounds without heuristics.</p>
 */
public final class SceneFacts {
    private final WorldDocument document;
    private final Map<TileCoordinate, List<SceneObjectFact>> tileObjects = new HashMap<>();
    private final Map<Integer, List<SceneObjectFact>> objectsById = new HashMap<>();
    private final int[][][] effectivePlanes;
    private final int[][][] tileCenterElevations;
    private final List<BridgeLink> bridgeLinks;

    public SceneFacts(WorldDocument document, DefinitionProvider definitions) {
        this.document = Objects.requireNonNull(document, "document");
        this.bridgeLinks = List.copyOf(document.bridgeLinks());
        this.effectivePlanes = new int[document.planes()][document.width()][document.length()];
        this.tileCenterElevations = new int[document.planes()][document.width()][document.length()];

        // Compute effective planes and elevations
        for (int p = 0; p < document.planes(); p++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(p, x, y).snapshot();
                    int effectivePlane = p;
                    if (p > 0 && OsrsTileFlags.hasBridge(tile.flags())) {
                        effectivePlane = p - 1;
                    }
                    effectivePlanes[p][x][y] = effectivePlane;

                    int elevation = (tile.southWestHeight() + tile.southEastHeight()
                            + tile.northEastHeight() + tile.northWestHeight()) >> 2;
                    tileCenterElevations[p][x][y] = elevation;

                    TileCoordinate coord = new TileCoordinate(p, x, y);
                    for (WorldObject obj : tile.objects()) {
                        int width = 1;
                        int length = 1;
                        List<Integer> modelIds = List.of();
                        if (definitions != null) {
                            Optional<ObjectDefinitionView> def = definitions.object(obj.id());
                            if (def.isPresent()) {
                                ObjectDefinitionView view = def.get();
                                width = obj.rotation() % 2 == 0 ? view.width() : view.length();
                                length = obj.rotation() % 2 == 0 ? view.length() : view.width();
                                modelIds = new ArrayList<>();
                                for (int id : view.modelIds()) modelIds.add(id);
                            }
                        }

                        SceneObjectFact fact = SceneObjectFact.of(obj, width, length,
                                effectivePlane, elevation, modelIds);
                        tileObjects.computeIfAbsent(coord, k -> new ArrayList<>()).add(fact);
                        objectsById.computeIfAbsent(obj.id(), k -> new ArrayList<>()).add(fact);
                    }
                }
            }
        }
    }

    /** Returns all resolved object facts at the specified tile coordinate. */
    public List<SceneObjectFact> objectsAt(TileCoordinate coordinate) {
        return tileObjects.getOrDefault(coordinate, List.of());
    }

    /** Returns all resolved scene instances of the specified object ID. */
    public List<SceneObjectFact> objectsWithId(int objectId) {
        return objectsById.getOrDefault(objectId, List.of());
    }

    /** Returns the effective rendering/collision plane for the tile. */
    public int effectivePlane(int plane, int x, int y) {
        if (plane < 0 || plane >= effectivePlanes.length
                || x < 0 || x >= effectivePlanes[plane].length
                || y < 0 || y >= effectivePlanes[plane][x].length) {
            return plane;
        }
        return effectivePlanes[plane][x][y];
    }

    /** Returns the center elevation of the tile in OSRS world units. */
    public int tileElevation(int plane, int x, int y) {
        if (plane < 0 || plane >= tileCenterElevations.length
                || x < 0 || x >= tileCenterElevations[plane].length
                || y < 0 || y >= tileCenterElevations[plane][x].length) {
            return 0;
        }
        return tileCenterElevations[plane][x][y];
    }

    /** Returns the document's active bridge links. */
    public List<BridgeLink> bridgeLinks() {
        return bridgeLinks;
    }
}
