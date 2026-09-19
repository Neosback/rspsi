package com.rspsi.editor.integration.npc;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

/**
 * Concrete server-defined NPC spawn placement.
 *
 * <p>Rendered in the viewport with distinct visual presentation (e.g. blue outline)
 * and managed under the Outliner's "Server Content" category.</p>
 */
public record NpcSpawn(
        int id,
        String symbolicName,
        TileCoordinate coordinate,
        int wanderRadius,
        int direction,
        String module,
        String script
) {
    public NpcSpawn {
        Objects.requireNonNull(symbolicName, "symbolicName");
        Objects.requireNonNull(coordinate, "coordinate");
        if (module == null) module = "";
        if (script == null) script = "";
    }
}
