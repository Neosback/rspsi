package com.rspsi.editor.plugin.event;

import com.rspsi.editor.model.WorldRegion;
import java.util.Objects;

public record RegionLoadedEvent(WorldRegion region) {
    public RegionLoadedEvent { Objects.requireNonNull(region, "region"); }
}
