package com.rspsi.renderer.opengl;

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
import com.rspsi.editor.render.OcclusionPlanFilter;
import com.rspsi.editor.render.TextureAnimation;
import com.rspsi.editor.render.RsFaceOrderPlanner;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

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
import static org.lwjgl.opengl.GL11.GL_LESS;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
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
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
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
    private static final float FOV_Y = (float) Math.toRadians(50.0);
    private static final float NEAR = 1.0f;
    private static final float FAR = 200000.0f;

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
    private Statistics statistics = Statistics.empty();
    private boolean diagnosticsLogged;

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
        glDepthFunc(GL_LESS);
        glDisable(GL_BLEND);
        glDepthMask(true);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        // The CPU reference renderer does not cull triangles, and the
        // winding contract is not yet parity-verified for every OSRS model
        // and shaped-tile family. Keep both sides visible in the Phase 0
        // native baseline; culling returns only with measured evidence.
        glDisable(GL_CULL_FACE);
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
        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        final GpuUploadPlan renderPlan = plan == null ? null : OcclusionPlanFilter.filter(plan, camera);
        if (renderPlan == null || renderPlan.vertices().isEmpty() || renderPlan.indices().isEmpty()) {
            statistics = statisticsFor(plan, renderPlan);
            captureGlError();
            return;
        }
        if (!renderPlan.fingerprint().equals(uploadedFingerprint)) uploadGeometry(renderPlan);
        String textureFingerprint = textureFingerprint(renderPlan.textures());
        if (!textureFingerprint.equals(uploadedTextureFingerprint)) {
            uploadTextureArray(renderPlan.textures());
            uploadedTextureFingerprint = textureFingerprint;
        }

        glUseProgram(program);
        glUniform3f(cameraLocation, camera.x(), camera.y(), camera.z());
        glUniform1f(pitchLocation, camera.pitch());
        glUniform1f(yawLocation, camera.yaw());
        glUniform1f(focalLocation, (float) (1.0 / Math.tan(FOV_Y * 0.5)));
        glUniform1f(aspectLocation, (float) width / height);
        glUniform1f(depthALocation, (FAR + NEAR) / (FAR - NEAR));
        glUniform1f(depthBLocation, -2.0f * FAR * NEAR / (FAR - NEAR));
        glUniform1f(brightnessLocation, (float) presentation.brightness());
        glUniform1f(exposureLocation, (float) presentation.exposure());
        glUniform1i(smoothBandingLocation, presentation.smoothBanding() ? 1 : 0);
        SceneFog.Bounds fogBounds = SceneFog.bounds(renderPlan);
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
        for (GpuDrawCommand command : renderPlan.commands()) {
            if (command.pass() == GpuDrawCommand.SubmissionPass.OPAQUE) {
                drawCommand(renderPlan, command, camera, false, clientCycle);
            }
        }
        // The software reference renderer composites transparent triangles
        // back-to-front. Keep opaque submission order stable, but apply the
        // same depth ordering to alpha ranges in the native backend.
        List<GpuDrawCommand> alpha = new ArrayList<>();
        for (GpuDrawCommand command : renderPlan.commands()) {
            if (command.pass() == GpuDrawCommand.SubmissionPass.ALPHA) alpha.add(command);
        }
        alpha = RsFaceOrderPlanner.orderAlpha(alpha,
                command -> averageDepth(renderPlan, command, camera));
        for (GpuDrawCommand command : alpha) drawCommand(renderPlan, command, camera, true, clientCycle);
        glBindVertexArray(0);
        glUseProgram(0);
        statistics = statisticsFor(plan, renderPlan);
        captureGlError();
        if (!diagnosticsLogged) {
            LOGGER.info("Native OpenGL {} / {} / {}; source={} vertices, rendered={} triangles, "
                            + "textures decoded={} fallback={} unavailable={}, firstGLerror={}",
                    statistics.vendor(), statistics.renderer(), statistics.version(),
                    statistics.sourceVertices(), statistics.renderedTriangles(),
                    statistics.decodedTextures(), statistics.fallbackTextures(),
                    statistics.unavailableTextures(), statistics.firstGlError());
            diagnosticsLogged = true;
        }
    }

    public Statistics statistics() {
        return statistics;
    }

    private Statistics statisticsFor(GpuUploadPlan source, GpuUploadPlan rendered) {
        int sourceVertices = source == null ? 0 : source.vertices().size();
        int sourceIndices = source == null ? 0 : source.indices().size();
        int renderedIndices = rendered == null ? 0 : rendered.indices().size();
        int terrainTriangles = 0;
        int objectTriangles = 0;
        if (rendered != null) {
            for (GpuDrawCommand command : rendered.commands()) {
                int triangles = command.indexCount() / 3;
                if (command.layer() == SceneLayer.Kind.TERRAIN) terrainTriangles += triangles;
                else objectTriangles += triangles;
            }
        }
        int decoded = 0;
        int fallback = 0;
        int unavailable = 0;
        if (source != null) {
            for (RenderTextureResource resource : source.textures().values()) {
                switch (resource.pixelStatus()) {
                    case AVAILABLE -> decoded++;
                    case AVERAGE_COLOR_FALLBACK -> fallback++;
                    case UNAVAILABLE, INVALID -> unavailable++;
                }
            }
        }
        return new Statistics(sourceVertices, sourceIndices, renderedIndices,
                terrainTriangles, objectTriangles, decoded, fallback, unavailable,
                safeGlString(GL_VENDOR), safeGlString(GL_RENDERER), safeGlString(GL_VERSION),
                firstGlError);
    }

    private void captureGlError() {
        int error = glGetError();
        if (firstGlError == GL_NO_ERROR && error != GL_NO_ERROR) firstGlError = error;
    }

    private static String safeGlString(int name) {
        String value = glGetString(name);
        return value == null ? "unknown" : value;
    }

    public record Statistics(int sourceVertices, int sourceIndices, int renderedIndices,
                             int terrainTriangles, int objectTriangles,
                             int decodedTextures, int fallbackTextures, int unavailableTextures,
                             String vendor, String renderer, String version, int firstGlError) {
        private static Statistics empty() {
            return new Statistics(0, 0, 0, 0, 0, 0, 0, 0,
                    "unknown", "unknown", "unknown", GL_NO_ERROR);
        }

        public int renderedTriangles() {
            return renderedIndices / 3;
        }
    }

    private void drawCommand(GpuUploadPlan plan, GpuDrawCommand command,
                             CameraState camera, boolean alpha, int clientCycle) {
        if (alpha) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);
        } else {
            glDisable(GL_BLEND);
            glDepthMask(true);
        }
        int layer = textureLayers.getOrDefault(command.textureId(), -1);
        glUniform1i(texturedLocation, command.textureId() < 0 ? 0 : 1);
        glUniform1i(textureAvailableLocation, layer < 0 ? 0 : 1);
        glUniform1i(terrainLocation, command.layer() == SceneLayer.Kind.TERRAIN ? 1 : 0);
        // Keep the cache's raw 0..255 face bias intact at the Java/GLSL
        // boundary. The vertex shader applies the RuneScape /128 scale once;
        // normalizing here would silently weaken the bias by another 128x.
        glUniform1f(faceBiasLocation, command.depthBias());
        RenderTextureResource resource = plan.textures().get(command.textureId());
        TextureAnimation.UvOffset animation = resource == null
                ? TextureAnimation.UvOffset.ZERO
                : TextureAnimation.offset(resource, clientCycle);
        glUniform2f(textureOffsetLocation, animation.u(), animation.v());
        float[] scale = textureScales.getOrDefault(command.textureId(), new float[]{1.0f, 1.0f});
        glUniform1i(textureLayerLocation, Math.max(0, layer));
        glUniform2f(textureScaleLocation, scale[0], scale[1]);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
        glDrawElements(GL_TRIANGLES, command.indexCount(), GL_UNSIGNED_INT,
                (long) command.firstIndex() * Integer.BYTES);
    }

    private static int clientCycle() {
        // RuneLite advances graphicsCycle at approximately 50 client cycles
        // per second. Monotonic time keeps the native path animated without
        // coupling the renderer to editor command timing.
        return (int) ((System.nanoTime() / 1_000_000L) / 20L);
    }

    private static float averageDepth(GpuUploadPlan plan, GpuDrawCommand command,
                                      CameraState camera) {
        float total = 0.0f;
        int count = 0;
        for (int offset = command.firstIndex(); offset < command.firstIndex() + command.indexCount(); offset++) {
            GpuSceneVertex vertex = plan.vertices().get(plan.indices().get(offset));
            float dx = vertex.x() - camera.x();
            float dy = vertex.y() - camera.y();
            float dz = vertex.z() - camera.z();
            float cosYaw = (float) Math.cos(camera.yaw());
            float sinYaw = (float) Math.sin(camera.yaw());
            float yawDepth = dx * sinYaw + dz * cosYaw;
            float cosPitch = (float) Math.cos(camera.pitch());
            float sinPitch = (float) Math.sin(camera.pitch());
            total += dy * sinPitch + yawDepth * cosPitch;
            count++;
        }
        return count == 0 ? Float.NEGATIVE_INFINITY : total / count;
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
        int width = available.stream().mapToInt(RenderTextureResource::width).max().orElse(1);
        int height = available.stream().mapToInt(RenderTextureResource::height).max().orElse(1);
        textureArray = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
        // GL_TEXTURE_2D_ARRAY, glTexImage3D, and nearest/clamp sampling are
        // all available in the OpenGL 3.3 baseline; no GL 4.x path is needed.
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
        int depth = Math.max(1, available.size());
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
        for (int layer = 0; layer < available.size(); layer++) {
            RenderTextureResource resource = available.get(layer);
            ByteBuffer pixels = BufferUtils.createByteBuffer(resource.width() * resource.height() * 4);
            for (int value : resource.pixels()) {
                int rgb = value & 0xFFFFFF;
                pixels.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8))
                        // Texture pixels are opaque client RGB data. Model
                        // face transparency is uploaded separately.
                        .put((byte) rgb).put((byte) 0xFF);
            }
            pixels.flip();
            glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer,
                    resource.width(), resource.height(), 1,
                    GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            textureLayers.put(resource.id(), layer);
            textureScales.put(resource.id(), new float[]{
                    (float) resource.width() / width,
                    (float) resource.height() / height});
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
            float priorityBand(float priority) {
                priority = clamp(priority, 0.0, 11.0);
                if (priority <= 3.0) return priority;
                if (priority <= 7.0) return 4.0 + floor((priority - 4.0) / 2.0);
                return 6.0 + min(1.0, floor((priority - 8.0) / 2.0));
            }
            void main() {
                vec3 d = aPosition - uCamera;
                float cy = cos(uYaw), sy = sin(uYaw);
                float x = d.x * cy - d.z * sy;
                float forward = d.x * sy + d.z * cy;
                float cp = cos(uPitch), sp = sin(uPitch);
                float y = d.y * cp - forward * sp;
                float depth = d.y * sp + forward * cp;
                vec4 projected = vec4(uFocal / uAspect * x, uFocal * y,
                                      uDepthA * depth + uDepthB, depth);
                float band = priorityBand(aPriority);
                float bias = band * 0.015;
                if (band == 7.0) bias += 0.01;
                projected.z -= (bias + uFaceBias / 128.0) * projected.w;
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
                    float lightness = clamp(vEncodedColor / 128.0, 0.0, 1.0);
                    vec2 textureUv = vUv + uTextureOffset;
                    if (uTextureOffset.x != 0.0 || uTextureOffset.y != 0.0) {
                        textureUv = fract(textureUv);
                    }
                    if (uTextureAvailable != 0) {
                        vec4 texel = texture(uTexture,
                                vec3(textureUv * uTextureScale, float(uTextureLayer)));
                        // RuneScape's indexed texture convention uses packed
                        // RGB zero as the transparent texel. The upload keeps
                        // texture alpha opaque because model-face alpha is a
                        // separate channel, so reproduce the client mask here.
                        if (texel.r == 0.0 && texel.g == 0.0 && texel.b == 0.0) discard;
                        color = texel.rgb * lightness;
                    } else {
                        color = vec3(clamp(vEncodedColor / 64.0, 0.0, 1.0));
                    }
                } else {
                    // RuneLite's default smooth-banding mode interpolates the
                    // packed HSL value without perspective correction and
                    // converts it per fragment. The alternate path preserves
                    // the vertex-RGB behavior used by the enhanced profile.
                    color = uSmoothBanding != 0 ? packedHslToRgb(vEncodedColor) : vColor;
                }
                float alpha = uTerrain != 0 ? clamp(vAlpha / 255.0, 0.0, 1.0)
                        : (vRenderType > 2.5 ? 0.5 : 1.0 - clamp(vAlpha / 255.0, 0.0, 1.0));
                color = clamp(color * uBrightness * exp2(uExposure), 0.0, 1.0);
                color = mix(color, uFogColor, vFogAmount);
                outColor = vec4(color, alpha);
            }
            """;
}
