package com.rspsi.editor.overlay;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Thread-safe registry for declarative HUD contributions. */
public final class OverlayRegistry {
    private final Map<String, OverlayContribution> contributions = new LinkedHashMap<>();

    public synchronized AutoCloseable register(OverlayContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        if (contributions.putIfAbsent(contribution.id(), contribution) != null) {
            throw new IllegalArgumentException("Duplicate declarative overlay: " + contribution.id());
        }
        return () -> unregister(contribution.id());
    }

    public synchronized void unregister(String id) {
        if (id != null) contributions.remove(id);
    }

    public synchronized List<OverlayContribution> contributions() {
        return contributions.values().stream()
                .sorted(Comparator.comparing(OverlayContribution::layer)
                        .thenComparingInt(OverlayContribution::priority)
                        .thenComparing(OverlayContribution::id))
                .toList();
    }

    public synchronized OverlayContribution contribution(String id) {
        return contributions.get(id);
    }

    public synchronized int size() {
        return contributions.size();
    }

    public synchronized void clear() {
        contributions.clear();
    }
}
