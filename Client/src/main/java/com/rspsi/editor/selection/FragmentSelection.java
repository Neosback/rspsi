package com.rspsi.editor.selection;

import com.rspsi.editor.model.WorldFragment;

import java.util.Objects;

public record FragmentSelection(WorldFragment fragment) implements Selection {
    public FragmentSelection {
        Objects.requireNonNull(fragment, "fragment");
    }
}
