package com.rspsi.renderer.opengl;

import com.rspsi.editor.render.BackfacePolicy;
import com.rspsi.editor.render.GpuCommandGeometry;
import com.rspsi.editor.render.GpuCommandVisibility;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuDrawBatchPlanner;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.SceneLayer;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuColorEncoding;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.RenderOrderKey;
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
import static org.lwjgl.opengl.GL11.GL_CW;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FILL;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL11.GL_LINE;
import static org.lwjgl.opengl.GL11.GL_GEQUAL;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_NEAREST_MIPMAP_LINEAR;
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
import static org.lwjgl.opengl.GL30.GL_TEXTURE1;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
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
    /**
     * Per-bias-step nudge added to clip Z, in normalised depth units.
     *
     * <p>The view-space face bias is the client's rule and is what separates
     * coplanar surfaces close to the camera, but expressed in the depth buffer
     * its effect is about {@code 2 * uDepthB * bias / depth^2} - roughly 530
     * units of a 24-bit buffer at 1,000 units of depth, about 5 at 10,000, and
     * below one at 20,000, which is where coplanar terrain overlays and wall
     * decorations start to speckle. 1e-5 of normalised depth is around 80
     * depth-buffer units and stays far below one world unit of real separation
     * at any camera distance, so it can never reorder surfaces that are
     * genuinely apart.</p>
     */
    private static final float DEPTH_BIAS_NUDGE = 1e-5f;

    /** Native model-facing modes. Terrain remains two-sided in every mode. */
    public static final int CULL_OFF = 0;
    public static final int CULL_FRONT_CCW = 1;
    public static final int CULL_FRONT_CW = 2;

    /**
     * Native state implied by one RuneLite-style submission pass and render
     * mode. Sorted versus unsorted affects command ordering upstream; the
     * OpenGL depth/blend contract differs only for no-depth modes and alpha.
     */
    static NativeDrawState nativeDrawState(GpuDrawCommand.SubmissionPass pass,
                                           GpuDrawCommand.RenderMode renderMode) {
        if (pass == null || renderMode == null) {
            throw new IllegalArgumentException("Native draw state requires pass and render mode");
        }
        boolean alpha = pass == GpuDrawCommand.SubmissionPass.ALPHA;
        boolean depthTest = !renderMode.noDepth();
        return new NativeDrawState(depthTest, depthTest && !alpha, alpha);
    }

    record NativeDrawState(boolean depthTest, boolean depthWrite, boolean blend) {
    }

    private int lastAlpha = -1;
    private int lastTextured = -1;
    private int lastTextureAvailable = -1;
    private int lastTextureMissing = -1;
    private int lastTerrain = -1;
    private int lastCull = -1;
    private int cullMode = CULL_FRONT_CCW;
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
        lastCull = -1;
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
    private int depthBiasNudgeLocation;
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
    private final ZoneVboManager zoneManager = new ZoneVboManager();
    private int paletteTexture;
    private int paletteLocation;
    private String uploadedFingerprint;
    private String uploadedTextureFingerprint;
    private Map<Integer, RenderTextureResource> fingerprintedTextureResources;
    private String fingerprintedTextureFingerprint;
    private GpuCommandGeometry fogBoundsGeometry;
    private SceneFog.Bounds cachedFogBounds;
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
    private final ArrayList<GpuDrawCommand> alphaCommands = new ArrayList<>();
    private final java.util.IdentityHashMap<GpuDrawCommand, Integer> alphaIndices =
            new java.util.IdentityHashMap<>();
    private final ArrayList<Integer> alphaOrder = new ArrayList<>();
    private final RsFaceOrderPlanner.Workspace alphaOrderWorkspace =
            new RsFaceOrderPlanner.Workspace();
    private final java.util.HashSet<Integer> missingTextureIds = new java.util.HashSet<>();
    private String glVendor = "unknown";
    private String glRenderer = "unknown";
    private String glVersion = "unknown";

    public void initialize() {
        GLCapabilities capabilities = GL.createCapabilities();
        glVendor = safeGlString(GL_VENDOR);
        glRenderer = safeGlString(GL_RENDERER);
        glVersion = safeGlString(GL_VERSION);
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
        depthBiasNudgeLocation = glGetUniformLocation(program, "uDepthBiasNudge");
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
        paletteLocation = glGetUniformLocation(program, "uPalette");
        glUseProgram(program);
        glUniform1i(textureLocation, 0);
        glUniform1i(paletteLocation, 1);
        glUseProgram(0);
        uploadPaletteTexture();
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GEQUAL);
        glClearDepth(0.0);
        glDisable(GL_BLEND);
        glDepthMask(true);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        // Client model faces use the verified GL_CCW mapping. Terrain does not
        // share Model.draw0's facing contract and remains two-sided per draw.
        glClearColor(0.063f, 0.094f, 0.153f, 1.0f);
        captureGlError();
    }

    private void uploadPaletteTexture() {
        if (paletteTexture != 0) {
            org.lwjgl.opengl.GL11.glDeleteTextures(paletteTexture);
        }
        paletteTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, paletteTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        ByteBuffer paletteBuffer = BufferUtils.createByteBuffer(256 * 256 * 4);
        for (int hsl = 0; hsl < 65536; hsl++) {
            int rgb = OsrsTerrainColorMath.packedHslToRgb(hsl, 0.6);
            if (rgb == OsrsTerrainColorMath.INVALID_HSL_COLOR) {
                rgb = 0;
            }
            paletteBuffer.put((byte) ((rgb >>> 16) & 0xFF));
            paletteBuffer.put((byte) ((rgb >>> 8) & 0xFF));
            paletteBuffer.put((byte) (rgb & 0xFF));
            paletteBuffer.put((byte) 0xFF);
        }
        paletteBuffer.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, paletteBuffer);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void draw(GpuUploadPlan plan, CameraState camera, int width, int height) {
        draw(plan, null, camera, width, height, RenderPresentation.neutral());
    }

    public void draw(GpuUploadPlan plan, CameraState camera, int width, int height,
              RenderPresentation presentation) {
        draw(plan, null, camera, width, height, presentation, clientCycle());
    }

    public void draw(GpuUploadPlan plan, CameraState camera, int width, int height,
              RenderPresentation presentation, int clientCycle) {
        draw(plan, null, camera, width, height, presentation, clientCycle);
    }

    public void draw(GpuUploadPlan plan, GpuZonedUploadPlan zonedPlan,
                     CameraState camera, int width, int height,
                     RenderPresentation presentation) {
        draw(plan, zonedPlan, camera, width, height, presentation, clientCycle());
    }

    public void draw(GpuUploadPlan plan, GpuZonedUploadPlan zonedPlan,
                     CameraState camera, int width, int height,
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
        if (cullMode != CULL_OFF) {
            glFrontFace(cullMode == CULL_FRONT_CW ? GL_CW : GL_CCW);
            glCullFace(GL_BACK);
        }
        // Begin each frame two-sided. applyDrawState() enables culling for
        // model commands and turns it back off for terrain. Client-front is
        // GL_CCW; reversed winding and fully two-sided rendering remain
        // explicit diagnostics.
        //
        // The earlier global GL_CW experiment produced see-through walls,
        // missing roofs and bridge regressions. RuneLite-melxin Model.draw0
        // shows that visible client faces use edge > 0, which maps to GL_CCW
        // after the Y-down software viewport -> Y-up OpenGL conversion.
        glDisable(GL_CULL_FACE);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        glClearDepth(0.0);
        lastFrameDepthWrites = true;
        lastFramePolygonMode = presentation.wireframe() ? GL_LINE : GL_FILL;
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (plan == null) {
            statistics = statisticsFor(null, null, null,
                    false, false, 0, 0, 0);
            captureGlError();
            return;
        }
        boolean zonedGeometryActive = zonedPlan != null
                && plan.fingerprint().equals(zonedPlan.sourceFingerprint());
        GpuCommandGeometry runtimeGeometry = zonedGeometryActive ? zonedPlan : plan;
        if (runtimeGeometry.vertexCount() == 0 || runtimeGeometry.indexCount() == 0) {
            statistics = statisticsFor(plan, runtimeGeometry, null,
                    false, false, 0, 0, 0);
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
        int gpuZoneUploads = 0;
        int gpuReusedAllocations = 0;
        if (!plan.fingerprint().equals(uploadedFingerprint)) {
            if (zonedGeometryActive) {
                zoneManager.upload(zonedPlan);
            } else {
                zoneManager.upload(plan);
            }
            gpuZoneUploads = zoneManager.dirtyZonesUploadedCount();
            gpuReusedAllocations = zoneManager.reusedAllocationsCount();
            geometryUploaded = gpuZoneUploads > 0;
            uploadedFingerprint = plan.fingerprint();
            orderedPlanFingerprint = null;
        }
        String textureFingerprint = textureFingerprintCached(plan.textures());
        if (!textureFingerprint.equals(uploadedTextureFingerprint)) {
            uploadTextureArray(plan.textures());
            uploadedTextureFingerprint = textureFingerprint;
            textureUploaded = true;
        }
        GpuCommandVisibility visibility =
                GpuCommandVisibility.of(runtimeGeometry, camera, plan.occluders(),
                        plan.sceneWindow());

        glUseProgram(program);
        glUniform3f(cameraLocation, camera.x(), camera.y(), camera.z());
        glUniform1f(pitchLocation, camera.pitch());
        glUniform1f(yawLocation, camera.yaw());
        glUniform1f(focalLocation, (float) (1.0 / Math.tan(FOV_Y * 0.5)));
        glUniform1f(aspectLocation, (float) width / height);
        glUniform1f(depthALocation, -(FAR + NEAR) / (FAR - NEAR));
        glUniform1f(depthBLocation, 2.0f * FAR * NEAR / (FAR - NEAR));
        glUniform1f(depthBiasNudgeLocation, DEPTH_BIAS_NUDGE);
        glUniform1f(brightnessLocation, (float) presentation.brightness());
        glUniform1f(exposureLocation, (float) presentation.exposure());
        glUniform1i(smoothBandingLocation, presentation.smoothBanding() ? 1 : 0);
        boolean fogEnabled = presentation.fogDepthTiles() > 0;
        glUniform1i(useFogLocation, fogEnabled ? 1 : 0);
        if (fogEnabled) {
            SceneFog.Bounds fogBounds = fogBounds(runtimeGeometry);
            glUniform1f(fogWestLocation, fogBounds.minX());
            glUniform1f(fogEastLocation, fogBounds.maxX());
            glUniform1f(fogSouthLocation, fogBounds.minZ());
            glUniform1f(fogNorthLocation, fogBounds.maxZ());
        }
        glUniform1f(fogDepthLocation, presentation.fogDepthTiles() * 128.0f);
        glUniform3f(fogColorLocation, ((presentation.fogColor() >>> 16) & 0xFF) / 255.0f,
                ((presentation.fogColor() >>> 8) & 0xFF) / 255.0f,
                (presentation.fogColor() & 0xFF) / 255.0f);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, paletteTexture);
        resetDrawState();
        int drawCalls = 0;
        List<GpuDrawCommand> commands = plan.commands();
        // RuneLite uses GL_LEQUAL for its forward-Z native path.  This
        // reversed-Z path uses the equivalent GL_GEQUAL so exactly-coplanar
        // wall trim/decor faces can be resolved by the OSRS submission order
        // instead of failing a strict depth test. List.sort is stable, so
        // indices are only reordered relative to distinct priority values.
        List<Integer> opaqueOrder = opaqueOrder(plan, commands, visibility, camera);
        drawCalls += drawBatches(plan, commands, opaqueOrder, visibility, camera, false, clientCycle);
        // The software reference renderer composites transparent triangles
        // back-to-front. Keep opaque submission order stable, but apply the
        // same depth ordering to alpha ranges in the native backend.
        alphaCommands.clear();
        alphaIndices.clear();
        alphaOrder.clear();
        for (int index = 0; index < commands.size(); index++) {
            GpuDrawCommand command = commands.get(index);
            if (command.pass() == GpuDrawCommand.SubmissionPass.ALPHA
                    && visibility.visible(index)) {
                alphaCommands.add(command);
                alphaIndices.put(command, index);
            }
        }
        float alphaCosYaw = (float) Math.cos(camera.yaw());
        float alphaSinYaw = (float) Math.sin(camera.yaw());
        float alphaCosPitch = (float) Math.cos(camera.pitch());
        float alphaSinPitch = (float) Math.sin(camera.pitch());
        List<GpuDrawCommand> orderedAlpha = RsFaceOrderPlanner.orderAlphaReusable(
                alphaCommands,
                command -> averageDepth(runtimeGeometry, alphaIndices.get(command), command, camera,
                        alphaCosYaw, alphaSinYaw, alphaCosPitch, alphaSinPitch),
                command -> command.wallDecorationPresentation().cameraOrder(command.tile(), camera),
                alphaOrderWorkspace);
        for (GpuDrawCommand command : orderedAlpha) {
            alphaOrder.add(alphaIndices.get(command));
        }
        drawCalls += drawBatches(
                plan, commands, alphaOrder, visibility, camera, true, clientCycle);
        // Alpha and no-depth submissions disable depth writes. Restore the
        // baseline before handing the context back to ImGui and before the
        // next frame's clear.
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GEQUAL);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        lastFrameDepthWrites = true;
        lastFramePolygonMode = presentation.wireframe() ? GL_LINE : GL_FILL;
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        glUseProgram(0);
        captureGlError();
        statistics = statisticsFor(plan, runtimeGeometry, visibility,
                geometryUploaded, textureUploaded, drawCalls,
                gpuZoneUploads, gpuReusedAllocations);
        if (!diagnosticsLogged) {
            LOGGER.info("Native OpenGL {} / {} / {}; source={} vertices, rendered={} triangles, "
                            + "textures decoded={} fallback={} unavailable={} missing={}, "
                            + "draws={}, zonedBytes={}, flatMaterializations={} flatBytes={}, "
                            + "gpuZonesUploaded={} gpuAllocationsReused={}, framebuffer=0x{}, "
                            + "polygonMode=0x{}, depthWrites={}, occlusion={}, firstGLerror={}",
                    statistics.vendor(), statistics.renderer(), statistics.version(),
                    statistics.sourceVertices(), statistics.renderedTriangles(),
                    statistics.decodedTextures(), statistics.fallbackTextures(),
                    statistics.unavailableTextures(), statistics.missingTextures(), statistics.drawCalls(),
                    statistics.zonedGeometryBytes(), statistics.flatMaterializationCount(),
                    statistics.flatMaterializationBytes(), statistics.gpuZoneUploads(),
                    statistics.gpuReusedAllocations(),
                    Integer.toHexString(statistics.framebufferStatus()),
                    Integer.toHexString(statistics.polygonMode()), statistics.depthWritesEnabled(),
                    statistics.occlusionApplied(), statistics.firstGlError());
            diagnosticsLogged = true;
        }
    }

    /**
     * Selects back-face culling for non-terrain model geometry.
     *
     * <p>{@link #applyDrawState(GpuUploadPlan, GpuDrawCommand, boolean, int)}
     * enables culling only for non-terrain commands and disables it again for
     * terrain. The default is client-front {@link #CULL_FRONT_CCW}; the
     * opposite winding and two-sided modes remain diagnostics.</p>
     */
    public void setCullMode(int mode) {
        cullMode = mode < CULL_OFF || mode > CULL_FRONT_CW ? CULL_OFF : mode;
    }

    public int cullMode() {
        return cullMode;
    }

    static boolean cullEnabledFor(SceneLayer.Kind layer, int mode) {
        BackfacePolicy.NativeCullingMode semanticMode = switch (mode) {
            case CULL_FRONT_CCW -> BackfacePolicy.NativeCullingMode.CLIENT_FRONT;
            case CULL_FRONT_CW -> BackfacePolicy.NativeCullingMode.REVERSED_DEBUG;
            default -> BackfacePolicy.NativeCullingMode.TWO_SIDED;
        };
        return BackfacePolicy.cullsLayer(layer, semanticMode);
    }

    public Statistics statistics() {
        return statistics;
    }

    private Statistics statisticsFor(GpuUploadPlan plan, GpuCommandGeometry geometry,
                                     GpuCommandVisibility visibility,
                                     boolean geometryUploaded, boolean textureUploaded,
                                     int drawCalls, int gpuZoneUploads,
                                     int gpuReusedAllocations) {
        int sourceVertices = geometry == null ? 0 : geometry.vertexCount();
        int sourceIndices = geometry == null ? 0 : geometry.indexCount();
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
            missingTextureIds.clear();
            for (GpuDrawCommand command : plan.commands()) {
                int textureId = command.textureId();
                if (textureId >= 0 && !plan.textures().containsKey(textureId)) {
                    missingTextureIds.add(textureId);
                }
            }
            missing = missingTextureIds.size();
        }

        long zonedGeometryBytes = geometry instanceof GpuZonedUploadPlan
                ? geometryBytes(geometry)
                : 0L;
        int flatMaterializationCount = plan == null ? 0 : plan.flatMaterializationCount();
        long flatMaterializationBytes = plan != null && plan.flatMaterialized()
                ? geometryBytes(plan)
                : 0L;

        return new Statistics(sourceVertices, sourceIndices, renderedIndices,
                terrainTriangles, objectTriangles, decoded, fallback, unavailable, missing,
                glVendor, glRenderer, glVersion,
                firstGlError, framebufferStatus, lastFramePolygonMode, lastFrameDepthWrites,
                geometryUploaded, textureUploaded, drawCalls,
                zonedGeometryBytes, flatMaterializationCount, flatMaterializationBytes,
                gpuZoneUploads, gpuReusedAllocations,
                visibility != null && visibility.occlusionApplied());
    }

    private static long geometryBytes(GpuCommandGeometry geometry) {
        if (geometry == null) return 0L;
        return (long) geometry.vertexCount() * FLOATS_PER_VERTEX * Float.BYTES
                + (long) geometry.indexCount() * Integer.BYTES;
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
                             long zonedGeometryBytes, int flatMaterializationCount,
                             long flatMaterializationBytes, int gpuZoneUploads,
                             int gpuReusedAllocations, boolean occlusionApplied) {
        private static Statistics empty() {
            return new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "unknown", "unknown", "unknown", GL_NO_ERROR,
                    org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE, GL_FILL, true,
                    false, false, 0, 0L, 0, 0L, 0, 0, false);
        }

        public int renderedTriangles() {
            return renderedIndices / 3;
        }
    }

    private int drawBatches(GpuUploadPlan plan, List<GpuDrawCommand> commands,
                            List<Integer> orderedIndices, GpuCommandVisibility visibility,
                            CameraState camera, boolean alpha, int clientCycle) {
        GpuDrawCommand.SubmissionPass pass = alpha
                ? GpuDrawCommand.SubmissionPass.ALPHA
                : GpuDrawCommand.SubmissionPass.OPAQUE;
        GpuDrawBatchPlanner.BatchCursor batches = GpuDrawBatchPlanner.cursor(
                commands, orderedIndices, pass, zoneManager::zoneKeyForCommand);

        int drawCalls = 0;
        int lastBoundVao = -1;
        while (batches.next()) {
            int firstIndex = batches.firstCommandIndex();
            GpuDrawCommand first = commands.get(firstIndex);
            ZoneVboManager.ZoneAllocation alloc = zoneManager.allocation(batches.zoneKey());
            if (alloc == null) {
                continue;
            }
            if (alloc.vao() != lastBoundVao) {
                glBindVertexArray(alloc.vao());
                lastBoundVao = alloc.vao();
            }
            applyDrawState(plan, first, alpha, clientCycle);
            if (batches.commandCount() == 1) {
                int localFirst = zoneManager.localFirstIndex(firstIndex);
                glDrawElements(GL_TRIANGLES, first.indexCount(), GL_UNSIGNED_INT,
                        (long) localFirst * Integer.BYTES);
            } else {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer counts = stack.mallocInt(batches.commandCount());
                    PointerBuffer offsets = stack.mallocPointer(batches.commandCount());
                    for (int offset = 0; offset < batches.commandCount(); offset++) {
                        int commandIndex = batches.commandIndexAt(offset);
                        GpuDrawCommand command = commands.get(commandIndex);
                        counts.put(command.indexCount());
                        offsets.put((long) zoneManager.localFirstIndex(commandIndex)
                                * Integer.BYTES);
                    }
                    counts.flip();
                    offsets.flip();
                    glMultiDrawElements(GL_TRIANGLES, counts, GL_UNSIGNED_INT, offsets);
                }
            }
            drawCalls++;
        }
        return drawCalls;
    }

    private static long drawStateKey(GpuDrawCommand command, boolean alpha) {
        return RenderOrderKey.nativeState(command) * 2L + (alpha ? 1L : 0L);
    }

    private List<Integer> opaqueOrder(GpuUploadPlan plan, List<GpuDrawCommand> commands,
                                      GpuCommandVisibility visibility, CameraState camera) {
        // A cached opaque order is only stored when the plan has no
        // camera-ordered decorations and occlusion is inactive, so a matching
        // fingerprint can return before scanning every command again.
        if (!visibility.occlusionApplied()
                && plan.fingerprint().equals(orderedPlanFingerprint)) {
            return cachedOpaqueOrder;
        }
        boolean cameraOrderedDecorations = commands.stream()
                .anyMatch(command -> command.wallDecorationPresentation().cameraOrdered());
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
        // drawn at a given depth wins.
        // To reach the same "higher priority wins" visual result (verified by
        // SoftwareSceneRendererTest.higherPriorityCoplanarFaceWinsWithStableDepthBias
        // and essential for layered models such as banners where cloth is priority 0
        // and embroidery/crests are priorities 1..3), this path sorts priority
        // ASCENDING so higher-priority coplanar faces are drawn last and win the
        // GL_GEQUAL tie. Wall decorations separately beat their mounting wall via
        // submissionDepthBias in view-space depth.
        result.sort(Comparator.comparingInt((Integer index) -> commands.get(index).priority())
                .thenComparingInt(index -> commands.get(index).wallDecorationPresentation()
                        .cameraOrder(commands.get(index).tile(), camera))
                .thenComparingLong(index -> zoneManager.zoneKeyForCommand(index))
                .thenComparingLong(index -> drawStateKey(commands.get(index), false)));
        if (!cameraOrderedDecorations && !visibility.occlusionApplied()) {
            orderedPlanFingerprint = plan.fingerprint();
            cachedOpaqueOrder = List.copyOf(result);
        }
        return result;
    }

    private void drawCommand(GpuUploadPlan plan, GpuDrawCommand command, int commandIndex,
                             CameraState camera, boolean alpha, int clientCycle) {
        long zoneKey = zoneManager.zoneKeyForCommand(commandIndex);
        ZoneVboManager.ZoneAllocation alloc = zoneManager.allocation(zoneKey);
        if (alloc == null) return;
        glBindVertexArray(alloc.vao());
        applyDrawState(plan, command, alpha, clientCycle);
        glDrawElements(GL_TRIANGLES, command.indexCount(), GL_UNSIGNED_INT,
                (long) zoneManager.localFirstIndex(commandIndex) * Integer.BYTES);
    }

    private void applyDrawState(GpuUploadPlan plan, GpuDrawCommand command,
                                boolean alpha, int clientCycle) {
        boolean expectedAlpha = command.pass() == GpuDrawCommand.SubmissionPass.ALPHA;
        if (alpha != expectedAlpha) {
            throw new IllegalArgumentException("Draw pass does not match command submission pass");
        }
        NativeDrawState state = nativeDrawState(command.pass(), command.renderMode());
        int isAlpha = state.blend() ? 1 : 0;
        int noDepth = state.depthTest() ? 0 : 1;
        boolean depthStateChanged = noDepth != lastNoDepth;
        if (depthStateChanged) {
            if (state.depthTest()) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
            lastNoDepth = noDepth;
        }
        boolean alphaStateChanged = isAlpha != lastAlpha;
        if (alphaStateChanged) {
            if (state.blend()) {
                glEnable(GL_BLEND);
                // Preserve destination alpha in the resolved scene texture.
                // ImGui composites that texture later, so plain glBlendFunc
                // would progressively erode alpha across overlapping water,
                // canopy, and other transparent surfaces.
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);
            } else {
                glDisable(GL_BLEND);
            }
            lastAlpha = isAlpha;
        }
        if (depthStateChanged || alphaStateChanged) {
            glDepthMask(state.depthWrite());
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
        int cull = cullEnabledFor(command.layer(), cullMode) ? 1 : 0;
        if (cull != lastCull) {
            if (cull == 1) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            lastCull = cull;
        }
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
        float[] scale = textureScales.get(command.textureId());
        float scaleX = scale == null ? 1.0f : scale[0];
        float scaleY = scale == null ? 1.0f : scale[1];
        if (scaleX != lastTextureScaleX || scaleY != lastTextureScaleY) {
            glUniform2f(textureScaleLocation, scaleX, scaleY);
            lastTextureScaleX = scaleX;
            lastTextureScaleY = scaleY;
        }
    }

    /** Supplies the render-target status for the native acceptance diagnostics. */
    public void setFramebufferStatus(int status) {
        framebufferStatus = status;
    }

    private static int clientCycle() {
        // RuneLite sends client.getGameCycle() & 127 to the GPU texture
        // animation path. Keep the same bounded phase while using monotonic
        // editor time instead of coupling rendering to command timing.
        long cycle = (System.nanoTime() / 1_000_000L) / 20L;
        return (int) (cycle & TextureAnimation.CLIENT_CYCLE_MASK);
    }

    private static float averageDepth(GpuCommandGeometry geometry, int commandIndex,
                                      GpuDrawCommand command, CameraState camera,
                                      float cosYaw, float sinYaw,
                                      float cosPitch, float sinPitch) {
        SceneOcclusionResolver.CommandBounds bounds =
                SceneOcclusionResolver.boundsOf(commandIndex, command, geometry);
        float dx = bounds.centerX() - camera.x();
        // OSRS world Y is a down-axis: smaller values are higher terrain.
        // Convert to camera-up space before applying pitch, matching the CPU
        // reference renderer and the TSPS scene convention.
        float upDelta = camera.y() - bounds.centerY();
        float dz = bounds.centerZ() - camera.z();
        float yawDepth = dx * sinYaw + dz * cosYaw;
        return upDelta * sinPitch + yawDepth * cosPitch;
    }

    private SceneFog.Bounds fogBounds(GpuCommandGeometry geometry) {
        if (geometry != fogBoundsGeometry || cachedFogBounds == null) {
            cachedFogBounds = SceneFog.bounds(geometry);
            fogBoundsGeometry = geometry;
        }
        return cachedFogBounds;
    }

    private String textureFingerprintCached(Map<Integer, RenderTextureResource> resources) {
        if (resources == fingerprintedTextureResources && fingerprintedTextureFingerprint != null) {
            return fingerprintedTextureFingerprint;
        }
        String fingerprint = textureFingerprint(resources);
        fingerprintedTextureResources = resources;
        fingerprintedTextureFingerprint = fingerprint;
        return fingerprint;
    }

    private static String textureFingerprint(Map<Integer, RenderTextureResource> resources) {
        StringBuilder value = new StringBuilder();
        resources.values().stream().sorted(Comparator.comparingInt(RenderTextureResource::id))
                .forEach(texture -> value.append(texture.id())
                        .append(':').append(texture.pixelStatus())
                        .append(':').append(texture.width()).append('x').append(texture.height())
                        .append(':').append(texture.pixelHash()).append('|'));
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
        // REPEAT was tried here to close the seams between wall segments
        // and reverted with the culling change above; it has not been
        // isolated yet, so it stays at the clamped baseline.
        // Match the client baseline first. Mipmapping can be added after
        // parity is proven; it is not safe while fallback layers can have a
        // different source dimension.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
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
            glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
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
                    int argb = source[sourceY * resource.width() + sourceX];
                    int rgb = argb & 0xFFFFFF;
                    // The client's transparency source is the texture's own
                    // alpha channel (`src >>> 24` in the textured scanline),
                    // which ARGBTexture/AlphaPalettedTexture really populate
                    // with partial values - that is how water and glass read
                    // through to the terrain beneath them. Only fall back to
                    // the binary cutout convention when the provider's pixels
                    // carry no alpha information at all.
                    int alpha;
                    if (resource.usesAlphaChannel()) {
                        alpha = RenderTextureResource.alphaOf(argb);
                    } else {
                        alpha = resource.pixelStatus() == RenderTextureResource.PixelStatus.AVAILABLE
                                && rgb == 0 ? 0x00 : 0xFF;
                    }
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
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
    }

    @Override
    public void close() {
        zoneManager.close();
        if (paletteTexture != 0) org.lwjgl.opengl.GL11.glDeleteTextures(paletteTexture);
        paletteTexture = 0;
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
        fingerprintedTextureResources = null;
        fingerprintedTextureFingerprint = null;
        fogBoundsGeometry = null;
        cachedFogBounds = null;
        orderedPlanFingerprint = null;
        cachedOpaqueOrder = List.of();
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
            // Constant depth-buffer separation per bias step, on top of the
            // view-space bias. The view-space term is correct up close but its
            // depth-buffer effect falls off as 1 / depth^2, so coplanar pairs
            // stop separating once the camera is far out. This uniform is added
            // to Z_ndc directly - i.e. it is applied to clip Z scaled by the
            // unbiased W (k * depth), not as a bare clip-space offset, which W
            // would divide back down by depth and turn into yet another
            // distance-dependent term. Only Z moves and W stays unbiased, so
            // screen X/Y and the silhouette are unchanged.
            uniform float uDepthBiasNudge;
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
                // Multiplying the nudge by depth (the unbiased W) is what makes
                // it survive the perspective divide as a constant separation in
                // normalised depth, independent of camera distance.
                vec4 projected = vec4(uFocal / uAspect * x, uFocal * y,
                                      uDepthA * depth + uDepthB * (depth / biasedDepth)
                                              + uFaceBias * uDepthBiasNudge * depth,
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
            uniform sampler2D uPalette;
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
            void main() {
                vec3 color;
                if (uTextured != 0) {
                    vec2 textureUv = vUv + uTextureOffset;
                    if (uTerrain != 0 || uTextureOffset.x != 0.0 || uTextureOffset.y != 0.0) {
                        textureUv = fract(textureUv);
                    }
                    if (uTextureAvailable != 0) {
                        vec3 texCoord = vec3(textureUv * uTextureScale, float(uTextureLayer));
                        // Base LOD 0 alpha test prevents cutout erosion at distance
                        vec4 texel0 = textureLod(uTexture, texCoord, 0.0);
                        if (uTerrain != 0) {
                            // Client floor rule: render_texture_triangle passes
                            // floor = true for tile tops, and a floor texel is
                            // never blended into the framebuffer. A zero-alpha
                            // texel leaves it untouched - so the underlay the
                            // floor covers stays visible - and every other texel
                            // is mixed toward the tile's own flat colour and
                            // written opaquely (see below).
                            if (texel0.a <= 0.0) discard;
                        } else if (texel0.a < 1.0) {
                            // RuneLite GPU frag.glsl rejects any model texture
                            // texel whose base-LOD alpha is not fully opaque.
                            // Keep cutouts in the opaque stream for depth
                            // ownership, but do not let partially transparent
                            // texels survive as opaque model fragments.
                            discard;
                        }

                        vec4 texel = texture(uTexture, texCoord);
                        if (uTerrain != 0) {
                            // Bank/shift integer texture shading: authentic client software rasterizer bit math
                            ivec3 texRgb = ivec3(round(texel.rgb * 255.0));
                            int texLum = ((texRgb.r >> 1) + (texRgb.g >> 1) + (texRgb.b >> 1) + 127) >> 2;
                            int startCol = int(vEncodedColor);
                            int shadedLight = clamp(((startCol & 0x7F) * texLum) >> 7, 0, 127);
                            int shadedHsl = (startCol & 0xFF80) | shadedLight;
                            vec3 shaded = texelFetch(uPalette, ivec2(shadedHsl & 255, (shadedHsl >> 8) & 255), 0).rgb;
                            // The client mixes a partial texel toward the TILE's
                            // flat colour - colourPalette[tile.getColour()] - and
                            // then writes it opaquely; it does not blend into
                            // whatever happens to be behind it. The flat colour
                            // is the same hue and saturation at full lightness.
                            int flatHsl = (startCol & 0xFF80) | 0x7F;
                            vec3 tileColour =
                                    texelFetch(uPalette, ivec2(flatHsl & 255, (flatHsl >> 8) & 255), 0).rgb;
                            color = mix(tileColour, shaded, texel0.a);
                        } else {
                            float lightness = clamp(vEncodedColor / 127.0, 0.0, 1.0);
                            color = texel.rgb * lightness;
                        }
                    } else if (uTextureMissing != 0) {
                        float lightness = clamp(vEncodedColor / 127.0, 0.0, 1.0);
                        color = vec3(1.0, 0.0, 1.0) * lightness;
                    } else {
                        color = vec3(clamp(vEncodedColor / 64.0, 0.0, 1.0));
                    }
                } else {
                    int hsl = clamp(int(vEncodedColor), 0, 65535);
                    vec3 paletteColor = texelFetch(uPalette, ivec2(hsl & 255, (hsl >> 8) & 255), 0).rgb;
                    color = mix(vColor, paletteColor, float(uSmoothBanding));
                }
                float alpha;
                if (uTerrain != 0) {
                    // Terrain vertex alpha is opacity (255 opaque), and a
                    // textured floor stays opaque even where its texture is
                    // partly transparent - that transparency was already spent
                    // mixing toward the tile colour above.
                    alpha = clamp(vAlpha / 255.0, 0.0, 1.0);
                } else {
                    // Model alpha is transparency (0 opaque, 255 invisible).
                    // Render type is a shading selector (1 and 3 are flat
                    // single-colour faces), never an opacity: the client's
                    // Mesh.renderFace only switches on it to choose shaded,
                    // flat-colour or textured output.
                    alpha = 1.0 - clamp(vAlpha / 255.0, 0.0, 1.0);
                }
                color = clamp(color * uBrightness * exp2(uExposure), 0.0, 1.0);
                color = mix(color, uFogColor, vFogAmount);
                outColor = vec4(color, alpha);
            }
            """;
}
