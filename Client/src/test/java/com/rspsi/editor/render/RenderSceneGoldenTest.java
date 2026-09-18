package com.rspsi.editor.render;

import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks one mixed-plane scene snapshot until external parity fixtures replace it. */
class RenderSceneGoldenTest {
    private static final String GOLDEN =
            "33bcb2783dc072fd43f2e117d4e5bb11105ff4cb7443431c084bd6e7c86a26c6";

    @Test
    void mixedTerrainObjectsAndBridgeHaveStableNeutralSceneSemantics() {
        WorldDocument document = fixture();
        RenderScene scene = new RenderSceneBuilder().build(document);

        assertEquals(8, scene.terrainMeshes().size());
        assertEquals(3, scene.objects().size());
        assertEquals(List.of(new BridgeLink(new TileCoordinate(1, 0, 1),
                new TileCoordinate(0, 0, 1))), scene.bridges());
        assertEquals(GOLDEN, RenderSceneFingerprint.sha256(scene));
    }

    private static WorldDocument fixture() {
        WorldDocument document = new WorldDocument(2, 2, 2);
        document.tile(0, 0, 0).restore(new TileSnapshot(10, 20, 30, 40,
                1, 2, 0, 0, 0,
                List.of(new WorldObject(100, 10, 0, 0, 0, 0))));
        document.tile(0, 1, 0).restore(new TileSnapshot(20, 30, 40, 50,
                2, 3, 4, 1, 0,
                List.of(new WorldObject(101, 0, 1, 0, 1, 0))));
        document.tile(1, 0, 1).restore(new TileSnapshot(30, 40, 50, 60,
                3, 4, 7, 2, OsrsTileFlags.BRIDGE,
                List.of(new WorldObject(102, 22, 3, 1, 0, 1))));
        return document;
    }

}
