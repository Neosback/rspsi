package com.rspsi.ui.workspace;

import com.rspsi.cache.definition.ModelGeometryView;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.Arrays;

/**
 * Small JavaFX adapter for inspecting one selected model without coupling the
 * asset browser to the map renderer. It is intentionally a bounded preview:
 * simple projected triangles are enough to catch missing geometry, winding,
 * and gross bounds errors before the future scene renderer exists.
 */
public final class ModelPreviewCanvas extends Region {
    private static final double PADDING = 18;
    private final Canvas canvas = new Canvas(240, 160);
    private ModelGeometryView geometry;

    public ModelPreviewCanvas() {
        getStyleClass().add("workspace-model-preview");
        setAccessibleText("Selected model geometry preview");
        setFocusTraversable(true);
        setMinHeight(120);
        setPrefHeight(180);
        setMaxHeight(260);
        canvas.setMouseTransparent(true);
        getChildren().add(canvas);
        widthProperty().addListener((observable, oldValue, newValue) -> render());
        heightProperty().addListener((observable, oldValue, newValue) -> render());
        render();
    }

    public void setGeometry(ModelGeometryView geometry) {
        this.geometry = geometry;
        render();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(Math.max(1, getWidth()));
        canvas.setHeight(Math.max(1, getHeight()));
        render();
    }

    private void render() {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        double width = Math.max(1, canvas.getWidth());
        double height = Math.max(1, canvas.getHeight());
        graphics.clearRect(0, 0, width, height);
        graphics.setFill(Color.web("#111827"));
        graphics.fillRect(0, 0, width, height);
        if (geometry == null) {
            drawMessage(graphics, "Select a model to preview");
            return;
        }
        int[] vertices = geometry.vertexPositions();
        int[] triangles = geometry.triangleIndices();
        if (vertices.length == 0 || triangles.length == 0) {
            drawMessage(graphics, "Geometry unavailable");
            return;
        }

        int vertexCount = geometry.vertexCount();
        double[] projectedX = new double[vertexCount];
        double[] projectedY = new double[vertexCount];
        double[] depth = new double[vertexCount];
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int offset = vertex * 3;
            double x = vertices[offset];
            double y = vertices[offset + 1];
            double z = vertices[offset + 2];
            projectedX[vertex] = (x - z) * 0.70710678;
            projectedY[vertex] = -y + (x + z) * 0.35;
            depth[vertex] = (x + z) * 0.70710678 + y * 0.2;
            minX = Math.min(minX, projectedX[vertex]);
            maxX = Math.max(maxX, projectedX[vertex]);
            minY = Math.min(minY, projectedY[vertex]);
            maxY = Math.max(maxY, projectedY[vertex]);
        }
        double spanX = Math.max(1, maxX - minX);
        double spanY = Math.max(1, maxY - minY);
        double scale = Math.min((width - 2 * PADDING) / spanX,
                (height - 2 * PADDING) / spanY);
        scale = Math.max(0.1, Math.min(scale, 4.0));
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            projectedX[vertex] = width * 0.5 + (projectedX[vertex] - centerX) * scale;
            projectedY[vertex] = height * 0.5 + (projectedY[vertex] - centerY) * scale;
        }

        Integer[] order = new Integer[geometry.triangleCount()];
        for (int triangle = 0; triangle < order.length; triangle++) order[triangle] = triangle;
        Arrays.sort(order, (left, right) -> Double.compare(averageDepth(triangles, depth, left),
                averageDepth(triangles, depth, right)));
        short[] colors = geometry.triangleColors();
        int[] alphas = geometry.triangleAlphas();
        for (int triangle : order) {
            int offset = triangle * 3;
            int a = triangles[offset];
            int b = triangles[offset + 1];
            int c = triangles[offset + 2];
            // Face transparency is a signed byte in the cache that the client
            // normalises to 0..255 while loading; -1 is 0xFF (invisible), not
            // "opaque". Masking keeps the preview's alpha identical to the
            // scene renderer's.
            double alpha = alphas.length == geometry.triangleCount()
                    ? 1.0 - Math.min(255, Math.max(0, alphas[triangle] & 0xFF)) / 255.0 : 0.88;
            if (alpha <= 0) continue;
            graphics.setGlobalAlpha(alpha);
            graphics.setFill(faceColor(triangle, colors));
            graphics.fillPolygon(new double[]{projectedX[a], projectedX[b], projectedX[c]},
                    new double[]{projectedY[a], projectedY[b], projectedY[c]}, 3);
            graphics.setStroke(Color.rgb(203, 213, 225, 0.42));
            graphics.strokePolygon(new double[]{projectedX[a], projectedX[b], projectedX[c]},
                    new double[]{projectedY[a], projectedY[b], projectedY[c]}, 3);
        }
        graphics.setGlobalAlpha(1.0);
    }

    private static double averageDepth(int[] triangles, double[] depth, int triangle) {
        int offset = triangle * 3;
        return (depth[triangles[offset]] + depth[triangles[offset + 1]]
                + depth[triangles[offset + 2]]) / 3.0;
    }

    private static Color faceColor(int triangle, short[] colors) {
        int encoded = colors.length == 0 ? triangle * 37 : Short.toUnsignedInt(colors[triangle]);
        double hue = (encoded & 63) * 360.0 / 64.0;
        double saturation = 0.48 + ((encoded >>> 6) & 7) / 20.0;
        double brightness = 0.58 + ((encoded >>> 9) & 7) / 22.0;
        return Color.hsb(hue, Math.min(1.0, saturation), Math.min(1.0, brightness));
    }

    private static void drawMessage(GraphicsContext graphics, String message) {
        graphics.setFill(Color.web("#94a3b8"));
        graphics.fillText(message, 14, 24);
    }
}
