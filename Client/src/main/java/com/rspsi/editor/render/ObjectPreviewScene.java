package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A single object staged for a turntable preview: the object placed on a
 * small patch of empty tiles, framed from its front, with a semi-transparent
 * one-tile grid drawn on the ground so the preview shows in-game scale.
 *
 * <p>Renderer-neutral: {@link #render} returns ARGB pixels from
 * {@link SoftwareSceneRenderer}, so it is testable headless. The Studio
 * uploads the pixels as a texture.</p>
 *
 * <p>Orbit angles are relative to the object's front. Game objects (types 10,
 * 11, 22) at rotation 0 face south, like an NPC at orientation 0
 * ({@code Actor.getOrientation}: 0 = south) whose model is drawn unrotated;
 * each quarter turn of rotation turns that front clockwise
 * ({@code ModelPacketBuilder.rotateQuarterTurn}). Walls and wall decoration
 * (types 0-9) sit on the tile edge named by the rotation (0 = west) and are
 * seen from inside their tile, which is where wall decoration is displaced
 * to.</p>
 */
public final class ObjectPreviewScene {
    /** Default orbit: a little to the side of dead-front, so the model reads as 3D. */
    public static final float DEFAULT_ORBIT_YAW = 0.3f;
    /** Default elevation: slightly above the object, looking down at it. */
    public static final float DEFAULT_ELEVATION = 0.38f;
    public static final float MIN_ELEVATION = -0.25f;
    public static final float MAX_ELEVATION = 1.45f;

    public static final SceneCameraProjection PROJECTION =
            new SceneCameraProjection((float) Math.toRadians(40.0), 1.0f, 20_000.0f);

    private static final int TILE = 128;
    /** Empty tiles of grid around the object's footprint. */
    private static final int MARGIN = 1;
    /** Fit headroom around the bounding sphere so the model does not touch the frame. */
    private static final float FIT_HEADROOM = 1.08f;

    private static final int GRID_FILL = 0x3A5A7A;
    private static final int GRID_LINE = 0xB8C8DC;
    private static final int FOOTPRINT = 0x4FA3FF;

    private final GpuUploadPlan plan;
    private final int type;
    private final int rotation;
    private final int gridWidth;
    private final int gridLength;
    private final int footprintWidth;
    private final int footprintLength;
    private final float groundY;
    private final float centerX;
    private final float centerY;
    private final float centerZ;
    private final float radius;

