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
import java.util.LinkedHashSet;
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
    private final Set<String> builtInBrushIds = new LinkedHashSet<>();
    private final Set<String> hostBrushIds = new LinkedHashSet<>();
    private int brushRadius = 0;

    public StudioBrushManager() {
        registerBuiltIn(new SquareBrush());
        registerBuiltIn(new CircleBrush());
        registerBuiltIn(new DiamondBrush());
        registerBuiltIn(new CheckerBrush());
        registerBuiltIn(new GaussianBrush());
        registerBuiltIn(new SlopeBrush());
        registerBuiltIn(new TerraceBrush());

        ServiceLoader.load(EditorBrush.class).forEach(this::registerBuiltIn);
    }

    private void registerBuiltIn(EditorBrush brush) {
        register(brush);
        builtInBrushIds.add(brush.id());
    }

    public synchronized void register(EditorBrush brush) {
        Objects.requireNonNull(brush, "brush");
        if (brush.id() == null || brush.id().isBlank()) {
            throw new IllegalArgumentException("Brush id cannot be blank");
        }
        brushes.put(brush.id(), brush);
        enabled.putIfAbsent(brush.id(), true);
    }

    /**
     * Mirrors brushes from the active neutral plugin host into Studio's shared
     * Brush Settings. Built-in IDs remain reserved and extension-owned brushes
     * disappear automatically when the rebuilt host no longer exposes them.
     */
    public synchronized void syncHostBrushes(List<? extends EditorBrush> hostBrushes) {
        Set<String> nextIds = new LinkedHashSet<>();
        if (hostBrushes != null) {
            for (EditorBrush brush : hostBrushes) {
                if (brush == null || brush.id() == null || brush.id().isBlank()) continue;
                if (builtInBrushIds.contains(brush.id())) continue;
                nextIds.add(brush.id());
                brushes.put(brush.id(), brush);
                enabled.putIfAbsent(brush.id(), true);
            }
        }

        Set<String> removed = new LinkedHashSet<>(hostBrushIds);
        removed.removeAll(nextIds);
        for (String id : removed) {
            brushes.remove(id);
            enabled.remove(id);
            activeByTool.entrySet().removeIf(entry -> id.equals(entry.getValue()));
        }

        hostBrushIds.clear();
        hostBrushIds.addAll(nextIds);
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
        Set<BrushCapability> required = requiredCapabilities == null || requiredCapabilities.isEmpty()
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
