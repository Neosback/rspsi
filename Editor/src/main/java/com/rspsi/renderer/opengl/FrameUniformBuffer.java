package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneFog;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

/**
 * OpenGL std140 backing store for state that is constant across one scene frame.
 *
 * <p>The layout mirrors {@code shaders/common/frame_uniforms.glsl}: six
 * explicit 16-byte slots. The staging buffer is reused every frame so moving
 * individual uniforms into the UBO does not replace driver calls with Java
 * allocation churn.</p>
 */
final class FrameUniformBuffer implements AutoCloseable {
    static final int BINDING_POINT = 0;
    static final int SLOT_BYTES = 16;
    static final int SLOT_COUNT = 6;
    static final int BYTE_SIZE = SLOT_BYTES * SLOT_COUNT;

    private final ByteBuffer staging =
            BufferUtils.createByteBuffer(BYTE_SIZE).order(ByteOrder.nativeOrder());
    private int buffer;

    void initialize() {
        if (buffer != 0) return;
        buffer = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, BYTE_SIZE, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, BINDING_POINT, buffer);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    void upload(CameraState camera,
                float focal,
                float aspect,
                float depthA,
                float depthB,
                float depthBiasNudge,
                RenderPresentation presentation,
                SceneFog.Bounds fogBounds,
                int clientCycle) {
        if (buffer == 0) {
            throw new IllegalStateException("Frame uniform buffer is not initialized");
        }
        write(staging, camera, focal, aspect, depthA, depthB, depthBiasNudge,
                presentation, fogBounds, clientCycle);

        glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferSubData(GL_UNIFORM_BUFFER, 0L, staging);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    static void write(ByteBuffer target,
                      CameraState camera,
                      float focal,
                      float aspect,
                      float depthA,
                      float depthB,
                      float depthBiasNudge,
                      RenderPresentation presentation,
                      SceneFog.Bounds fogBounds,
                      int clientCycle) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(presentation, "presentation");
        if (target.capacity() < BYTE_SIZE) {
            throw new IllegalArgumentException(
                    "Frame uniform target capacity is " + target.capacity()
                            + " bytes; expected at least " + BYTE_SIZE);
        }

        target.clear();
        target.limit(BYTE_SIZE);

        // slot 0: camera.xyz, pitch
        target.putFloat(camera.x()).putFloat(camera.y()).putFloat(camera.z())
                .putFloat(camera.pitch());

        // slot 1: yaw, focal, aspect, depthA
        target.putFloat(camera.yaw()).putFloat(focal).putFloat(aspect).putFloat(depthA);

        // slot 2: depthB, depth bias nudge, brightness, exposure
        target.putFloat(depthB).putFloat(depthBiasNudge)
                .putFloat((float) presentation.brightness())
                .putFloat((float) presentation.exposure());

        // slot 3: integer frame flags
        target.putInt(presentation.smoothBanding() ? 1 : 0);
        target.putInt(fogBounds != null ? 1 : 0);
        target.putInt(clientCycle);
        target.putInt(0);

        // slot 4: fog bounds
        if (fogBounds == null) {
            target.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        } else {
            target.putFloat(fogBounds.minX()).putFloat(fogBounds.maxX())
                    .putFloat(fogBounds.minZ()).putFloat(fogBounds.maxZ());
        }

        // slot 5: fog color.rgb, fog depth in world units
        int fogColor = presentation.fogColor();
        target.putFloat(((fogColor >>> 16) & 0xFF) / 255.0f)
                .putFloat(((fogColor >>> 8) & 0xFF) / 255.0f)
                .putFloat((fogColor & 0xFF) / 255.0f)
                .putFloat(presentation.fogDepthTiles() * 128.0f);

        if (target.position() != BYTE_SIZE) {
            throw new IllegalStateException(
                    "Frame uniform layout wrote " + target.position()
                            + " bytes; expected " + BYTE_SIZE);
        }
        target.flip();
    }

    /** Reasserts the binding point in case another renderer changed global GL state. */
    void bind() {
        if (buffer == 0) {
            throw new IllegalStateException("Frame uniform buffer is not initialized");
        }
        glBindBufferBase(GL_UNIFORM_BUFFER, BINDING_POINT, buffer);
    }

    @Override
    public void close() {
        if (buffer != 0) glDeleteBuffers(buffer);
        buffer = 0;
    }
}
