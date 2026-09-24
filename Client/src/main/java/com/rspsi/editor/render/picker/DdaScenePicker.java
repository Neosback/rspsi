package com.rspsi.editor.render.picker;

import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.GpuZonedUploadPlan;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.SceneOcclusionResolver;

import java.util.Objects;
import java.util.Optional;

/**
 * CPU scene picker based on Amanatides-Woo uniform-grid traversal.
 *
 * <p>The picker consumes the renderer's resident 8x8 zone geometry when it is
 * available. Unchanged GPU zones therefore retain their triangle metadata and
 * direct 8x8 tile buckets across local edits. Large objects remain pickable
 * outside their anchor zone because their triangle bounds are indexed into
 * every spatial zone they overlap.</p>
 */
public final class DdaScenePicker {
    private static final float TILE_SIZE = 128.0f;
    private static final float EPSILON = 1.0e-5f;

    private final PickingSpatialIndex spatialIndex = new PickingSpatialIndex();
    private final PickScratch scratch = new PickScratch();
    private Metrics lastMetrics = Metrics.empty();

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY) {
        return pick(plan, null, camera, width, height, screenX, screenY,
                SceneCameraProjection.editorDefault(), null);
    }

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY,
                                     SceneCameraProjection projection) {
        return pick(plan, null, camera, width, height, screenX, screenY, projection, null);
    }

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY,
                                     SceneCameraProjection projection, Integer restrictToPlane) {
        return pick(plan, null, camera, width, height, screenX, screenY,
                projection, restrictToPlane);
    }

    /**
     * Picks against a flat compatibility plan while reusing the corresponding
     * native zoned plan when available.
     */
    public Optional<PickResult> pick(GpuUploadPlan plan, GpuZonedUploadPlan zonedPlan,
                                     CameraState camera, int width, int height,
                                     float screenX, float screenY,
                                     SceneCameraProjection projection, Integer restrictToPlane) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(projection, "projection");
        if (width <= 0 || height <= 0 || !Float.isFinite(screenX) || !Float.isFinite(screenY)
                || screenX < 0.0f || screenY < 0.0f || screenX >= width || screenY >= height
                || (restrictToPlane != null
                    && (restrictToPlane < 0 || restrictToPlane >= PickingSpatialIndex.PLANE_COUNT))) {
            lastMetrics = Metrics.empty();
            return Optional.empty();
        }

        PickingSpatialIndex.Snapshot index = spatialIndex.indexFor(plan, zonedPlan);
        if (index.isEmpty()) {
            lastMetrics = Metrics.empty();
            return Optional.empty();
        }

        buildRay(camera, width, height, screenX, screenY, projection, scratch);
        if (!intersectGridBounds(scratch, index, projection)) {
            lastMetrics = Metrics.empty();
            return Optional.empty();
        }

        float startT = scratch.intervalEnter;
        float sampleT = Math.min(scratch.intervalExit, startT + 1.0e-3f);
        int tileX = floorTile(scratch.ox + scratch.dx * sampleT);
        int tileY = floorTile(scratch.oz + scratch.dz * sampleT);
        tileX = clamp(tileX, index.minTileX(), index.maxTileX());
        tileY = clamp(tileY, index.minTileY(), index.maxTileY());

        int stepX = sign(scratch.dx);
        int stepY = sign(scratch.dz);
        float tDeltaX = stepX == 0 ? Float.POSITIVE_INFINITY : TILE_SIZE / Math.abs(scratch.dx);
        float tDeltaY = stepY == 0 ? Float.POSITIVE_INFINITY : TILE_SIZE / Math.abs(scratch.dz);
        float tMaxX = firstBoundaryT(scratch.ox, scratch.dx, tileX, stepX, startT, tDeltaX);
        float tMaxY = firstBoundaryT(scratch.oz, scratch.dz, tileY, stepY, startT, tDeltaY);

        int generation = spatialIndex.beginPick();
        PickingSpatialIndex.TriangleRef bestTriangle = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        int visitedCells = 0;
        int triangleTests = 0;
        int duplicateSkips = 0;
        int broadPhaseTests = 0;
        int broadPhaseRejects = 0;
        int broadPhaseTriangleSkips = 0;
        float currentT = startT;

        while (currentT <= scratch.intervalExit + EPSILON
                && tileX >= index.minTileX() && tileX <= index.maxTileX()
                && tileY >= index.minTileY() && tileY <= index.maxTileY()) {
            visitedCells++;

            int firstPlane = restrictToPlane == null ? 0 : restrictToPlane;
            int lastPlane = restrictToPlane == null
                    ? PickingSpatialIndex.PLANE_COUNT - 1 : restrictToPlane;
            for (int plane = firstPlane; plane <= lastPlane; plane++) {
                PickingSpatialIndex.TriangleRef[] bucket = index.bucket(plane, tileX, tileY);
                for (PickingSpatialIndex.TriangleRef triangle : bucket) {
                    if (!triangle.markTested(generation)) {
                        duplicateSkips++;
                        continue;
                    }
                    byte broadPhase = triangle.broadPhase(generation,
                            scratch.ox, scratch.oy, scratch.oz,
                            scratch.dx, scratch.dy, scratch.dz,
                            projection.nearPlane(), projection.farPlane());
                    if (broadPhase == 3 || broadPhase == 4) {
                        broadPhaseTests++;
                        if (broadPhase == 4) broadPhaseRejects++;
                    }
                    if (broadPhase == 2 || broadPhase == 4) {
                        broadPhaseTriangleSkips++;
                        continue;
                    }

                    if (SceneOcclusionResolver.occludesTriangle(triangle.command(),
                            triangle.a(), triangle.b(), triangle.c(), camera, plan.occluders())) {
                        continue;
                    }
                    triangleTests++;
                    float distance = intersect(scratch, triangle.a(), triangle.b(), triangle.c());
                    if (!Float.isFinite(distance)
                            || distance < projection.nearPlane()
                            || distance > projection.farPlane()) {
                        continue;
                    }
                    if (precedes(triangle, distance, bestTriangle, bestDistance)) {
                        bestTriangle = triangle;
                        bestDistance = distance;
                    }
                }
            }

            float nextBoundary = Math.min(Math.min(tMaxX, tMaxY), scratch.intervalExit);
            if (bestTriangle != null && bestDistance <= nextBoundary + EPSILON) {
                lastMetrics = new Metrics(visitedCells, triangleTests, duplicateSkips,
                        index.triangleCount(), true, broadPhaseTests, broadPhaseRejects,
                        broadPhaseTriangleSkips);
                return Optional.of(toResult(bestTriangle, bestDistance, scratch));
            }

            if (stepX == 0 && stepY == 0) break;

            if (Math.abs(tMaxX - tMaxY) <= EPSILON) {
                currentT = tMaxX;
                tileX += stepX;
                tileY += stepY;
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
            } else if (tMaxX < tMaxY) {
                currentT = tMaxX;
                tileX += stepX;
                tMaxX += tDeltaX;
            } else {
                currentT = tMaxY;
                tileY += stepY;
                tMaxY += tDeltaY;
            }
        }

        lastMetrics = new Metrics(visitedCells, triangleTests, duplicateSkips,
                index.triangleCount(), bestTriangle != null, broadPhaseTests,
                broadPhaseRejects, broadPhaseTriangleSkips);
        return bestTriangle == null
                ? Optional.empty()
                : Optional.of(toResult(bestTriangle, bestDistance, scratch));
    }

    /** Diagnostics for the most recent pick, useful for performance overlays/tests. */
    public Metrics lastMetrics() {
        return lastMetrics;
    }

    /** Diagnostics for the most recent spatial-index update. */
    public IndexMetrics lastIndexMetrics() {
        PickingSpatialIndex.Metrics metrics = spatialIndex.lastMetrics();
        return new IndexMetrics(metrics.rebuiltZones(), metrics.reusedZones(), metrics.totalZones(),
                metrics.rebuiltSourceZones(), metrics.reusedSourceZones(),
                metrics.totalSourceZones());
    }

    private static boolean precedes(PickingSpatialIndex.TriangleRef candidate,
                                    float candidateDistance,
                                    PickingSpatialIndex.TriangleRef current,
                                    float currentDistance) {
        if (current == null) return true;
        if (candidateDistance < currentDistance - EPSILON) return true;
        if (Math.abs(candidateDistance - currentDistance) > EPSILON) return false;
        if (candidate.command().priority() != current.command().priority()) {
            return candidate.command().priority() > current.command().priority();
        }
        return candidate.order() < current.order();
    }

    private static PickResult toResult(PickingSpatialIndex.TriangleRef triangle,
                                       float distance, PickScratch ray) {
        GpuDrawCommand command = triangle.command();
        WorldTileAddress address = command.tile();
        float hitX = ray.ox + distance * ray.dx;
        float hitZ = ray.oz + distance * ray.dz;
        WorldTile hitTile = new WorldTile(address.plane(), floorTile(hitX), floorTile(hitZ));
        WorldTile objectTile = command.objectId() >= 0
                ? new WorldTile(address.plane(), address.worldX(), address.worldY())
                : hitTile;
        return new PickResult(hitTile, objectTile, address.plane(), command.objectId(), distance,
                command.layer(), command.priority(), command.depthBias(), command.textureId());
    }

    private static boolean intersectGridBounds(PickScratch ray,
                                               PickingSpatialIndex.Snapshot index,
                                               SceneCameraProjection projection) {
        float minX = index.minTileX() * TILE_SIZE;
        float maxX = (index.maxTileX() + 1) * TILE_SIZE;
        float minZ = index.minTileY() * TILE_SIZE;
        float maxZ = (index.maxTileY() + 1) * TILE_SIZE;

        ray.intervalEnter = projection.nearPlane();
        ray.intervalExit = projection.farPlane();
        if (!clipSlab(ray, ray.ox, ray.dx, minX, maxX)) return false;
        if (!clipSlab(ray, ray.oz, ray.dz, minZ, maxZ)) return false;
        return ray.intervalExit + EPSILON >= ray.intervalEnter;
    }

    private static boolean clipSlab(PickScratch scratch, float origin, float direction,
                                    float min, float max) {
        if (Math.abs(direction) <= EPSILON) {
            return origin >= min && origin <= max;
        }
        float a = (min - origin) / direction;
        float b = (max - origin) / direction;
        float near = Math.min(a, b);
        float far = Math.max(a, b);
        scratch.intervalEnter = Math.max(scratch.intervalEnter, near);
        scratch.intervalExit = Math.min(scratch.intervalExit, far);
        return scratch.intervalExit + EPSILON >= scratch.intervalEnter;
    }

    private static float firstBoundaryT(float origin, float direction, int tile, int step,
                                        float startT, float delta) {
        if (step == 0) return Float.POSITIVE_INFINITY;
        float boundary = (step > 0 ? tile + 1 : tile) * TILE_SIZE;
        float value = (boundary - origin) / direction;
        while (value < startT - EPSILON) value += delta;
        return value;
    }

    private static void buildRay(CameraState camera, int width, int height,
                                 float screenX, float screenY,
                                 SceneCameraProjection projection, PickScratch out) {
        float focal = (float) ((height * 0.5) / Math.tan(projection.verticalFieldOfView() * 0.5));
        float cameraX = (screenX - width * 0.5f) / focal;
        float cameraY = (height * 0.5f - screenY) / focal;
        float yawDepth = -cameraY * (float) Math.sin(camera.pitch())
                + (float) Math.cos(camera.pitch());
        float worldY = -(cameraY * (float) Math.cos(camera.pitch())
                + (float) Math.sin(camera.pitch()));
        float worldX = cameraX * (float) Math.cos(camera.yaw())
                + yawDepth * (float) Math.sin(camera.yaw());
        float worldZ = -cameraX * (float) Math.sin(camera.yaw())
                + yawDepth * (float) Math.cos(camera.yaw());

        float length = (float) Math.sqrt(worldX * worldX + worldY * worldY + worldZ * worldZ);
        if (!Float.isFinite(length) || length <= EPSILON) {
            throw new IllegalArgumentException("Pick ray direction cannot be zero");
        }
        out.ox = camera.x();
        out.oy = camera.y();
        out.oz = camera.z();
        out.dx = worldX / length;
        out.dy = worldY / length;
        out.dz = worldZ / length;
    }

    private static float intersect(PickScratch ray, GpuSceneVertex a,
                                   GpuSceneVertex b, GpuSceneVertex c) {
        float edge1X = b.x() - a.x();
        float edge1Y = b.y() - a.y();
        float edge1Z = b.z() - a.z();
        float edge2X = c.x() - a.x();
        float edge2Y = c.y() - a.y();
        float edge2Z = c.z() - a.z();
        float pX = ray.dy * edge2Z - ray.dz * edge2Y;
        float pY = ray.dz * edge2X - ray.dx * edge2Z;
        float pZ = ray.dx * edge2Y - ray.dy * edge2X;
        float determinant = edge1X * pX + edge1Y * pY + edge1Z * pZ;
        if (Math.abs(determinant) <= EPSILON) return Float.NaN;
        float inverse = 1.0f / determinant;
        float tX = ray.ox - a.x();
        float tY = ray.oy - a.y();
        float tZ = ray.oz - a.z();
        float u = (tX * pX + tY * pY + tZ * pZ) * inverse;
        if (u < -EPSILON || u > 1.0f + EPSILON) return Float.NaN;
        float qX = tY * edge1Z - tZ * edge1Y;
        float qY = tZ * edge1X - tX * edge1Z;
        float qZ = tX * edge1Y - tY * edge1X;
        float v = (ray.dx * qX + ray.dy * qY + ray.dz * qZ) * inverse;
        if (v < -EPSILON || u + v > 1.0f + EPSILON) return Float.NaN;
        return (edge2X * qX + edge2Y * qY + edge2Z * qZ) * inverse;
    }

    private static int floorTile(float worldCoordinate) {
        return (int) Math.floor(worldCoordinate / TILE_SIZE);
    }

    private static int sign(float value) {
        if (value > EPSILON) return 1;
        if (value < -EPSILON) return -1;
        return 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Metrics(int visitedCells, int triangleTests, int duplicateSkips,
                          int indexedTriangles, boolean hit,
                          int broadPhaseTests, int broadPhaseRejects,
                          int broadPhaseTriangleSkips) {
        public Metrics {
            if (visitedCells < 0 || triangleTests < 0 || duplicateSkips < 0
                    || indexedTriangles < 0 || broadPhaseTests < 0
                    || broadPhaseRejects < 0 || broadPhaseTriangleSkips < 0) {
                throw new IllegalArgumentException("Picker metrics cannot be negative");
            }
            if (broadPhaseRejects > broadPhaseTests) {
                throw new IllegalArgumentException("AABB rejects cannot exceed AABB tests");
            }
        }

        public static Metrics empty() {
            return new Metrics(0, 0, 0, 0, false, 0, 0, 0);
        }
    }

    public record IndexMetrics(int rebuiltZones, int reusedZones, int totalZones,
                               int rebuiltSourceZones, int reusedSourceZones,
                               int totalSourceZones) {
        public IndexMetrics {
            if (rebuiltZones < 0 || reusedZones < 0 || totalZones < 0
                    || rebuiltSourceZones < 0 || reusedSourceZones < 0
                    || totalSourceZones < 0) {
                throw new IllegalArgumentException("Picker index metrics cannot be negative");
            }
        }
    }

    private static final class PickScratch {
        private float ox;
        private float oy;
        private float oz;
        private float dx;
        private float dy;
        private float dz;
        private float intervalEnter;
        private float intervalExit;
    }
}
