package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldObject;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuDrawCommandTest {
    @Test
    void carriesRuneScapeNoDepthModesWithoutChangingCompatibilityDefaults() {
        GpuDrawCommand defaultCommand = command(GpuDrawCommand.RenderMode.DEFAULT);
        GpuDrawCommand sortedNoDepth = command(GpuDrawCommand.RenderMode.SORTED_NO_DEPTH);
        GpuDrawCommand unsortedNoDepth = command(GpuDrawCommand.RenderMode.UNSORTED_NO_DEPTH);

        assertFalse(defaultCommand.renderMode().noDepth());
        assertTrue(defaultCommand.scenePlane() == defaultCommand.tile().plane());
        assertTrue(defaultCommand.planeCullLevel() == defaultCommand.tile().plane());
        assertTrue(sortedNoDepth.renderMode().noDepth());
        assertTrue(unsortedNoDepth.renderMode().noDepth());
    }

    @Test
    void terrainCommandsNeverMergeAcrossWorldZoneBoundary() {
        WorldTileAddress lastTileInZone = WorldTileAddress.of(7, 4, 0);
        WorldTileAddress firstTileNextZone = WorldTileAddress.of(8, 4, 0);
        GpuDrawCommand command = new GpuDrawCommand(
                lastTileInZone, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, -1, GpuDrawCommand.RenderMode.DEFAULT);

        assertFalse(command.canMerge(firstTileNextZone, SceneLayer.Kind.TERRAIN,
                GpuDrawCommand.SubmissionPass.OPAQUE,
                -1, 0, 0, -1, 3, GpuDrawCommand.RenderMode.DEFAULT));
    }

    @Test
    void modelCommandsDoNotMergeAcrossDistinctSceneInstances() {
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);
        SceneObjectIdentity firstIdentity = SceneObjectIdentity.of(
                new WorldObject(42, 10, 0, 0, 3200, 3200), 1, 1);
        SceneObjectIdentity secondIdentity = SceneObjectIdentity.of(
                new WorldObject(42, 10, 1, 0, 3200, 3200), 1, 1);
        GpuDrawCommand command = new GpuDrawCommand(tile, 0, 0,
                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 42, GpuDrawCommand.RenderMode.DEFAULT,
                WallDecorationPresentation.none(), GameObjectSceneMetadata.none(),
                List.of(), List.of(), firstIdentity, 0, 3200, 3200);

        assertFalse(command.canMerge(tile, 0, 0, SceneLayer.Kind.GROUND_OBJECT,
                GpuDrawCommand.SubmissionPass.OPAQUE, -1, 0, 0, 42, 3,
                GpuDrawCommand.RenderMode.DEFAULT, WallDecorationPresentation.none(),
                GameObjectSceneMetadata.none(), List.of(), List.of(),
                secondIdentity, 0, 3200, 3200));
    }

    @Test
    void modelCommandsDoNotMergeAcrossDifferentRenderablePlacements() {
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);
        ClientModelBounds bounds = ClientModelBounds.calculate(
                List.of(new ModelVertex(-10, -10, 0, 0, 0, 0, 0, 0, 0),
                        new ModelVertex(10, -10, 0, 0, 0, 0, 0, 0, 0),
                        new ModelVertex(0, 10, 0, 0, 0, 0, 0, 0, 0)),
                0, false);
        SceneObjectIdentity identity = SceneObjectIdentity.of(
                new WorldObject(42, 5, 0, 0, 3200, 3200), 1, 1);
        GpuDrawCommand command = new GpuDrawCommand(tile, 0, 0,
                SceneLayer.Kind.WALL_DECORATION, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 42, GpuDrawCommand.RenderMode.DEFAULT,
                WallDecorationPresentation.none(), GameObjectSceneMetadata.none(),
                List.of(bounds), List.of(new ClientRenderablePlacement(16, 0)),
                identity, 0, 3200, 3200);

        assertFalse(command.canMerge(tile, 0, 0, SceneLayer.Kind.WALL_DECORATION,
                GpuDrawCommand.SubmissionPass.OPAQUE, -1, 0, 0, 42, 3,
                GpuDrawCommand.RenderMode.DEFAULT, WallDecorationPresentation.none(),
                GameObjectSceneMetadata.none(), List.of(bounds),
                List.of(new ClientRenderablePlacement(32, 0)),
                identity, 0, 3200, 3200));
    }

    private static GpuDrawCommand command(GpuDrawCommand.RenderMode mode) {
        return new GpuDrawCommand(WorldTileAddress.of(3200, 3200, 0),
                SceneLayer.Kind.GROUND_OBJECT, GpuDrawCommand.SubmissionPass.OPAQUE,
                0, 3, -1, 0, 0, 1, mode);
    }
}
