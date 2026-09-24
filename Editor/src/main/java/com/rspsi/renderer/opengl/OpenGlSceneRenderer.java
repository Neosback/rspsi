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
import com.rspsi.editor.render.PickerId;
import com.rspsi.editor.render.RenderPresentation;
import com.rspsi.editor.render.RenderOrderKey;
import com.rspsi.editor.render.SceneFog;
import com.rspsi.editor.render.SceneOcclusionResolver;
import com.rspsi.editor.render.TextureAnimation;
import com.rspsi.editor.render.RsFaceOrderPlanner;
import com.rspsi.renderer.opengl.shader.GlShaderProgram;
import com.rspsi.renderer.opengl.shader.ShaderSourceLoader;

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
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL11.glPolygonMode;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
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
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL14.glMultiDrawElements;
import static org.lwjgl.opengl.GL30.GL_TEXTURE0;
import static org.lwjgl.opengl.GL30.GL_TEXTURE1;
import static org.lwjgl.opengl.GL30.GL_TEXTURE2;
import static org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL30.glActiveTexture;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/** OpenGL 3.3 consumer of the immutable world-space upload plan. */
public final class OpenGlSceneRenderer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGlSceneRenderer.class);
    private static final ShaderSourceLoader SHADER_SOURCES = new ShaderSourceLoader("shaders");
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
    private int lastTextureLayer = -1;

    private void resetDrawState() {
        lastAlpha = -1;
        lastTextured = -1;
        lastTextureAvailable = -1;
        lastTextureMissing = -1;
        lastTerrain = -1;
        lastCull = -1;
        lastNoDepth = -1;
        lastFaceBias = -1;
        lastTextureLayer = -1;
    }

    private int program;
    private int faceBiasLocation;
    private int texturedLocation;
    private int textureAvailableLocation;
    private int textureMissingLocation;
    private int terrainLocation;
    private int textureLocation;
    private int textureLayerLocation;
    private int textureStateLocation;

    private int pickerProgram;
    private int pickerFaceBiasLocation;
    private int pickerTexturedLocation;
    private int pickerTextureAvailableLocation;
    private int pickerTerrainLocation;
    private int pickerTextureLocation;
    private int pickerTextureLayerLocation;
    private int pickerTextureStateLocation;

    private final ZoneVboManager zoneManager = new ZoneVboManager();
    private final GpuPickerFramebuffer pickerFramebuffer = new GpuPickerFramebuffer();
    private final ArrayList<Integer> pickerOrderWorkspace = new ArrayList<>();
    private boolean gpuPickingEnabled;
    private RenderPresentation lastDrawPresentation = RenderPresentation.neutral();
    private int lastDrawClientCycle;
    private final TextureStateBuffer textureStateBuffer = new TextureStateBuffer();
    private final FrameUniformBuffer frameUniformBuffer = new FrameUniformBuffer();
    private final GpuCommandVisibility.Cache visibilityCache =
            new GpuCommandVisibility.Cache();
    private int paletteTexture;
    private int paletteLocation;
    private String uploadedFingerprint;
    private String uploadedTextureFingerprint;
    private String uploadedTextureStateFingerprint;
    private Map<Integer, RenderTextureResource> fingerprintedTextureResources;
    private String fingerprintedTextureFingerprint;
    private Map<Integer, RenderTextureResource> fingerprintedTextureStateResources;
    private String fingerprintedTextureStateFingerprint;
    private GpuCommandGeometry fogBoundsGeometry;
    private SceneFog.Bounds cachedFogBounds;
    private int textureArray;
    private final Map<Integer, Integer> textureLayers = new HashMap<>();
    private int firstGlError = GL_NO_ERROR;
    private int framebufferStatus = org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
    private Statistics statistics = Statistics.empty();
    private boolean diagnosticsLogged;
    private boolean lastFrameDepthWrites = true;
    private int lastFramePolygonMode = GL_FILL;
    private String orderedPlanFingerprint;
    private List<Integer> cachedOpaqueOrder = List.of();
    private final ArrayList<Integer> opaqueOrderWorkspace = new ArrayList<>();
    private final ArrayList<GpuDrawCommand> alphaCommands = new ArrayList<>();
    private final java.util.IdentityHashMap<GpuDrawCommand, Integer> alphaIndices =
            new java.util.IdentityHashMap<>();
    private List<GpuDrawCommand> indexedCommands = List.of();
    private final ArrayList<Integer> alphaOrder = new ArrayList<>();
    private final RsFaceOrderPlanner.Workspace alphaOrderWorkspace =
            new RsFaceOrderPlanner.Workspace();
    private GpuCommandGeometry cachedAlphaGeometry;
    private GpuCommandVisibility cachedAlphaVisibility;
    private CameraState cachedAlphaCamera;
    private List<GpuDrawCommand> cachedAlphaCommandList = List.of();
    private List<Integer> cachedAlphaOrder = List.of();
    private final java.util.HashSet<Integer> missingTextureIds = new java.util.HashSet<>();
    private final FrameMetrics frameMetrics = new FrameMetrics();
    private GpuUploadPlan statisticsPlan;
    private PlanStatistics cachedPlanStatistics = PlanStatistics.empty();
    private GpuCommandGeometry statisticsGeometry;
    private GeometryStatistics cachedGeometryStatistics = GeometryStatistics.empty();
    private String glVendor = "unknown";
    private String glRenderer = "unknown";
    private String glVersion = "unknown";
    private OpenGlCapabilityProfile capabilityProfile;

    public void initialize() {
        GLCapabilities capabilities = GL.createCapabilities();
        capabilityProfile = OpenGlCapabilityProfile.capture(capabilities);
        capabilityProfile.requireVanillaBaseline();
        glVendor = capabilityProfile.vendor();
        glRenderer = capabilityProfile.renderer();
        glVersion = capabilityProfile.version();
        LOGGER.info("OpenGL capability profile: {}", capabilityProfile.diagnosticSummary());
        // ZoneVboManager owns all resident scene VAOs/VBOs. Shader compilation
        // is independent of vertex-array state, so keep one authoritative
        // native scene layout instead of maintaining a second empty VAO here.
        program = GlShaderProgram.link(
                SHADER_SOURCES.load("scene/vanilla.vert"),
                SHADER_SOURCES.load("scene/vanilla.frag"));
        pickerProgram = GlShaderProgram.link(
                SHADER_SOURCES.load("scene/vanilla.vert"),
                SHADER_SOURCES.load("scene/picker.frag"));

        bindFrameUniformBlock(program, "Vanilla");
        bindFrameUniformBlock(pickerProgram, "Picker");
        frameUniformBuffer.initialize();

        faceBiasLocation = glGetUniformLocation(program, "uFaceBias");
        texturedLocation = glGetUniformLocation(program, "uTextured");
        textureAvailableLocation = glGetUniformLocation(program, "uTextureAvailable");
        textureMissingLocation = glGetUniformLocation(program, "uTextureMissing");
        terrainLocation = glGetUniformLocation(program, "uTerrain");
        textureLocation = glGetUniformLocation(program, "uTexture");
        textureLayerLocation = glGetUniformLocation(program, "uTextureLayer");
        textureStateLocation = glGetUniformLocation(program, "uTextureState");
        paletteLocation = glGetUniformLocation(program, "uPalette");
        glUseProgram(program);
        glUniform1i(textureLocation, 0);
        glUniform1i(paletteLocation, 1);
        glUniform1i(textureStateLocation, 2);

        pickerFaceBiasLocation = glGetUniformLocation(pickerProgram, "uFaceBias");
        pickerTexturedLocation = glGetUniformLocation(pickerProgram, "uTextured");
        pickerTextureAvailableLocation = glGetUniformLocation(pickerProgram, "uTextureAvailable");
        pickerTerrainLocation = glGetUniformLocation(pickerProgram, "uTerrain");
        pickerTextureLocation = glGetUniformLocation(pickerProgram, "uTexture");
        pickerTextureLayerLocation = glGetUniformLocation(pickerProgram, "uTextureLayer");
        pickerTextureStateLocation = glGetUniformLocation(pickerProgram, "uTextureState");
        glUseProgram(pickerProgram);
        glUniform1i(pickerTextureLocation, 0);
        glUniform1i(pickerTextureStateLocation, 2);
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

    private static void bindFrameUniformBlock(int shaderProgram, String label) {
        int block = org.lwjgl.opengl.GL31.glGetUniformBlockIndex(shaderProgram, "FrameUniforms");
        if (block < 0) {
            throw new IllegalStateException(label + " shader is missing FrameUniforms block");
        }
        org.lwjgl.opengl.GL31.glUniformBlockBinding(
                shaderProgram, block, FrameUniformBuffer.BINDING_POINT);
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
        lastDrawPresentation = presentation;
        lastDrawClientCycle = clientCycle & TextureAnimation.CLIENT_CYCLE_MASK;
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
        frameMetrics.reset();

        // Optional native streams are reconciled before empty-scene exits so
        // disabling a feature immediately drops its resident GPU cost even
        // when the editor has no scene loaded.
        boolean auxiliaryLayoutChanged = zoneManager.setAuxiliaryStreams(
                presentation.debugView().requiresNormals(), gpuPickingEnabled);
        if (auxiliaryLayoutChanged) {
            uploadedFingerprint = null;
            orderedPlanFingerprint = null;
        }
        if (!gpuPickingEnabled && pickerFramebuffer.allocated()) {
            pickerFramebuffer.release();
        }

        if (plan == null) {
            statistics = statisticsFor(null, null, null,
                    false, false, 0, 0, 0, 0, 0, 0);
            captureGlError();
            return;
        }
        boolean zonedGeometryActive = zonedPlan != null
                && plan.fingerprint().equals(zonedPlan.sourceFingerprint());
        GpuCommandGeometry runtimeGeometry = zonedGeometryActive ? zonedPlan : plan;
        GeometryStatistics geometryStatistics = geometryStatistics(runtimeGeometry);
        if (geometryStatistics.sourceVertices() == 0
                || geometryStatistics.sourceIndices() == 0) {
            statistics = statisticsFor(plan, runtimeGeometry, null,
                    false, false, 0, 0, 0, 0, 0, 0);
            captureGlError();
            return;
        }
        // Geometry/texture upload is gated on the plan's own camera-independent
        // fingerprint only, and always uploads the complete, unfiltered plan.
        // Occlusion visibility is a separate per-frame decision and camera
        // movement alone must never trigger a scene-buffer re-upload.
        boolean geometryUploaded = false;
        boolean textureUploaded = false;
        int gpuZoneUploads = 0;
        int gpuReusedAllocations = 0;
        int gpuGeometryStreamUploads = 0;
        int gpuShadingStreamUploads = 0;
        int gpuIndexStreamUploads = 0;
        if (!plan.fingerprint().equals(uploadedFingerprint)) {
            if (zonedGeometryActive) {
                zoneManager.upload(zonedPlan);
            } else {
                zoneManager.upload(plan);
            }
            gpuZoneUploads = zoneManager.dirtyZonesUploadedCount();
            gpuReusedAllocations = zoneManager.reusedAllocationsCount();
            gpuGeometryStreamUploads = zoneManager.geometryStreamUploads();
            gpuShadingStreamUploads = zoneManager.shadingStreamUploads();
            gpuIndexStreamUploads = zoneManager.indexStreamUploads();
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
        String textureStateFingerprint = textureStateFingerprintCached(plan.textures());
        if (!textureStateFingerprint.equals(uploadedTextureStateFingerprint)) {
            capabilityProfile.requireTextureStateEntries(requiredTextureCapacity(plan.textures()));
            textureStateBuffer.upload(plan.textures(), TEXTURE_LAYER_CAPACITY);
            uploadedTextureStateFingerprint = textureStateFingerprint;
        }
        GpuCommandVisibility visibility = visibilityCache.resolve(
                runtimeGeometry, camera, plan.occluders(), plan.sceneWindow());

        boolean fogEnabled = presentation.fogDepthTiles() > 0;
        SceneFog.Bounds frameFogBounds = fogEnabled ? fogBounds(runtimeGeometry) : null;
        frameUniformBuffer.upload(
                camera,
                (float) (1.0 / Math.tan(FOV_Y * 0.5)),
                (float) width / height,
                -(FAR + NEAR) / (FAR - NEAR),
                2.0f * FAR * NEAR / (FAR - NEAR),
                DEPTH_BIAS_NUDGE,
                presentation,
                frameFogBounds,
                clientCycle & TextureAnimation.CLIENT_CYCLE_MASK);
        frameUniformBuffer.bind();

        glUseProgram(program);
        glPolygonMode(GL_FRONT_AND_BACK, presentation.wireframe() ? GL_LINE : GL_FILL);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, paletteTexture);
        glActiveTexture(GL_TEXTURE2);
        textureStateBuffer.bind();
        resetDrawState();
        List<GpuDrawCommand> commands = plan.commands();
        // RuneLite uses GL_LEQUAL for its forward-Z native path.  This
        // reversed-Z path uses the equivalent GL_GEQUAL so exactly-coplanar
        // wall trim/decor faces can be resolved by the OSRS submission order
        // instead of failing a strict depth test. List.sort is stable, so
        // indices are only reordered relative to distinct priority values.
        List<Integer> opaqueOrder = opaqueOrder(plan, commands, visibility, camera);
        drawBatches(commands, opaqueOrder, visibility, camera, false);
        // The software reference renderer composites transparent triangles
        // back-to-front. Keep opaque submission order stable, but apply the
        // same depth ordering to alpha ranges in the native backend.
        List<Integer> alphaOrder = alphaOrderFor(
                runtimeGeometry, commands, visibility, camera);
        drawBatches(
                commands, alphaOrder, visibility, camera, true);
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
        glActiveTexture(GL_TEXTURE2);
        textureStateBuffer.unbind();
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        glUseProgram(0);
        captureGlError();
        statistics = statisticsFor(plan, runtimeGeometry, visibility,
                geometryUploaded, textureUploaded, frameMetrics.drawCalls,
                gpuZoneUploads, gpuReusedAllocations,
                gpuGeometryStreamUploads, gpuShadingStreamUploads, gpuIndexStreamUploads);
        if (!diagnosticsLogged) {
            LOGGER.info("Native OpenGL {} / {} / {}; source={} vertices, rendered={} triangles, "
                            + "textures decoded={} fallback={} unavailable={} missing={}, "
                            + "draws={}, zonedBytes={}, flatMaterializations={} flatBytes={}, "
                            + "gpuZonesUploaded={} gpuAllocationsReused={}, streams[g={},s={},i={}], framebuffer=0x{}, "
                            + "polygonMode=0x{}, depthWrites={}, occlusion={}, firstGLerror={}",
                    statistics.vendor(), statistics.renderer(), statistics.version(),
                    statistics.sourceVertices(), statistics.renderedTriangles(),
                    statistics.decodedTextures(), statistics.fallbackTextures(),
                    statistics.unavailableTextures(), statistics.missingTextures(), statistics.drawCalls(),
                    statistics.zonedGeometryBytes(), statistics.flatMaterializationCount(),
                    statistics.flatMaterializationBytes(), statistics.gpuZoneUploads(),
                    statistics.gpuReusedAllocations(),
                    statistics.gpuGeometryStreamUploads(), statistics.gpuShadingStreamUploads(),
                    statistics.gpuIndexStreamUploads(),
                    Integer.toHexString(statistics.framebufferStatus()),
                    Integer.toHexString(statistics.polygonMode()), statistics.depthWritesEnabled(),
                    statistics.occlusionApplied(), statistics.firstGlError());
            diagnosticsLogged = true;
        }
    }

    /**
     * Selects back-face culling for non-terrain model geometry.
     *
     * <p>{@link #applyDrawState(GpuDrawCommand, boolean)}
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

    /**
     * Enables the optional resident picker-ID stream. The integer framebuffer
     * itself remains lazy and is not allocated until a pick is actually issued.
     */
    public void setGpuPickingEnabled(boolean enabled) {
        gpuPickingEnabled = enabled;
    }

    public boolean gpuPickingEnabled() {
        return gpuPickingEnabled;
    }

    boolean pickerFramebufferAllocated() {
        return pickerFramebuffer.allocated();
    }

    /**
     * Renders the current scene into the dedicated integer picker target and
     * reads one top-left-origin viewport pixel.
     *
     * <p>The pass is intentionally single-sample even when the visible scene
     * uses MSAA. Integer IDs therefore never require a multisample resolve.
     * Returning {@link PickerId#INVALID} leaves the CPU DDA picker as the
     * correctness fallback.</p>
     */
    public int pickId(GpuUploadPlan plan, GpuZonedUploadPlan zonedPlan,
                      CameraState camera, int width, int height,
                      float screenX, float screenY, Integer restrictToPlane) {
        if (!gpuPickingEnabled || plan == null || camera == null
                || width <= 0 || height <= 0
                || !Float.isFinite(screenX) || !Float.isFinite(screenY)
                || screenX < 0.0f || screenY < 0.0f
                || screenX >= width || screenY >= height
                || (restrictToPlane != null && (restrictToPlane < 0 || restrictToPlane > 3))
                || !zoneManager.pickerStreamEnabled()
                || !plan.fingerprint().equals(uploadedFingerprint)) {
            return PickerId.INVALID;
        }

        boolean zonedGeometryActive = zonedPlan != null
                && plan.fingerprint().equals(zonedPlan.sourceFingerprint());
        GpuCommandGeometry runtimeGeometry = zonedGeometryActive ? zonedPlan : plan;
        GeometryStatistics geometryStatistics = geometryStatistics(runtimeGeometry);
        if (geometryStatistics.sourceVertices() == 0
                || geometryStatistics.sourceIndices() == 0) {
            return PickerId.INVALID;
        }

        GpuCommandVisibility visibility = visibilityCache.resolve(
                runtimeGeometry, camera, plan.occluders(), plan.sceneWindow());

        int previousDrawFramebuffer = org.lwjgl.opengl.GL11.glGetInteger(
                org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = org.lwjgl.opengl.GL11.glGetInteger(
                org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer previousViewport = stack.mallocInt(4);
            org.lwjgl.opengl.GL11.glGetIntegerv(
                    org.lwjgl.opengl.GL11.GL_VIEWPORT, previousViewport);

            try {
                pickerFramebuffer.resize(width, height);
                pickerFramebuffer.bindAndClear();

                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_GEQUAL);
                glDepthMask(true);
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

                boolean fogEnabled = lastDrawPresentation.fogDepthTiles() > 0;
                SceneFog.Bounds frameFogBounds = fogEnabled ? fogBounds(runtimeGeometry) : null;
                frameUniformBuffer.upload(
                        camera,
                        (float) (1.0 / Math.tan(FOV_Y * 0.5)),
                        (float) width / height,
                        -(FAR + NEAR) / (FAR - NEAR),
                        2.0f * FAR * NEAR / (FAR - NEAR),
                        DEPTH_BIAS_NUDGE,
                        lastDrawPresentation,
                        frameFogBounds,
                        lastDrawClientCycle);
                frameUniformBuffer.bind();

                glUseProgram(pickerProgram);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D_ARRAY, textureArray);
                glActiveTexture(GL_TEXTURE2);
                textureStateBuffer.bind();
                resetDrawState();

                List<GpuDrawCommand> commands = plan.commands();
                List<Integer> opaque = pickerOrder(
                        opaqueOrder(plan, commands, visibility, camera),
                        commands, restrictToPlane);
                drawPickerBatches(commands, opaque, false);

                List<Integer> alpha = pickerOrder(
                        alphaOrderFor(runtimeGeometry, commands, visibility, camera),
                        commands, restrictToPlane);
                drawPickerBatches(commands, alpha, true);

                glDepthMask(true);
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_GEQUAL);
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
                glBindVertexArray(0);

                int result = pickerFramebuffer.readTopLeft(screenX, screenY);
                captureGlError();
                return result;
            } finally {
                glBindVertexArray(0);
                glActiveTexture(GL_TEXTURE2);
                textureStateBuffer.unbind();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
                glUseProgram(0);
                glDepthMask(true);
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_GEQUAL);
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
                resetDrawState();

                org.lwjgl.opengl.GL30.glBindFramebuffer(
                        org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
                org.lwjgl.opengl.GL30.glBindFramebuffer(
                        org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
                glViewport(previousViewport.get(0), previousViewport.get(1),
                        previousViewport.get(2), previousViewport.get(3));
            }
        }
    }

    private List<Integer> pickerOrder(List<Integer> source,
                                      List<GpuDrawCommand> commands,
                                      Integer restrictToPlane) {
        if (restrictToPlane == null) return source;
        pickerOrderWorkspace.clear();
        for (int index : source) {
            if (commands.get(index).tile().plane() == restrictToPlane) {
                pickerOrderWorkspace.add(index);
            }
        }
        return pickerOrderWorkspace;
    }

    private void drawPickerBatches(List<GpuDrawCommand> commands,
                                   List<Integer> orderedIndices, boolean alpha) {
        GpuDrawCommand.SubmissionPass pass = alpha
                ? GpuDrawCommand.SubmissionPass.ALPHA
                : GpuDrawCommand.SubmissionPass.OPAQUE;
        GpuDrawBatchPlanner.BatchCursor batches = GpuDrawBatchPlanner.cursor(
                commands, orderedIndices, pass, zoneManager::zoneKeyForCommand);

        int lastBoundVao = -1;
        while (batches.next()) {
            int firstIndex = batches.firstCommandIndex();
            GpuDrawCommand first = commands.get(firstIndex);
            ZoneVboManager.ZoneAllocation allocation = zoneManager.allocation(batches.zoneKey());
            if (allocation == null || allocation.pickerVbo() == 0) continue;

            if (allocation.vao() != lastBoundVao) {
                glBindVertexArray(allocation.vao());
                lastBoundVao = allocation.vao();
            }
            applyPickerDrawState(first, alpha);

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
        }
    }

    private void applyPickerDrawState(GpuDrawCommand command, boolean alpha) {
        boolean expectedAlpha = command.pass() == GpuDrawCommand.SubmissionPass.ALPHA;
        if (alpha != expectedAlpha) {
            throw new IllegalArgumentException("Picker draw pass does not match command submission pass");
        }

        NativeDrawState state = nativeDrawState(command.pass(), command.renderMode());
        int noDepth = state.depthTest() ? 0 : 1;
        if (noDepth != lastNoDepth) {
            if (state.depthTest()) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
            lastNoDepth = noDepth;
        }
        glDepthMask(state.depthWrite());
        glDisable(GL_BLEND);

        int layer = textureLayers.getOrDefault(command.textureId(), -1);
        int textured = command.textureId() < 0 ? 0 : 1;
        if (textured != lastTextured) {
            glUniform1i(pickerTexturedLocation, textured);
            lastTextured = textured;
        }
        int textureAvailable = layer < 0 ? 0 : 1;
        if (textureAvailable != lastTextureAvailable) {
            glUniform1i(pickerTextureAvailableLocation, textureAvailable);
            lastTextureAvailable = textureAvailable;
        }

        int isTerrain = command.layer() == SceneLayer.Kind.TERRAIN ? 1 : 0;
        if (isTerrain != lastTerrain) {
            glUniform1i(pickerTerrainLocation, isTerrain);
            lastTerrain = isTerrain;
        }

        int cull = cullEnabledFor(command.layer(), cullMode) ? 1 : 0;
        if (cull != lastCull) {
            if (cull == 1) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            lastCull = cull;
        }

        int faceBias = command.depthBias();
        if (faceBias != lastFaceBias) {
            glUniform1f(pickerFaceBiasLocation, faceBias);
            lastFaceBias = faceBias;
        }

        int texLayer = Math.max(0, layer);
        if (texLayer != lastTextureLayer) {
            glUniform1i(pickerTextureLayerLocation, texLayer);
            lastTextureLayer = texLayer;
        }
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

    public OpenGlCapabilityProfile capabilityProfile() {
        if (capabilityProfile == null) {
            throw new IllegalStateException("OpenGL renderer is not initialized");
        }
        return capabilityProfile;
    }

    private Statistics statisticsFor(GpuUploadPlan plan, GpuCommandGeometry geometry,
                                     GpuCommandVisibility visibility,
                                     boolean geometryUploaded, boolean textureUploaded,
                                     int drawCalls, int gpuZoneUploads,
                                     int gpuReusedAllocations, int gpuGeometryStreamUploads,
                                     int gpuShadingStreamUploads, int gpuIndexStreamUploads) {
        GeometryStatistics geometryStatistics = geometryStatistics(geometry);
        PlanStatistics planStatistics = planStatistics(plan);

        int flatMaterializationCount = plan == null ? 0 : plan.flatMaterializationCount();
        long flatMaterializationBytes = plan != null && plan.flatMaterialized()
                ? geometryBytes(plan)
                : 0L;

        return new Statistics(
                geometryStatistics.sourceVertices(),
                geometryStatistics.sourceIndices(),
                frameMetrics.renderedIndices,
                frameMetrics.terrainTriangles,
                frameMetrics.objectTriangles,
                planStatistics.decodedTextures(),
                planStatistics.fallbackTextures(),
                planStatistics.unavailableTextures(),
                planStatistics.missingTextures(),
                glVendor, glRenderer, glVersion,
                firstGlError, framebufferStatus, lastFramePolygonMode, lastFrameDepthWrites,
                geometryUploaded, textureUploaded, drawCalls,
                geometryStatistics.zonedGeometryBytes(),
                flatMaterializationCount, flatMaterializationBytes,
                gpuZoneUploads, gpuReusedAllocations,
                gpuGeometryStreamUploads, gpuShadingStreamUploads, gpuIndexStreamUploads,
                visibility != null && visibility.occlusionApplied());
    }

    private PlanStatistics planStatistics(GpuUploadPlan plan) {
        if (plan == null) return PlanStatistics.empty();
        if (plan == statisticsPlan) return cachedPlanStatistics;

        int decoded = 0;
        int fallback = 0;
        int unavailable = 0;
        for (RenderTextureResource resource : plan.textures().values()) {
            switch (resource.pixelStatus()) {
                case AVAILABLE -> decoded++;
                case AVERAGE_COLOR_FALLBACK -> fallback++;
                case UNAVAILABLE, INVALID -> unavailable++;
            }
        }

        missingTextureIds.clear();
        for (GpuDrawCommand command : plan.commands()) {
            int textureId = command.textureId();
            if (textureId >= 0 && !plan.textures().containsKey(textureId)) {
                missingTextureIds.add(textureId);
            }
        }

        statisticsPlan = plan;
        cachedPlanStatistics = new PlanStatistics(
                decoded, fallback, unavailable, missingTextureIds.size());
        return cachedPlanStatistics;
    }

    private GeometryStatistics geometryStatistics(GpuCommandGeometry geometry) {
        if (geometry == null) return GeometryStatistics.empty();
        if (geometry == statisticsGeometry) return cachedGeometryStatistics;

        int sourceVertices = geometry.vertexCount();
        int sourceIndices = geometry.indexCount();
        long zonedGeometryBytes = geometry instanceof GpuZonedUploadPlan
                ? (long) sourceVertices * NativeSceneVertexLayout.BYTES_PER_VERTEX
                        + (long) sourceIndices * Integer.BYTES
                : 0L;

        statisticsGeometry = geometry;
        cachedGeometryStatistics = new GeometryStatistics(
                sourceVertices, sourceIndices, zonedGeometryBytes);
        return cachedGeometryStatistics;
    }

    private static long geometryBytes(GpuCommandGeometry geometry) {
        if (geometry == null) return 0L;
        return (long) geometry.vertexCount() * NativeSceneVertexLayout.BYTES_PER_VERTEX
                + (long) geometry.indexCount() * Integer.BYTES;
    }

    private void captureGlError() {
        int error = glGetError();
        if (firstGlError == GL_NO_ERROR && error != GL_NO_ERROR) firstGlError = error;
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
    private record PlanStatistics(int decodedTextures, int fallbackTextures,
                                  int unavailableTextures, int missingTextures) {
        private static PlanStatistics empty() {
            return new PlanStatistics(0, 0, 0, 0);
        }
    }

    private record GeometryStatistics(int sourceVertices, int sourceIndices,
                                      long zonedGeometryBytes) {
        private static GeometryStatistics empty() {
            return new GeometryStatistics(0, 0, 0L);
        }
    }

    static final class FrameMetrics {
        private int drawCalls;
        private int renderedIndices;
        private int terrainTriangles;
        private int objectTriangles;

        void reset() {
            drawCalls = 0;
            renderedIndices = 0;
            terrainTriangles = 0;
            objectTriangles = 0;
        }

        void record(GpuDrawCommand command) {
            int triangles = command.indexCount() / 3;
            renderedIndices += command.indexCount();
            if (command.layer() == SceneLayer.Kind.TERRAIN) {
                terrainTriangles += triangles;
            } else {
                objectTriangles += triangles;
            }
        }

        int drawCalls() {
            return drawCalls;
        }

        int renderedIndices() {
            return renderedIndices;
        }

        int terrainTriangles() {
            return terrainTriangles;
        }

        int objectTriangles() {
            return objectTriangles;
        }
    }

    public record Statistics(int sourceVertices, int sourceIndices, int renderedIndices,
                             int terrainTriangles, int objectTriangles,
                             int decodedTextures, int fallbackTextures, int unavailableTextures,
                             int missingTextures,
                             String vendor, String renderer, String version, int firstGlError,
                             int framebufferStatus, int polygonMode, boolean depthWritesEnabled,
                             boolean geometryUploaded, boolean textureUploaded, int drawCalls,
                             long zonedGeometryBytes, int flatMaterializationCount,
                             long flatMaterializationBytes, int gpuZoneUploads,
                             int gpuReusedAllocations, int gpuGeometryStreamUploads,
                             int gpuShadingStreamUploads, int gpuIndexStreamUploads,
                             boolean occlusionApplied) {
        private static Statistics empty() {
            return new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0,
                    "unknown", "unknown", "unknown", GL_NO_ERROR,
                    org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE, GL_FILL, true,
                    false, false, 0, 0L, 0, 0L, 0, 0, 0, 0, 0, false);
        }

        public int renderedTriangles() {
            return renderedIndices / 3;
        }
    }

    private void drawBatches(List<GpuDrawCommand> commands,
                            List<Integer> orderedIndices, GpuCommandVisibility visibility,
                            CameraState camera, boolean alpha) {
        GpuDrawCommand.SubmissionPass pass = alpha
                ? GpuDrawCommand.SubmissionPass.ALPHA
                : GpuDrawCommand.SubmissionPass.OPAQUE;
        GpuDrawBatchPlanner.BatchCursor batches = GpuDrawBatchPlanner.cursor(
                commands, orderedIndices, pass, zoneManager::zoneKeyForCommand);

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
            applyDrawState(first, alpha);
            if (batches.commandCount() == 1) {
                frameMetrics.record(first);
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
                        frameMetrics.record(command);
                        counts.put(command.indexCount());
                        offsets.put((long) zoneManager.localFirstIndex(commandIndex)
                                * Integer.BYTES);
                    }
                    counts.flip();
                    offsets.flip();
                    glMultiDrawElements(GL_TRIANGLES, counts, GL_UNSIGNED_INT, offsets);
                }
            }
            frameMetrics.drawCalls++;
        }
    }

    List<Integer> alphaOrderFor(GpuCommandGeometry geometry,
                                List<GpuDrawCommand> commands,
                                GpuCommandVisibility visibility,
                                CameraState camera) {
        if (geometry == cachedAlphaGeometry
                && commands == cachedAlphaCommandList
                && visibility == cachedAlphaVisibility
                && camera.equals(cachedAlphaCamera)) {
            return cachedAlphaOrder;
        }

        alphaCommands.clear();
        alphaOrder.clear();
        ensureCommandIndices(commands);
        for (int index = 0; index < commands.size(); index++) {
            GpuDrawCommand command = commands.get(index);
            if (command.pass() == GpuDrawCommand.SubmissionPass.ALPHA
                    && visibility.visible(index)) {
                alphaCommands.add(command);
            }
        }

        float cosYaw = (float) Math.cos(camera.yaw());
        float sinYaw = (float) Math.sin(camera.yaw());
        float cosPitch = (float) Math.cos(camera.pitch());
        float sinPitch = (float) Math.sin(camera.pitch());
        List<GpuDrawCommand> orderedAlpha = RsFaceOrderPlanner.orderAlphaReusable(
                alphaCommands,
                command -> averageDepth(geometry, alphaIndices.get(command), command, camera,
                        cosYaw, sinYaw, cosPitch, sinPitch),
                command -> command.wallDecorationPresentation().cameraOrder(
                        command.tile(), camera),
                alphaOrderWorkspace);
        for (GpuDrawCommand command : orderedAlpha) {
            alphaOrder.add(alphaIndices.get(command));
        }

        cachedAlphaGeometry = geometry;
        cachedAlphaCommandList = commands;
        cachedAlphaVisibility = visibility;
        cachedAlphaCamera = camera;
        cachedAlphaOrder = List.copyOf(alphaOrder);
        return cachedAlphaOrder;
    }

    private void ensureCommandIndices(List<GpuDrawCommand> commands) {
        if (commands == indexedCommands) {
            return;
        }
        alphaIndices.clear();
        for (int index = 0; index < commands.size(); index++) {
            alphaIndices.put(commands.get(index), index);
        }
        indexedCommands = commands;
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
        boolean cameraOrderedDecorations = false;
        for (GpuDrawCommand command : commands) {
            if (command.wallDecorationPresentation().cameraOrdered()) {
                cameraOrderedDecorations = true;
                break;
            }
        }
        opaqueOrderWorkspace.clear();
        List<Integer> result = opaqueOrderWorkspace;
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
            return cachedOpaqueOrder;
        }
        return result;
    }

    private void applyDrawState(GpuDrawCommand command, boolean alpha) {
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
        int texLayer = Math.max(0, layer);
        if (texLayer != lastTextureLayer) {
            glUniform1i(textureLayerLocation, texLayer);
            lastTextureLayer = texLayer;
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

    private String textureStateFingerprintCached(
            Map<Integer, RenderTextureResource> resources) {
        if (resources == fingerprintedTextureStateResources
                && fingerprintedTextureStateFingerprint != null) {
            return fingerprintedTextureStateFingerprint;
        }
        String fingerprint = textureStateFingerprint(resources);
        fingerprintedTextureStateResources = resources;
        fingerprintedTextureStateFingerprint = fingerprint;
        return fingerprint;
    }

    private static String textureStateFingerprint(
            Map<Integer, RenderTextureResource> resources) {
        StringBuilder value = new StringBuilder();
        resources.values().stream().sorted(Comparator.comparingInt(RenderTextureResource::id))
                .forEach(texture -> value.append(texture.id())
                        .append(':').append(texture.pixelStatus())
                        .append(':').append(texture.width()).append('x').append(texture.height())
                        .append(':').append(texture.definition().animationDirection())
                        .append(':').append(texture.definition().animationSpeed()).append('|'));
        return sha256(value.toString());
    }

    private static String textureFingerprint(Map<Integer, RenderTextureResource> resources) {
        StringBuilder value = new StringBuilder();
        resources.values().stream().sorted(Comparator.comparingInt(RenderTextureResource::id))
                .forEach(texture -> value.append(texture.id())
                        .append(':').append(texture.pixelStatus())
                        .append(':').append(texture.width()).append('x').append(texture.height())
                        .append(':').append(texture.pixelHash()).append('|'));
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int requiredTextureCapacity(
            Map<Integer, RenderTextureResource> resources) {
        int largestTextureId = resources.values().stream()
                .filter(RenderTextureResource::hasGpuPixels)
                .mapToInt(RenderTextureResource::id)
                .max()
                .orElse(-1);
        return Math.max(TEXTURE_LAYER_CAPACITY, largestTextureId + 1);
    }

    private void uploadTextureArray(Map<Integer, RenderTextureResource> resources) {
        int depth = requiredTextureCapacity(resources);
        capabilityProfile.requireTextureArrayLayers(depth);

        if (textureArray != 0) org.lwjgl.opengl.GL11.glDeleteTextures(textureArray);
        textureLayers.clear();
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
        }
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
    }

    @Override
    public void close() {
        zoneManager.close();
        pickerFramebuffer.close();
        visibilityCache.clear();
        if (paletteTexture != 0) org.lwjgl.opengl.GL11.glDeleteTextures(paletteTexture);
        paletteTexture = 0;
        if (textureArray != 0) org.lwjgl.opengl.GL11.glDeleteTextures(textureArray);
        textureArray = 0;
        textureStateBuffer.close();
        frameUniformBuffer.close();
        textureLayers.clear();
        if (program != 0) org.lwjgl.opengl.GL20.glDeleteProgram(program);
        program = 0;
        if (pickerProgram != 0) org.lwjgl.opengl.GL20.glDeleteProgram(pickerProgram);
        pickerProgram = 0;
        uploadedFingerprint = null;
        uploadedTextureFingerprint = null;
        uploadedTextureStateFingerprint = null;
        fingerprintedTextureResources = null;
        fingerprintedTextureFingerprint = null;
        fingerprintedTextureStateResources = null;
        fingerprintedTextureStateFingerprint = null;
        fogBoundsGeometry = null;
        cachedFogBounds = null;
        orderedPlanFingerprint = null;
        cachedOpaqueOrder = List.of();
        opaqueOrderWorkspace.clear();
        pickerOrderWorkspace.clear();
        alphaCommands.clear();
        alphaIndices.clear();
        indexedCommands = List.of();
        alphaOrder.clear();
        cachedAlphaGeometry = null;
        cachedAlphaVisibility = null;
        cachedAlphaCamera = null;
        cachedAlphaCommandList = List.of();
        cachedAlphaOrder = List.of();
        missingTextureIds.clear();
        frameMetrics.reset();
        statisticsPlan = null;
        cachedPlanStatistics = PlanStatistics.empty();
        statisticsGeometry = null;
        cachedGeometryStatistics = GeometryStatistics.empty();
        diagnosticsLogged = false;
        capabilityProfile = null;
    }


}
