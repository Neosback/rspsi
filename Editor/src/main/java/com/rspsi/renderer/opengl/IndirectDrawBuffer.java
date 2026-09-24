package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.GpuDrawCommand;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import static org.lwjgl.opengl.GL15C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glBufferSubData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;

/**
 * Reusable GPU command buffer for OpenGL 4.3 multi-draw-indirect submission.
 *
 * <p>The full ordered pass is staged once, uploaded once, then each native-state
 * batch references a contiguous range by byte offset. The command order is
 * therefore identical to the GL 3.3 multi-draw fallback.</p>
 */
final class IndirectDrawBuffer implements AutoCloseable {
    static final int COMMAND_BYTES = 5 * Integer.BYTES;
    private static final int MIN_COMMAND_CAPACITY = 256;

    private int buffer;
    private int gpuCapacityCommands;
    private ByteBuffer staging;
    private int stagedCommands;

    void upload(List<GpuDrawCommand> commands,
                List<Integer> orderedIndices,
                IntUnaryOperator firstIndexForCommand) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(orderedIndices, "orderedIndices");
        Objects.requireNonNull(firstIndexForCommand, "firstIndexForCommand");

        int count = orderedIndices.size();
        ensureCpuCapacity(count);
        staging.clear();

        for (int orderedIndex : orderedIndices) {
            if (orderedIndex < 0 || orderedIndex >= commands.size()) {
                throw new IllegalArgumentException(
                        "Indirect draw command index is outside the command list: " + orderedIndex);
            }
            GpuDrawCommand command = commands.get(orderedIndex);
            staging.putInt(command.indexCount());             // count
            staging.putInt(1);                                // instanceCount
            staging.putInt(firstIndexForCommand.applyAsInt(orderedIndex)); // firstIndex
            staging.putInt(0);                                // baseVertex
            staging.putInt(0);                                // baseInstance
        }
        staging.flip();
        stagedCommands = count;

        ensureGpuCapacity(count);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, buffer);
        if (count > 0) {
            glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0L, staging);
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
    }

    void draw(int orderedStart, int commandCount) {
        if (commandCount < 1) return;
        if (orderedStart < 0 || orderedStart + commandCount > stagedCommands) {
            throw new IndexOutOfBoundsException(
                    "Indirect draw range outside staged commands: start="
                            + orderedStart + ", count=" + commandCount
                            + ", staged=" + stagedCommands);
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, buffer);
        glMultiDrawElementsIndirect(
                GL_TRIANGLES,
                GL_UNSIGNED_INT,
                (long) orderedStart * COMMAND_BYTES,
                commandCount,
                0);
    }

    void unbind() {
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
    }

    int stagedCommands() {
        return stagedCommands;
    }

    int gpuCapacityCommands() {
        return gpuCapacityCommands;
    }

    private void ensureCpuCapacity(int commands) {
        int required = Math.max(1, commands) * COMMAND_BYTES;
        if (staging != null && staging.capacity() >= required) return;

        int commandCapacity = nextCapacity(commands);
        int bytes = Math.multiplyExact(commandCapacity, COMMAND_BYTES);
        if (staging == null) {
            staging = MemoryUtil.memAlloc(bytes).order(ByteOrder.nativeOrder());
        } else {
            staging = MemoryUtil.memRealloc(staging, bytes).order(ByteOrder.nativeOrder());
        }
    }

    private void ensureGpuCapacity(int commands) {
        int required = Math.max(1, commands);
        if (buffer != 0 && gpuCapacityCommands >= required) return;

        if (buffer == 0) buffer = glGenBuffers();
        gpuCapacityCommands = nextCapacity(commands);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, buffer);
        glBufferData(GL_DRAW_INDIRECT_BUFFER,
                (long) gpuCapacityCommands * COMMAND_BYTES,
                GL_STREAM_DRAW);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
    }

    private static int nextCapacity(int required) {
        int target = Math.max(MIN_COMMAND_CAPACITY, Math.max(1, required));
        int highest = Integer.highestOneBit(target - 1);
        if (highest >= (1 << 29)) return target;
        return highest << 1;
    }

    @Override
    public void close() {
        if (buffer != 0) glDeleteBuffers(buffer);
        buffer = 0;
        gpuCapacityCommands = 0;
        stagedCommands = 0;
        if (staging != null) MemoryUtil.memFree(staging);
        staging = null;
    }
}
