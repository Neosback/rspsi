package com.rspsi.editor.integration;

/**
 * Declared capabilities provided by a server integration provider.
 */
public enum IntegrationCapability {
    SYMBOLS("Symbols & RSCM Mappings"),
    GAMEVALS("Gameval Definitions"),
    CONTENT_INDEX("Declarative Content Index"),
    CONTENT_MANIFESTS("Content Manifests"),
    NPC_SPAWNS("NPC Spawn Data"),
    AREAS("Area Data"),
    DROP_TABLES("Drop Table Data"),
    SKILL_NODES("Skill Node Data"),
    SERVER_COLLISION("Server Collision Data"),
    CONTENT_DIAGNOSTICS("Content Parse Diagnostics"),
    LOC_REFERENCES("Location Content References"),
    MAP_REFERENCES("Map Region Script Triggers"),
    INTERFACE_REFERENCES("Interface Component Mappings"),
    CS2_SOURCES("ClientScript 2 Source Files"),
    CACHE_BUILD("Cache Compilation & Packing"),
    CUSTOM_CACHE_ASSETS("Custom Asset Packing"),
    RUNTIME_SIMULATION("Server Script Simulation"),
    LIVE_SERVER("Live Server Connection"),
    SOURCE_NAVIGATION("Direct Source File Navigation"),
    SOURCE_SEMANTICS("Kotlin/OpenRune Semantic Source Index");

    private final String description;

    IntegrationCapability(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
