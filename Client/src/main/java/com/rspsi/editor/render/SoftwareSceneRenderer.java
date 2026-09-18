package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CPU reference rasterizer for the immutable GPU upload plan.
 *
 * <p>This is intentionally independent of JavaFX and native graphics APIs. It
 * provides deterministic pixels for parity tests while a platform renderer
 * can consume the exact same upload plan. It implements the static scene
 * path: perspective projection, depth testing, OSRS palette conversion,
 * nearest texture sampling, vertex lightness, alpha blending, and ordered
 * opaque/alpha submissions. Camera-plane clipping and perspective-correct
 * depth/material interpolation are performed before rasterization.</p>
 */
public final class SoftwareSceneRenderer {
    private static final int BACKGROUND = 0xFF101827;

    public SoftwareRenderFrame render(GpuUploadPlan plan, CameraState camera,
                                      int width, int height) {
        return render(plan, camera, width, height, SceneCameraProjection.editorDefault());
    }

    public SoftwareRenderFrame render(GpuUploadPlan plan, CameraState camera,
                                      int width, int height, SceneCameraProjection projection) {
        return render(plan, camera, width, height, projection, 0);
    }

    /** Renders at a deterministic client cycle, including animated textures. */
    public SoftwareRenderFrame render(GpuUploadPlan plan, CameraState camera,
                                      int width, int height, SceneCameraProjection projection,
                                      int clientCycle) {
        return render(plan, camera, width, height, projection, clientCycle,
                RenderPresentation.neutral());
    }

