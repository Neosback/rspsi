package com.rspsi.editor.knowledge;

import com.rspsi.editor.knowledge.derived.TerrainTopology;
import com.rspsi.editor.knowledge.graph.AssetKnowledgeGraph;
import com.rspsi.editor.model.TileCoordinate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable snapshot of derived world knowledge, provenance facts, and semantic classifications.
 */
public record KnowledgeSnapshot(
        Map<TileCoordinate, Set<SemanticTag>> tileTags,
        Map<SemanticTag, Set<TileCoordinate>> tagTiles,
        Map<TileCoordinate, List<KnowledgeFact<SemanticTag>>> tileFacts,
        Map<TileCoordinate, TerrainTopology> topology,
        Map<Integer, RegionProfile> regionProfiles,
        RegionProfile worldProfile,
        AssetKnowledgeGraph knowledgeGraph
) {
    /** Backwards-compatible constructor for 4-argument snapshot. */
    public KnowledgeSnapshot(
            Map<TileCoordinate, Set<SemanticTag>> tileTags,
            Map<SemanticTag, Set<TileCoordinate>> tagTiles,
            Map<Integer, RegionProfile> regionProfiles,
            RegionProfile worldProfile
    ) {
        this(tileTags, tagTiles, Map.of(), Map.of(), regionProfiles, worldProfile, new AssetKnowledgeGraph());
    }

    public KnowledgeSnapshot {
        Objects.requireNonNull(tileTags, "tileTags");
        Objects.requireNonNull(tagTiles, "tagTiles");
        Objects.requireNonNull(tileFacts, "tileFacts");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(regionProfiles, "regionProfiles");
        Objects.requireNonNull(worldProfile, "worldProfile");
        Objects.requireNonNull(knowledgeGraph, "knowledgeGraph");
        tileTags = Collections.unmodifiableMap(Map.copyOf(tileTags));
        tagTiles = Collections.unmodifiableMap(Map.copyOf(tagTiles));
        tileFacts = Collections.unmodifiableMap(Map.copyOf(tileFacts));
        topology = Collections.unmodifiableMap(Map.copyOf(topology));
        regionProfiles = Collections.unmodifiableMap(Map.copyOf(regionProfiles));
    }

    /** Returns all semantic tags associated with the given tile. */
    public Set<SemanticTag> tagsAt(TileCoordinate coordinate) {
        if (coordinate == null) return Set.of();
        return tileTags.getOrDefault(coordinate, Set.of());
    }

    /** Returns all provenance-bearing knowledge facts for the given tile. */
    public List<KnowledgeFact<SemanticTag>> factsAt(TileCoordinate coordinate) {
        if (coordinate == null) return List.of();
        return tileFacts.getOrDefault(coordinate, List.of());
    }

    /** Returns the derived geometric topology for the given tile if computed. */
    public Optional<TerrainTopology> topologyAt(TileCoordinate coordinate) {
        if (coordinate == null) return Optional.empty();
        return Optional.ofNullable(topology.get(coordinate));
    }

    /** Returns whether the given tile possesses the specified semantic tag. */
    public boolean hasTag(TileCoordinate coordinate, SemanticTag tag) {
        if (coordinate == null || tag == null) return false;
        Set<SemanticTag> tags = tileTags.get(coordinate);
        return tags != null && tags.contains(tag);
    }

    /** Returns all tile coordinates possessing the specified semantic tag. */
    public Set<TileCoordinate> findTiles(SemanticTag tag) {
        if (tag == null) return Set.of();
        return tagTiles.getOrDefault(tag, Set.of());
    }

    /** Returns all tile coordinates that possess at least one semantic tag. */
    public Set<TileCoordinate> allTaggedTiles() {
        return tileTags.keySet();
    }

    /** Returns the statistical profile for a specific region, if computed. */
    public Optional<RegionProfile> regionProfile(int regionId) {
        return Optional.ofNullable(regionProfiles.get(regionId));
    }
}
