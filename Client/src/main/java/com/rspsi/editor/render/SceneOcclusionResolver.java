package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.List;
import java.util.Objects;

/** Conservative camera-space implementation of the client occluder tests. */
public final class SceneOcclusionResolver {
    private SceneOcclusionResolver() {
    }

    /**
     * Axis-aligned bounds of a draw command's referenced vertices, used as a
     * cheap per-frame occlusion proxy so {@link #occludesCommand} does not
     * have to run the full per-vertex occluder-plane test (division, camera-
     * relative projection) for every triangle of every command every frame.
     */
    public record CommandBounds(float minX, float maxX, float minY, float maxY,
                                float minZ, float maxZ) {
        public static CommandBounds of(int commandIndex, GpuDrawCommand command,
                                       GpuCommandGeometry geometry) {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(geometry, "geometry");
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int offset = 0; offset < command.indexCount(); offset++) {
                GpuSceneVertex vertex = geometry.indexedVertex(commandIndex, offset);
                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxY = Math.max(maxY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxZ = Math.max(maxZ, vertex.z());
            }
            return new CommandBounds(minX, maxX, minY, maxY, minZ, maxZ);
        }

        /** Compatibility helper for callers that only carry the command object. */
        public static CommandBounds of(GpuDrawCommand command, GpuCommandGeometry geometry) {
            for (int index = 0; index < geometry.commandCount(); index++) {
                GpuDrawCommand candidate = geometry.command(index);
                if (candidate == command || candidate.equals(command)) {
                    return of(index, command, geometry);
                }
            }
            throw new IllegalArgumentException("Command is not present in geometry");
        }

        /** Depth-sort key for alpha ordering: the bounds' center, camera-relative. */
        public float centerX() {
            return (minX + maxX) * 0.5f;
        }

        public float centerY() {
            return (minY + maxY) * 0.5f;
        }

        public float centerZ() {
            return (minZ + maxZ) * 0.5f;
        }
    }

    /**
     * Returns true only when every corner of {@code command}'s bounding box
     * is occluded. This is a conservative, command-granularity proxy for
     * {@link #occludesTriangle}: it can occasionally draw a command whose
     * individual triangles were all actually hidden (when the triangles
     * don't fill their own bounding box), but it never hides something that
     * should be visible, and it costs O(occluders) per command instead of
     * O(triangles-in-command x occluders) - the per-triangle test walked
     * every triangle of every command against every occluder on every
     * frame, which dominated frame time once the per-frame geometry
     * re-upload was fixed separately.
     */
    /*
     * GpuUploadPlan is a value record whose generated hashCode walks every
     * vertex, index, command, texture, and occluder. Using it as a normal
     * hash-map key turns a supposedly cheap per-frame lookup back into
     * O(scene-size) work, so geometry identity is intentionally the key.
     *
     * Do not use IdentityHashMap + arbitrary iterator eviction here. Zoned
     * animation refreshes create a fresh GpuZonedUploadPlan identity every
     * few seconds; once the cache exceeded its old four-entry limit, the
     * arbitrary eviction could remove the entry that had just been inserted.
     * Alpha-sort comparator calls then rebuilt every command bound over and
     * over on the same frame.
     *
     * A tiny explicit identity-LRU makes eviction deterministic and keeps the
     * current geometry resident. Each entry is also lazy per command: an alpha
     * sort only computes bounds for alpha commands instead of walking every
     * draw command in the scene up front.
     */
    private static final int MAX_CACHED_PLANS = 4;
    private static final java.util.List<BoundsCacheEntry> BOUNDS_CACHE =
            new java.util.ArrayList<>(MAX_CACHED_PLANS);

