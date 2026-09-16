package com.rspsi.editor.selection;

import com.rspsi.editor.model.WorldObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** A unified selection containing more than one world object. */
public record ObjectSetSelection(Set<WorldObject> objects) implements Selection {
    public ObjectSetSelection {
        objects = Collections.unmodifiableSet(new LinkedHashSet<>(objects == null ? Set.of() : objects));
        if (objects.isEmpty()) {
            throw new IllegalArgumentException("An object selection cannot be empty");
        }
    }
}
