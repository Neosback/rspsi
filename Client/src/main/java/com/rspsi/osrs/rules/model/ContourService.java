package com.rspsi.osrs.rules.model;

import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.osrs.rules.model.ModelTransformPipeline.TransformedVertex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Formal OSRS ground contouring service.
 *
 * <p>Warp vertex heights based on the underlying terrain surface.
 * In OSRS, models are first lit using pre-contour normals, then warped by contourGround.</p>
 */
public final class ContourService {
    private ContourService() {}

    /**
     * Applies ground contouring to a model's vertices according to OSRS rules.
     * Returns null if contouring is disabled, out of bounds, or flat-skipped.
     */
    public static List<TransformedVertex> applyContour(
            WorldDocument document,
            WorldObject object,
            int footprintWidth,
            int footprintLength,
            List<TransformedVertex> transformed,
            ObjectAppearanceView appearance
    ) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(transformed, "transformed");
        if (appearance == null) return null;

        int type = appearance.contourGroundType();
        int parameter = appearance.contourGroundParameter();
        if (type < 0) return null;
        boolean usesAbovePlane = type == 4 || type == 5;
        if (usesAbovePlane && document.planes() <= object.plane() + 1) return null;

        int downwardHeight = 0;
        long radiusSquared = 0L;
        int modelMinY = Integer.MAX_VALUE;
        int modelMaxY = Integer.MIN_VALUE;

        for (TransformedVertex vertex : transformed) {
            if (-vertex.y() > downwardHeight) downwardHeight = -vertex.y();
            long squared = (long) vertex.x() * vertex.x() + (long) vertex.z() * vertex.z();
            if (squared > radiusSquared) radiusSquared = squared;
            modelMinY = Math.min(modelMinY, vertex.y());
            modelMaxY = Math.max(modelMaxY, vertex.y());
        }

        downwardHeight = Math.max(1, downwardHeight);
        int xzRadius = (int) (Math.sqrt((double) radiusSquared) + 0.99D);
        int verticalSpan = Math.max(1, modelMaxY - modelMinY);

        int anchorX = object.x() * 128;
        int anchorZ = object.y() * 128;
        int plane = object.plane();
        int minWorldX = anchorX - xzRadius;
        int maxWorldX = anchorX + xzRadius;
        int minWorldZ = anchorZ - xzRadius;
        int maxWorldZ = anchorZ + xzRadius;

        if (minWorldX < 0 || (maxWorldX + 128) >> 7 >= document.width()
                || minWorldZ < 0 || (maxWorldZ + 128) >> 7 >= document.length()) {
            return null;
        }

        int sceneHeight = objectCenterHeight(document, object, footprintWidth, footprintLength);
        int startTileX = minWorldX >> 7;
        int endTileX = (maxWorldX + 127) >> 7;
        int startTileZ = minWorldZ >> 7;
        int endTileZ = (maxWorldZ + 127) >> 7;

        if (!usesAbovePlane
                && sampleGrid(document, plane, startTileX, startTileZ) == sceneHeight
                && sampleGrid(document, plane, endTileX, startTileZ) == sceneHeight
                && sampleGrid(document, plane, startTileX, endTileZ) == sceneHeight
                && sampleGrid(document, plane, endTileX, endTileZ) == sceneHeight) {
            return null;
        }

        List<TransformedVertex> result = new ArrayList<>(transformed.size());
        for (TransformedVertex vertex : transformed) {
            int worldX = anchorX + vertex.x();
            int worldZ = anchorZ + vertex.z();
            int fractionX = worldX & 127;
            int fractionZ = worldZ & 127;
            int tileX = worldX >> 7;
            int tileZ = worldZ >> 7;

            int south = contourBlend(sampleGrid(document, plane, tileX, tileZ),
                    sampleGrid(document, plane, tileX + 1, tileZ), fractionX);
            int north = contourBlend(sampleGrid(document, plane, tileX, tileZ + 1),
                    sampleGrid(document, plane, tileX + 1, tileZ + 1), fractionX);
            int height = contourBlend(south, north, fractionZ);

            int newY;
            if ((type == 1 || type == 2) && parameter > 0) {
                int yRatio = ((-vertex.y()) << 16) / downwardHeight;
                if (yRatio < parameter) {
                    newY = vertex.y() + (parameter - yRatio) * (height - sceneHeight) / parameter;
                } else {
                    newY = vertex.y();
                }
            } else if (type == 3) {
                int delta = height - sceneHeight;
                if (parameter != 0) {
                    int limit = Math.abs(parameter);
                    delta = Math.max(-limit, Math.min(limit, delta));
                }
                newY = vertex.y() + delta;
            } else if (type == 4) {
                int aboveHeight = contourSampleAbove(document, plane, worldX, worldZ, fractionX, fractionZ);
                newY = vertex.y() + aboveHeight - sceneHeight + verticalSpan;
            } else if (type == 5) {
                int aboveHeight = contourSampleAbove(document, plane, worldX, worldZ, fractionX, fractionZ);
                int deltaHeight = height - aboveHeight;
                newY = (((vertex.y() << 8) / verticalSpan) * deltaHeight >> 8) - (sceneHeight - height);
            } else {
                newY = vertex.y() + height - sceneHeight;
            }

            result.add(new TransformedVertex(vertex.x(), newY, vertex.z()));
        }
        return result;
    }

    public static int contourBlend(int first, int second, int amount) {
        return (first * (128 - amount) + second * amount) >> 7;
    }

    private static int contourSampleAbove(WorldDocument document, int plane, int worldX,
                                          int worldZ, int fractionX, int fractionZ) {
        int tileX = worldX >> 7;
        int tileZ = worldZ >> 7;
        int south = contourBlend(sampleGrid(document, plane + 1, tileX, tileZ),
                sampleGrid(document, plane + 1, tileX + 1, tileZ), fractionX);
        int north = contourBlend(sampleGrid(document, plane + 1, tileX, tileZ + 1),
                sampleGrid(document, plane + 1, tileX + 1, tileZ + 1), fractionX);
        return contourBlend(south, north, fractionZ);
    }

    public static int sampleGrid(WorldDocument document, int plane, int gridX, int gridZ) {
        boolean eastEdge = gridX >= document.width();
        boolean northEdge = gridZ >= document.length();
        int tileX = Math.max(0, eastEdge ? document.width() - 1 : gridX);
        int tileZ = Math.max(0, northEdge ? document.length() - 1 : gridZ);
        TileSnapshot tile = document.tile(plane, tileX, tileZ).snapshot();
        if (eastEdge && northEdge) return tile.northEastHeight();
        if (eastEdge) return tile.southEastHeight();
        if (northEdge) return tile.northWestHeight();
        return tile.southWestHeight();
    }

    public static int objectCenterHeight(WorldDocument document, WorldObject object,
                                         int footprintWidth, int footprintLength) {
        int midX = object.x() + (footprintWidth >> 1);
        int midZ = object.y() + (footprintLength >> 1);
        long sum = (long) sampleGrid(document, object.plane(), midX, midZ)
                + sampleGrid(document, object.plane(), midX + 1, midZ)
                + sampleGrid(document, object.plane(), midX, midZ + 1)
                + sampleGrid(document, object.plane(), midX + 1, midZ + 1);
        return (int) (sum >> 2);
    }
}
