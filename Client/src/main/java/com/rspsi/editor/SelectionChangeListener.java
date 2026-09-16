package com.rspsi.editor;

import com.rspsi.editor.selection.Selection;

/** Observes the single neutral selection value used by all editor tools. */
@FunctionalInterface
public interface SelectionChangeListener {
    void changed(Selection selection);
}
