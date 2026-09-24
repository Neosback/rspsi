package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelBuildWorkspaceTest {

    @Test
    void growsGeometricallyAndReusesPrimitiveStorage() {
        ModelBuildWorkspace workspace = new ModelBuildWorkspace();

        workspace.prepare(10);
        int initial = workspace.capacity();
        assertTrue(initial >= 10);

        workspace.setVertex(0, 1, 2, 3);
        workspace.prepare(8);

        assertEquals(initial, workspace.capacity());
        // Coordinates are intentionally not cleared because every active
        // vertex is overwritten by the next model transform.
        assertEquals(1, workspace.x(0));
    }

    @Test
    void prepareClearsNormalAccumulatorsWithoutReallocating() {
        ModelBuildWorkspace workspace = new ModelBuildWorkspace();
        workspace.prepare(3);
        workspace.setVertex(0, 0, 0, 0);
        workspace.setVertex(1, 128, 0, 0);
        workspace.setVertex(2, 0, 0, 128);
        workspace.computeFaceNormal(0, 1, 2);
        workspace.accumulateFaceNormal(0);

        assertEquals(1, workspace.normalMagnitude(0));
        int capacity = workspace.capacity();

        workspace.prepare(3);

        assertEquals(capacity, workspace.capacity());
        assertEquals(0, workspace.normalX(0));
        assertEquals(0, workspace.normalY(0));
        assertEquals(0, workspace.normalZ(0));
        assertEquals(0, workspace.normalMagnitude(0));
    }

    @Test
    void faceNormalMatchesClientFixedPointConvention() {
        ModelBuildWorkspace workspace = new ModelBuildWorkspace();
        workspace.prepare(3);
        workspace.setVertex(0, 0, 0, 0);
        workspace.setVertex(1, 128, 0, 0);
        workspace.setVertex(2, 0, 0, 128);

        workspace.computeFaceNormal(0, 1, 2);

        assertEquals(0, workspace.faceNormalX());
        assertEquals(-256, workspace.faceNormalY());
        assertEquals(0, workspace.faceNormalZ());

        workspace.accumulateFaceNormal(0);
        assertEquals(0, workspace.normalX(0));
        assertEquals(-256, workspace.normalY(0));
        assertEquals(0, workspace.normalZ(0));
        assertEquals(1, workspace.normalMagnitude(0));
    }

    @Test
    void capturesPreContourYWithoutAllocatingPerModelArray() {
        ModelBuildWorkspace workspace = new ModelBuildWorkspace();
        workspace.prepare(2);
        workspace.setVertex(0, 1, -20, 3);
        workspace.setVertex(1, 4, -40, 6);

        workspace.captureUnskewedY(2);
        workspace.setY(0, -5);
        workspace.setY(1, -10);

        assertEquals(-20, workspace.unskewedY(0));
        assertEquals(-40, workspace.unskewedY(1));
        assertEquals(-5, workspace.y(0));
        assertEquals(-10, workspace.y(1));
    }
}
