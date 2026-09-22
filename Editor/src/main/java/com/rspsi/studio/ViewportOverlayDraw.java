package com.rspsi.studio;

import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.CameraState;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.render.SceneCameraProjection;
import com.rspsi.editor.render.ScreenPoint;
import com.rspsi.studio.ui.SelectionOverlayStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.Objects;

/**
 * Dear ImGui implementation of the rich 3D/2D overlay drawing vocabulary.
 *
 * <p>Projects 3D world coordinates onto the 2D ImGui viewport surface using
 * the active camera and perspective projection parameters, drawing directly
 * onto the active {@link ImDrawList}.</p>
 */
public final class ViewportOverlayDraw implements OverlayDraw {

    @FunctionalInterface
    public interface ViewportElevationSampler {
        float[] cornerHeights(int plane, int x, int y);
    }

    private final ImDrawList drawList;
    private final float originX;
    private final float originY;
    private final int width;
    private final int height;
    private final CameraState camera;
    private final SceneCameraProjection projection;
    private final ViewportElevationSampler elevationSampler;

    public ViewportOverlayDraw(ImDrawList drawList, float originX, float originY,
                               int width, int height, CameraState camera) {
        this(drawList, originX, originY, width, height, camera, SceneCameraProjection.editorDefault(), null);
    }

    public ViewportOverlayDraw(ImDrawList drawList, float originX, float originY,
                               int width, int height, CameraState camera,
                               SceneCameraProjection projection) {
        this(drawList, originX, originY, width, height, camera, projection, null);
    }

    public ViewportOverlayDraw(ImDrawList drawList, float originX, float originY,
                               int width, int height, CameraState camera,
                               SceneCameraProjection projection,
                               ViewportElevationSampler elevationSampler) {
        this.drawList = Objects.requireNonNull(drawList, "drawList");
        this.originX = originX;
        this.originY = originY;
        this.width = width;
        this.height = height;
        this.camera = Objects.requireNonNull(camera, "camera");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.elevationSampler = elevationSampler;
    }

    public static int toImGuiColor(int colorRgba) {
        float r = ((colorRgba >>> 24) & 0xFF) / 255.0f;
        float g = ((colorRgba >>> 16) & 0xFF) / 255.0f;
        float b = ((colorRgba >>> 8) & 0xFF) / 255.0f;
        float a = (colorRgba & 0xFF) / 255.0f;
        return ImGui.getColorU32(r, g, b, a);
    }

    @Override
    public ScreenPoint worldToScreen(float worldX, float worldY, float worldZ) {
        return project(worldX, worldY, worldZ, originX, originY, width, height, camera, projection);
    }

    public static ScreenPoint project(float worldX, float worldY, float worldZ,
                                      float originX, float originY, int width, int height,
                                      CameraState camera, SceneCameraProjection projection) {
        if (width <= 0 || height <= 0) return ScreenPoint.hidden();

        float focal = (float) ((height * 0.5) / Math.tan(projection.verticalFieldOfView() * 0.5));
        float dx = worldX - camera.x();
        float dy = worldY - camera.y();
        float dz = worldZ - camera.z();

        float sinYaw = (float) Math.sin(camera.yaw());
        float cosYaw = (float) Math.cos(camera.yaw());
        float x1 = dx * cosYaw - dz * sinYaw;
        float z1 = dx * sinYaw + dz * cosYaw;

        float sinPitch = (float) Math.sin(camera.pitch());
        float cosPitch = (float) Math.cos(camera.pitch());
        float depth = z1 * cosPitch - dy * sinPitch;

        if (!Float.isFinite(depth) || depth <= projection.nearPlane()) {
            return ScreenPoint.hidden();
        }

        float camX = x1 / depth;
        float camY = (-dy * cosPitch - z1 * sinPitch) / depth;

        float screenX = originX + width * 0.5f + camX * focal;
        float screenY = originY + height * 0.5f - camY * focal;

        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) {
            return ScreenPoint.hidden();
        }

