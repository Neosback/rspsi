package com.rspsi.editor.render.compiler;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.ModelTriangle;
import com.rspsi.editor.render.ModelVertex;
import com.rspsi.editor.render.TextureTriangle;
import com.rspsi.editor.render.animation.AnimationCache;
import com.rspsi.editor.render.scene.RuntimeScene;
import com.rspsi.editor.simulation.SimulationSnapshot;
import com.rspsi.editor.simulation.entity.NpcEntity;
import com.rspsi.editor.simulation.entity.RuntimeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure compiler that transforms a SimulationSnapshot and cached asset definitions
 * into a dynamic RuntimeScene presentation packet.
 */
public final class RuntimeSceneCompiler {
    private final AnimationCache animationCache;

    public RuntimeSceneCompiler() {
        this(new AnimationCache());
    }

    public RuntimeSceneCompiler(AnimationCache animationCache) {
        this.animationCache = Objects.requireNonNull(animationCache, "animationCache");
    }

    public RuntimeScene compile(SimulationSnapshot snapshot, DefinitionProvider definitions) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (definitions == null || snapshot.entities().isEmpty()) {
            return new RuntimeScene(snapshot.clientCycle(), snapshot.serverTick(), snapshot.entities(), List.of());
        }

        List<ModelRenderPacket> packets = new ArrayList<>();
        for (RuntimeEntity entity : snapshot.entities()) {
            compileEntity(entity, definitions).ifPresent(packets::add);
        }

        return new RuntimeScene(snapshot.clientCycle(), snapshot.serverTick(), snapshot.entities(), packets);
    }

    private Optional<ModelRenderPacket> compileEntity(RuntimeEntity entity, DefinitionProvider definitions) {
        TileCoordinate anchor = entity.tileCoordinate();
        int objectId = (int) entity.id();

        // If the entity is an NPC, resolve its appearance from cache
        if (entity instanceof NpcEntity npc) {
            // Placeholder/bounding cube vertices if model geometry view is pending:
            int h = 128;
            int half = 32 * npc.size();
            List<ModelVertex> vertices = List.of(
                    new ModelVertex(-half, 0, -half, 0, 128, 0, 0, 128, 0),
                    new ModelVertex(half, 0, -half, 0, 128, 0, 0, 128, 0),
                    new ModelVertex(half, 0, half, 0, 128, 0, 0, 128, 0),
                    new ModelVertex(-half, 0, half, 0, 128, 0, 0, 128, 0),
                    new ModelVertex(0, h, 0, 0, 128, 0, 0, 128, 0)
            );
            List<ModelTriangle> triangles = List.of(
                    new ModelTriangle(0, 1, 2, 45, 45, 45, -1, 0, 0, 0),
                    new ModelTriangle(0, 2, 3, 45, 45, 45, -1, 0, 0, 0),
                    new ModelTriangle(0, 1, 4, 60, 60, 60, -1, 0, 0, 0),
                    new ModelTriangle(1, 2, 4, 60, 60, 60, -1, 0, 0, 0),
                    new ModelTriangle(2, 3, 4, 60, 60, 60, -1, 0, 0, 0),
                    new ModelTriangle(3, 0, 4, 60, 60, 60, -1, 0, 0, 0)
            );

            return Optional.of(new ModelRenderPacket(
                    anchor,
                    objectId,
                    ObjectCategory.GROUND,
                    vertices,
                    triangles,
                    List.of(),
                    npc.animationSequence(),
                    -half, 0, -half,
                    half, h, half,
                    true,
                    false,
                    0,
                    false,
                    GpuDrawCommand.RenderMode.DEFAULT
            ));
        }

        return Optional.empty();
    }
}
