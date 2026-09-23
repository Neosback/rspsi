package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.inspector.ObjectSceneResolutionAudit;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorGhostTest {
    private static final WorldObject HIDDEN = new WorldObject(100, 10, 0, 0, 0, 0);
    private static final WorldObject BLOCKER = new WorldObject(300, 0, 0, 0, 1, 0);

    @Test
    void parityScenesSubmitNothingForLocsThatDrawNothing() {
        List<ModelRenderPacket> packets = new ModelPacketBuilder(new Definitions()).build(document());

        assertTrue(packets.isEmpty());
    }

    @Test
    void editorGhostsHiddenMultilocWithItsFirstVisibleStateTranslucently() {
        List<ModelRenderPacket> packets = editorPackets().stream()
                .filter(packet -> packet.objectId() == 100).toList();

        assertFalse(packets.isEmpty());
        assertTrue(packets.stream().allMatch(ModelRenderPacket::editorGhost));
        assertTrue(packets.stream().flatMap(packet -> packet.triangles().stream())
                .allMatch(triangle -> triangle.alpha() >= ModelPacketBuilder.GHOST_TRANSPARENCY));
        assertEquals(100, packets.get(0).sceneObjectIdentity().objectId(),
                "the ghost keeps the placed identity so it stays selectable");
    }

    @Test
    void authoredEmptyWallGetsAPanelOnItsWallEdge() {
        ModelRenderPacket marker = editorPackets().stream()
                .filter(packet -> packet.objectId() == 300).findFirst().orElseThrow();

        assertTrue(marker.editorGhost());
        // Straight wall, rotation 0: wall orientation A = 1 (west edge).
        assertTrue(marker.vertices().stream().allMatch(vertex -> vertex.x() >= 0 && vertex.x() <= 16));
        assertTrue(marker.vertices().stream().anyMatch(vertex -> vertex.y() < 0), "stands up from the floor");
    }

    @Test
    void auditIgnoresGhostsEvenInAnEditorScene() {
        WorldDocument document = document();
        RenderScene scene = new RenderSceneBuilder(new Definitions(), ScenePresentation.EDITOR).build(document);

        ObjectSceneResolutionAudit.Report report =
                ObjectSceneResolutionAudit.audit(document, new Definitions(), scene);

        assertEquals(0, report.submittedCount());
        assertEquals(2, report.warningCount());
    }

    private static List<ModelRenderPacket> editorPackets() {
        return new ModelPacketBuilder(new Definitions(), LightingProfile.osrs(), ScenePresentation.EDITOR)
                .build(document());
    }

    private static WorldDocument document() {
        WorldDocument document = new WorldDocument(2, 1, 1);
        document.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(HIDDEN)));
        document.tile(0, 1, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(BLOCKER)));
        return document;
    }

    private static final class Definitions implements DefinitionProvider {
        @Override
        public Optional<ObjectDefinitionView> object(int id) {
            return switch (id) {
                // Fresh account selects transforms[0] = -1: hidden, like rev-240 object 10818.
                case 100 -> Optional.of(new ObjectDefinitionView(id, null, 1, 1, List.of(),
                        new int[0], new int[0], -1, false, 1234, -1, new int[]{-1, 200, -1}, -1));
                case 200 -> Optional.of(new ObjectDefinitionView(id, "Later state", 1, 1, List.of(),
                        new int[]{7}, new int[]{10}, -1, false));
                // Invisible blocker: its only model has no faces (rev-240 model 2214).
                case 300 -> Optional.of(new ObjectDefinitionView(id, null, 1, 1, List.of(),
                        new int[]{8}, new int[]{0}, -1, false));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<ModelGeometryView> modelGeometry(int id) {
            if (id == 8) {
                return Optional.of(new ModelGeometryView(8, new int[]{0, 0, 0}, new int[0],
                        new short[0], new int[0], new int[0]));
            }
            return id == 7 ? Optional.of(new ModelGeometryView(7,
                    new int[]{0, 0, 0, 64, 0, 0, 0, -64, 64},
                    new int[]{0, 1, 2},
                    new short[]{100},
                    new int[]{0},
                    new int[]{-1})) : Optional.empty();
        }

        @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
        @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
    }
}
