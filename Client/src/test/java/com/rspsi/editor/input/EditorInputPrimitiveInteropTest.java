package com.rspsi.editor.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorInputPrimitiveInteropTest {

    @Test
    void keyEventPreservesRecordStyleJavaSurfaceAndNormalization() {
        EditorKeyEvent event = new EditorKeyEvent(
                "  Enter  ",
                true,
                false,
                true,
                false,
                true,
                false);

        assertEquals("Enter", event.key());
        assertTrue(event.pressed());
        assertFalse(event.repeat());
        assertTrue(event.shift());
        assertFalse(event.ctrl());
        assertTrue(event.alt());
        assertFalse(event.meta());
    }

    @Test
    void keyEventPreservesValueSemantics() {
        EditorKeyEvent left = new EditorKeyEvent("A", true, false, false, true, false, false);
        EditorKeyEvent right = new EditorKeyEvent("A", true, false, false, true, false, false);
        EditorKeyEvent different = new EditorKeyEvent("B", true, false, false, true, false, false);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().startsWith("EditorKeyEvent["));
    }

    @Test
    void keyEventRejectsNullOrBlankKeys() {
        assertThrows(NullPointerException.class,
                () -> new EditorKeyEvent(null, true, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new EditorKeyEvent("   ", true, false, false, false, false, false));
    }

    @Test
    void pointerEventPreservesRecordStyleSurfaceAndValueSemantics() {
        PointerEvent left = new PointerEvent(
                12.5f, 33.0f, PointerButton.SECONDARY, true, false, true);
        PointerEvent right = new PointerEvent(
                12.5f, 33.0f, PointerButton.SECONDARY, true, false, true);

        assertEquals(12.5f, left.x());
        assertEquals(33.0f, left.y());
        assertEquals(PointerButton.SECONDARY, left.button());
        assertTrue(left.shift());
        assertFalse(left.ctrl());
        assertTrue(left.alt());
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertTrue(left.toString().startsWith("PointerEvent["));
    }

    @Test
    void pointerEventRejectsInvalidCoordinatesAndNullButton() {
        assertThrows(IllegalArgumentException.class,
                () -> new PointerEvent(Float.NaN, 1.0f, PointerButton.PRIMARY, false, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new PointerEvent(1.0f, Float.POSITIVE_INFINITY, PointerButton.PRIMARY, false, false, false));
        assertThrows(NullPointerException.class,
                () -> new PointerEvent(1.0f, 2.0f, null, false, false, false));
    }

    @Test
    void pointerEventKeepsJavaRecordFloatEqualitySemantics() {
        PointerEvent positiveZero = new PointerEvent(
                0.0f, 1.0f, PointerButton.NONE, false, false, false);
        PointerEvent negativeZero = new PointerEvent(
                -0.0f, 1.0f, PointerButton.NONE, false, false, false);

        assertNotEquals(positiveZero, negativeZero);
    }

    @Test
    void pointerButtonEnumKeepsExistingJavaConstants() {
        assertArrayEquals(
                new PointerButton[]{
                        PointerButton.NONE,
                        PointerButton.PRIMARY,
                        PointerButton.SECONDARY,
                        PointerButton.MIDDLE
                },
                PointerButton.values());
    }
}
