package com.rspsi.legacy;

import com.rspsi.cache.definition.FrameConstants;
import com.rspsi.osrs.rules.render.TileAttributes;
import com.rspsi.osrs.rules.tile.Orientation;
import com.rspsi.osrs.rules.tile.RenderFlags;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class LegacyConstantRehomeCompatibilityTest {

    @Test
    void animationFrameConstantsPreserveValuesAndUtilitySurface() throws Exception {
        assertEquals(0, FrameConstants.CENTROID_TRANSFORMATION);
        assertEquals(1, FrameConstants.POSITION_TRANSFORMATION);
        assertEquals(2, FrameConstants.ROTATION_TRANSFORMATION);
        assertEquals(3, FrameConstants.SCALE_TRANSFORMATION);
        assertEquals(5, FrameConstants.ALPHA_TRANSFORMATION);

        assertEquals(0b1, FrameConstants.TRANSFORM_X);
        assertEquals(0b10, FrameConstants.TRANSFORM_Y);
        assertEquals(0b100, FrameConstants.TRANSFORM_Z);

        assertTrue(Modifier.isPublic(
                FrameConstants.class.getDeclaredConstructor().getModifiers()));
        assertTrue(Modifier.isFinal(FrameConstants.class.getModifiers()));
    }

    @Test
    void orientationConstantsPreserveQuarterTurnEncoding() throws Exception {
        assertEquals(0, Orientation.NORTH);
        assertEquals(1, Orientation.EAST);
        assertEquals(2, Orientation.SOUTH);
        assertEquals(3, Orientation.WEST);

        assertTrue(Modifier.isPublic(
                Orientation.class.getDeclaredConstructor().getModifiers()));
        assertTrue(Modifier.isFinal(Orientation.class.getModifiers()));
    }

    @Test
    void renderFlagsPreserveExactBitEncoding() {
        assertEquals(1, RenderFlags.BLOCKED_TILE.getBit());
        assertEquals(2, RenderFlags.BRIDGE_TILE.getBit());
        assertEquals(4, RenderFlags.FORCE_LOWEST_PLANE.getBit());
        assertEquals(8, RenderFlags.RENDER_ON_LOWER_Z.getBit());
        assertEquals(16, RenderFlags.DISABLE_RENDERING.getBit());
    }

    @Test
    void sceneTileAttributesPreserveMutableStaticMasks() {
        assertEquals(8, TileAttributes.RENDER_TILE_NORTH);
        assertEquals(2, TileAttributes.RENDER_TILE_SOUTH);
        assertEquals(1, TileAttributes.RENDER_TILE_WEST);
        assertEquals(4, TileAttributes.RENDER_TILE_EAST);

        int original = TileAttributes.RENDER_TILE_NORTH;
        try {
            TileAttributes.RENDER_TILE_NORTH = 99;
            assertEquals(99, TileAttributes.RENDER_TILE_NORTH);
        } finally {
            TileAttributes.RENDER_TILE_NORTH = original;
        }
    }

    @Test
    void legacyOrgMajorClassesAreNoLongerPresent() {
        for (String className : new String[] {
                "org.major.cache.anim.FrameConstants",
                "org.major.client.Actions",
                "org.major.map.Orientation",
                "org.major.map.RenderFlags",
                "org.major.map.TileAttributes"
        }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
        }
    }
}
