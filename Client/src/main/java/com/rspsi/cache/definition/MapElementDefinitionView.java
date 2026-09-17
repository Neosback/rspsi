package com.rspsi.cache.definition;

import java.util.List;

/** Stable metadata for one OSRS world-map/minimap element. */
public record MapElementDefinitionView(
        int id,
        int spriteId,
        int hoverSpriteId,
        String name,
        int textColor,
        int hoverTextColor,
        int textSize,
        boolean worldMapVisible,
        boolean minimapVisible,
        boolean randomizePosition,
        List<String> actions) {
    public MapElementDefinitionView {
        if (id < 0 || spriteId < -1 || hoverSpriteId < -1
                || textColor < 0 || textColor > 0xFFFFFF
                || hoverTextColor < 0 || hoverTextColor > 0xFFFFFF
                || textSize < 0 || name == null || actions == null) {
            throw new IllegalArgumentException("Invalid map element definition");
        }
        name = name.trim();
        actions = List.copyOf(actions);
        if (actions.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Map element actions cannot be blank");
        }
    }
}