        return ScreenPoint.of(screenX, screenY);
    }

    @Override
    public void tileOutline(WorldTile tile) {
        // Reads the live setting each call (not cached) - Selection Overlay's
        // settings panel can change this color while the app is running.
        tileOutline(tile, SelectionOverlayStyle.shared().tileOutlineColor());
    }

    @Override
    public void tileOutline(WorldTile tile, int colorRgba) {
        if (tile == null) return;
        float x0 = tile.x() * 128.0f;
        float x1 = (tile.x() + 1) * 128.0f;
        float z0 = tile.y() * 128.0f;
        float z1 = (tile.y() + 1) * 128.0f;
        float basePlaneY = -tile.plane() * 240.0f;
        float y0 = basePlaneY, y1 = basePlaneY, y2 = basePlaneY, y3 = basePlaneY;
        if (elevationSampler != null) {
            float[] h = elevationSampler.cornerHeights(tile.plane(), tile.x(), tile.y());
            if (h != null && h.length >= 4) {
                y0 = h[0] - 2.0f;
                y1 = h[1] - 2.0f;
                y2 = h[2] - 2.0f;
                y3 = h[3] - 2.0f;
            }
        }

        ScreenPoint p0 = worldToScreen(x0, y0, z0);
        ScreenPoint p1 = worldToScreen(x1, y1, z0);
        ScreenPoint p2 = worldToScreen(x1, y2, z1);
        ScreenPoint p3 = worldToScreen(x0, y3, z1);

        if (p0.visible() && p1.visible() && p2.visible() && p3.visible()) {
            int col = toImGuiColor(colorRgba);
            drawList.addQuad(p0.x(), p0.y(), p1.x(), p1.y(), p2.x(), p2.y(), p3.x(), p3.y(), col, 2.0f);
        }
    }

    @Override
    public void tileFilled(WorldTile tile) {
        SelectionOverlayStyle style = SelectionOverlayStyle.shared();
        tileFilled(tile, (style.tileOutlineColor() & 0xFFFFFF00) | style.tileFillAlpha());
    }

    @Override
    public void tileFilled(WorldTile tile, int colorRgba) {
        if (tile == null) return;
        float x0 = tile.x() * 128.0f;
        float x1 = (tile.x() + 1) * 128.0f;
        float z0 = tile.y() * 128.0f;
        float z1 = (tile.y() + 1) * 128.0f;
        float basePlaneY = -tile.plane() * 240.0f;
        float y0 = basePlaneY, y1 = basePlaneY, y2 = basePlaneY, y3 = basePlaneY;
        if (elevationSampler != null) {
            float[] h = elevationSampler.cornerHeights(tile.plane(), tile.x(), tile.y());
            if (h != null && h.length >= 4) {
                y0 = h[0] - 2.0f;
                y1 = h[1] - 2.0f;
                y2 = h[2] - 2.0f;
                y3 = h[3] - 2.0f;
            }
        }

        ScreenPoint p0 = worldToScreen(x0, y0, z0);
        ScreenPoint p1 = worldToScreen(x1, y1, z0);
        ScreenPoint p2 = worldToScreen(x1, y2, z1);
        ScreenPoint p3 = worldToScreen(x0, y3, z1);

        if (p0.visible() && p1.visible() && p2.visible() && p3.visible()) {
            int col = toImGuiColor(colorRgba);
            drawList.addQuadFilled(p0.x(), p0.y(), p1.x(), p1.y(), p2.x(), p2.y(), p3.x(), p3.y(), col);
        }
    }

    @Override
    public void line(float x1, float y1, float z1, float x2, float y2, float z2, int colorRgba, float thickness) {
        ScreenPoint p1 = worldToScreen(x1, y1, z1);
        ScreenPoint p2 = worldToScreen(x2, y2, z2);
        if (p1.visible() && p2.visible()) {
            drawList.addLine(p1.x(), p1.y(), p2.x(), p2.y(), toImGuiColor(colorRgba), thickness);
        }
    }

    @Override
    public void box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int colorRgba, boolean filled) {
        ScreenPoint c000 = worldToScreen(minX, minY, minZ);
        ScreenPoint c100 = worldToScreen(maxX, minY, minZ);
        ScreenPoint c101 = worldToScreen(maxX, minY, maxZ);
        ScreenPoint c001 = worldToScreen(minX, minY, maxZ);
        ScreenPoint c010 = worldToScreen(minX, maxY, minZ);
        ScreenPoint c110 = worldToScreen(maxX, maxY, minZ);
        ScreenPoint c111 = worldToScreen(maxX, maxY, maxZ);
        ScreenPoint c011 = worldToScreen(minX, maxY, maxZ);

        int col = toImGuiColor(colorRgba);

        if (filled) {
            fillQuadIfVisible(c000, c100, c101, c001, col);
            fillQuadIfVisible(c010, c110, c111, c011, col);
            fillQuadIfVisible(c000, c100, c110, c010, col);
            fillQuadIfVisible(c001, c101, c111, c011, col);
            fillQuadIfVisible(c000, c001, c011, c010, col);
            fillQuadIfVisible(c100, c101, c111, c110, col);
        }

        lineScreen(c000, c100, col, 1.5f);
        lineScreen(c100, c101, col, 1.5f);
        lineScreen(c101, c001, col, 1.5f);
        lineScreen(c001, c000, col, 1.5f);

        lineScreen(c010, c110, col, 1.5f);
        lineScreen(c110, c111, col, 1.5f);
        lineScreen(c111, c011, col, 1.5f);
        lineScreen(c011, c010, col, 1.5f);

        lineScreen(c000, c010, col, 1.5f);
        lineScreen(c100, c110, col, 1.5f);
        lineScreen(c101, c111, col, 1.5f);
        lineScreen(c001, c011, col, 1.5f);
    }

    private void fillQuadIfVisible(ScreenPoint p0, ScreenPoint p1, ScreenPoint p2, ScreenPoint p3, int col) {
        if (p0.visible() && p1.visible() && p2.visible() && p3.visible()) {
            drawList.addQuadFilled(p0.x(), p0.y(), p1.x(), p1.y(), p2.x(), p2.y(), p3.x(), p3.y(), col);
        }
    }

    private void lineScreen(ScreenPoint p0, ScreenPoint p1, int col, float thickness) {
        if (p0.visible() && p1.visible()) {
            drawList.addLine(p0.x(), p0.y(), p1.x(), p1.y(), col, thickness);
        }
    }

    @Override
    public void circle(float cx, float cy, float cz, float radius, int colorRgba, float thickness) {
        int segments = 24;
        ScreenPoint prev = null;
        ScreenPoint first = null;
        int col = toImGuiColor(colorRgba);

        for (int i = 0; i <= segments; i++) {
            double angle = (2.0 * Math.PI * i) / segments;
            float px = cx + (float) Math.cos(angle) * radius;
            float pz = cz + (float) Math.sin(angle) * radius;
            ScreenPoint sp = worldToScreen(px, cy, pz);

            if (i == 0) {
                first = sp;
            } else if (prev != null && prev.visible() && sp.visible()) {
                drawList.addLine(prev.x(), prev.y(), sp.x(), sp.y(), col, thickness);
            }
            prev = sp;
        }
        if (prev != null && first != null && prev.visible() && first.visible()) {
            drawList.addLine(prev.x(), prev.y(), first.x(), first.y(), col, thickness);
        }
    }

    @Override
    public void arrow(float startX, float startY, float startZ, float endX, float endY, float endZ, int colorRgba) {
        line(startX, startY, startZ, endX, endY, endZ, colorRgba, 2.0f);
        ScreenPoint pEnd = worldToScreen(endX, endY, endZ);
        if (!pEnd.visible()) return;

        float dirX = endX - startX;
        float dirY = endY - startY;
        float dirZ = endZ - startZ;
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len > 0.001f) {
            dirX /= len;
            dirY /= len;
            dirZ /= len;
            float headLen = Math.min(len * 0.3f, 40.0f);
            float perpX = -dirZ * headLen * 0.5f;
            float perpZ = dirX * headLen * 0.5f;

            float arrowBaseX = endX - dirX * headLen;
            float arrowBaseY = endY - dirY * headLen;
            float arrowBaseZ = endZ - dirZ * headLen;

            line(endX, endY, endZ, arrowBaseX + perpX, arrowBaseY, arrowBaseZ + perpZ, colorRgba, 2.0f);
            line(endX, endY, endZ, arrowBaseX - perpX, arrowBaseY, arrowBaseZ - perpZ, colorRgba, 2.0f);
        }
    }

    @Override
    public void worldLabel(String text, float worldX, float worldY, float worldZ, int textColorRgba, int bgColorRgba) {
        ScreenPoint sp = worldToScreen(worldX, worldY, worldZ);
        if (sp.visible()) {
            screenLabel(text, sp.x(), sp.y(), textColorRgba, bgColorRgba);
        }
    }

    @Override
    public void screenLabel(String text, float screenX, float screenY, int textColorRgba, int bgColorRgba) {
        if (text == null || text.isBlank()) return;
        ImVec2 size = ImGui.calcTextSize(text);
        float padX = 5.0f;
        float padY = 2.0f;
        float x0 = screenX - padX;
        float y0 = screenY - padY;
        float x1 = screenX + size.x + padX;
        float y1 = screenY + size.y + padY;

        drawList.addRectFilled(x0, y0, x1, y1, toImGuiColor(bgColorRgba), 3.0f);
        drawList.addRect(x0, y0, x1, y1, toImGuiColor((textColorRgba & 0xFFFFFF00) | 0x44), 3.0f, 0, 1.0f);
        drawList.addText(screenX, screenY, toImGuiColor(textColorRgba), text);
    }

    @Override
    public void screenPolygon(java.util.List<float[]> screenPoints, int colorRgba, boolean filled, float thickness) {
        if (screenPoints == null || screenPoints.size() < 3) return;
        int n = screenPoints.size();
        ImVec2[] points = new ImVec2[n];
        for (int i = 0; i < n; i++) {
            float[] p = screenPoints.get(i);
            points[i] = new ImVec2(p[0], p[1]);
        }
        if (filled) {
            // colorRgba already carries the caller's configured fill alpha (see
            // SelectionOverlayStyle.fillColor) - no separate hardcoded alpha here.
            drawList.addConvexPolyFilled(points, n, toImGuiColor(colorRgba));
        } else {
            drawList.addPolyline(points, n, toImGuiColor(colorRgba), imgui.flag.ImDrawFlags.Closed, thickness);
        }
    }

    @Override
    public void screenRect(float minX, float minY, float maxX, float maxY, int colorRgba, boolean filled) {
        float x0 = originX + minX;
        float y0 = originY + minY;
        float x1 = originX + maxX;
        float y1 = originY + maxY;
        int col = toImGuiColor(colorRgba);
        if (filled) {
            drawList.addRectFilled(x0, y0, x1, y1, col, 0.0f);
        } else {
            drawList.addRect(x0, y0, x1, y1, col, 0.0f, 0, 1.5f);
        }
    }
}
