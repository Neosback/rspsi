package com.rspsi.editor.generation;

import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.plugin.ContributionOwner;
import com.rspsi.editor.plugin.EditorPluginContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorContractTest {

    @Test
    void generatorProducesNonDestructiveProposalThatCanBeCommittedAndUndone() {
        WorldDocument world = new WorldDocument(10, 10, 2);
        EditorSession session = new EditorSession(world);
        EditorPluginContext context = new EditorPluginContext(session, AssetRepository.empty());
        GeneratorService service = context.generators();

        Generator roadGen = (request, ctx) -> {
            ProposedChanges.Builder builder = ProposedChanges.builder();
            for (int x = request.min().x(); x <= request.max().x(); x++) {
                TileCoordinate coord = new TileCoordinate(0, x, 5);
                builder.setTile(coord, new TileSnapshot(0, 0, 0, 0, 12, 4, 0, 0, 0, List.of()));
                builder.addObject(new WorldObject(1050, 10, 0, 0, x, 5));
            }
            return builder.build();
        };

        service.registerGenerator(ContributionOwner.plugin("test-roads"), "road-gen",
                GenerationSchema.ROAD, "Road Generator", "Generates roads", roadGen);

        Generator.GenerationRequest request = Generator.GenerationRequest.of(
                GenerationSchema.ROAD,
                new TileCoordinate(0, 2, 5),
                new TileCoordinate(0, 4, 5),
                1337L
        );

        // 1. Preview must NOT mutate world
        ProposedChanges preview = service.preview("road-gen", request, context);
        assertFalse(preview.isEmpty());
        assertEquals(3, preview.proposedTiles().size());
        assertEquals(3, preview.addedObjects().size());

        // Verify world is untouched
        assertEquals(0, world.tile(0, 3, 5).snapshot().overlayId());
        assertTrue(world.tile(0, 3, 5).snapshot().objects().isEmpty());

        // 2. Commit proposal via command
        EditorCommand command = service.commit("road-gen", request, context, "Build Road");

        // Verify world is now mutated
        assertEquals(4, world.tile(0, 3, 5).snapshot().overlayId());
        assertEquals(1, world.tile(0, 3, 5).snapshot().objects().size());
        assertEquals(1050, world.tile(0, 3, 5).snapshot().objects().get(0).id());

        // 3. Undo command
        assertTrue(session.history().canUndo());
        session.undo();

        // Verify world is completely restored
        assertEquals(0, world.tile(0, 3, 5).snapshot().overlayId());
        assertTrue(world.tile(0, 3, 5).snapshot().objects().isEmpty());

        // 4. Redo command
        assertTrue(session.history().canRedo());
        session.redo();
        assertEquals(4, world.tile(0, 3, 5).snapshot().overlayId());
        assertEquals(1, world.tile(0, 3, 5).snapshot().objects().size());
    }
}