    private SoftwareRenderFrame render(GpuUploadPlan plan, CameraState camera,
                                       int width, int height, SceneCameraProjection projection,
                                       int clientCycle, RenderPresentation presentation) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(projection, "projection");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Frame dimensions must be positive");

        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, BACKGROUND);
        float[] depth = new float[pixels.length];
        java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY);
        SceneFog.Bounds fogBounds = SceneFog.bounds(plan);
        float focalLength = (float) ((height * 0.5) / Math.tan(projection.verticalFieldOfView() * 0.5));
        List<DrawWork> opaque = new ArrayList<>();
        List<DrawWork> alpha = new ArrayList<>();
        int submittedTriangles = 0;
        for (GpuDrawCommand command : plan.commands()) {
            List<TriangleWork> triangles = project(command, plan, camera, projection,
                    width, height, focalLength);
            submittedTriangles += command.indexCount() / 3;
            for (TriangleWork triangle : triangles) {
                DrawWork work = new DrawWork(command, triangle);
                if (command.pass() == GpuDrawCommand.SubmissionPass.ALPHA) alpha.add(work);
                else opaque.add(work);
            }
        }
        List<GpuDrawCommand> alphaCommands = plan.commands().stream()
                .filter(command -> command.pass() == GpuDrawCommand.SubmissionPass.ALPHA)
                .toList();
        List<GpuDrawCommand> orderedAlpha = RsFaceOrderPlanner.orderAlpha(alphaCommands,
                command -> averageDepth(plan, command, camera));
        Map<GpuDrawCommand, Integer> alphaOrder = new java.util.HashMap<>();
        for (int index = 0; index < orderedAlpha.size(); index++) {
            alphaOrder.put(orderedAlpha.get(index), index);
        }
        alpha.sort(Comparator.comparingInt((DrawWork value) -> alphaOrder.get(value.command))
                .thenComparingInt(value -> value.command.firstIndex()));
        // The depth test below only accepts a strictly nearer fragment
        // (pixelDepth >= depth[offset] is skipped), so exactly-coplanar
        // opaque faces - e.g. a decal sitting exactly on the terrain height
        // it decorates - can only be resolved by draw order, never depth.
        // Higher RuneScape face priority must draw first so it claims the
        // depth value and a same-depth lower-priority face is then skipped.
        // This mirrors the real client: priority affects paint order, never
        // a depth offset (see GpuPriority/OpenGlSceneRenderer for the same
        // no-longer-synthetic-bias contract). The sort is stable, so faces
        // with equal priority keep their original relative order.
        opaque.sort(Comparator.comparingInt((DrawWork value) -> value.command.priority()).reversed());

        int rasterized = 0;
        for (DrawWork work : opaque) {
            if (rasterize(work.command, work.triangle, plan.textures(), pixels, depth,
                    width, height, false, clientCycle, presentation, fogBounds)) rasterized++;
        }
        for (DrawWork work : alpha) {
            if (rasterize(work.command, work.triangle, plan.textures(), pixels, depth,
                    width, height, true, clientCycle, presentation, fogBounds)) rasterized++;
        }
        return new SoftwareRenderFrame(width, height, pixels, submittedTriangles, rasterized);
    }

    private static float averageDepth(GpuUploadPlan plan, GpuDrawCommand command,
                                      CameraState camera) {
        float total = 0.0f;
        int count = 0;
        float cosYaw = (float) Math.cos(camera.yaw());
        float sinYaw = (float) Math.sin(camera.yaw());
        float cosPitch = (float) Math.cos(camera.pitch());
        float sinPitch = (float) Math.sin(camera.pitch());
        for (int offset = command.firstIndex(); offset < command.firstIndex() + command.indexCount(); offset++) {
            GpuSceneVertex vertex = plan.vertices().get(plan.indices().get(offset));
            float dx = vertex.x() - camera.x();
            float dy = vertex.y() - camera.y();
            float dz = vertex.z() - camera.z();
            float yawDepth = dx * sinYaw + dz * cosYaw;
            total += dy * sinPitch + yawDepth * cosPitch;
            count++;
        }
        return count == 0 ? Float.NEGATIVE_INFINITY : total / count;
    }

    /** Renders the immutable frame contract used by native backends as well. */
    public SoftwareRenderFrame render(RenderFrame frame, int width, int height) {
        Objects.requireNonNull(frame, "render frame");
        return render(frame.plan(), frame.camera(), width, height,
                SceneCameraProjection.editorDefault(), frame.clientCycle(),
                frame.config().presentation());
    }

    private static List<TriangleWork> project(GpuDrawCommand command, GpuUploadPlan plan,
                                              CameraState camera, SceneCameraProjection projection,
                                              int width, int height, float focalLength) {
        List<TriangleWork> result = new ArrayList<>();
        for (int index = command.firstIndex(); index < command.firstIndex() + command.indexCount(); index += 3) {
            GpuSceneVertex first = plan.vertices().get(plan.indices().get(index));
            GpuSceneVertex second = plan.vertices().get(plan.indices().get(index + 1));
            GpuSceneVertex third = plan.vertices().get(plan.indices().get(index + 2));
            if (SceneOcclusionResolver.occludesTriangle(command, first, second, third,
                    camera, plan.occluders())) continue;
            List<ViewVertex> clipped = clipDepth(List.of(
                    view(first, camera), view(second, camera), view(third, camera)),
                    projection.nearPlane(), projection.farPlane());
            if (clipped.size() < 3) continue;
                ProjectedVertex firstProjected = project(clipped.get(0), width, height, focalLength,
                    projection, command);
            for (int fan = 1; fan < clipped.size() - 1; fan++) {
                ProjectedVertex secondProjected = project(clipped.get(fan), width, height, focalLength,
                        projection, command);
                ProjectedVertex thirdProjected = project(clipped.get(fan + 1), width, height, focalLength,
                        projection, command);
                result.add(new TriangleWork(firstProjected, secondProjected, thirdProjected,
                        (firstProjected.depth + secondProjected.depth + thirdProjected.depth) / 3.0f));
            }
        }
        return result;
    }

    private static ViewVertex view(GpuSceneVertex vertex, CameraState camera) {
        float dx = vertex.x() - camera.x();
        float dy = vertex.y() - camera.y();
        float dz = vertex.z() - camera.z();
        float cosYaw = (float) Math.cos(camera.yaw());
        float sinYaw = (float) Math.sin(camera.yaw());
        float yawX = dx * cosYaw - dz * sinYaw;
        float yawDepth = dx * sinYaw + dz * cosYaw;
        float cosPitch = (float) Math.cos(camera.pitch());
        float sinPitch = (float) Math.sin(camera.pitch());
        float viewY = dy * cosPitch - yawDepth * sinPitch;
        float depth = dy * sinPitch + yawDepth * cosPitch;
        return new ViewVertex(yawX, viewY, depth, vertex);
    }

    private static ProjectedVertex project(ViewVertex vertex, int width, int height, float focalLength,
                                           SceneCameraProjection projection,
                                           GpuDrawCommand command) {
        return new ProjectedVertex(width * 0.5f + vertex.x / vertex.depth * focalLength,
                height * 0.5f - vertex.y / vertex.depth * focalLength,
                GpuPriority.biasedDepth(vertex.depth, projection.nearPlane(),
                        projection.farPlane(), vertex.vertex.priority(), command.depthBias()), vertex.vertex);
    }

    /** Clips a triangle polygon against both camera depth planes. */
    private static List<ViewVertex> clipDepth(List<ViewVertex> polygon, float near, float far) {
        List<ViewVertex> clipped = clipAgainstDepth(polygon, near, true);
        return clipAgainstDepth(clipped, far, false);
    }

    private static List<ViewVertex> clipAgainstDepth(List<ViewVertex> polygon, float boundary,
                                                     boolean keepGreater) {
        List<ViewVertex> result = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            ViewVertex current = polygon.get(i);
            ViewVertex previous = polygon.get((i + polygon.size() - 1) % polygon.size());
            boolean currentInside = keepGreater ? current.depth > boundary : current.depth < boundary;
            boolean previousInside = keepGreater ? previous.depth > boundary : previous.depth < boundary;
            if (currentInside != previousInside) {
                float denominator = current.depth - previous.depth;
                float amount = denominator == 0.0f ? 0.0f : (boundary - previous.depth) / denominator;
                result.add(interpolate(previous, current, amount));
            }
            if (currentInside) result.add(current);
        }
        return result;
    }

    private static ViewVertex interpolate(ViewVertex first, ViewVertex second, float amount) {
        return new ViewVertex(
                mix(first.x, second.x, amount),
                mix(first.y, second.y, amount),
                mix(first.depth, second.depth, amount),
                interpolateVertex(first.vertex, second.vertex, amount));
    }

    private static GpuSceneVertex interpolateVertex(GpuSceneVertex first, GpuSceneVertex second,
                                                    float amount) {
        return new GpuSceneVertex(
                mix(first.x(), second.x(), amount),
                mix(first.y(), second.y(), amount),
                mix(first.z(), second.z(), amount),
                mix(first.u(), second.u(), amount),
                mix(first.v(), second.v(), amount),
                Math.round(mix(first.encodedColor(), second.encodedColor(), amount)),
                first.colorEncoding(), first.renderType(),
                Math.round(mix(first.normalX(), second.normalX(), amount)),
                Math.round(mix(first.normalY(), second.normalY(), amount)),
                Math.round(mix(first.normalZ(), second.normalZ(), amount)),
                Math.round(mix(first.normalMagnitude(), second.normalMagnitude(), amount)),
                first.textureId(),
                Math.round(mix(first.alpha(), second.alpha(), amount)),
                first.priority());
    }

    private static float mix(float first, float second, float amount) {
        return first + (second - first) * amount;
    }

    private static boolean rasterize(GpuDrawCommand command, TriangleWork triangle,
                                     Map<Integer, RenderTextureResource> textures,
                                     int[] pixels, float[] depth, int width, int height,
                                     boolean alphaPass, int clientCycle,
                                     RenderPresentation presentation,
                                     SceneFog.Bounds fogBounds) {
        float area = edge(triangle.a.x, triangle.a.y, triangle.b.x, triangle.b.y,
                triangle.c.x, triangle.c.y);
        if (!BackfacePolicy.isFrontFacingSoftware(area)) return false;
        int minX = Math.max(0, (int) Math.floor(Math.min(triangle.a.x, Math.min(triangle.b.x, triangle.c.x))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(triangle.a.x, Math.max(triangle.b.x, triangle.c.x))));
        int minY = Math.max(0, (int) Math.floor(Math.min(triangle.a.y, Math.min(triangle.b.y, triangle.c.y))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(triangle.a.y, Math.max(triangle.b.y, triangle.c.y))));
        boolean touched = false;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float py = y + 0.5f;
                float w0 = edge(triangle.b.x, triangle.b.y, triangle.c.x, triangle.c.y, px, py);
                float w1 = edge(triangle.c.x, triangle.c.y, triangle.a.x, triangle.a.y, px, py);
                float w2 = edge(triangle.a.x, triangle.a.y, triangle.b.x, triangle.b.y, px, py);
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                w0 /= area;
                w1 /= area;
                w2 /= area;
                float reciprocalDepth = w0 / triangle.a.depth + w1 / triangle.b.depth
                        + w2 / triangle.c.depth;
                if (!Float.isFinite(reciprocalDepth) || reciprocalDepth <= 0.0f) continue;
                float pixelDepth = 1.0f / reciprocalDepth;
                int offset = y * width + x;
                if (!Float.isFinite(pixelDepth) || pixelDepth >= depth[offset]) continue;
                float perspectiveA = (w0 / triangle.a.depth) / reciprocalDepth;
                float perspectiveB = (w1 / triangle.b.depth) / reciprocalDepth;
                float perspectiveC = (w2 / triangle.c.depth) / reciprocalDepth;
                int color = shade(triangle.a.vertex, triangle.b.vertex, triangle.c.vertex,
                        perspectiveA, perspectiveB, perspectiveC, w0, w1, w2,
                        textures, clientCycle, presentation);
                // RuneLite's GPU texture upload preserves the client texture
                // convention that RGB zero is a transparent texel.
                if (triangle.a.vertex.colorEncoding() == GpuColorEncoding.TEXTURE_LIGHTNESS
                        && (color >>> 24) == 0) continue;
                color = present(color, presentation);
                color = applyFog(color, triangle, perspectiveA, perspectiveB, perspectiveC,
                        presentation, fogBounds);
                int opacity = opacity(command, triangle.a.vertex, triangle.b.vertex,
                        triangle.c.vertex, perspectiveA, perspectiveB, perspectiveC);
                if (opacity <= 0) continue;
                if (!alphaPass || opacity >= 255) {
                    pixels[offset] = color;
                    depth[offset] = pixelDepth;
                } else {
                    pixels[offset] = blend(pixels[offset], color, opacity);
                }
                touched = true;
            }
        }
        return touched;
    }

    private static int shade(GpuSceneVertex a, GpuSceneVertex b, GpuSceneVertex c,
                             float wa, float wb, float wc,
                             float screenWa, float screenWb, float screenWc,
                             Map<Integer, RenderTextureResource> textures,
                             int clientCycle, RenderPresentation presentation) {
        float u = a.u() * wa + b.u() * wb + c.u() * wc;
        float v = a.v() * wa + b.v() * wb + c.v() * wc;
        boolean flat = a.renderType() == 1 || a.renderType() == 3;
        float light = flat ? a.encodedColor()
                : a.encodedColor() * screenWa + b.encodedColor() * screenWb
                + c.encodedColor() * screenWc;
        if (a.colorEncoding() == GpuColorEncoding.TEXTURE_LIGHTNESS && a.textureId() >= 0) {
            RenderTextureResource texture = textures.get(a.textureId());
            if (texture != null && texture.hasPixels()) {
                TextureAnimation.UvOffset animation = TextureAnimation.offset(texture, clientCycle);
                boolean animated = animation.u() != 0.0f || animation.v() != 0.0f;
                int sampled = sample(texture, u + animation.u(), v + animation.v(), animated);
                if ((sampled >>> 24) == 0) return 0;
                int scale = Math.max(0, Math.min(128, Math.round(light)));
                return scaleRgb(sampled, scale);
            }
            int gray = Math.max(0, Math.min(255, Math.round(light * 2.0f)));
            return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
        }
        if (flat) {
            return 0xFF000000 | OsrsTerrainColorMath.packedHslToRgb(a.encodedColor(), 0.6);
        }
        if (presentation.smoothBanding()) {
            int packedHsl = (int) (a.encodedColor() * screenWa
                    + b.encodedColor() * screenWb + c.encodedColor() * screenWc);
            return 0xFF000000 | OsrsTerrainColorMath.packedHslToRgb(packedHsl, 0.6);
        }
        int first = OsrsTerrainColorMath.packedHslToRgb(a.encodedColor(), 0.6);
        int second = OsrsTerrainColorMath.packedHslToRgb(b.encodedColor(), 0.6);
        int third = OsrsTerrainColorMath.packedHslToRgb(c.encodedColor(), 0.6);
        return interpolateRgb(first, second, third, wa, wb, wc);
    }

    private static int sample(RenderTextureResource texture, float u, float v,
                              boolean wrap) {
        int width = texture.width();
        int height = texture.height();
        // Static model UVs use RuneLite's clamp-to-edge policy. Animated
        // textures are shifted in pixel space by the client and wrap around
        // their tile, so only that path uses modular UVs.
        float sampleU = wrap ? wrapUv(u) : clampUv(u);
        float sampleV = wrap ? wrapUv(v) : clampUv(v);
        int x = Math.max(0, Math.min(width - 1, (int) Math.floor(sampleU * width)));
        int y = Math.max(0, Math.min(height - 1, (int) Math.floor(sampleV * height)));
        int pixel = texture.pixelAt(x, y) & 0xFFFFFF;
        return pixel == 0 ? 0 : 0xFF000000 | pixel;
    }

    private static float clampUv(float value) {
        return Math.max(0.0f, Math.min(0.99999994f, value));
    }

    private static float wrapUv(float value) {
        float wrapped = value - (float) Math.floor(value);
        return wrapped >= 1.0f ? 0.0f : wrapped;
    }

    private static int opacity(GpuDrawCommand command, GpuSceneVertex a, GpuSceneVertex b,
                               GpuSceneVertex c, float wa, float wb, float wc) {
        float raw = a.alpha() * wa + b.alpha() * wb + c.alpha() * wc;
        if (command.layer() == SceneLayer.Kind.TERRAIN) return Math.round(raw);
        if (a.renderType() == 3) return 128;
        return 255 - Math.round(raw);
    }

    private static int blend(int background, int foreground, int opacity) {
        int inverse = 255 - opacity;
        int r = (((foreground >> 16) & 0xFF) * opacity + ((background >> 16) & 0xFF) * inverse) / 255;
        int g = (((foreground >> 8) & 0xFF) * opacity + ((background >> 8) & 0xFF) * inverse) / 255;
        int b = ((foreground & 0xFF) * opacity + (background & 0xFF) * inverse) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int scaleRgb(int color, int scale) {
        int r = (((color >> 16) & 0xFF) * scale) / 128;
        int g = (((color >> 8) & 0xFF) * scale) / 128;
        int b = ((color & 0xFF) * scale) / 128;
        return 0xFF000000 | Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
    }

    private static int present(int color, RenderPresentation presentation) {
        if ((color >>> 24) == 0) return color;
        int red = presentation.apply((color >>> 16) & 0xFF);
        int green = presentation.apply((color >>> 8) & 0xFF);
        int blue = presentation.apply(color & 0xFF);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int applyFog(int color, TriangleWork triangle,
                                float firstWeight, float secondWeight, float thirdWeight,
                                RenderPresentation presentation, SceneFog.Bounds bounds) {
        float amount = SceneFog.amount(
                triangle.a.vertex.x() * firstWeight
                        + triangle.b.vertex.x() * secondWeight
                        + triangle.c.vertex.x() * thirdWeight,
                triangle.a.vertex.z() * firstWeight
                        + triangle.b.vertex.z() * secondWeight
                        + triangle.c.vertex.z() * thirdWeight,
                bounds, presentation.fogDepthTiles());
        if (amount <= 0.0f) return color;
        int fog = presentation.fogColor();
        int red = mix((color >>> 16) & 0xFF, (fog >>> 16) & 0xFF, amount);
        int green = mix((color >>> 8) & 0xFF, (fog >>> 8) & 0xFF, amount);
        int blue = mix(color & 0xFF, fog & 0xFF, amount);
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    private static int mix(int first, int second, float amount) {
        return clamp(Math.round(first + (second - first) * amount));
    }

    private static int interpolateRgb(int first, int second, int third,
                                      float wa, float wb, float wc) {
        int r = Math.round(((first >> 16) & 0xFF) * wa + ((second >> 16) & 0xFF) * wb
                + ((third >> 16) & 0xFF) * wc);
        int g = Math.round(((first >> 8) & 0xFF) * wa + ((second >> 8) & 0xFF) * wb
                + ((third >> 8) & 0xFF) * wc);
        int b = Math.round((first & 0xFF) * wa + (second & 0xFF) * wb + (third & 0xFF) * wc);
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private static float edge(float ax, float ay, float bx, float by, float cx, float cy) {
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
    }

    private record ProjectedVertex(float x, float y, float depth, GpuSceneVertex vertex) { }

    private record ViewVertex(float x, float y, float depth, GpuSceneVertex vertex) { }

    private record TriangleWork(ProjectedVertex a, ProjectedVertex b, ProjectedVertex c,
                                float averageDepth) { }

    private record DrawWork(GpuDrawCommand command, TriangleWork triangle) { }
}
