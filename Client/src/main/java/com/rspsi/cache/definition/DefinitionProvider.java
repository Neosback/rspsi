package com.rspsi.cache.definition;

import java.util.Optional;

/** Provides cache definitions without exposing a backend library. */
public interface DefinitionProvider {
    Optional<ObjectDefinitionView> object(int id);

    Optional<FloorDefinitionView> underlay(int id);

    Optional<FloorDefinitionView> overlay(int id);
}
