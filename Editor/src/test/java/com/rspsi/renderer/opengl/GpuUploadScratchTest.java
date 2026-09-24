package com.rspsi.renderer.opengl;

import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class GpuUploadScratchTest {

    @Test
    void stagingBuffersGrowGeometricallyAndReuseHighWaterCapacity() {
        try (GpuUploadScratch scratch = new GpuUploadScratch()) {
            FloatBuffer firstVertices = scratch.vertices(100);
            IntBuffer firstIndices = scratch.indices(100);

            assertTrue(firstVertices.isDirect());
            assertTrue(firstIndices.isDirect());
            assertEquals(GpuUploadScratch.INITIAL_VERTEX_FLOATS,
                    scratch.vertexCapacityFloats());
            assertEquals(GpuUploadScratch.INITIAL_INDICES,
                    scratch.indexCapacity());

            FloatBuffer reusedVertices = scratch.vertices(1_000);
            IntBuffer reusedIndices = scratch.indices(1_000);
            assertSame(firstVertices, reusedVertices);
            assertSame(firstIndices, reusedIndices);

            int requestedFloats = GpuUploadScratch.INITIAL_VERTEX_FLOATS + 1;
            FloatBuffer grown = scratch.vertices(requestedFloats);
            assertNotSame(firstVertices, grown);
            assertTrue(grown.capacity() >= requestedFloats);
            assertEquals(2, scratch.vertexGrowths());

            FloatBuffer reusedHighWater = scratch.vertices(32);
            assertSame(grown, reusedHighWater);
            assertEquals(2, scratch.vertexGrowths());
        }
    }

    @Test
    void closeReleasesRetainedNativeBuffers() {
        GpuUploadScratch scratch = new GpuUploadScratch();
        scratch.vertices(1);
        scratch.indices(1);

        scratch.close();

        assertEquals(0, scratch.vertexCapacityFloats());
        assertEquals(0, scratch.indexCapacity());
    }

    @Test
    void capacityGrowthHandlesLargeRequestsWithoutOverflowLoop() {
        assertEquals(131_072,
                GpuUploadScratch.nextCapacity(65_536, 65_537, 65_536));
        assertEquals(Integer.MAX_VALUE,
                GpuUploadScratch.nextCapacity(
                        1 << 30, Integer.MAX_VALUE, 65_536));
    }
}
