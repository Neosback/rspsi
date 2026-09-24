package com.rspsi.renderer.opengl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glGetError;

class GpuTimerQueryAcceptanceTest {
    private static long window;

    @BeforeAll
    static void createContext() {
        window = HeadlessGlContext.createOrSkip(32, "RSPSi GPU timer query acceptance");
        GL.createCapabilities();
    }

    @AfterAll
    static void destroyContext() {
        HeadlessGlContext.destroy(window);
    }

    @Test
    void elapsedQueryCanBeConsumedWithoutBlockingRenderLoopContract() {
        try (GpuTimerQuery timer = new GpuTimerQuery()) {
            assertTrue(timer.begin());
            glClear(GL_COLOR_BUFFER_BIT);
            timer.end();
            assertTrue(timer.hasInFlightQueries());

            // The test may synchronize to make the result deterministic; production
            // never calls glFinish and only consumes already-available results.
            glFinish();
            long nanos = timer.pollCompletedNanos();

            assertTrue(nanos >= 0L);
            assertFalse(timer.hasInFlightQueries());
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }
}