    public static CommandBounds boundsOf(int commandIndex, GpuDrawCommand command,
                                         GpuCommandGeometry geometry) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(geometry, "geometry");
        if (commandIndex >= 0 && commandIndex < geometry.commandCount()) {
            return boundsCacheEntry(geometry).boundsOf(commandIndex);
        }
        return CommandBounds.of(command, geometry);
    }

    private static synchronized BoundsCacheEntry boundsCacheEntry(GpuCommandGeometry geometry) {
        for (int index = 0; index < BOUNDS_CACHE.size(); index++) {
            BoundsCacheEntry entry = BOUNDS_CACHE.get(index);
            if (entry.geometry == geometry) {
                if (index != BOUNDS_CACHE.size() - 1) {
                    BOUNDS_CACHE.remove(index);
                    BOUNDS_CACHE.add(entry);
                }
                return entry;
            }
        }

        BoundsCacheEntry entry = new BoundsCacheEntry(geometry);
        BOUNDS_CACHE.add(entry);
        while (BOUNDS_CACHE.size() > MAX_CACHED_PLANS) {
            BOUNDS_CACHE.remove(0);
        }
        return entry;
    }

    static synchronized void clearBoundsCacheForTests() {
        BOUNDS_CACHE.clear();
    }

    static synchronized int cachedGeometryCountForTests() {
        return BOUNDS_CACHE.size();
    }

    private static final class BoundsCacheEntry {
        private final GpuCommandGeometry geometry;
        private final CommandBounds[] bounds;

        private BoundsCacheEntry(GpuCommandGeometry geometry) {
            this.geometry = geometry;
            this.bounds = new CommandBounds[geometry.commandCount()];
        }

        private synchronized CommandBounds boundsOf(int commandIndex) {
            CommandBounds cached = bounds[commandIndex];
            if (cached == null) {
                GpuDrawCommand command = geometry.command(commandIndex);
                cached = CommandBounds.of(commandIndex, command, geometry);
                bounds[commandIndex] = cached;
            }
            return cached;
        }
    }

    public static boolean occludesCommand(int commandIndex, GpuDrawCommand command,
                                          GpuCommandGeometry geometry,
                                          CameraState camera, List<SceneOccluder> occluders) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        if (occluders.isEmpty()) return false;
        return occludesBounds(command, boundsOf(commandIndex, command, geometry), camera, occluders);
    }

    public static boolean occludesCommand(GpuDrawCommand command, GpuCommandGeometry geometry,
                                          CameraState camera, List<SceneOccluder> occluders) {
        return occludesBounds(command, CommandBounds.of(command, geometry), camera, occluders);
    }

    /** Bounds-based counterpart of {@link #occludesCommand} for a precomputed box. */
    public static boolean occludesBounds(GpuDrawCommand command, CommandBounds bounds,
                                         CameraState camera, List<SceneOccluder> occluders) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        if (occluders.isEmpty()) return false;
        for (SceneOccluder occluder : occluders) {
            if (!occluderPlaneMatches(command, occluder)) continue;
            if (occludesAllCorners(bounds, camera, occluder)) return true;
        }
        return false;
    }

    private static boolean occludesAllCorners(CommandBounds bounds, CameraState camera,
                                               SceneOccluder occluder) {
        float minX = bounds.minX();
        float maxX = bounds.maxX();
        float minY = bounds.minY();
        float maxY = bounds.maxY();
        float minZ = bounds.minZ();
        float maxZ = bounds.maxZ();
        return occludesPoint(minX, minY, minZ, camera, occluder)
                && occludesPoint(minX, minY, maxZ, camera, occluder)
                && occludesPoint(minX, maxY, minZ, camera, occluder)
                && occludesPoint(minX, maxY, maxZ, camera, occluder)
                && occludesPoint(maxX, minY, minZ, camera, occluder)
                && occludesPoint(maxX, minY, maxZ, camera, occluder)
                && occludesPoint(maxX, maxY, minZ, camera, occluder)
                && occludesPoint(maxX, maxY, maxZ, camera, occluder);
    }

    /** Returns true only when the complete triangle is behind one occluder. */
    public static boolean occludesTriangle(GpuDrawCommand command,
                                           GpuSceneVertex first,
                                           GpuSceneVertex second,
                                           GpuSceneVertex third,
                                           CameraState camera,
                                           List<SceneOccluder> occluders) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        for (SceneOccluder occluder : occluders) {
            if (!occluderPlaneMatches(command, occluder)) continue;
            if (occludesPoint(first.x(), first.y(), first.z(), camera, occluder)
                    && occludesPoint(second.x(), second.y(), second.z(), camera, occluder)
                    && occludesPoint(third.x(), third.y(), third.z(), camera, occluder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean occluderPlaneMatches(GpuDrawCommand command, SceneOccluder occluder) {
        return command.scenePlane() >= occluder.minPlane()
                && command.scenePlane() <= occluder.maxPlane();
    }

    private static boolean occludesPoint(float px, float py, float pz, CameraState camera,
                                         SceneOccluder occluder) {
        return switch (occluder.type()) {
            case 1 -> occludesXPlane(px, py, pz, camera, occluder);
            case 2 -> occludesZPlane(px, py, pz, camera, occluder);
            case 4 -> occludesYPlane(px, py, pz, camera, occluder);
            default -> false;
        };
    }

    private static boolean occludesXPlane(float px, float py, float pz, CameraState camera,
                                          SceneOccluder occluder) {
        float cameraDistance = camera.x() - occluder.minWorldX();
        if (Math.abs(cameraDistance) <= 32.0f) {
            return false;
        }
        float delta = cameraDistance > 0.0f
                ? occluder.minWorldX() - px
                : px - occluder.minWorldX();
        if (delta <= 0.0f) return false;
        float distance = Math.abs(cameraDistance);
        return between(pz, projectedMin(occluder.minWorldY(), occluder.maxWorldY(),
                        camera.z(), delta, distance),
                projectedMax(occluder.minWorldY(), occluder.maxWorldY(), camera.z(), delta, distance))
                && between(py, projectedMin(occluder.minHeight(), occluder.maxHeight(),
                        camera.y(), delta, distance),
                projectedMax(occluder.minHeight(), occluder.maxHeight(), camera.y(), delta, distance));
    }

    private static boolean occludesZPlane(float px, float py, float pz, CameraState camera,
                                          SceneOccluder occluder) {
        float cameraDistance = camera.z() - occluder.minWorldY();
        if (Math.abs(cameraDistance) <= 32.0f) {
            return false;
        }
        float delta = cameraDistance > 0.0f
                ? occluder.minWorldY() - pz
                : pz - occluder.minWorldY();
        if (delta <= 0.0f) return false;
        float distance = Math.abs(cameraDistance);
        return between(px, projectedMin(occluder.minWorldX(), occluder.maxWorldX(),
                        camera.x(), delta, distance),
                projectedMax(occluder.minWorldX(), occluder.maxWorldX(), camera.x(), delta, distance))
                && between(py, projectedMin(occluder.minHeight(), occluder.maxHeight(),
                        camera.y(), delta, distance),
                projectedMax(occluder.minHeight(), occluder.maxHeight(), camera.y(), delta, distance));
    }

    private static boolean occludesYPlane(float px, float py, float pz, CameraState camera,
                                          SceneOccluder occluder) {
        float cameraDistance = occluder.minHeight() - camera.y();
        float delta = py - occluder.minHeight();
        if (cameraDistance <= 128.0f || delta <= 0.0f) return false;
        return between(px, projectedMin(occluder.minWorldX(), occluder.maxWorldX(),
                        camera.x(), delta, cameraDistance),
                projectedMax(occluder.minWorldX(), occluder.maxWorldX(), camera.x(), delta, cameraDistance))
                && between(pz, projectedMin(occluder.minWorldY(), occluder.maxWorldY(),
                        camera.z(), delta, cameraDistance),
                projectedMax(occluder.minWorldY(), occluder.maxWorldY(), camera.z(), delta, cameraDistance));
    }

    private static float projected(float edge, float cameraCoordinate,
                                   float delta, float distance) {
        return edge + delta * (edge - cameraCoordinate) / distance;
    }

    private static float projectedMin(float minEdge, float maxEdge, float cameraCoordinate,
                                      float delta, float distance) {
        return Math.min(projected(minEdge, cameraCoordinate, delta, distance),
                projected(maxEdge, cameraCoordinate, delta, distance));
    }

    private static float projectedMax(float minEdge, float maxEdge, float cameraCoordinate,
                                          float delta, float distance) {
        return Math.max(projected(minEdge, cameraCoordinate, delta, distance),
                projected(maxEdge, cameraCoordinate, delta, distance));
    }

    private static boolean between(float value, float min, float max) {
        return value >= min && value <= max;
    }
}
