package com.rspsi.editor.brush;

/** Optional extension for brushes that can evaluate an elevation result. */
public interface HeightBrush extends EditorBrush {
    int evaluateHeight(int currentHeight, int anchorHeight, double weight, HeightBrushContext context);
}