    private ObjectPreviewScene(GpuUploadPlan plan, int type, int rotation, int footprintWidth,
                               int footprintLength, float groundY, float[] box) {
        this.plan = plan;
        this.type = type;
        this.rotation = rotation;
        this.footprintWidth = footprintWidth;
        this.footprintLength = footprintLength;
        this.gridWidth = footprintWidth + MARGIN * 2;
        this.gridLength = footprintLength + MARGIN * 2;
        this.groundY = groundY;
        this.centerX = (box[0] + box[3]) * 0.5f;
        this.centerY = (box[1] + box[4]) * 0.5f;
        this.centerZ = (box[2] + box[5]) * 0.5f;
        float dx = box[3] - box[0];
        float dy = box[4] - box[1];
        float dz = box[5] - box[2];
        this.radius = Math.max(48.0f, 0.5f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** Stages an object, or returns empty when it has no renderable model in the cache's var state. */
    public static Optional<ObjectPreviewScene> build(DefinitionProvider definitions, int objectId,
                                                     int type, int rotation) {
        int[] footprint = footprint(definitions, objectId, type, rotation);
        int width = footprint[0] + MARGIN * 2;
        int length = footprint[1] + MARGIN * 2;
        WorldDocument document = new WorldDocument(width, length, 1);
        WorldObject object = new WorldObject(objectId, type, rotation, 0, MARGIN, MARGIN);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                List<WorldObject> objects = x == MARGIN && y == MARGIN ? List.of(object) : List.of();
                document.tile(0, x, y).restore(new TileSnapshot(0, 0, 0, 0, -1, -1, 0, 0, 0, objects));
            }
        }
        Optional<ModelRenderPacket> built = new ModelPacketBuilder(definitions).build(object, document);
        if (built.isEmpty() || built.get().triangles().isEmpty()) return Optional.empty();
        ModelRenderPacket model = doubleSided(built.get());

        Map<Integer, RenderTextureResource> textures = RenderTextureResourceBuilder.build(
                definitions, LightingProfile.osrs(), List.of(), List.of(model));
        SceneLayer.Kind kind = switch (object.category()) {
            case WALL -> SceneLayer.Kind.WALL;
            case WALL_DECOR -> SceneLayer.Kind.WALL_DECORATION;
            case GROUND_DECOR -> SceneLayer.Kind.GROUND_DECORATION;
            default -> SceneLayer.Kind.GROUND_OBJECT;
        };
        TileCoordinate coordinate = new TileCoordinate(0, MARGIN, MARGIN);
        SceneTileSnapshot tile = new SceneTileSnapshot(coordinate, WorldTileAddress.of(MARGIN, MARGIN, 0),
                0, 0, Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(kind, List.of(0))), List.of(), false, false);
        SceneWindow window = new SceneWindow(new WorldRegionWindow(0, 0, 1, 1, Map.of()),
                0, 0, 1, 0, Set.of(), List.of());
        GpuScenePacket packet = new GpuScenePacket(window, List.of(tile), LightingProfile.osrs(),
                "object-preview-" + objectId, textures);
        GpuUploadPlan plan = new GpuUploadPlanBuilder().build(packet);

