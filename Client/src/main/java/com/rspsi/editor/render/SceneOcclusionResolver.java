package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.List;
import java.util.Objects;

/** Conservative camera-space implementation of the client occluder tests. */
public final class SceneOcclusionResolver {
    private SceneOcclusionResolver() {
    }

    /**
     * Returns true only when every triangle referenced by {@code command} is
     * individually occluded. This is the command-granularity counterpart of
     * {@link #occludesTriangle}, used to decide whether to skip an entire
     * already-merged draw command without rebuilding its geometry: a command
     * is conservatively considered visible (returns false) as soon as any one
     * of its triangles is not fully occluded.
     */
    public static boolean occludesCommand(GpuDrawCommand command, GpuUploadPlan plan,
                                          CameraState camera, List<SceneOccluder> occluders) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(occluders, "occluders");
        if (occluders.isEmpty()) return false;
        for (int offset = command.firstIndex();
             offset < command.firstIndex() + command.indexCount(); offset += 3) {
            GpuSceneVertex first = plan.vertices().get(plan.indices().get(offset));
            GpuSceneVertex second = plan.vertices().get(plan.indices().get(offset + 1));
            GpuSceneVertex third = plan.vertices().get(plan.indices().get(offset + 2));
            if (!occludesTriangle(command, first, second, third, camera, occluders)) {
                return false;
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
            if (occludesPoint(first, camera, occluder)
                    && occludesPoint(second, camera, occluder)
                    && occludesPoint(third, camera, occluder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean occluderPlaneMatches(WorldTileAddress tile, SceneOccluder occluder) {
        return tile.plane() >= occluder.minPlane() && tile.plane() <= occluder.maxPlane();
    }

    private static boolean occludesPoint(GpuSceneVertex point, CameraState camera,
                                         SceneOccluder occluder) {
        return switch (occluder.type()) {
            case 1 -> occludesXPlane(point, camera, occluder);
            case 2 -> occludesZPlane(point, camera, occluder);
            case 4 -> occludesYPlane(point, camera, occluder);
            default -> false;
        };
    }

    private static boolean occludesXPlane(GpuSceneVertex point, CameraState camera,
                                          SceneOccluder occluder) {
        float cameraDistance = camera.x() - occluder.minWorldX();
        if (Math.abs(cameraDistance) <= 32.0f) {
            return false;
        }
        float delta = cameraDistance > 0.0f
                ? occluder.minWorldX() - point.x()
                : point.x() - occluder.minWorldX();
        if (delta <= 0.0f) return false;
        float distance = Math.abs(cameraDistance);
        return between(point.z(), projectedMin(occluder.minWorldY(), occluder.maxWorldY(),
                        camera.z(), delta, distance),
                projectedMax(occluder.minWorldY(), occluder.maxWorldY(), camera.z(), delta, distance))
                && between(point.y(), projectedMin(occluder.minHeight(), occluder.maxHeight(),
                        camera.y(), delta, distance),
                projectedMax(occluder.minHeight(), occluder.maxHeight(), camera.y(), delta, distance));
    }

    private static boolean occludesZPlane(GpuSceneVertex point, CameraState camera,
                                          SceneOccluder occluder) {
        float cameraDistance = camera.z() - occluder.minWorldY();
        if (Math.abs(cameraDistance) <= 32.0f) {
            return false;
        }
        float delta = cameraDistance > 0.0f
                ? occluder.minWorldY() - point.z()
                : point.z() - occluder.minWorldY();
        if (delta <= 0.0f) return false;
        float distance = Math.abs(cameraDistance);
        return between(point.x(), projectedMin(occluder.minWorldX(), occluder.maxWorldX(),
                        camera.x(), delta, distance),
                projectedMax(occluder.minWorldX(), occluder.maxWorldX(), camera.x(), delta, distance))
                && between(point.y(), projectedMin(occluder.minHeight(), occluder.maxHeight(),
                        camera.y(), delta, distance),
                projectedMax(occluder.minHeight(), occluder.maxHeight(), camera.y(), delta, distance));
    }

    private static boolean occludesYPlane(GpuSceneVertex point, CameraState camera,
                                          SceneOccluder occluder) {
        float cameraDistance = occluder.minHeight() - camera.y();
        float delta = point.y() - occluder.minHeight();
        if (cameraDistance <= 128.0f || delta <= 0.0f) return false;
        return between(point.x(), projectedMin(occluder.minWorldX(), occluder.maxWorldX(),
                        camera.x(), delta, cameraDistance),
                projectedMax(occluder.minWorldX(), occluder.maxWorldX(), camera.x(), delta, cameraDistance))
                && between(point.z(), projectedMin(occluder.minWorldY(), occluder.maxWorldY(),
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
