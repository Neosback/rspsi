package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderSceneBuilderTest {
    @Test
    void buildsTerrainForEveryPlaneAndExposesCanonicalObjects() {
        WorldDocument document = new WorldDocument(2, 3, 2);
        WorldObject object = new WorldObject(100, 10, 2, 1, 1, 2);
        document.tile(1, 1, 2).restore(new TileSnapshot(
                10, 20, 30, 40, 2, 3, 6, 1, 0, List.of(object)));

        RenderScene scene = new RenderSceneBuilder().build(document);

        assertSame(document, scene.document());
        assertEquals(12, scene.terrainMeshes().size());
        assertEquals(1, scene.objects().size());
        assertEquals(object, scene.objects().get(0));
        assertEquals(6, scene.terrainMeshes()
                .get(new TileCoordinate(1, 1, 2)).vertices().size());
    }

    @Test
    void compatibilityConstructorDoesNotInventDerivedGeometry() {
        WorldDocument document = new WorldDocument(1, 1, 1);

        RenderScene scene = new RenderScene(document);

        assertSame(document, scene.document());
        assertEquals(0, scene.terrainMeshes().size());
        assertEquals(0, scene.objects().size());
    }
}
