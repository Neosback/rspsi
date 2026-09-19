package com.rspsi.editor.knowledge.derived;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Layer 3 Derived Analyzer: Evaluates whole-document terrain heights into slope, aspect, and curvature maps.
 */
public final class TerrainTopologyAnalyzer {
    private TerrainTopologyAnalyzer() {}

    public static Map<TileCoordinate, TerrainTopology> analyze(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        Map<TileCoordinate, TerrainTopology> results = new HashMap<>();

        for (int p = 0; p < document.planes(); p++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot snapshot = document.tile(p, x, y).snapshot();
                    TerrainTopology topo = TerrainTopology.calculate(
                            snapshot.southWestHeight(),
                            snapshot.southEastHeight(),
                            snapshot.northEastHeight(),
                            snapshot.northWestHeight()
                    );
                    results.put(new TileCoordinate(p, x, y), topo);
                }
            }
        }
        return Collections.unmodifiableMap(results);
    }
}
