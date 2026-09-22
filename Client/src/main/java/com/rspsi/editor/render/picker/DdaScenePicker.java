package com.rspsi.editor.render.picker;

import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.GpuSceneVertex;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.SceneLayer;
import com.rspsi.editor.render.SceneOcclusionResolver;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * CPU scene picker based on Amanatides-Woo uniform-grid traversal.
 *
 * <p>The immutable GPU plan is spatially indexed once per plan identity.
 * Picking then visits only the X/Z tile cells crossed by the camera ray and
 * ray-tests triangles assigned to those cells. Object triangles are inserted
 * into every tile overlapped by their world-space bounds, so large models are
 * pickable outside their anchor tile without falling back to a scene-wide
 * scan.</p>
 */
public final class DdaScenePicker {
    private static final float TILE_SIZE = 128.0f;
    private static final float EPSILON = 1.0e-5f;

    private GpuUploadPlan cachedPlan;
    private SpatialIndex cachedIndex;
    private Metrics lastMetrics = Metrics.empty();

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY) {
        return pick(plan, camera, width, height, screenX, screenY,
                SceneCameraProjection.editorDefault(), null);
    }

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY,
                                     SceneCameraProjection projection) {
        return pick(plan, camera, width, height, screenX, screenY, projection, null);
    }

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY,
                                     SceneCameraProjection projection, Integer restrictToPlane) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(projection, "projection");
        if (width <= 0 || height <= 0 || !Float.isFinite(screenX) || !Float.isFinite(screenY)
                || screenX < 0.0f || screenY < 0.0f || screenX >= width || screenY >= height
                || (restrictToPlane != null && restrictToPlane < 0)) {
            lastMetrics = Metrics.empty();
            return Optional.empty();
        }

        SpatialIndex index = indexFor(plan);
        if (index.triangles().isEmpty()) {
            lastMetrics = Metrics.empty();
            return Optional.empty();
        }

        Ray ray = ray(camera, width, height, screenX, screenY, projection);
        Interval interval = intersectGridBounds(ray, index, projection);
        if (interval == null) {
            lastMetrics = Metrics.empty();
            return Optional.empty();
        }

        float startT = interval.enter();
        float sampleT = Math.min(interval.exit(), startT + 1.0e-3f);
        int tileX = floorTile(ray.ox() + ray.dx() * sampleT);
        int tileY = floorTile(ray.oz() + ray.dz() * sampleT);
        tileX = clamp(tileX, index.minTileX(), index.maxTileX());
        tileY = clamp(tileY, index.minTileY(), index.maxTileY());

        int stepX = sign(ray.dx());
        int stepY = sign(ray.dz());
        float tDeltaX = stepX == 0 ? Float.POSITIVE_INFINITY : TILE_SIZE / Math.abs(ray.dx());
        float tDeltaY = stepY == 0 ? Float.POSITIVE_INFINITY : TILE_SIZE / Math.abs(ray.dz());
        float tMaxX = firstBoundaryT(ray.ox(), ray.dx(), tileX, stepX, startT, tDeltaX);
        float tMaxY = firstBoundaryT(ray.oz(), ray.dz(), tileY, stepY, startT, tDeltaY);

        BitSet tested = new BitSet(index.triangles().size());
        Hit best = null;
        int visitedCells = 0;
        int triangleTests = 0;
        int duplicateSkips = 0;
        float currentT = startT;

        while (currentT <= interval.exit() + EPSILON
                && tileX >= index.minTileX() && tileX <= index.maxTileX()
                && tileY >= index.minTileY() && tileY <= index.maxTileY()) {
            visitedCells++;

            for (int plane : index.planes()) {
                if (restrictToPlane != null && plane != restrictToPlane) continue;
                List<TriangleRef> bucket = index.buckets().get(new CellKey(plane, tileX, tileY));
                if (bucket == null) continue;
                for (TriangleRef triangle : bucket) {
                    if (tested.get(triangle.id())) {
                        duplicateSkips++;
                        continue;
                    }
                    tested.set(triangle.id());
                    triangleTests++;

                    if (SceneOcclusionResolver.occludesTriangle(triangle.command(),
                            triangle.a(), triangle.b(), triangle.c(), camera, plan.occluders())) {
                        continue;
                    }
                    float distance = intersect(ray, triangle.a(), triangle.b(), triangle.c());
                    if (!Float.isFinite(distance)
                            || distance < projection.nearPlane()
                            || distance > projection.farPlane()) {
                        continue;
                    }
                    Hit candidate = new Hit(triangle, distance);
                    if (best == null || candidate.precedes(best)) best = candidate;
                }
            }

            float nextBoundary = Math.min(Math.min(tMaxX, tMaxY), interval.exit());
            if (best != null && best.distance() <= nextBoundary + EPSILON) {
                lastMetrics = new Metrics(visitedCells, triangleTests, duplicateSkips,
                        index.triangles().size(), true);
                return Optional.of(toResult(best, ray));
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
                index.triangles().size(), best != null);
        return best == null ? Optional.empty() : Optional.of(toResult(best, ray));
    }

    /** Diagnostics for the most recent pick, useful for performance overlays/tests. */
    public Metrics lastMetrics() {
        return lastMetrics;
    }

    private synchronized SpatialIndex indexFor(GpuUploadPlan plan) {
        if (plan == cachedPlan && cachedIndex != null) return cachedIndex;
        cachedPlan = plan;
        cachedIndex = SpatialIndex.build(plan);
        return cachedIndex;
    }

    private static PickResult toResult(Hit hit, Ray ray) {
        GpuDrawCommand command = hit.triangle().command();
        WorldTileAddress address = command.tile();
        float hitX = ray.ox() + hit.distance() * ray.dx();
        float hitZ = ray.oz() + hit.distance() * ray.dz();
        WorldTile hitTile = new WorldTile(address.plane(), floorTile(hitX), floorTile(hitZ));
        WorldTile objectTile = command.objectId() >= 0
                ? new WorldTile(address.plane(), address.worldX(), address.worldY())
                : hitTile;
        return new PickResult(hitTile, objectTile, address.plane(), command.objectId(), hit.distance(),
                command.layer(), command.priority(), command.depthBias(), command.textureId());
    }

    private static Interval intersectGridBounds(Ray ray, SpatialIndex index,
                                                SceneCameraProjection projection) {
        float minX = index.minTileX() * TILE_SIZE;
        float maxX = (index.maxTileX() + 1) * TILE_SIZE;
        float minZ = index.minTileY() * TILE_SIZE;
        float maxZ = (index.maxTileY() + 1) * TILE_SIZE;

        float enter = projection.nearPlane();
        float exit = projection.farPlane();

        float[] x = slab(ray.ox(), ray.dx(), minX, maxX);
        if (x == null) return null;
        enter = Math.max(enter, x[0]);
        exit = Math.min(exit, x[1]);

        float[] z = slab(ray.oz(), ray.dz(), minZ, maxZ);
        if (z == null) return null;
        enter = Math.max(enter, z[0]);
        exit = Math.min(exit, z[1]);

        return exit + EPSILON < enter ? null : new Interval(enter, exit);
    }

    private static float[] slab(float origin, float direction, float min, float max) {
        if (Math.abs(direction) <= EPSILON) {
            return origin >= min && origin <= max
                    ? new float[]{Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}
                    : null;
        }
        float a = (min - origin) / direction;
        float b = (max - origin) / direction;
        return new float[]{Math.min(a, b), Math.max(a, b)};
    }

    private static float firstBoundaryT(float origin, float direction, int tile, int step,
                                        float startT, float delta) {
        if (step == 0) return Float.POSITIVE_INFINITY;
        float boundary = (step > 0 ? tile + 1 : tile) * TILE_SIZE;
        float value = (boundary - origin) / direction;
        while (value < startT - EPSILON) value += delta;
        return value;
    }

    private static Ray ray(CameraState camera, int width, int height,
                           float screenX, float screenY, SceneCameraProjection projection) {
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
        return new Ray(camera.x(), camera.y(), camera.z(), worldX, worldY, worldZ).normalized();
    }

    private static float intersect(Ray ray, GpuSceneVertex a, GpuSceneVertex b,
                                   GpuSceneVertex c) {
        float edge1X = b.x() - a.x();
        float edge1Y = b.y() - a.y();
        float edge1Z = b.z() - a.z();
        float edge2X = c.x() - a.x();
        float edge2Y = c.y() - a.y();
        float edge2Z = c.z() - a.z();
        float pX = ray.dy() * edge2Z - ray.dz() * edge2Y;
        float pY = ray.dz() * edge2X - ray.dx() * edge2Z;
        float pZ = ray.dx() * edge2Y - ray.dy() * edge2X;
        float determinant = edge1X * pX + edge1Y * pY + edge1Z * pZ;
        if (Math.abs(determinant) <= EPSILON) return Float.NaN;
        float inverse = 1.0f / determinant;
        float tX = ray.ox() - a.x();
        float tY = ray.oy() - a.y();
        float tZ = ray.oz() - a.z();
        float u = (tX * pX + tY * pY + tZ * pZ) * inverse;
        if (u < -EPSILON || u > 1.0f + EPSILON) return Float.NaN;
        float qX = tY * edge1Z - tZ * edge1Y;
        float qY = tZ * edge1X - tX * edge1Z;
        float qZ = tX * edge1Y - tY * edge1X;
        float v = (ray.dx() * qX + ray.dy() * qY + ray.dz() * qZ) * inverse;
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
                          int indexedTriangles, boolean hit) {
        public Metrics {
            if (visitedCells < 0 || triangleTests < 0 || duplicateSkips < 0 || indexedTriangles < 0) {
                throw new IllegalArgumentException("Picker metrics cannot be negative");
            }
        }

        public static Metrics empty() {
            return new Metrics(0, 0, 0, 0, false);
        }
    }

    private record Ray(float ox, float oy, float oz, float dx, float dy, float dz) {
        private Ray normalized() {
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Float.isFinite(length) || length <= EPSILON) {
                throw new IllegalArgumentException("Pick ray direction cannot be zero");
            }
            return new Ray(ox, oy, oz, dx / length, dy / length, dz / length);
        }
    }

    private record Interval(float enter, float exit) { }

    private record CellKey(int plane, int tileX, int tileY) { }

    private record TriangleRef(int id, GpuDrawCommand command, GpuSceneVertex a,
                               GpuSceneVertex b, GpuSceneVertex c, int order) { }

    private record Hit(TriangleRef triangle, float distance) {
        private boolean precedes(Hit other) {
            if (distance < other.distance - EPSILON) return true;
            if (Math.abs(distance - other.distance) > EPSILON) return false;
            if (triangle.command().priority() != other.triangle.command().priority()) {
                return triangle.command().priority() > other.triangle.command().priority();
            }
            return triangle.order() < other.triangle.order();
        }
    }

    private record SpatialIndex(Map<CellKey, List<TriangleRef>> buckets,
                                List<TriangleRef> triangles,
                                Set<Integer> planes,
                                int minTileX, int maxTileX, int minTileY, int maxTileY) {
        private static SpatialIndex build(GpuUploadPlan plan) {
            Map<CellKey, List<TriangleRef>> mutableBuckets = new LinkedHashMap<>();
            List<TriangleRef> triangles = new ArrayList<>();
            Set<Integer> planes = new LinkedHashSet<>();
            int minTileX = Integer.MAX_VALUE;
            int maxTileX = Integer.MIN_VALUE;
            int minTileY = Integer.MAX_VALUE;
            int maxTileY = Integer.MIN_VALUE;
            int id = 0;

            for (GpuDrawCommand command : plan.commands()) {
                int plane = command.tile().plane();
                planes.add(plane);
                for (int offset = command.firstIndex();
                     offset + 2 < command.firstIndex() + command.indexCount(); offset += 3) {
                    GpuSceneVertex a = plan.vertices().get(plan.indices().get(offset));
                    GpuSceneVertex b = plan.vertices().get(plan.indices().get(offset + 1));
                    GpuSceneVertex c = plan.vertices().get(plan.indices().get(offset + 2));
                    TriangleRef ref = new TriangleRef(id++, command, a, b, c, offset);
                    triangles.add(ref);

                    int triMinX = floorTile(Math.min(a.x(), Math.min(b.x(), c.x())));
                    int triMaxX = floorTile(Math.max(a.x(), Math.max(b.x(), c.x())));
                    int triMinY = floorTile(Math.min(a.z(), Math.min(b.z(), c.z())));
                    int triMaxY = floorTile(Math.max(a.z(), Math.max(b.z(), c.z())));
                    minTileX = Math.min(minTileX, triMinX);
                    maxTileX = Math.max(maxTileX, triMaxX);
                    minTileY = Math.min(minTileY, triMinY);
                    maxTileY = Math.max(maxTileY, triMaxY);

                    for (int tileX = triMinX; tileX <= triMaxX; tileX++) {
                        for (int tileY = triMinY; tileY <= triMaxY; tileY++) {
                            mutableBuckets.computeIfAbsent(
                                    new CellKey(plane, tileX, tileY),
                                    ignored -> new ArrayList<>()).add(ref);
                        }
                    }
                }
            }

            if (triangles.isEmpty()) {
                return new SpatialIndex(Map.of(), List.of(), Set.of(), 0, -1, 0, -1);
            }

            Map<CellKey, List<TriangleRef>> buckets = new LinkedHashMap<>();
            mutableBuckets.forEach((key, value) -> buckets.put(key, List.copyOf(value)));
            return new SpatialIndex(Map.copyOf(buckets), List.copyOf(triangles), Set.copyOf(planes),
                    minTileX, maxTileX, minTileY, maxTileY);
        }
    }
}
