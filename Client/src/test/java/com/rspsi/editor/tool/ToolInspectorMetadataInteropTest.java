package com.rspsi.editor.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolInspectorMetadataInteropTest {

    @Test
    void propertyDescriptorPreservesRecordStyleJavaSurface() {
        PropertyDescriptor descriptor = new PropertyDescriptor(
                "radius",
                "Brush Radius",
                PropertyDescriptor.ValueType.INTEGER,
                0,
                64);

        assertEquals("radius", descriptor.id());
        assertEquals("Brush Radius", descriptor.label());
        assertEquals(PropertyDescriptor.ValueType.INTEGER, descriptor.type());
        assertEquals(0, descriptor.minimum());
        assertEquals(64, descriptor.maximum());
    }

    @Test
    void propertyDescriptorPreservesValueSemantics() {
        PropertyDescriptor left = new PropertyDescriptor(
                "radius", "Brush Radius", PropertyDescriptor.ValueType.INTEGER, 0, 64);
        PropertyDescriptor right = new PropertyDescriptor(
                "radius", "Brush Radius", PropertyDescriptor.ValueType.INTEGER, 0, 64);
        PropertyDescriptor different = new PropertyDescriptor(
                "radius", "Brush Radius", PropertyDescriptor.ValueType.INTEGER, 1, 64);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertNotEquals(left, different);
        assertTrue(left.toString().startsWith("PropertyDescriptor["));
    }

    @Test
    void propertyDescriptorPreservesValidationContract() {
        assertThrows(NullPointerException.class,
                () -> new PropertyDescriptor(null, "Label", PropertyDescriptor.ValueType.INTEGER, 0, 1));
        assertThrows(NullPointerException.class,
                () -> new PropertyDescriptor("id", null, PropertyDescriptor.ValueType.INTEGER, 0, 1));
        assertThrows(NullPointerException.class,
                () -> new PropertyDescriptor("id", "Label", null, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PropertyDescriptor("id", "Label", PropertyDescriptor.ValueType.INTEGER, 2, 1));
    }

    @Test
    void toolInspectorRemainsJavaSamCompatible() {
        PropertyDescriptor descriptor = new PropertyDescriptor(
                "enabled", "Enabled", PropertyDescriptor.ValueType.BOOLEAN, 0, 1);
        ToolInspector inspector = () -> List.of(descriptor);

        assertEquals(List.of(descriptor), inspector.properties());
    }
}
