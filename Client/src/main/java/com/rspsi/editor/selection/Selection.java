package com.rspsi.editor.selection;

/** Common UI-neutral selection value used by all editor tools. */
public sealed interface Selection permits TileSelection, TileSetSelection,
        TileAreaSelection, VertexSelection, ObjectSelection, ObjectSetSelection, FragmentSelection {
}
