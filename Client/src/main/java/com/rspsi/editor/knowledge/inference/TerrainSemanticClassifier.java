package com.rspsi.editor.knowledge.inference;

import com.rspsi.editor.knowledge.Evidence;
import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.knowledge.derived.TerrainTopology;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Layer 4 Inference Classifier: Classifies terrain tiles into semantic tags using derived topology and surface properties.
 */
public final class TerrainSemanticClassifier {
    private TerrainSemanticClassifier() {}

    public static List<KnowledgeFact<SemanticTag>> classify(
            WorldDocument document,
            TileCoordinate coord,
            TerrainTopology topo
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(coord, "coord");
        TileSnapshot tile = document.tile(coord.plane(), coord.x(), coord.y()).snapshot();
        List<KnowledgeFact<SemanticTag>> facts = new ArrayList<>();

        // 1. Cliff inference
        if (topo != null && topo.isCliff()) {
            List<Evidence> cliffEvidence = List.of(
                    new Evidence("Corner height delta is " + topo.heightVariance() + " (>= 120 units)", 0.65f),
                    new Evidence("Steep slope gradient " + String.format("%.1f", topo.slopeMagnitude()), 0.35f)
            );
            facts.add(KnowledgeFact.inferred(SemanticTag.CLIFF, 0.96f, cliffEvidence));
        }

        // 2. Road / Path inference from overlay
        if (tile.overlayId() > 0) {
            List<Evidence> pathEvidence = new ArrayList<>();
            pathEvidence.add(new Evidence("Has authored overlay ID " + tile.overlayId(), 0.6f));
            float conf = 0.6f;
            if (topo != null && topo.slopeMagnitude() < 25.0) {
                pathEvidence.add(new Evidence("Gradual traversal slope: " + String.format("%.1f", topo.slopeMagnitude()), 0.32f));
                conf += 0.32f;
            }
            facts.add(KnowledgeFact.inferred(SemanticTag.ROAD, Math.min(1.0f, conf), pathEvidence));
        }

        return List.copyOf(facts);
    }
}
