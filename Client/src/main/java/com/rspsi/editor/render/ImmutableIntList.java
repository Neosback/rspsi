package com.rspsi.editor.render;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Immutable primitive-backed List<Integer> used for large renderer index buffers.
 *
 * <p>The public renderer contracts still expose List<Integer> for compatibility,
 * but retaining one boxed Integer object per index is prohibitively expensive for
 * map scenes. Boxing now occurs only when a caller actually reads an element.</p>
 */
final class ImmutableIntList extends AbstractList<Integer> implements RandomAccess {
    private final int[] values;

    private ImmutableIntList(int[] values) {
        this.values = values;
    }

    static List<Integer> copyOf(List<Integer> source) {
        Objects.requireNonNull(source, "source");
        if (source instanceof ImmutableIntList) return source;
        int[] values = new int[source.size()];
        for (int i = 0; i < values.length; i++) {
            Integer value = source.get(i);
            if (value == null) throw new NullPointerException("Renderer index cannot be null");
            values[i] = value;
        }
        return new ImmutableIntList(values);
    }

    static List<Integer> wrapOwned(int[] values) {
        return new ImmutableIntList(Objects.requireNonNull(values, "values"));
    }

    int getInt(int index) {
        return values[index];
    }

    @Override
    public Integer get(int index) {
        return values[index];
    }

    @Override
    public int size() {
        return values.length;
    }
}
