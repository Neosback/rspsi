package com.rspsi.editor.selection;

import com.rspsi.editor.model.WorldObject;

import java.util.Set;

/**
 * A unified selection containing more than one world object.
 *
 * <p>The Java record shell remains because it directly implements the sealed Java
 * {@link Selection} hierarchy. Input normalization lives in {@link SelectionSetSemantics}.</p>
 */
public record ObjectSetSelection(Set<WorldObject> objects) implements Selection {
    public ObjectSetSelection {
        objects = SelectionSetSemantics.normalizeObjects(objects);
    }
}
