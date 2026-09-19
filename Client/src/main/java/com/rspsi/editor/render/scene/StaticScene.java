package com.rspsi.editor.render.scene;

import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.TerrainRenderPacket;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the static, authored world environment:
 * terrain, walls, locations, bridges, roofs, static collision, and lighting.
 */
public record StaticScene(
        WorldDocument document,
        Map<TileCoordinate, TerrainRenderPacket> terrainPackets,
        List<WorldObject> objects,
        List<ModelRenderPacket> modelPackets,
        List<BridgeLink> bridges
) {
    public StaticScene {
        Objects.requireNonNull(document, "document");
        terrainPackets = Collections.unmodifiableMap(Map.copyOf(terrainPackets == null ? Map.of() : terrainPackets));
        objects = Collections.unmodifiableList(List.copyOf(objects == null ? List.of() : objects));
        modelPackets = Collections.unmodifiableList(List.copyOf(modelPackets == null ? List.of() : modelPackets));
        bridges = Collections.unmodifiableList(List.copyOf(bridges == null ? List.of() : bridges));
    }

    public static StaticScene from(RenderScene renderScene) {
        Objects.requireNonNull(renderScene, "renderScene");
        return new StaticScene(
                renderScene.document(),
                renderScene.terrainPackets(),
                renderScene.objects(),
                renderScene.modelPackets(),
                renderScene.bridges()
        );
    }
}
