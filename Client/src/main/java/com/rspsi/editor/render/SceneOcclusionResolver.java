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
        public static CommandBounds of(GpuDrawCommand command, GpuUploadPlan plan) {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(plan, "plan");
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int offset = command.firstIndex();
                 offset < command.firstIndex() + command.indexCount(); offset++) {
                GpuSceneVertex vertex = plan.vertices().get(plan.indices().get(offset));
                minX = Math.min(minX, vertex.x());
                maxX = Math.max(maxX, vertex.x());
                minY = Math.min(minY, vertex.y());
                maxY = Math.max(maxY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxZ = Math.max(maxZ, vertex.z());
            }
            return new CommandBounds(minX, maxX, minY, maxY, minZ, maxZ);
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
    private static final java.util.Map<GpuUploadPlan, CommandBounds[]> BOUNDS_CACHE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static CommandBounds boundsOf(int commandIndex, GpuDrawCommand command, GpuUploadPlan plan) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(plan, "plan");
        CommandBounds[] cached = BOUNDS_CACHE.computeIfAbsent(plan, p -> {
            CommandBounds[] array = new CommandBounds[p.commands().size()];
            for (int i = 0; i < p.commands().size(); i++) {
                array[i] = CommandBounds.of(p.commands().get(i), p);
            }
            return array;
        });
        if (commandIndex >= 0 && commandIndex < cached.length) {
            return cached[commandIndex];
        }
        return CommandBounds.of(command, plan);
    }

    public static boolean occludesCommand(int commandIndex, GpuDrawCommand command, GpuUploadPlan plan,
                                          CameraState camera, List<SceneOccluder> occluders) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        if (occluders.isEmpty()) return false;
        return occludesBounds(command, boundsOf(commandIndex, command, plan), camera, occluders);
    }

    public static boolean occludesCommand(GpuDrawCommand command, GpuUploadPlan plan,
                                          CameraState camera, List<SceneOccluder> occluders) {
        return occludesCommand(-1, command, plan, camera, occluders);
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
            if (!occluderPlaneMatches(command.tile(), occluder)) continue;
            if (occludesAllCorners(bounds, camera, occluder)) return true;
        }
        return false;
    }

    private static boolean occludesAllCorners(CommandBounds bounds, CameraState camera,
                                               SceneOccluder occluder) {
        for (float x : new float[]{bounds.minX(), bounds.maxX()}) {
            for (float y : new float[]{bounds.minY(), bounds.maxY()}) {
                for (float z : new float[]{bounds.minZ(), bounds.maxZ()}) {
                    if (!occludesPoint(x, y, z, camera, occluder)) return false;
                }
            }
        }
        return true;
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
            if (!occluderPlaneMatches(command.tile(), occluder)) continue;
            if (occludesPoint(first.x(), first.y(), first.z(), camera, occluder)
                    && occludesPoint(second.x(), second.y(), second.z(), camera, occluder)
                    && occludesPoint(third.x(), third.y(), third.z(), camera, occluder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean occluderPlaneMatches(WorldTileAddress tile, SceneOccluder occluder) {
        return tile.plane() >= occluder.minPlane() && tile.plane() <= occluder.maxPlane();
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
