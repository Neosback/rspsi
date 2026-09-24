package com.rspsi.renderer.opengl;

import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15C.glBeginQuery;
import static org.lwjgl.opengl.GL15C.glDeleteQueries;
import static org.lwjgl.opengl.GL15C.glEndQuery;
import static org.lwjgl.opengl.GL15C.glGenQueries;
import static org.lwjgl.opengl.GL15C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33C.glGetQueryObjectui64;

/**
 * Double-buffered {@code GL_TIME_ELAPSED} query.
 *
 * <p>Results are consumed only after {@code GL_QUERY_RESULT_AVAILABLE} reports
 * ready. If both query objects are still in flight, the frame simply skips a
 * GPU sample rather than stalling the render thread.</p>
 */
final class GpuTimerQuery implements AutoCloseable {
    private final int[] queries = new int[2];
    private final boolean[] inFlight = new boolean[2];
    private int writeCursor;
    private int activeSlot = -1;
    private boolean closed;

    boolean begin() {
        ensureOpen();
        ensureQueries();
        if (activeSlot >= 0) {
            throw new IllegalStateException("GPU timer query is already active");
        }
        for (int attempt = 0; attempt < queries.length; attempt++) {
            int slot = (writeCursor + attempt) % queries.length;
            if (!inFlight[slot]) {
                glBeginQuery(GL_TIME_ELAPSED, queries[slot]);
                activeSlot = slot;
                writeCursor = (slot + 1) % queries.length;
                return true;
            }
        }
        return false;
    }

    void end() {
        ensureOpen();
        if (activeSlot < 0) return;
        glEndQuery(GL_TIME_ELAPSED);
        inFlight[activeSlot] = true;
        activeSlot = -1;
    }

    /**
     * Returns one completed sample, or {@code -1} when no result is ready.
     * Never blocks waiting for the GPU.
     */
    long pollCompletedNanos() {
        ensureOpen();
        ensureQueries();
        for (int slot = 0; slot < queries.length; slot++) {
            if (!inFlight[slot]) continue;
            if (glGetQueryObjecti(queries[slot], GL_QUERY_RESULT_AVAILABLE) == 0) continue;
            long nanos = glGetQueryObjectui64(queries[slot], GL_QUERY_RESULT);
            inFlight[slot] = false;
            return nanos;
        }
        return -1L;
    }

    boolean hasInFlightQueries() {
        return inFlight[0] || inFlight[1] || activeSlot >= 0;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (activeSlot >= 0) {
            glEndQuery(GL_TIME_ELAPSED);
            activeSlot = -1;
        }
        for (int query : queries) {
            if (query != 0) glDeleteQueries(query);
        }
        queries[0] = 0;
        queries[1] = 0;
        inFlight[0] = false;
        inFlight[1] = false;
    }

    private void ensureQueries() {
        if (queries[0] != 0) return;
        queries[0] = glGenQueries();
        queries[1] = glGenQueries();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("GPU timer query is closed");
        }
    }
}
