package com.rspsi.studio.brush;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;
import com.rspsi.editor.brush.builtin.CheckerBrush;
import com.rspsi.editor.brush.builtin.CircleBrush;
import com.rspsi.editor.brush.builtin.DiamondBrush;
import com.rspsi.editor.brush.builtin.GaussianBrush;
import com.rspsi.editor.brush.builtin.SlopeBrush;
import com.rspsi.editor.brush.builtin.SquareBrush;
import com.rspsi.editor.brush.builtin.TerraceBrush;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Studio-owned registry for frontend-neutral brush contributions.
 *
 * <p>The brush SPI lives in Client so tools can consume it without depending
 * on Dear ImGui. Studio owns discovery, enablement and per-tool selection.</p>
 */
public final class StudioBrushManager {
    private final Map<String, EditorBrush> brushes = new LinkedHashMap<>();
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private final Map<String, String> activeByTool = new LinkedHashMap<>();
    private int brushRadius = 0;

    public StudioBrushManager() {
        register(new SquareBrush());
        register(new CircleBrush());
        register(new DiamondBrush());
        register(new CheckerBrush());
        register(new GaussianBrush());
        register(new SlopeBrush());
        register(new TerraceBrush());

        ServiceLoader.load(EditorBrush.class).forEach(this::register);
    }

    public synchronized void register(EditorBrush brush) {
        Objects.requireNonNull(brush, "brush");
        if (brush.id() == null || brush.id().isBlank()) {
            throw new IllegalArgumentException("Brush id cannot be blank");
        }
        brushes.put(brush.id(), brush);
        enabled.putIfAbsent(brush.id(), true);
    }

    public synchronized List<EditorBrush> allBrushes() {
        return brushes.values().stream()
                .sorted(Comparator.comparing(EditorBrush::name).thenComparing(EditorBrush::id))
                .toList();
    }

    public synchronized List<EditorBrush> enabledBrushes() {
        return brushes.values().stream()
                .filter(brush -> enabled.getOrDefault(brush.id(), false))
                .sorted(Comparator.comparing(EditorBrush::name).thenComparing(EditorBrush::id))
                .toList();
    }

    public synchronized List<EditorBrush> compatibleBrushes(Set<BrushCapability> requiredCapabilities) {
        Set<BrushCapability> required = requiredCapabilities == null
                ? Set.of() : EnumSet.copyOf(requiredCapabilities);
        return enabledBrushes().stream()
                .filter(brush -> brush.capabilities().containsAll(required))
                .toList();
    }

    public synchronized boolean isEnabled(String brushId) {
        return enabled.getOrDefault(brushId, false);
    }

    public synchronized boolean setEnabled(String brushId, boolean value) {
        if (!brushes.containsKey(brushId)) return false;
        if (!value && enabledBrushes().size() <= 1 && isEnabled(brushId)) return false;
        enabled.put(brushId, value);
        if (!value) {
            activeByTool.entrySet().removeIf(entry -> entry.getValue().equals(brushId));
        }
        return true;
    }

    public synchronized EditorBrush activeBrush(String toolId, Set<BrushCapability> requiredCapabilities) {
        List<EditorBrush> compatible = compatibleBrushes(requiredCapabilities);
        if (compatible.isEmpty()) return null;
        String activeId = activeByTool.get(toolId);
        if (activeId != null) {
            for (EditorBrush brush : compatible) {
                if (brush.id().equals(activeId)) return brush;
            }
        }
        EditorBrush fallback = compatible.get(0);
        activeByTool.put(toolId, fallback.id());
        return fallback;
    }

    public synchronized void setActiveBrush(String toolId, String brushId) {
        if (!brushes.containsKey(brushId) || !isEnabled(brushId)) {
            throw new IllegalArgumentException("Brush is not available: " + brushId);
        }
        activeByTool.put(Objects.requireNonNull(toolId, "toolId"), brushId);
    }

    public synchronized int brushRadius() {
        return brushRadius;
    }

    public synchronized void setBrushRadius(int brushRadius) {
        if (brushRadius < 0 || brushRadius > 64) {
            throw new IllegalArgumentException("Brush radius must be 0 through 64");
        }
        this.brushRadius = brushRadius;
    }

    public synchronized List<String> capabilityLabels(EditorBrush brush) {
        List<String> labels = new ArrayList<>();
        for (BrushCapability capability : brush.capabilities()) {
            labels.add(switch (capability) {
                case SPATIAL_FOOTPRINT -> "Spatial";
                case WEIGHTED_FALLOFF -> "Falloff";
                case HEIGHT_MANIPULATION -> "Height";
                case TILE_PAINT -> "Paint";
                case OBJECT_SCATTER -> "Scatter";
            });
        }
        return List.copyOf(labels);
    }
}