        // Frame the model where the upload actually puts it (the packet is
        // relative to its anchor tile's SW corner; GpuUploadPlanBuilder adds
        // anchor*128 and the render placement height), unioned with the
        // footprint on the ground so the tile it stands on is always in shot.
        float groundY = model.renderPlacementHeight();
        float originX = MARGIN * TILE;
        float originZ = MARGIN * TILE;
        float[] box = {
                Math.min(originX + model.minX(), originX),
                Math.min(groundY + model.minY(), groundY),
                Math.min(originZ + model.minZ(), originZ),
                Math.max(originX + model.maxX(), originX + footprint[0] * TILE),
                Math.max(groundY + model.maxY(), groundY),
                Math.max(originZ + model.maxZ(), originZ + footprint[1] * TILE)};
        return Optional.of(new ObjectPreviewScene(plan, type, rotation, footprint[0], footprint[1], groundY, box));
    }

    /**
     * Renders the staged object.
     *
     * @param orbitYaw  radians around the object, 0 = straight at its front
     * @param elevation radians above the horizon, positive = camera above looking down
     * @param zoom      multiplies the fit distance; 1 frames the object, less is closer
     */
    public int[] render(float orbitYaw, float elevation, float zoom, int width, int height) {
        CameraState camera = camera(orbitYaw, elevation, zoom, width, height);
        SoftwareRenderFrame frame = new SoftwareSceneRenderer().render(plan, camera, width, height, PROJECTION);
        int[] pixels = frame.argb();
        drawGround(pixels, width, height, camera);
        return pixels;
    }

    public CameraState camera(float orbitYaw, float elevation, float zoom, int width, int height) {
        float yaw = frontYaw(type, rotation) + orbitYaw;
        // Fit the bounding sphere in the narrower field of view.
        float halfVertical = PROJECTION.verticalFieldOfView() * 0.5f;
        float halfHorizontal = (float) Math.atan(Math.tan(halfVertical) * width / (double) height);
        float halfFov = Math.min(halfVertical, halfHorizontal);
        float distance = radius / (float) Math.sin(halfFov) * FIT_HEADROOM * zoom;
        float cosElevation = (float) Math.cos(elevation);
        // RuneScape Y is negative-up, so a camera above the object has a smaller Y.
        float x = centerX - distance * cosElevation * (float) Math.sin(yaw);
        float y = centerY - distance * (float) Math.sin(elevation);
        float z = centerZ - distance * cosElevation * (float) Math.cos(yaw);
        return new CameraState(x, y, z, -elevation, yaw);
    }

    /** Camera yaw that looks at the object's front (see the class docs). */
    public static float frontYaw(int type, int rotation) {
        int quarter = rotation & 3;
        if (type >= 0 && type <= 9) quarter -= 1;
        return quarter * (float) (Math.PI / 2.0);
    }

    public int gridWidth() { return gridWidth; }

    public int gridLength() { return gridLength; }

    public int footprintWidth() { return footprintWidth; }

    public int footprintLength() { return footprintLength; }

    /** Object footprint in tiles after rotation; walls and decoration occupy one tile. */
    static int[] footprint(DefinitionProvider definitions, int objectId, int type, int rotation) {
        if (type != 10 && type != 11) return new int[]{1, 1};
        Optional<ObjectDefinitionView> definition = definitions.object(objectId);
        int width = definition.map(ObjectDefinitionView::width).orElse(1);
        int length = definition.map(ObjectDefinitionView::length).orElse(1);
        width = Math.max(1, Math.min(width, 16));
        length = Math.max(1, Math.min(length, 16));
        return (rotation & 1) == 1 ? new int[]{length, width} : new int[]{width, length};
    }

    /**
     * Draws the ground grid behind the model: each background pixel's view
     * ray is intersected with the ground plane, and grid lines are
     * anti-aliased from the ground-space distance to the nearest tile edge
     * (a shader-style {@code fwidth} grid). Only background pixels are
     * touched, so the model always occludes the ground it stands on.
     */
    private void drawGround(int[] pixels, int width, int height, CameraState camera) {
        float focal = (float) ((height * 0.5) / Math.tan(PROJECTION.verticalFieldOfView() * 0.5));
        float cosYaw = (float) Math.cos(camera.yaw());
        float sinYaw = (float) Math.sin(camera.yaw());
        float cosPitch = (float) Math.cos(camera.pitch());
        float sinPitch = (float) Math.sin(camera.pitch());
        float heightAboveGround = groundY - camera.y();

        // Ground-plane hit per pixel corner row, so each pixel can difference its neighbours.
        float[] groundX = new float[(width + 1) * (height + 1)];
        float[] groundZ = new float[groundX.length];
        for (int py = 0; py <= height; py++) {
            for (int px = 0; px <= width; px++) {
                float viewX = (px - width * 0.5f) / focal;
                float viewY = -(py - height * 0.5f) / focal;
                // Inverse of SoftwareSceneRenderer.view: pitch, then yaw.
                float up = viewY * cosPitch + sinPitch;
                float yawDepth = -viewY * sinPitch + cosPitch;
                float dirX = viewX * cosYaw + yawDepth * sinYaw;
                float dirZ = -viewX * sinYaw + yawDepth * cosYaw;
                int index = py * (width + 1) + px;
                // World Y along the ray is camera.y - t*up; it reaches the ground where t > 0.
                float t = -heightAboveGround / up;
                if (!(t > 0.0f) || Float.isInfinite(t)) {
                    groundX[index] = Float.NaN;
                    groundZ[index] = Float.NaN;
                    continue;
                }
                groundX[index] = camera.x() + dirX * t;
                groundZ[index] = camera.z() + dirZ * t;
            }
        }

        int background = SoftwareSceneRenderer.BACKGROUND;
        float maxX = gridWidth * TILE;
        float maxZ = gridLength * TILE;
        float footMinX = MARGIN * TILE;
        float footMinZ = MARGIN * TILE;
        float footMaxX = footMinX + footprintWidth * TILE;
        float footMaxZ = footMinZ + footprintLength * TILE;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int offset = py * width + px;
                if (pixels[offset] != background) continue;
                int corner = py * (width + 1) + px;
                float x0 = groundX[corner];
                float z0 = groundZ[corner];
                float x1 = groundX[corner + 1];
                float z1 = groundZ[corner + 1];
                float x2 = groundX[corner + width + 1];
                float z2 = groundZ[corner + width + 1];
                float x3 = groundX[corner + width + 2];
                float z3 = groundZ[corner + width + 2];
                if (Float.isNaN(x0) || Float.isNaN(x1) || Float.isNaN(x2) || Float.isNaN(x3)) continue;
                float gx = (x0 + x1 + x2 + x3) * 0.25f;
                float gz = (z0 + z1 + z2 + z3) * 0.25f;
                // Ground units covered by this pixel along each axis.
                float spanX = Math.max(Math.abs(x1 - x0) + Math.abs(x2 - x0), 1e-3f);
                float spanZ = Math.max(Math.abs(z1 - z0) + Math.abs(z2 - z0), 1e-3f);
                float inside = Math.min(coverage(gx, 0.0f, maxX, spanX), coverage(gz, 0.0f, maxZ, spanZ));
                if (inside <= 0.0f) continue;

                float footprint = Math.min(coverage(gx, footMinX, footMaxX, spanX),
                        coverage(gz, footMinZ, footMaxZ, spanZ));
                float line = Math.max(lineCoverage(gx, spanX), lineCoverage(gz, spanZ));
                float outline = footprint > 0.0f ? Math.max(
                        Math.max(edge(gx, footMinX, spanX), edge(gx, footMaxX, spanX)),
                        Math.max(edge(gz, footMinZ, spanZ), edge(gz, footMaxZ, spanZ))) : 0.0f;
                // Fade the grid out towards its outer edge so it reads as a patch of ground.
                float fade = Math.min(Math.min(gx, maxX - gx) / (TILE * 0.6f),
                        Math.min(gz, maxZ - gz) / (TILE * 0.6f));
                fade = clamp01(0.35f + fade);

                int color = pixels[offset];
                color = blend(color, GRID_FILL, 0.30f * inside * fade);
                color = blend(color, FOOTPRINT, 0.22f * footprint);
                color = blend(color, GRID_LINE, 0.45f * line * inside * fade);
                color = blend(color, FOOTPRINT, 0.85f * outline * footprint);
                pixels[offset] = color;
            }
        }
    }

    /** 1 inside [min, max], 0 outside, anti-aliased over one pixel's ground span. */
    private static float coverage(float value, float min, float max, float span) {
        return clamp01(Math.min(value - min, max - value) / span + 0.5f);
    }

    /** Coverage of a one-pixel-wide line on every tile edge. */
    private static float lineCoverage(float value, float span) {
        float distance = Math.abs(value - Math.round(value / TILE) * TILE);
        return clamp01(1.0f - distance / span);
    }

    /** Coverage of a line about 1.5 pixels wide at one coordinate. */
    private static float edge(float value, float at, float span) {
        return clamp01(1.5f - Math.abs(value - at) / span);
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : Math.min(1.0f, value);
    }

    private static int blend(int argb, int rgb, float amount) {
        if (amount <= 0.0f) return argb;
        float keep = 1.0f - amount;
        int r = (int) (((argb >> 16) & 0xFF) * keep + ((rgb >> 16) & 0xFF) * amount);
        int g = (int) (((argb >> 8) & 0xFF) * keep + ((rgb >> 8) & 0xFF) * amount);
        int b = (int) ((argb & 0xFF) * keep + (rgb & 0xFF) * amount);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * The native renderer leaves backface culling off for models (see
     * {@link BackfacePolicy}), but {@link SoftwareSceneRenderer} culls, so
     * every triangle is emitted a second time with reversed winding to
     * match the no-cull look from any orbit angle.
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
}
