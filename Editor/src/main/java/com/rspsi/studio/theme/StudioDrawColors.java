package com.rspsi.studio.theme;

/**
 * Dear ImGui's {@code ImDrawList} primitives (addRectFilled, addRect,
 * addText, addTriangleFilled, ...) pack color ints as {@code 0xAABBGGRR},
 * not the {@code 0xAARRGGBB} every other convention in this codebase uses -
 * a raw hex color handed to them directly comes out with red and blue
 * swapped. {@code ImGui.pushStyleColor}/{@code textColored} and the rest of
 * the style-color API do not need this: their binding already normalizes to
 * the natural {@code 0xAARRGGBB} order. Route every literal color through
 * here before it reaches an {@code ImDrawList} call.
 */
public final class StudioDrawColors {
    private StudioDrawColors() {
    }

    public static int abgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
