package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral ray picker for one immutable GPU upload plan.
 *
 * <p>This is the correctness fallback for native ID-buffer picking. It uses
 * the same world-space triangles and occluder inputs that the software and
 * OpenGL renderers consume, so a tool never needs to maintain a second object
 * footprint or scene-coordinate implementation. A future native picker may
 * replace the ray test with an ID framebuffer while preserving this result
 * contract.</p>
 */
public final class GpuPlanPicker {
    private static final float EPSILON = 1.0e-5f;

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY) {
        return pick(plan, camera, width, height, screenX, screenY,
                SceneCameraProjection.editorDefault());
    }

    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY,
                                     SceneCameraProjection projection) {
        return pick(plan, camera, width, height, screenX, screenY, projection, null);
    }

    /**
     * Same ray-triangle pick, but ignores geometry on any plane other than {@code restrictToPlane}
     * when it is non-null. Lets a caller keep multiple planes visible ("show all levels") while
     * still only allowing clicks to land on the plane the user is actually editing.
     */
    public Optional<PickResult> pick(GpuUploadPlan plan, CameraState camera,
                                     int width, int height, float screenX, float screenY,
                                     SceneCameraProjection projection, Integer restrictToPlane) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(projection, "projection");
        if (width <= 0 || height <= 0 || !Float.isFinite(screenX) || !Float.isFinite(screenY)
                || screenX < 0.0f || screenY < 0.0f || screenX >= width || screenY >= height) {
            return Optional.empty();
        }

        Ray ray = ray(camera, width, height, screenX, screenY, projection);
        Hit best = null;
        for (GpuDrawCommand command : plan.commands()) {
            if (restrictToPlane != null && command.tile().plane() != restrictToPlane) {
                continue;
            }
            for (int offset = command.firstIndex();
                 offset + 2 < command.firstIndex() + command.indexCount(); offset += 3) {
                GpuSceneVertex first = plan.vertices().get(plan.indices().get(offset));
                GpuSceneVertex second = plan.vertices().get(plan.indices().get(offset + 1));
                GpuSceneVertex third = plan.vertices().get(plan.indices().get(offset + 2));
                if (SceneOcclusionResolver.occludesTriangle(command, first, second, third,
                        camera, plan.occluders())) {
                    continue;
                }
                float distance = intersect(ray, first, second, third);
                if (!Float.isFinite(distance) || distance < projection.nearPlane()
                        || distance > projection.farPlane()) {
                    continue;
                }
                Hit candidate = new Hit(command, distance);
                if (best == null || candidate.precedes(best)) best = candidate;
            }
        }
        if (best == null) return Optional.empty();
        WorldTileAddress address = best.command.tile();
        float hitX = ray.ox() + best.distance * ray.dx();
        float hitZ = ray.oz() + best.distance * ray.dz();
        int tileX = (int) Math.floor(hitX / 128.0f);
        int tileY = (int) Math.floor(hitZ / 128.0f);
        WorldTile hitTile = new WorldTile(address.plane(), tileX, tileY);
        WorldTile objectTile = best.command.objectId() >= 0
                ? new WorldTile(address.plane(), address.worldX(), address.worldY())
                : hitTile;
        return Optional.of(new PickResult(hitTile, objectTile, address.plane(), best.command.objectId(),
                best.distance, best.command.layer(), best.command.priority(),
                best.command.depthBias(), best.command.textureId(),
                best.command.gameObjectSceneMetadata(), best.command.clientRenderableBounds()));
    }

    private static Ray ray(CameraState camera, int width, int height,
                           float screenX, float screenY, SceneCameraProjection projection) {
        float focal = (float) ((height * 0.5) / Math.tan(projection.verticalFieldOfView() * 0.5));
        float cameraX = (screenX - width * 0.5f) / focal;
        float cameraY = (height * 0.5f - screenY) / focal;
        float yawDepth = -cameraY * (float) Math.sin(camera.pitch())
                + (float) Math.cos(camera.pitch());
        // The scene's canonical Y axis points down (OSRS terrain heights are
        // negative-up), so invert the reconstructed camera-up displacement.
        float worldY = -(cameraY * (float) Math.cos(camera.pitch())
                + (float) Math.sin(camera.pitch()));
        float worldX = cameraX * (float) Math.cos(camera.yaw())
                + yawDepth * (float) Math.sin(camera.yaw());
        float worldZ = -cameraX * (float) Math.sin(camera.yaw())
                + yawDepth * (float) Math.cos(camera.yaw());
        return new Ray(camera.x(), camera.y(), camera.z(),
                worldX, worldY, worldZ).normalized();
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

    private record Ray(float ox, float oy, float oz, float dx, float dy, float dz) {
        private Ray normalized() {
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Float.isFinite(length) || length <= EPSILON) {
                throw new IllegalArgumentException("Pick ray direction cannot be zero");
            }
            return new Ray(ox, oy, oz, dx / length, dy / length, dz / length);
        }
    }

    private record Hit(GpuDrawCommand command, float distance) {
        private boolean precedes(Hit other) {
            if (distance < other.distance - EPSILON) return true;
            if (Math.abs(distance - other.distance) > EPSILON) return false;
            if (command.priority() != other.command.priority()) {
                return command.priority() > other.command.priority();
            }
            return command.firstIndex() < other.command.firstIndex();
        }
    }
}
