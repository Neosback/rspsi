package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TileEditCommandInvariantTest {
    private static final TileCoordinate TILE = new TileCoordinate(0, 1, 1);
    private static final TileSnapshot BASE = new TileSnapshot(1, 2, 3, 4,
            5, 6, 7, 2, 8, List.of());

    @Test
    void fieldCommandsRejectCrossFieldStateChanges() {
        TileSnapshot changedHeightAndUnderlay = new TileSnapshot(9, 2, 3, 4,
                10, 6, 7, 2, 8, List.of());
        TileSnapshot changedFlagsAndOverlay = new TileSnapshot(1, 2, 3, 4,
                5, 11, 7, 2, 12, List.of());

        assertThrows(IllegalArgumentException.class,
                () -> new ChangeHeightCommand(TILE, BASE, changedHeightAndUnderlay, "height"));
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeTileFlagsCommand(TILE, BASE, changedFlagsAndOverlay, "flags"));
        assertDoesNotThrow(() -> new ChangeHeightCommand(TILE, BASE,
                new TileSnapshot(9, 2, 3, 4, 5, 6, 7, 2, 8, List.of()), "height"));
        assertDoesNotThrow(() -> new ChangeTileFlagsCommand(TILE, BASE,
                new TileSnapshot(1, 2, 3, 4, 5, 6, 7, 2, 12, List.of()), "flags"));
    }
}
