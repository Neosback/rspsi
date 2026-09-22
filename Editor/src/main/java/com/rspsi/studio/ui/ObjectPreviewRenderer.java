package com.rspsi.studio.ui;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuUploadPlanBuilder;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.ModelPacketBuilder;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.ModelTriangle;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.RenderTextureResourceBuilder;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.SceneLayer;
import com.rspsi.editor.render.SceneTileSnapshot;
import com.rspsi.editor.render.SceneWindow;
import com.rspsi.editor.render.SoftwareRenderFrame;
import com.rspsi.editor.render.SoftwareSceneRenderer;
import com.rspsi.editor.render.TerrainRenderFace;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.render.TerrainRenderVertex;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Renders a single object's model to an off-screen software frame and
 * uploads it as a reusable OpenGL texture - the same neutral
 * {@link SoftwareSceneRenderer} pipeline the CPU/GPU parity tests exercise,
 * not the native GL scene machinery. Callers must invoke {@link #render}
 * from the render thread (the same thread that already owns the GL context
 * for the rest of the Studio UI).
 */
public final class ObjectPreviewRenderer {
    private static final SceneCameraProjection PROJECTION =
            new SceneCameraProjection((float) Math.toRadians(45.0), 1.0f, 20_000.0f);

    private int textureId;
    private int textureWidth;
    private int textureHeight;
    private int cachedObjectId = Integer.MIN_VALUE;
    private int cachedType = Integer.MIN_VALUE;
    private int cachedRotation = Integer.MIN_VALUE;
    private GpuUploadPlan cachedPlan;
    private Bounds cachedBounds;
    private boolean cachedEmpty;
    private float lastYaw = Float.NaN;
    private float lastPitch = Float.NaN;
    private float lastZoom = Float.NaN;

    /**
     * Renders the object at the given orbit angles and uploads it to this
     * renderer's texture, returning the GL texture id (0 if the object has
     * no renderable model - e.g. an unresolved multiloc shell). The texture
     * is reused/overwritten across calls; callers must not hold onto the id
     * past the next render.
     *
     * @param zoom multiplies the auto-fit distance; 1.0 is the default
     *             framing, less than 1 moves the camera closer, more moves
     *             it away. Callers own the value (e.g. from a scroll wheel)
     *             and should clamp it to a sane range themselves.
     */
    public int render(DefinitionProvider definitions, int objectId, int type, int rotation,
                      float orbitYaw, float orbitPitch, float zoom, int width, int height) {
        if (definitions == null || width <= 0 || height <= 0) return 0;
        if (cachedPlan == null || cachedObjectId != objectId || cachedType != type
                || cachedRotation != rotation) {
            Built built = buildPlan(definitions, objectId, type, rotation);
            cachedPlan = built == null ? null : built.plan();
            cachedBounds = built == null ? null : built.bounds();
            cachedObjectId = objectId;
            cachedType = type;
            cachedRotation = rotation;
            cachedEmpty = built == null;
            lastYaw = Float.NaN;
        }
        if (cachedEmpty) return 0;
        if (textureId != 0 && orbitYaw == lastYaw && orbitPitch == lastPitch && zoom == lastZoom
                && textureWidth == width && textureHeight == height) {
            return textureId;
        }
        lastYaw = orbitYaw;
        lastPitch = orbitPitch;
        lastZoom = zoom;

        // Distance to fit the bounding sphere inside the vertical FOV cone is
        // radius / sin(fov/2) at the sphere's silhouette edge; 1.6x on top of
        // that is headroom so the model doesn't touch the frame border, then
        // the caller's zoom scales that baseline distance in or out.
        float halfFov = PROJECTION.verticalFieldOfView() * 0.5f;
        float distance = cachedBounds.radius() / (float) Math.sin(halfFov) * 1.6f * zoom;
        float cosPitch = (float) Math.cos(orbitPitch);
        float camX = cachedBounds.centerX() - distance * cosPitch * (float) Math.sin(orbitYaw);
        float camY = cachedBounds.centerY() + distance * (float) Math.sin(orbitPitch);
        float camZ = cachedBounds.centerZ() - distance * cosPitch * (float) Math.cos(orbitYaw);
        CameraState camera = new CameraState(camX, camY, camZ, orbitPitch, orbitYaw);

        SoftwareRenderFrame frame = new SoftwareSceneRenderer()
                .render(cachedPlan, camera, width, height, PROJECTION);
        upload(frame);
        return textureId;
    }

    /** True once a plan has been attempted for this (object, type, rotation) and it had no model. */
    public boolean lastAttemptWasEmpty() {
        return cachedEmpty;
    }

    public void dispose() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    private record Bounds(float centerX, float centerY, float centerZ, float radius) {
    }

    private record Built(GpuUploadPlan plan, Bounds bounds) {
    }

    /**
     * Framing must come from the model's own geometry, not every vertex in
     * the plan - once a floor grid is added purely for scale reference
     * (several whole tiles wide), it would otherwise dominate the bounding
     * box and zoom the camera out until the actual object was a speck.
     */
    private static Bounds boundsOf(ModelRenderPacket model) {
        float centerX = (model.minX() + model.maxX()) / 2.0f;
        float centerY = (model.minY() + model.maxY()) / 2.0f;
        float centerZ = (model.minZ() + model.maxZ()) / 2.0f;
        // The true bounding-SPHERE radius (half the box diagonal), not half
        // of the largest single axis - a diagonal 3/4 view sees corners that
        // extend past any one axis's half-extent, which is exactly what put
        // the camera inside the model at every angle but face-on.
        float dx = model.maxX() - model.minX();
        float dy = model.maxY() - model.minY();
        float dz = model.maxZ() - model.minZ();
        float radius = Math.max(32.0f, 0.5f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        return new Bounds(centerX, centerY, centerZ, radius);
    }

    /**
     * The native renderer this app ships deliberately leaves backface culling
     * off for models - real cache models are not reliably wound consistently
     * (see {@link com.rspsi.editor.render.BackfacePolicy}'s own javadoc), and
     * turning culling on made walls/roofs/bridges disappear when it was
     * tried. {@link com.rspsi.editor.render.SoftwareSceneRenderer} (the
     * pipeline behind this preview) culls unconditionally, so a model that
     * looks complete in the real client can have faces vanish here as the
     * camera orbits past whichever side those faces are wound for - the
     * fountain's basin among them. Emitting each triangle a second time in
     * reverse order gives every face a front-facing winding from both
     * sides, matching the real renderer's no-cull behavior for this one
     * preview instead of re-deriving correct winding per model.
     */
    private static ModelRenderPacket doubleSided(ModelRenderPacket model) {
        List<ModelTriangle> doubled = new ArrayList<>(model.triangles().size() * 2);
        for (ModelTriangle t : model.triangles()) {
            doubled.add(t);
            doubled.add(new ModelTriangle(t.a(), t.c(), t.b(), t.colorA(), t.colorC(), t.colorB(),
                    t.textureId(), t.alpha(), t.priority(), t.renderType(),
                    t.uA(), t.vA(), t.uC(), t.vC(), t.uB(), t.vB(), t.baseColor(), t.depthBias()));
        }
        return new ModelRenderPacket(model.anchor(), model.objectId(), model.category(),
                model.vertices(), List.copyOf(doubled), model.textureTriangles(), model.animationId(),
                model.minX(), model.minY(), model.minZ(), model.maxX(), model.maxY(), model.maxZ(),
                model.supportsAnimation(), model.supportsParticles(), model.placementHeight(),
                model.roofRelated(), model.renderMode());
    }

    // Odd so the object's own tile sits exactly in the middle, matching
    // Displee's viewer: the model on a small patch of ground, not floating
    // in a void, but not so wide the ground reads as "the scene" either.
    private static final int GRID_SIZE = 3;
    private static final int GRID_CENTER = GRID_SIZE / 2;

    private static Built buildPlan(DefinitionProvider definitions, int objectId,
                                   int type, int rotation) {
        WorldDocument document = new WorldDocument(GRID_SIZE, GRID_SIZE, 1);
        WorldObject object = new WorldObject(objectId, type, rotation, 0, GRID_CENTER, GRID_CENTER);
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                List<WorldObject> objects = x == GRID_CENTER && y == GRID_CENTER
                        ? List.of(object) : List.of();
                document.tile(0, x, y).restore(new TileSnapshot(0, 0, 0, 0, -1, -1, 0, 0, 0, objects));
            }
        }

        Optional<ModelRenderPacket> modelOpt = new ModelPacketBuilder(definitions).build(object, document);
        if (modelOpt.isEmpty()) return null;
        ModelRenderPacket model = doubleSided(modelOpt.get());
        Bounds bounds = boundsOf(modelOpt.get());

        Map<Integer, RenderTextureResource> textures = RenderTextureResourceBuilder.build(
                definitions, LightingProfile.osrs(), List.of(), List.of(model));

        SceneLayer.Kind kind = switch (object.category()) {
            case WALL -> SceneLayer.Kind.WALL;
            case WALL_DECOR -> SceneLayer.Kind.WALL_DECORATION;
            case GROUND_DECOR -> SceneLayer.Kind.GROUND_DECORATION;
            default -> SceneLayer.Kind.GROUND_OBJECT;
        };
        SceneLayer modelLayer = new SceneLayer(kind, List.of(0));

        List<SceneTileSnapshot> tiles = new ArrayList<>(GRID_SIZE * GRID_SIZE);
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                WorldTileAddress address = WorldTileAddress.of(x, y, 0);
                TileCoordinate coordinate = new TileCoordinate(0, x, y);
                boolean center = x == GRID_CENTER && y == GRID_CENTER;
                tiles.add(new SceneTileSnapshot(coordinate, address, 0, 0,
                        Optional.empty(), Optional.of(scaleFloor(coordinate)),
                        center ? List.of(model) : List.of(),
                        center ? List.of(modelLayer) : List.of(),
                        List.of(), false, false));
            }
        }

        SceneWindow window = new SceneWindow(new WorldRegionWindow(0, 0, 1, 1, Map.of()),
                0, 0, 1, 0, Set.of(), List.of());
        GpuScenePacket packet = new GpuScenePacket(window, tiles, LightingProfile.osrs(),
                "object-preview-" + objectId, textures);
        return new Built(new GpuUploadPlanBuilder().build(packet), bounds);
    }

    /**
     * A faint, flat reference tile so the object reads as sitting on
     * ground at a known scale instead of floating in a void.
     *
     * <p>A true wireframe grid (thin bordered strips, nothing filled
     * between) was attempted here first, matching Displee's viewer exactly,
     * but those thin terrain strips did not render through this pipeline
     * for reasons that didn't resolve with alpha, width, or brightness
     * changes - worth a real fix later, but not at the cost of shipping
     * nothing. A plain semi-transparent tile is a confirmed-working
     * fallback: less faithful to the reference image, but it still answers
     * the actual question ("how big is this relative to a tile?").</p>
     */
    private static TerrainRenderPacket scaleFloor(TileCoordinate coordinate) {
        int packedGrey = OsrsTerrainColorMath.packHsl(0, 0, 110);
        int alpha = 70;
        List<TerrainRenderVertex> vertices = List.of(
                new TerrainRenderVertex(0, 0, 0, packedGrey, 0, 0),
                new TerrainRenderVertex(128, 0, 0, packedGrey, 128, 0),
                new TerrainRenderVertex(128, 128, 0, packedGrey, 128, 128),
                new TerrainRenderVertex(0, 128, 0, packedGrey, 0, 128));
        List<TerrainRenderFace> faces = List.of(
                new TerrainRenderFace(0, 1, 2, 0, -1, alpha, 0),
                new TerrainRenderFace(0, 2, 3, 0, -1, alpha, 0));
        return new TerrainRenderPacket(coordinate, vertices, faces, 0, 0, -1, packedGrey, -1, true, false);
    }

    private void upload(SoftwareRenderFrame frame) {
        int w = frame.width();
        int h = frame.height();
        ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
        for (int pixel : frame.argb()) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();

        if (textureId == 0 || textureWidth != w || textureHeight != h) {
            if (textureId != 0) GL11.glDeleteTextures(textureId);
            textureId = GL11.glGenTextures();
            textureWidth = w;
            textureHeight = h;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
}
