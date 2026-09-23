package com.rspsi.editor.integration.content;

import java.util.Locale;

/** Stable declarative server-content capabilities understood by Studio. */
public enum ContentCapability {
    NPC_SPAWNS("spawns"),
    AREAS("areas"),
    SERVER_DEFINITIONS("server-definitions"),
    PACK_DEFINITIONS("pack-definitions"),
    CACHE_DEFINITIONS("cache-definitions"),
    DROP_TABLES("drops"),
    SKILL_NODES("skill-nodes"),
    GAMEVALS("gamevals"),
    SYMBOLS("symbols"),
    COLLISION("collision"),
    WORLD_MAP("world-map"),
    QUESTS("quests"),
    CUSTOM("custom");

    private final String id;

    ContentCapability(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ContentCapability fromId(String value) {
        if (value == null || value.isBlank()) return CUSTOM;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (ContentCapability capability : values()) {
            if (capability.id.equals(normalized)) return capability;
        }
        return CUSTOM;
    }
}
