package com.rspsi.editor.knowledge;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.function.BiConsumer;

/**
 * Deterministic analyzer that evaluates authored world documents and derives semantic intent.
 */
@FunctionalInterface
public interface KnowledgeAnalyzer {

    /**
     * Evaluates authored world data and reports semantic classifications.
     *
     * @param world the authored document to evaluate
     * @param tagEmitter consumer that accepts tile coordinates and their semantic tags
     */
    void analyze(WorldDocument world, BiConsumer<TileCoordinate, SemanticTag> tagEmitter);

    /**
     * Built-in analyzer that derives terrain semantics from heights, overlays, and flags.
     */
    static KnowledgeAnalyzer standardTerrainAnalyzer() {
        return (world, emitter) -> {
            for (int plane = 0; plane < world.planes(); plane++) {
                for (int x = 0; x < world.width(); x++) {
                    for (int y = 0; y < world.length(); y++) {
                        TileCoordinate coord = new TileCoordinate(plane, x, y);
                        TileSnapshot tile = world.tile(plane, x, y).snapshot();

                        if (OsrsTileFlags.hasBridge(tile.flags())) {
                            emitter.accept(coord, SemanticTag.BRIDGE);
                        }
                        if (OsrsTileFlags.removesRoofs(tile.flags())) {
                            emitter.accept(coord, SemanticTag.BUILDING);
                        }
                        if (tile.overlayId() > 0) {
                            emitter.accept(coord, SemanticTag.PATH);
                        }

                        int minH = Math.min(Math.min(tile.southWestHeight(), tile.southEastHeight()),
                                Math.min(tile.northEastHeight(), tile.northWestHeight()));
                        int maxH = Math.max(Math.max(tile.southWestHeight(), tile.southEastHeight()),
                                Math.max(tile.northEastHeight(), tile.northWestHeight()));
                        if (maxH - minH >= 120) {
                            emitter.accept(coord, SemanticTag.CLIFF);
                        }
                    }
                }
            }
        };
    }

    /**
     * Built-in analyzer that derives object semantics from object categories and shapes.
     */
    static KnowledgeAnalyzer standardObjectAnalyzer() {
        return (world, emitter) -> {
            for (int plane = 0; plane < world.planes(); plane++) {
                for (int x = 0; x < world.width(); x++) {
                    for (int y = 0; y < world.length(); y++) {
                        TileCoordinate coord = new TileCoordinate(plane, x, y);
                        TileSnapshot tile = world.tile(plane, x, y).snapshot();
                        for (WorldObject obj : tile.objects()) {
                            if (obj.category() == ObjectCategory.WALL) {
                                emitter.accept(coord, SemanticTag.WALL);
                            } else if (obj.category() == ObjectCategory.GROUND) {
                                emitter.accept(coord, SemanticTag.BUILDING);
                            }
                        }
                    }
                }
            }
        };
    }
}
