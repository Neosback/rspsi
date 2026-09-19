package com.rspsi.editor.knowledge.user;

import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.model.TileCoordinate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 5 User Metadata: Author-defined semantic overrides and manual classifications.
 *
 * <p>User overrides take precedence over inferred (Layer 4) tags while leaving
 * underlying OSRS cache archives and world documents unmutated.</p>
 */
public final class UserKnowledgeOverrides {
    private final Map<TileCoordinate, Set<SemanticTag>> addedTileTags = new ConcurrentHashMap<>();
    private final Map<TileCoordinate, Set<SemanticTag>> removedTileTags = new ConcurrentHashMap<>();
    private final Map<Integer, Set<SemanticTag>> addedObjectTags = new ConcurrentHashMap<>();

    /** Adds a user-specified semantic tag to the given tile. */
    public void addTileTag(TileCoordinate coord, SemanticTag tag) {
        Objects.requireNonNull(coord, "coord");
        Objects.requireNonNull(tag, "tag");
        Set<SemanticTag> removed = removedTileTags.get(coord);
        if (removed != null) removed.remove(tag);
        addedTileTags.computeIfAbsent(coord, k -> ConcurrentHashMap.newKeySet()).add(tag);
    }

    /** Removes/suppresses a semantic tag on the given tile. */
    public void removeTileTag(TileCoordinate coord, SemanticTag tag) {
        Objects.requireNonNull(coord, "coord");
        Objects.requireNonNull(tag, "tag");
        Set<SemanticTag> added = addedTileTags.get(coord);
        if (added != null) added.remove(tag);
        removedTileTags.computeIfAbsent(coord, k -> ConcurrentHashMap.newKeySet()).add(tag);
    }

    /** Returns all user-added tags for a tile. */
    public Set<SemanticTag> addedTags(TileCoordinate coord) {
        Set<SemanticTag> set = addedTileTags.get(coord);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    /** Returns whether a tag is suppressed by the user on this tile. */
    public boolean isTagSuppressed(TileCoordinate coord, SemanticTag tag) {
        Set<SemanticTag> set = removedTileTags.get(coord);
        return set != null && set.contains(tag);
    }

    /** Adds a user-specified semantic tag to all instances of an object ID. */
    public void addObjectTag(int objectId, SemanticTag tag) {
        Objects.requireNonNull(tag, "tag");
        addedObjectTags.computeIfAbsent(objectId, k -> ConcurrentHashMap.newKeySet()).add(tag);
    }

    /** Returns all user-added tags for an object ID. */
    public Set<SemanticTag> addedObjectTags(int objectId) {
        Set<SemanticTag> set = addedObjectTags.get(objectId);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }
}
