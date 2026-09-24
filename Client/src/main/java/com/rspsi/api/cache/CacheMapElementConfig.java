package com.rspsi.api.cache;

import com.rspsi.api.SpritePixels;
import com.rspsi.api.worldmap.MapElementConfig;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.MapElementDefinitionView;

/** {@link MapElementConfig} over a decoded map element definition. */
public final class CacheMapElementConfig implements MapElementConfig {
    private final MapElementDefinitionView definition;
    private final DefinitionProvider definitions;

    public CacheMapElementConfig(MapElementDefinitionView definition, DefinitionProvider definitions) {
        this.definition = definition;
        this.definitions = definitions;
    }

    @Override public int getId() { return definition.id(); }

    @Override
    public SpritePixels getMapIcon(boolean unused) {
        if (definition.spriteId() < 0) return null;
        return definitions.sprite(definition.spriteId(), 0).map(CacheSpritePixels::of).orElse(null);
    }

    @Override public int getCategory() { return definition.category(); }

    @Override public String getName() { return definition.name(); }

    @Override public int getSpriteId() { return definition.spriteId(); }

    @Override public boolean isMinimapVisible() { return definition.minimapVisible(); }

    @Override public boolean isWorldMapVisible() { return definition.worldMapVisible(); }
}
