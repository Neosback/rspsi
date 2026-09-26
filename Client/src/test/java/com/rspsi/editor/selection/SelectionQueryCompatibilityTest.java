package com.rspsi.editor.selection;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SelectionQueryCompatibilityTest {

    @Test
    void queryKeepsUtilityClassAndStaticApi() throws Exception {
        assertTrue(Modifier.isPublic(SelectionQuery.class.getModifiers()));
        assertTrue(Modifier.isFinal(SelectionQuery.class.getModifiers()));

        var constructor = SelectionQuery.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        var objects = SelectionQuery.class.getDeclaredMethod(
                "objects",
                com.rspsi.editor.model.WorldDocument.class,
                SelectionQuery.ObjectFilter.class);
        assertTrue(Modifier.isPublic(objects.getModifiers()));
        assertTrue(Modifier.isStatic(objects.getModifiers()));

        var tiles = SelectionQuery.class.getDeclaredMethod(
                "tiles",
                com.rspsi.editor.model.WorldDocument.class,
                SelectionQuery.TileFilter.class);
        assertTrue(Modifier.isPublic(tiles.getModifiers()));
        assertTrue(Modifier.isStatic(tiles.getModifiers()));
    }

    @Test
    void objectFilterRemainsRecordWithFourAndFiveArgumentConstructors() throws Exception {
        assertTrue(SelectionQuery.ObjectFilter.class.isRecord());

        assertNotNull(SelectionQuery.ObjectFilter.class.getDeclaredConstructor(
                Integer.class,
                Integer.class,
                Integer.class,
                Integer.class));
        assertNotNull(SelectionQuery.ObjectFilter.class.getDeclaredConstructor(
                Integer.class,
                Integer.class,
                Integer.class,
                Integer.class,
                ObjectCategory.class));

        SelectionQuery.ObjectFilter filter =
                new SelectionQuery.ObjectFilter(100, 10, 2, 3);
        assertEquals(100, filter.id());
        assertEquals(10, filter.type());
        assertEquals(2, filter.plane());
        assertEquals(3, filter.rotation());
        assertNull(filter.category());
    }

    @Test
    void objectFilterPreservesValidationMessagesAndMatchSemantics() {
        assertEquals(
                "Object ID cannot be negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.ObjectFilter(-1, null, null, null))
                        .getMessage());
        assertEquals(
                "Object type must be 0..63",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.ObjectFilter(null, 64, null, null))
                        .getMessage());
        assertEquals(
                "Object plane cannot be negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.ObjectFilter(null, null, -1, null))
                        .getMessage());
        assertEquals(
                "Object rotation must be 0..3",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.ObjectFilter(null, null, null, 4))
                        .getMessage());

        WorldObject object = new WorldObject(100, 10, 3, 2, 4, 5);
        assertTrue(new SelectionQuery.ObjectFilter(
                100, 10, 2, 3, object.category()).matches(object));
        assertFalse(new SelectionQuery.ObjectFilter(
                101, null, null, null, null).matches(object));
    }

    @Test
    void tileFilterRemainsRecordAndPreservesValidationMessages() {
        assertTrue(SelectionQuery.TileFilter.class.isRecord());

        assertEquals(
                "Tile plane cannot be negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.TileFilter(-1, null, null, null))
                        .getMessage());
        assertEquals(
                "Underlay ID must be 0..255",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.TileFilter(null, 256, null, null))
                        .getMessage());
        assertEquals(
                "Overlay ID must be 0..65535",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.TileFilter(null, null, 65536, null))
                        .getMessage());
        assertEquals(
                "Required flags mask cannot be negative",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new SelectionQuery.TileFilter(null, null, null, -1))
                        .getMessage());
    }

    @Test
    void tileFilterPreservesMaskMatching() {
        TileSnapshot tile = new TileSnapshot(
                1, 2, 3, 4,
                12, 34, 5, 1, 0b10110,
                List.of());

        assertTrue(new SelectionQuery.TileFilter(
                2, 12, 34, 0b00110).matches(2, tile));
        assertFalse(new SelectionQuery.TileFilter(
                2, 12, 34, 0b01001).matches(2, tile));
        assertFalse(new SelectionQuery.TileFilter(
                1, null, null, null).matches(2, tile));
    }
}
