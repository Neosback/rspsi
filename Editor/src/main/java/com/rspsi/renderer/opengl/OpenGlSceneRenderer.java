package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.GpuCommandVisibility;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.SceneLayer;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.SceneFog;
import com.rspsi.editor.render.SceneOcclusionResolver;
import com.rspsi.editor.render.TextureAnimation;
import com.rspsi.editor.render.RsFaceOrderPlanner;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FILL;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL11.GL_LINE;
import static org.lwjgl.opengl.GL11.GL_GEQUAL;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_REPEAT;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glClearDepth;
import static org.lwjgl.opengl.GL11.glDepthMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glDepthFunc;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11.glPolygonMode;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VENDOR;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL12.glTexSubImage3D;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL14.glMultiDrawElements;
import static org.lwjgl.opengl.GL30.GL_TEXTURE0;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/** OpenGL 3.3 consumer of the immutable world-space upload plan. */
public final class OpenGlSceneRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGlSceneRenderer.class);
    // Position, UV, encoded light/color, alpha, render type, and native RGB.
    // The reference packet retains normals; this backend does not need them
    // after ModelPacketBuilder has produced its lit face values.
    private static final int FLOATS_PER_VERTEX = 12;
    private static final int TEXTURE_SIZE = 128;
    private static final int TEXTURE_LAYER_CAPACITY = 256;
    private static final float FOV_Y = (float) Math.toRadians(50.0);
    private static final float NEAR = 16.0f;
    private static final float FAR = 65536.0f;

    private int lastAlpha = -1;
    private int lastTextured = -1;
    private int lastTextureAvailable = -1;
    private int lastTextureMissing = -1;
    private int lastTerrain = -1;
    private int lastNoDepth = -1;
    private int lastFaceBias = -1;
    private float lastTextureOffsetU = Float.NaN;
    private float lastTextureOffsetV = Float.NaN;
    private int lastTextureLayer = -1;
    private float lastTextureScaleX = Float.NaN;
    private float lastTextureScaleY = Float.NaN;

    private void resetDrawState() {
        lastAlpha = -1;
        lastTextured = -1;
        lastTextureAvailable = -1;
        lastTextureMissing = -1;
        lastTerrain = -1;
        lastNoDepth = -1;
        lastFaceBias = -1;
        lastTextureOffsetU = Float.NaN;
        lastTextureOffsetV = Float.NaN;
        lastTextureLayer = -1;
        lastTextureScaleX = Float.NaN;
        lastTextureScaleY = Float.NaN;
    }

    private int program;
    private int vertexArray;
    private int vertexBuffer;
    private int indexBuffer;
    private int cameraLocation;
    private int pitchLocation;
    private int yawLocation;
    private int focalLocation;
    private int aspectLocation;
    private int depthALocation;
    private int depthBLocation;
    private int faceBiasLocation;
    private int texturedLocation;
    private int textureAvailableLocation;
    private int textureMissingLocation;
    private int terrainLocation;
    private int textureLocation;
    private int textureLayerLocation;
    private int textureScaleLocation;
    private int textureOffsetLocation;
    private int brightnessLocation;
    private int exposureLocation;
    private int smoothBandingLocation;
    private int useFogLocation;
    private int fogWestLocation;
    private int fogEastLocation;
    private int fogSouthLocation;
    private int fogNorthLocation;
    private int fogDepthLocation;
    private int fogColorLocation;
    private String uploadedFingerprint;
    private String uploadedTextureFingerprint;
    private int textureArray;
    private final Map<Integer, Integer> textureLayers = new HashMap<>();
    private final Map<Integer, float[]> textureScales = new HashMap<>();
    private int firstGlError = GL_NO_ERROR;
    private int framebufferStatus = org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
    private Statistics statistics = Statistics.empty();
    private boolean diagnosticsLogged;
    private boolean lastFrameDepthWrites = true;
    private int lastFramePolygonMode = GL_FILL;
    private String orderedPlanFingerprint;
    private List<Integer> cachedOpaqueOrder = List.of();

    public void initialize() {
        GLCapabilities capabilities = GL.createCapabilities();
        if (!capabilities.OpenGL33) {
            throw new IllegalStateException("RSPSi requires an OpenGL 3.3 core context; detected "
                    + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
        }
        vertexArray = glGenVertexArrays();
        vertexBuffer = glGenBuffers();
        indexBuffer = glGenBuffers();
        glBindVertexArray(vertexArray);
        // A core context has no usable default VAO. Bind the editor-owned VAO
        // before shader validation and all attribute setup so initialization
        // never depends on compatibility-profile behavior.
        program = link(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(5, 3, GL_FLOAT, false, stride, 8L * Float.BYTES);
        glEnableVertexAttribArray(5);
        glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 11L * Float.BYTES);
        glEnableVertexAttribArray(6);
        glBindVertexArray(0);

        cameraLocation = glGetUniformLocation(program, "uCamera");
        pitchLocation = glGetUniformLocation(program, "uPitch");
        yawLocation = glGetUniformLocation(program, "uYaw");
        focalLocation = glGetUniformLocation(program, "uFocal");
        aspectLocation = glGetUniformLocation(program, "uAspect");
        depthALocation = glGetUniformLocation(program, "uDepthA");
        depthBLocation = glGetUniformLocation(program, "uDepthB");
        faceBiasLocation = glGetUniformLocation(program, "uFaceBias");
        texturedLocation = glGetUniformLocation(program, "uTextured");
        textureAvailableLocation = glGetUniformLocation(program, "uTextureAvailable");
        textureMissingLocation = glGetUniformLocation(program, "uTextureMissing");
        terrainLocation = glGetUniformLocation(program, "uTerrain");
        textureLocation = glGetUniformLocation(program, "uTexture");
        textureLayerLocation = glGetUniformLocation(program, "uTextureLayer");
        textureScaleLocation = glGetUniformLocation(program, "uTextureScale");
        textureOffsetLocation = glGetUniformLocation(program, "uTextureOffset");
        brightnessLocation = glGetUniformLocation(program, "uBrightness");
        exposureLocation = glGetUniformLocation(program, "uExposure");
        smoothBandingLocation = glGetUniformLocation(program, "uSmoothBanding");
        useFogLocation = glGetUniformLocation(program, "uUseFog");
        fogWestLocation = glGetUniformLocation(program, "uFogWest");
        fogEastLocation = glGetUniformLocation(program, "uFogEast");
        fogSouthLocation = glGetUniformLocation(program, "uFogSouth");
        fogNorthLocation = glGetUniformLocation(program, "uFogNorth");
        fogDepthLocation = glGetUniformLocation(program, "uFogDepth");
        fogColorLocation = glGetUniformLocation(program, "uFogColor");
        glUseProgram(program);
        glUniform1i(textureLocation, 0);
        glUseProgram(0);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GEQUAL);
        glClearDepth(0.0);
        glDisable(GL_BLEND);
        glDepthMask(true);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        // Winding parity is not proven for every shaped-tile and cache-model
        // family. Keep the Phase 0 native baseline two-sided, matching the
        // visibility contract of the CPU reference renderer.
        glClearColor(0.063f, 0.094f, 0.153f, 1.0f);
        captureGlError();
    }

    public void draw(GpuUploadPlan plan, CameraState camera, int width, int height) {
        draw(plan, camera, width, height, RenderPresentation.neutral());
    }

    public void draw(GpuUploadPlan plan, CameraState camera, int width, int height,
              RenderPresentation presentation) {
        draw(plan, camera, width, height, presentation, clientCycle());
    }

    public void draw(GpuUploadPlan plan, CameraState camera, int width, int height,
              RenderPresentation presentation, int clientCycle) {
        if (presentation == null) throw new IllegalArgumentException("presentation cannot be null");
        // Reassert all state that the previous alpha pass or the ImGui backend
        // may have changed. In particular, glDepthMask(false) survives a
        // frame and would make the next depth clear ineffective, producing
        // holes and ghosted fragments while the camera moves.
        glViewport(0, 0, width, height);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GEQUAL);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        glClearDepth(0.0);
        lastFrameDepthWrites = true;
        lastFramePolygonMode = presentation.wireframe() ? GL_LINE : GL_FILL;
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (plan == null || plan.vertices().isEmpty() || plan.indices().isEmpty()) {
            statistics = statisticsFor(plan, null, false, false, 0);
            captureGlError();
            return;
        }
        // Geometry/texture upload is gated on the plan's own camera-independent
        // fingerprint only, and always uploads the complete, unfiltered plan.
        // Occlusion visibility below is a separate, cheap, per-frame decision
        // (GpuCommandVisibility) that never rebuilds vertex/index data or
        // shatters merged draw commands - camera movement alone must never
        // trigger a glBufferData re-upload. geometryUploaded/textureUploaded
        // are surfaced through Statistics so camera-drag regressions can be
        // caught by a debug overlay/log rather than assumed fixed.
        boolean geometryUploaded = false;
        boolean textureUploaded = false;
        if (!plan.fingerprint().equals(uploadedFingerprint)) {
            uploadGeometry(plan);
            geometryUploaded = true;
        }
        String textureFingerprint = textureFingerprint(plan.textures());
        if (!textureFingerprint.equals(uploadedTextureFingerprint)) {
            uploadTextureArray(plan.textures());
            uploadedTextureFingerprint = textureFingerprint;
            textureUploaded = true;
        }
        GpuCommandVisibility visibility = GpuCommandVisibility.of(plan, camera);

        glUseProgram(program);
        glUniform3f(cameraLocation, camera.x(), camera.y(), camera.z());
        glUniform1f(pitchLocation, camera.pitch());
        glUniform1f(yawLocation, camera.yaw());
        glUniform1f(focalLocation, (float) (1.0 / Math.tan(FOV_Y * 0.5)));
        glUniform1f(aspectLocation, (float) width / height);
        glUniform1f(depthALocation, -(FAR + NEAR) / (FAR - NEAR));
        glUniform1f(depthBLocation, 2.0f * FAR * NEAR / (FAR - NEAR));
        glUniform1f(brightnessLocation, (float) presentation.brightness());
        glUniform1f(exposureLocation, (float) presentation.exposure());
        glUniform1i(smoothBandingLocation, presentation.smoothBanding() ? 1 : 0);
        SceneFog.Bounds fogBounds = SceneFog.bounds(plan);
        glUniform1i(useFogLocation, presentation.fogDepthTiles() > 0 ? 1 : 0);
        glUniform1f(fogWestLocation, fogBounds.minX());
        glUniform1f(fogEastLocation, fogBounds.maxX());
        glUniform1f(fogSouthLocation, fogBounds.minZ());
        glUniform1f(fogNorthLocation, fogBounds.maxZ());
        glUniform1f(fogDepthLocation, presentation.fogDepthTiles() * 128.0f);
        glUniform3f(fogColorLocation, ((presentation.fogColor() >>> 16) & 0xFF) / 255.0f,
                ((presentation.fogColor() >>> 8) & 0xFF) / 255.0f,
                (presentation.fogColor() & 0xFF) / 255.0f);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        glBindVertexArray(vertexArray);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
        resetDrawState();
        int drawCalls = 0;
        List<GpuDrawCommand> commands = plan.commands();
        // RuneLite uses GL_LEQUAL for its forward-Z native path.  This
        // reversed-Z path uses the equivalent GL_GEQUAL so exactly-coplanar
        // wall trim/decor faces can be resolved by the OSRS submission order
        // instead of failing a strict depth test. List.sort is stable, so
        // indices are only reordered relative to distinct priority values.
        List<Integer> opaqueOrder = opaqueOrder(plan, commands, visibility);
        drawCalls += drawBatches(plan, commands, opaqueOrder, visibility, camera, false, clientCycle);
        // The software reference renderer composites transparent triangles
        // back-to-front. Keep opaque submission order stable, but apply the
        // same depth ordering to alpha ranges in the native backend.
        List<GpuDrawCommand> alpha = new ArrayList<>();
        Map<GpuDrawCommand, Integer> alphaIndices = new java.util.IdentityHashMap<>();
        for (int index = 0; index < commands.size(); index++) {
            GpuDrawCommand command = commands.get(index);
            if (command.pass() == GpuDrawCommand.SubmissionPass.ALPHA && visibility.visible(index)) {
                alpha.add(command);
                alphaIndices.put(command, index);
            }
        }
        alpha = RsFaceOrderPlanner.orderAlpha(alpha,
                command -> averageDepth(plan, command, camera));
        List<Integer> alphaOrder = new ArrayList<>(alpha.size());
        for (GpuDrawCommand command : alpha) {
            alphaOrder.add(alphaIndices.get(command));
        }
        drawCalls += drawBatches(plan, commands, alphaOrder, visibility, camera, true, clientCycle);
        // Alpha and no-depth submissions disable depth writes. Restore the
        // baseline before handing the context back to ImGui and before the
        // next frame's clear.
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GEQUAL);
        glDisable(GL_BLEND);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        lastFrameDepthWrites = true;
        lastFramePolygonMode = presentation.wireframe() ? GL_LINE : GL_FILL;
        glBindVertexArray(0);
        glUseProgram(0);
        captureGlError();
        statistics = statisticsFor(plan, visibility, geometryUploaded, textureUploaded, drawCalls);
        if (!diagnosticsLogged) {
            LOGGER.info("Native OpenGL {} / {} / {}; source={} vertices, rendered={} triangles, "
                            + "textures decoded={} fallback={} unavailable={} missing={}, "
                            + "draws={}, framebuffer=0x{}, polygonMode=0x{}, depthWrites={}, "
                            + "occlusion={}, firstGLerror={}",
                    statistics.vendor(), statistics.renderer(), statistics.version(),
                    statistics.sourceVertices(), statistics.renderedTriangles(),
                    statistics.decodedTextures(), statistics.fallbackTextures(),
                    statistics.unavailableTextures(), statistics.missingTextures(), statistics.drawCalls(),
                    Integer.toHexString(statistics.framebufferStatus()),
                    Integer.toHexString(statistics.polygonMode()), statistics.depthWritesEnabled(),
                    statistics.occlusionApplied(), statistics.firstGlError());
            diagnosticsLogged = true;
        }
    }

    public Statistics statistics() {
        return statistics;
    }

    private Statistics statisticsFor(GpuUploadPlan plan, GpuCommandVisibility visibility,
                                     boolean geometryUploaded, boolean textureUploaded, int drawCalls) {
        int sourceVertices = plan == null ? 0 : plan.vertices().size();
        int sourceIndices = plan == null ? 0 : plan.indices().size();
        int renderedIndices = 0;
        int terrainTriangles = 0;
        int objectTriangles = 0;
        if (plan != null && visibility != null) {
            List<GpuDrawCommand> commands = plan.commands();
            for (int index = 0; index < commands.size(); index++) {
                if (!visibility.visible(index)) continue;
                GpuDrawCommand command = commands.get(index);
                int triangles = command.indexCount() / 3;
                renderedIndices += command.indexCount();
                if (command.layer() == SceneLayer.Kind.TERRAIN) terrainTriangles += triangles;
                else objectTriangles += triangles;
            }
        }
        int decoded = 0;
        int fallback = 0;
        int unavailable = 0;
        if (plan != null) {
            for (RenderTextureResource resource : plan.textures().values()) {
                switch (resource.pixelStatus()) {
                    case AVAILABLE -> decoded++;
                    case AVERAGE_COLOR_FALLBACK -> fallback++;
                    case UNAVAILABLE, INVALID -> unavailable++;
                }
            }
        }
        int missing = 0;
        if (plan != null) {
            missing = (int) plan.commands().stream()
                    .map(GpuDrawCommand::textureId)
                    .filter(id -> id >= 0 && !plan.textures().containsKey(id))
                    .distinct()
                    .count();
        }
        return new Statistics(sourceVertices, sourceIndices, renderedIndices,
                terrainTriangles, objectTriangles, decoded, fallback, unavailable, missing,
                safeGlString(GL_VENDOR), safeGlString(GL_RENDERER), safeGlString(GL_VERSION),
                firstGlError, framebufferStatus, lastFramePolygonMode, lastFrameDepthWrites,
                geometryUploaded, textureUploaded, drawCalls,
                visibility != null && visibility.occlusionApplied());
    }

    private void captureGlError() {
        int error = glGetError();
        if (firstGlError == GL_NO_ERROR && error != GL_NO_ERROR) firstGlError = error;
    }

    private static String safeGlString(int name) {
        String value = glGetString(name);
        return value == null ? "unknown" : value;
    }

    /**
     * {@code geometryUploaded}/{@code textureUploaded} report whether this
     * frame performed a {@code glBufferData}/texture-array upload; both
     * should be {@code false} for every frame after the first one a given
     * scene is resident, including every frame of camera movement. {@code
     * drawCalls} is the number of {@code glDrawElements} calls issued this
     * frame - it should stay proportional to the scene's merged command
     * count, not explode near occluders.
     */
    public record Statistics(int sourceVertices, int sourceIndices, int renderedIndices,
                             int terrainTriangles, int objectTriangles,
                             int decodedTextures, int fallbackTextures, int unavailableTextures,
                             int missingTextures,
                             String vendor, String renderer, String version, int firstGlError,
                             int framebufferStatus, int polygonMode, boolean depthWritesEnabled,
                             boolean geometryUploaded, boolean textureUploaded, int drawCalls,
                             boolean occlusionApplied) {
        private static Statistics empty() {
            return new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "unknown", "unknown", "unknown", GL_NO_ERROR,
                    org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE, GL_FILL, true,
                    false, false, 0, false);
        }

        public int renderedTriangles() {
            return renderedIndices / 3;
        }
    }

    private int drawBatches(GpuUploadPlan plan, List<GpuDrawCommand> commands,
                            List<Integer> orderedIndices, GpuCommandVisibility visibility,
                            CameraState camera, boolean alpha, int clientCycle) {
        int drawCalls = 0;
        int cursor = 0;
        while (cursor < orderedIndices.size()) {
            int firstIndex = orderedIndices.get(cursor);
            GpuDrawCommand first = commands.get(firstIndex);
            int end = cursor + 1;
            while (end < orderedIndices.size()) {
                GpuDrawCommand candidate = commands.get(orderedIndices.get(end));
                if (!sameDrawState(first, candidate, alpha)) break;
                end++;
            }
            applyDrawState(plan, first, alpha, clientCycle);
            int count = end - cursor;
            if (count == 1) {
                glDrawElements(GL_TRIANGLES, first.indexCount(), GL_UNSIGNED_INT,
                        (long) first.firstIndex() * Integer.BYTES);
            } else {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer counts = stack.mallocInt(count);
                    PointerBuffer offsets = stack.mallocPointer(count);
                    for (int i = cursor; i < end; i++) {
                        GpuDrawCommand command = commands.get(orderedIndices.get(i));
                        counts.put(command.indexCount());
                        offsets.put((long) command.firstIndex() * Integer.BYTES);
                    }
                    counts.flip();
                    offsets.flip();
                    glMultiDrawElements(GL_TRIANGLES, counts, GL_UNSIGNED_INT, offsets);
                }
            }
            drawCalls++;
            cursor = end;
        }
        return drawCalls;
    }

    private static boolean sameDrawState(GpuDrawCommand first, GpuDrawCommand candidate,
                                         boolean alpha) {
        return (candidate.pass() == (alpha ? GpuDrawCommand.SubmissionPass.ALPHA
                : GpuDrawCommand.SubmissionPass.OPAQUE))
                && first.textureId() == candidate.textureId()
                && first.layer() == candidate.layer()
                && first.depthBias() == candidate.depthBias()
                && first.renderMode() == candidate.renderMode();
    }

    private static long drawStateKey(GpuDrawCommand command, boolean alpha) {
        long key = command.textureId() + 1L;
        key = key * 17L + command.layer().ordinal();
        key = key * 257L + command.depthBias();
        key = key * 8L + command.renderMode().ordinal();
        return key * 2L + (alpha ? 1L : 0L);
    }

    private List<Integer> opaqueOrder(GpuUploadPlan plan, List<GpuDrawCommand> commands,
                                      GpuCommandVisibility visibility) {
        if (!visibility.occlusionApplied() && plan.fingerprint().equals(orderedPlanFingerprint)) {
            return cachedOpaqueOrder;
        }
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            if (commands.get(index).pass() == GpuDrawCommand.SubmissionPass.OPAQUE
                    && visibility.visible(index)) {
                result.add(index);
            }
        }
        // Opposite tie-break polarity from SoftwareSceneRenderer, on purpose.
        // The CPU renderer's depth test skips a fragment when
        // pixelDepth >= depth[offset], so the FIRST face drawn at a given
        // depth claims it and wins - it therefore sorts priority
        // descending (highest first) so higher priority wins coplanar ties.
        // This reversed-Z GPU path uses GL_GEQUAL, where an incoming
        // fragment at an EQUAL depth PASSES and overwrites - the LAST face
        // drawn at a given depth wins. To reach the same "higher priority
        // wins" visual result (verified by
        // SoftwareSceneRendererTest.higherPriorityCoplanarFaceWinsWithStableDepthBias
        // and required for wall decorations, which submissionPriority()
        // deliberately bumps to >=10 specifically so they beat their wall),
        // this path must sort priority ASCENDING so the higher-priority
        // face is drawn last and wins the GL_GEQUAL tie, not first and
        // loses it to the wall drawn after it.
        result.sort(Comparator.comparingInt((Integer index) -> commands.get(index).priority())
                .thenComparingLong(index -> drawStateKey(commands.get(index), false)));
        if (!visibility.occlusionApplied()) {
            orderedPlanFingerprint = plan.fingerprint();
            cachedOpaqueOrder = List.copyOf(result);
        }
        return result;
    }

    private void drawCommand(GpuUploadPlan plan, GpuDrawCommand command,
                             CameraState camera, boolean alpha, int clientCycle) {
        applyDrawState(plan, command, alpha, clientCycle);
        glDrawElements(GL_TRIANGLES, command.indexCount(), GL_UNSIGNED_INT,
                (long) command.firstIndex() * Integer.BYTES);
    }

    private void applyDrawState(GpuUploadPlan plan, GpuDrawCommand command,
                                boolean alpha, int clientCycle) {
        int isAlpha = alpha ? 1 : 0;
        int noDepth = command.renderMode().noDepth() ? 1 : 0;
        if (noDepth != lastNoDepth) {
            if (noDepth != 0) {
                glDisable(GL_DEPTH_TEST);
                glDepthMask(false);
            } else {
                glEnable(GL_DEPTH_TEST);
                glDepthMask(alpha ? false : true);
            }
            lastNoDepth = noDepth;
        }
        if (isAlpha != lastAlpha) {
            if (alpha) {
                glEnable(GL_BLEND);
                // Preserve destination alpha in the resolved scene texture.
                // ImGui composites that texture later, so plain glBlendFunc
                // would progressively erode alpha across overlapping water,
                // canopy, and other transparent surfaces.
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);
                glDepthMask(false);
            } else {
                glDisable(GL_BLEND);
                glDepthMask(noDepth == 0);
            }
            lastAlpha = isAlpha;
        }
        int layer = textureLayers.getOrDefault(command.textureId(), -1);
        int textured = command.textureId() < 0 ? 0 : 1;
        if (textured != lastTextured) {
            glUniform1i(texturedLocation, textured);
            lastTextured = textured;
        }
        int textureAvailable = layer < 0 ? 0 : 1;
        if (textureAvailable != lastTextureAvailable) {
            glUniform1i(textureAvailableLocation, textureAvailable);
            lastTextureAvailable = textureAvailable;
        }
        int textureMissing = command.textureId() >= 0 && layer < 0 ? 1 : 0;
        if (textureMissing != lastTextureMissing) {
            glUniform1i(textureMissingLocation, textureMissing);
            lastTextureMissing = textureMissing;
        }
        int isTerrain = command.layer() == SceneLayer.Kind.TERRAIN ? 1 : 0;
        if (isTerrain != lastTerrain) {
            glUniform1i(terrainLocation, isTerrain);
            lastTerrain = isTerrain;
        }
        int faceBias = command.depthBias();
        if (faceBias != lastFaceBias) {
            glUniform1f(faceBiasLocation, faceBias);
            lastFaceBias = faceBias;
        }
        RenderTextureResource resource = plan.textures().get(command.textureId());
        TextureAnimation.UvOffset animation = resource == null
                ? TextureAnimation.UvOffset.ZERO
                : TextureAnimation.offset(resource, clientCycle);
        if (animation.u() != lastTextureOffsetU || animation.v() != lastTextureOffsetV) {
            glUniform2f(textureOffsetLocation, animation.u(), animation.v());
            lastTextureOffsetU = animation.u();
            lastTextureOffsetV = animation.v();
        }
        int texLayer = Math.max(0, layer);
        if (texLayer != lastTextureLayer) {
            glUniform1i(textureLayerLocation, texLayer);
            lastTextureLayer = texLayer;
        }
        float[] scale = textureScales.getOrDefault(command.textureId(), new float[]{1.0f, 1.0f});
        if (scale[0] != lastTextureScaleX || scale[1] != lastTextureScaleY) {
            glUniform2f(textureScaleLocation, scale[0], scale[1]);
            lastTextureScaleX = scale[0];
            lastTextureScaleY = scale[1];
        }
    }

    /** Supplies the render-target status for the native acceptance diagnostics. */
    public void setFramebufferStatus(int status) {
        framebufferStatus = status;
    }

    private static int clientCycle() {
        // RuneLite advances graphicsCycle at approximately 50 client cycles
        // per second. Monotonic time keeps the native path animated without
        // coupling the renderer to editor command timing.
        return (int) ((System.nanoTime() / 1_000_000L) / 20L);
    }

    private static float averageDepth(GpuUploadPlan plan, GpuDrawCommand command,
                                      CameraState camera) {
        // The bounding-box center is a cheaper, more representative sort key
        // than the average of every vertex (also avoids re-deriving cos/sin
        // per vertex, which the previous version did) - reuses
        // SceneOcclusionResolver.CommandBounds instead of a second bespoke
        // per-vertex walk.
        SceneOcclusionResolver.CommandBounds bounds =
                SceneOcclusionResolver.CommandBounds.of(command, plan);
        float cosYaw = (float) Math.cos(camera.yaw());
        float sinYaw = (float) Math.sin(camera.yaw());
        float cosPitch = (float) Math.cos(camera.pitch());
        float sinPitch = (float) Math.sin(camera.pitch());
        float dx = bounds.centerX() - camera.x();
        // OSRS world Y is a down-axis: smaller values are higher terrain.
        // Convert to camera-up space before applying pitch, matching the CPU
        // reference renderer and the TSPS scene convention.
        float upDelta = camera.y() - bounds.centerY();
        float dz = bounds.centerZ() - camera.z();
        float yawDepth = dx * sinYaw + dz * cosYaw;
        return upDelta * sinPitch + yawDepth * cosPitch;
    }

    private void uploadGeometry(GpuUploadPlan plan) {
        FloatBuffer vertexData = BufferUtils.createFloatBuffer(plan.vertices().size() * FLOATS_PER_VERTEX);
        for (GpuSceneVertex vertex : plan.vertices()) {
            int rgb = packedColor(vertex);
            vertexData.put(vertex.x()).put(vertex.y()).put(vertex.z())
                    .put(vertex.u()).put(vertex.v()).put(vertex.encodedColor())
                    .put(vertex.alpha()).put(vertex.renderType())
                    .put(((rgb >>> 16) & 0xFF) / 255.0f)
                    .put(((rgb >>> 8) & 0xFF) / 255.0f)
                    .put((rgb & 0xFF) / 255.0f)
                    .put((float) vertex.priority());
        }
        vertexData.flip();
        IntBuffer indexData = BufferUtils.createIntBuffer(plan.indices().size());
        plan.indices().forEach(indexData::put);
        indexData.flip();
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertexData, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_STATIC_DRAW);
        glBindVertexArray(0);
        uploadedFingerprint = plan.fingerprint();
    }

    private static String textureFingerprint(Map<Integer, RenderTextureResource> resources) {
        StringBuilder value = new StringBuilder();
        resources.values().stream().sorted(Comparator.comparingInt(RenderTextureResource::id))
                .forEach(texture -> value.append(texture.id())
                        .append(':').append(texture.pixelStatus())
                        .append(':').append(texture.width()).append('x').append(texture.height())
                        .append(':').append(java.util.Arrays.hashCode(texture.pixels())).append('|'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private void uploadTextureArray(Map<Integer, RenderTextureResource> resources) {
        if (textureArray != 0) org.lwjgl.opengl.GL11.glDeleteTextures(textureArray);
        textureLayers.clear();
        textureScales.clear();
        List<RenderTextureResource> available = resources.values().stream()
                .filter(RenderTextureResource::hasGpuPixels)
                .sorted(Comparator.comparingInt(RenderTextureResource::id))
                .toList();
        // RuneLite's texture provider exposes a fixed 128x128 texture array
        // indexed by the cache texture id. Keeping that contract here avoids
        // compact-layer remapping and prevents 1x1 fallbacks from being
        // surrounded by zero-filled mip padding.
        int width = TEXTURE_SIZE;
        int height = TEXTURE_SIZE;
        textureArray = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
        // GL_TEXTURE_2D_ARRAY, glTexImage3D, and nearest/clamp sampling are
        // all available in the OpenGL 3.3 baseline; no GL 4.x path is needed.
        // Match the client baseline first. Mipmapping can be added after
        // parity is proven; it is not safe while fallback layers can have a
        // different source dimension.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        // Keep a real sampler2DArray bound even when the cache provider only
        // supplied texture metadata. macOS validates sampler targets at draw
        // time, so binding texture 0 here makes every textured command emit
        // the "texture unloadable" warning and can turn the whole textured
        // path into undefined output. The shader still receives
        // uTextureAvailable=0 for these commands and uses its explicit
        // lightness fallback instead of sampling this diagnostic layer.
        int largestTextureId = available.stream().mapToInt(RenderTextureResource::id)
                .max().orElse(-1);
        int depth = Math.max(TEXTURE_LAYER_CAPACITY, largestTextureId + 1);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, width, height, depth,
                0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        if (available.isEmpty()) {
            ByteBuffer fallback = BufferUtils.createByteBuffer(4)
                    .put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
            fallback.flip();
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, 0, 1, 1, 1,
                    GL_RGBA, GL_UNSIGNED_BYTE, fallback);
            return;
        }
        for (RenderTextureResource resource : available) {
            int[] source = resource.pixels();
            ByteBuffer pixels = BufferUtils.createByteBuffer(TEXTURE_SIZE * TEXTURE_SIZE * 4);
            for (int y = 0; y < TEXTURE_SIZE; y++) {
                int sourceY = Math.min(resource.height() - 1,
                        y * resource.height() / TEXTURE_SIZE);
                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    int sourceX = Math.min(resource.width() - 1,
                            x * resource.width() / TEXTURE_SIZE);
                    int rgb = source[sourceY * resource.width() + sourceX] & 0xFFFFFF;
                    // Real cache pixels use RGB zero as the transparent texel.
                    // Average-color fallbacks are explicit opaque material
                    // colors even when their average happens to be black.
                    int alpha = resource.pixelStatus() == RenderTextureResource.PixelStatus.AVAILABLE
                            && rgb == 0 ? 0x00 : 0xFF;
                    pixels.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8))
                            .put((byte) rgb).put((byte) alpha);
                }
            }
            pixels.flip();
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, resource.id(),
                    TEXTURE_SIZE, TEXTURE_SIZE, 1,
                    GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            textureLayers.put(resource.id(), resource.id());
            textureScales.put(resource.id(), new float[]{1.0f, 1.0f});
        }
    }

    private static int packedColor(GpuSceneVertex vertex) {
        int rgb = vertex.colorEncoding() == GpuColorEncoding.PACKED_JAGEX_HSL
                ? OsrsTerrainColorMath.packedHslToRgb(vertex.encodedColor(), 0.6)
                : 0;
        // This helper is expanded by the caller below; keeping the conversion
        // here avoids ever feeding packed HSL into a native interpolation.
        return rgb;
    }

    @Override
    public void close() {
        if (textureArray != 0) org.lwjgl.opengl.GL11.glDeleteTextures(textureArray);
        textureArray = 0;
        textureLayers.clear();
        textureScales.clear();
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
        if (program != 0) org.lwjgl.opengl.GL20.glDeleteProgram(program);
        if (vertexBuffer != 0) org.lwjgl.opengl.GL15.glDeleteBuffers(vertexBuffer);
        if (indexBuffer != 0) org.lwjgl.opengl.GL15.glDeleteBuffers(indexBuffer);
        vertexArray = vertexBuffer = indexBuffer = program = 0;
        uploadedFingerprint = null;
        uploadedTextureFingerprint = null;
        diagnosticsLogged = false;
    }

    private static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        org.lwjgl.opengl.GL20.glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("OpenGL program link failed: " + glGetProgramInfoLog(program));
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("OpenGL shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static final String VERTEX_SHADER_SOURCE = """
            #version 330 core
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in float aEncodedColor;
            layout(location = 3) in float aAlpha;
            layout(location = 4) in float aRenderType;
            layout(location = 5) in vec3 aColor;
            layout(location = 6) in float aPriority;
            uniform vec3 uCamera;
            uniform float uPitch;
            uniform float uYaw;
            uniform float uFocal;
            uniform float uAspect;
            uniform float uDepthA;
            uniform float uDepthB;
            uniform float uFaceBias;
            uniform int uUseFog;
            uniform float uFogWest;
            uniform float uFogEast;
            uniform float uFogSouth;
            uniform float uFogNorth;
            uniform float uFogDepth;
            out vec2 vUv;
            noperspective out float vEncodedColor;
            out float vAlpha;
            out float vRenderType;
            out vec3 vColor;
            out float vFogAmount;
            void main() {
                vec3 d = aPosition - uCamera;
                float cy = cos(uYaw), sy = sin(uYaw);
                float x = d.x * cy - d.z * sy;
                float forward = d.x * sy + d.z * cy;
                float cp = cos(uPitch), sp = sin(uPitch);
                // The cache stores higher OSRS terrain with a smaller (more
                // negative) world-Y value. Negate that down-axis before the
                // camera pitch rotation; otherwise slopes render backwards.
                float up = -d.y;
                float y = up * cp - forward * sp;
                float depth = up * sp + forward * cp;
                // The real client subtracts faceBias * 2 from the vertex's
                // VIEW-SPACE depth, in world units, and applies it to the
                // depth value only - screen x/y come from the unbiased
                // divisor (Model.java: field3037[v] - faceBias * 2, while
                // modelViewportXs/Ys divide by the raw field3037). So keep w
                // at the true depth and rewrite z so that, after the
                // perspective divide, z_ndc equals uDepthA + uDepthB /
                // biasedDepth. A clip-space "z += bias / 128" instead makes
                // the offset shrink with proximity, which is why flush wall
                // decorations z-fought their wall when zoomed in.
                float biasedDepth = max(depth - uFaceBias * 2.0, 1.0);
                vec4 projected = vec4(uFocal / uAspect * x, uFocal * y,
                                      uDepthA * depth + uDepthB * (depth / biasedDepth),
                                      depth);
                gl_Position = projected;
                vUv = aUv;
                vEncodedColor = aEncodedColor;
                vAlpha = aAlpha;
                vRenderType = aRenderType;
                vColor = aColor;
                if (uUseFog == 0 || uFogDepth <= 0.0) {
                    vFogAmount = 0.0;
                } else {
                    float xDistance = min(aPosition.x - uFogWest, uFogEast - aPosition.x);
                    float zDistance = min(aPosition.z - uFogSouth, uFogNorth - aPosition.z);
                    float nearest = min(xDistance, zDistance);
                    float second = max(xDistance, zDistance);
                    float rounding = 192.0;
                    float distance = nearest - rounding * max(0.0,
                            (nearest + rounding * rounding)
                                    / (second + rounding * rounding));
                    vFogAmount = 1.0 - clamp(distance / uFogDepth, 0.0, 1.0);
                }
            }
            """;

    private static final String FRAGMENT_SHADER_SOURCE = """
            #version 330 core
            in vec2 vUv;
            noperspective in float vEncodedColor;
            in float vAlpha;
            in float vRenderType;
            in vec3 vColor;
            in float vFogAmount;
            uniform sampler2DArray uTexture;
            uniform int uTextured;
            uniform int uTextureAvailable;
            uniform int uTextureMissing;
            uniform int uTerrain;
            uniform vec2 uTextureOffset;
            uniform int uTextureLayer;
            uniform vec2 uTextureScale;
            uniform float uBrightness;
            uniform float uExposure;
            uniform int uSmoothBanding;
            uniform vec3 uFogColor;
            out vec4 outColor;
            float hueChannel(float lower, float upper, float hue) {
                if (hue > 1.0) hue -= 1.0;
                if (hue < 0.0) hue += 1.0;
                if (6.0 * hue < 1.0) return lower + (upper - lower) * 6.0 * hue;
                if (2.0 * hue < 1.0) return upper;
                if (3.0 * hue < 2.0) return lower + (upper - lower) * (2.0 / 3.0 - hue) * 6.0;
                return lower;
            }
            vec3 packedHslToRgb(float packed) {
                float hueBand = float((int(packed) >> 10) & 63);
                float saturationBand = float((int(packed) >> 7) & 7);
                float lightness = float(int(packed) & 127);
                float hue = hueBand / 64.0 + 0.0078125;
                float saturation = saturationBand / 8.0 + 0.0625;
                float luminance = lightness / 128.0;
                float upper = luminance < 0.5
                        ? luminance * (1.0 + saturation)
                        : luminance + saturation - luminance * saturation;
                float lower = 2.0 * luminance - upper;
                vec3 rgb = vec3(hueChannel(lower, upper, hue + 1.0 / 3.0),
                        hueChannel(lower, upper, hue),
                        hueChannel(lower, upper, hue - 1.0 / 3.0));
                return pow(rgb, vec3(0.6));
            }
            void main() {
                vec3 color;
                if (uTextured != 0) {
                    // RuneLite's frag.glsl divides textured-face lightness by
                    // 127, not 128; align the constant exactly.
                    float lightness = clamp(vEncodedColor / 127.0, 0.0, 1.0);
                    vec2 textureUv = vUv + uTextureOffset;
                    if (uTerrain != 0 || uTextureOffset.x != 0.0 || uTextureOffset.y != 0.0) {
                        textureUv = fract(textureUv);
                    }
                    if (uTextureAvailable != 0) {
                        vec4 texel = texture(uTexture,
                                vec3(textureUv * uTextureScale, float(uTextureLayer)));
                        // RuneScape's indexed texture convention marks the
                        // transparent texel via alpha=0 at upload
                        // (uploadTextureArray), matching RuneLite's
                        // TextureManager. Testing alpha instead of rgb==0
                        // lets legitimately black texels render instead of
                        // becoming holes, and stays correct once mipmapping
                        // blends alpha near cutout edges.
                        if (texel.a < 1.0) discard;
                        color = texel.rgb * lightness;
                    } else if (uTextureMissing != 0) {
                        // Missing texture definitions are a cache/contract
                        // failure, not a valid grayscale material. Make the
                        // problem visible in the viewport and diagnostics.
                        color = vec3(1.0, 0.0, 1.0) * lightness;
                    } else {
                        color = vec3(clamp(vEncodedColor / 64.0, 0.0, 1.0));
                    }
                } else {
                    // RuneLite's frag.glsl picks between two HSL
                    // interpolation strategies via a "smooth banding" mix,
                    // not a single fixed one: interpolating pre-decoded RGB
                    // (vColor, decoded once per vertex at upload) is the
                    // client's classic/default look and can band across
                    // large faces; interpolating the packed HSL integer
                    // itself and decoding it per pixel (packedHslToRgb of
                    // the noperspective-interpolated vEncodedColor) removes
                    // that banding, matching RuneLite's smoothBanding=on
                    // path exactly (uSmoothBanding was previously declared
                    // but never read here, so the setting did nothing).
                    color = mix(vColor, packedHslToRgb(vEncodedColor), float(uSmoothBanding));
                }
                float alpha = uTerrain != 0 ? clamp(vAlpha / 255.0, 0.0, 1.0)
                        : (vRenderType > 2.5 ? 0.5 : 1.0 - clamp(vAlpha / 255.0, 0.0, 1.0));
                color = clamp(color * uBrightness * exp2(uExposure), 0.0, 1.0);
                color = mix(color, uFogColor, vFogAmount);
                outColor = vec4(color, alpha);
            }
            """;
}
