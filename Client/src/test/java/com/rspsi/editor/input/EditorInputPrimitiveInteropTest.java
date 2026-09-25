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
