package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneFog;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameUniformBufferTest {
    @Test
    void packsTheExactStd140FrameLayout() {
        ByteBuffer bytes = BufferUtils.createByteBuffer(FrameUniformBuffer.BYTE_SIZE)
                .order(ByteOrder.nativeOrder());
        CameraState camera = new CameraState(10.0f, -20.0f, 30.0f, 0.25f, -0.5f);
        RenderPresentation presentation =
                new RenderPresentation(1.5, -0.25, false, true, 12, 0x336699);
        SceneFog.Bounds fog = new SceneFog.Bounds(100.0f, 900.0f, 200.0f, 800.0f);

        FrameUniformBuffer.write(bytes, camera,
                2.0f, 16.0f / 9.0f, -1.25f, 32.0f, 0.00001f,
                presentation, fog, 127);

        assertEquals(FrameUniformBuffer.BYTE_SIZE, bytes.remaining());

        assertEquals(10.0f, bytes.getFloat(0));
        assertEquals(-20.0f, bytes.getFloat(4));
        assertEquals(30.0f, bytes.getFloat(8));
        assertEquals(0.25f, bytes.getFloat(12));

        assertEquals(-0.5f, bytes.getFloat(16));
        assertEquals(2.0f, bytes.getFloat(20));
        assertEquals(16.0f / 9.0f, bytes.getFloat(24));
        assertEquals(-1.25f, bytes.getFloat(28));

        assertEquals(32.0f, bytes.getFloat(32));
        assertEquals(0.00001f, bytes.getFloat(36));
        assertEquals(1.5f, bytes.getFloat(40));
        assertEquals(-0.25f, bytes.getFloat(44));

        assertEquals(1, bytes.getInt(48));
        assertEquals(1, bytes.getInt(52));
        assertEquals(127, bytes.getInt(56));
        assertEquals(0, bytes.getInt(60));

        assertEquals(100.0f, bytes.getFloat(64));
        assertEquals(900.0f, bytes.getFloat(68));
        assertEquals(200.0f, bytes.getFloat(72));
        assertEquals(800.0f, bytes.getFloat(76));

        assertEquals(0x33 / 255.0f, bytes.getFloat(80));
        assertEquals(0x66 / 255.0f, bytes.getFloat(84));
        assertEquals(0x99 / 255.0f, bytes.getFloat(88));
        assertEquals(12 * 128.0f, bytes.getFloat(92));
    }

    @Test
    void disabledFogWritesAZeroBoundsSlotAndFlag() {
        ByteBuffer bytes = BufferUtils.createByteBuffer(FrameUniformBuffer.BYTE_SIZE)
                .order(ByteOrder.nativeOrder());

        FrameUniformBuffer.write(bytes,
                new CameraState(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                1.0f, 1.0f, -1.0f, 1.0f, 0.0f,
                RenderPresentation.neutral(), null, 0);

        assertEquals(0, bytes.getInt(52));
        assertEquals(0.0f, bytes.getFloat(64));
        assertEquals(0.0f, bytes.getFloat(68));
        assertEquals(0.0f, bytes.getFloat(72));
        assertEquals(0.0f, bytes.getFloat(76));
    }
}
