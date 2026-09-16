package com.rspsi.editor;

import com.rspsi.editor.io.WorldFragmentCodec;
import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldFragmentCodecTest {
    @Test
    void versionedJsonRoundTripsTerrainAndObjects() {
        WorldFragment fragment = new WorldFragment(new TileBounds(2, 3, 2, 3),
                List.of(new TerrainTilePatch(0, 2, 3,
                        new TileSnapshot(8, 16, 24, 32, 7, 9, 4, 2, 6, List.of()))),
                List.of(new WorldObject(100, 10, 1, 0, 2, 3)));

        String json = WorldFragmentCodec.encode(fragment);

        assertEquals(fragment, WorldFragmentCodec.decode(json));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"formatVersion\":1"));
    }

    @Test
    void rejectsUnknownFormatVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> WorldFragmentCodec.decode("{\"formatVersion\":99,\"fragment\":{}}"));
    }
}
