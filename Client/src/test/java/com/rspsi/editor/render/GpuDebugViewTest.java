package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuDebugViewTest {
    @Test
    void shaderCodesAreStableAndUnique() {
        var codes = new HashSet<Integer>();
        Arrays.stream(GpuDebugView.values()).forEach(view -> codes.add(view.shaderCode()));

        assertEquals(GpuDebugView.values().length, codes.size());
        assertEquals(0, GpuDebugView.NONE.shaderCode());
    }

    @Test
    void onlyNormalVisualizationRequiresAuxiliaryNormalResidency() {
        for (GpuDebugView view : GpuDebugView.values()) {
            if (view == GpuDebugView.NORMALS) {
                assertTrue(view.requiresNormals());
            } else {
                assertFalse(view.requiresNormals(), view.name());
            }
        }
    }
}
