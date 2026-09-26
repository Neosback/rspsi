package com.rspsi.editor.selection;

import com.rspsi.editor.model.WorldObject;

import java.util.Objects;

public record ObjectSelection(WorldObject object) implements Selection {
    public ObjectSelection {
        Objects.requireNonNull(object, "object");
    }
}
