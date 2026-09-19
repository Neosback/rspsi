package com.rspsi.editor.knowledge;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.knowledge.cache.CacheKnowledgeIndex;
import com.rspsi.editor.knowledge.derived.TerrainTopology;
import com.rspsi.editor.knowledge.derived.TerrainTopologyAnalyzer;
import com.rspsi.editor.knowledge.graph.AssetKnowledgeGraph;
import com.rspsi.editor.knowledge.graph.GraphEdge;
import com.rspsi.editor.knowledge.graph.GraphNode;
import com.rspsi.editor.knowledge.inference.ObjectSemanticClassifier;
import com.rspsi.editor.knowledge.inference.TerrainSemanticClassifier;
import com.rspsi.editor.knowledge.scene.SceneFacts;
import com.rspsi.editor.knowledge.scene.SceneObjectFact;
import com.rspsi.editor.knowledge.user.UserKnowledgeOverrides;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.plugin.ContributionOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative 5-Layer World Knowledge Service.
 *
 * <p>Orchestrates Layer 1 (Cache Facts), Layer 2 (Scene Facts), Layer 3 (Derived Topology &
 * Connectivity), Layer 4 (Inference Classifiers with Confidence & Evidence), and Layer 5
 * (User Metadata Overrides), while maintaining an indexed {@link AssetKnowledgeGraph}.</p>
 */
public final class WorldKnowledgeService {
    private final EditorSession session;
    private final DefinitionProvider definitions;
    private final CacheKnowledgeIndex cacheIndex;
    private final ObjectSemanticClassifier objectClassifier;
    private final UserKnowledgeOverrides userOverrides = new UserKnowledgeOverrides();
    private final List<AnalyzerEntry> analyzers = new CopyOnWriteArrayList<>();

    private volatile KnowledgeSnapshot cachedSnapshot;
    private volatile boolean dirty = true;

    private record AnalyzerEntry(ContributionOwner owner, KnowledgeAnalyzer analyzer) { }

    public WorldKnowledgeService(EditorSession session) {
        this(session, null);
    }

    public WorldKnowledgeService(EditorSession session, DefinitionProvider definitions) {
        this.session = Objects.requireNonNull(session, "session");
        this.definitions = definitions;
        this.cacheIndex = definitions != null ? new CacheKnowledgeIndex(definitions) : null;
        this.objectClassifier = definitions != null ? new ObjectSemanticClassifier(definitions) : null;

        registerDefaultAnalyzers();
        session.addChangeListener(changedTiles -> invalidate());
    }

    private void registerDefaultAnalyzers() {
        registerAnalyzer(ContributionOwner.SYSTEM, KnowledgeAnalyzer.standardTerrainAnalyzer());
        registerAnalyzer(ContributionOwner.SYSTEM, KnowledgeAnalyzer.standardObjectAnalyzer());
    }

    /**
     * Registers a custom analyzer bound to a contribution owner.
     * The returned handle unregisters the analyzer on close.
     */
    public AutoCloseable registerAnalyzer(ContributionOwner owner, KnowledgeAnalyzer analyzer) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(analyzer, "analyzer");
        AnalyzerEntry entry = new AnalyzerEntry(owner, analyzer);
        analyzers.add(entry);
        invalidate();
        return () -> {
            analyzers.remove(entry);
            invalidate();
        };
    }

    /** Unregisters all analyzers associated with the given contribution owner. */
    public void unregisterAll(ContributionOwner owner) {
        Objects.requireNonNull(owner, "owner");
        boolean removed = analyzers.removeIf(e -> e.owner().equals(owner));
        if (removed) {
            invalidate();
        }
    }

    /** Marks the current cached knowledge snapshot dirty. */
    public void invalidate() {
        dirty = true;
    }

    /** Returns the user knowledge overrides manager. */
    public UserKnowledgeOverrides userOverrides() {
        return userOverrides;
    }

    /** Returns the cache knowledge index, if definition provider was supplied. */
    public Optional<CacheKnowledgeIndex> cacheIndex() {
        return Optional.ofNullable(cacheIndex);
    }

    /** Evaluates an object and returns all inferred semantic facts with evidence. */
    public List<KnowledgeFact<SemanticTag>> classifyObject(WorldObject object) {
        if (objectClassifier == null || object == null) return List.of();
        return objectClassifier.classify(object);
    }

    /** Returns the up-to-date immutable knowledge snapshot. */
    public KnowledgeSnapshot snapshot() {
        KnowledgeSnapshot current = cachedSnapshot;
        if (!dirty && current != null) {
            return current;
        }
        synchronized (this) {
            if (!dirty && cachedSnapshot != null) {
                return cachedSnapshot;
            }
            cachedSnapshot = computeSnapshot();
            dirty = false;
            return cachedSnapshot;
        }
    }

    private KnowledgeSnapshot computeSnapshot() {
        WorldDocument world = session.world();
        Map<TileCoordinate, Set<SemanticTag>> tileTags = new HashMap<>();
        Map<SemanticTag, Set<TileCoordinate>> tagTiles = new HashMap<>();
        Map<TileCoordinate, List<KnowledgeFact<SemanticTag>>> tileFacts = new HashMap<>();

        // Layer 3: Derived Topology
        Map<TileCoordinate, TerrainTopology> topology = TerrainTopologyAnalyzer.analyze(world);

        // Layer 2: Scene Facts
        SceneFacts sceneFacts = new SceneFacts(world, definitions);

        // Asset Knowledge Graph
        AssetKnowledgeGraph graph = new AssetKnowledgeGraph();
        String regionIdStr = "region:0";
        graph.addNode(GraphNode.of(regionIdStr, GraphNode.NodeType.REGION));

        // 1. Run registered analyzers (Layer 3 / System / Plugin)
        for (AnalyzerEntry entry : analyzers) {
            try {
                entry.analyzer().analyze(world, (coord, tag) -> {
                    if (!userOverrides.isTagSuppressed(coord, tag)) {
                        tileTags.computeIfAbsent(coord, k -> new HashSet<>()).add(tag);
                        tagTiles.computeIfAbsent(tag, k -> new HashSet<>()).add(coord);
                        tileFacts.computeIfAbsent(coord, k -> new ArrayList<>())
                                .add(KnowledgeFact.exact(tag, KnowledgeSource.RULE));
                    }
                });
            } catch (Exception ignored) {
                // Resilient to analyzer failure
            }
        }

        // 2. Layer 4: Multi-signal Inferences
        for (int plane = 0; plane < world.planes(); plane++) {
            for (int x = 0; x < world.width(); x++) {
                for (int y = 0; y < world.length(); y++) {
                    TileCoordinate coord = new TileCoordinate(plane, x, y);
                    String tileNodeId = "tile:" + plane + "_" + x + "_" + y;
                    graph.addNode(GraphNode.of(tileNodeId, GraphNode.NodeType.TILE));
                    graph.addEdge(GraphEdge.of(regionIdStr, tileNodeId, GraphEdge.EdgeType.CONTAINS));

                    TerrainTopology topo = topology.get(coord);

                    // Terrain inference
                    List<KnowledgeFact<SemanticTag>> terrainInferred = TerrainSemanticClassifier.classify(world, coord, topo);
                    for (KnowledgeFact<SemanticTag> fact : terrainInferred) {
                        if (!userOverrides.isTagSuppressed(coord, fact.value())) {
                            tileTags.computeIfAbsent(coord, k -> new HashSet<>()).add(fact.value());
                            tagTiles.computeIfAbsent(fact.value(), k -> new HashSet<>()).add(coord);
                            tileFacts.computeIfAbsent(coord, k -> new ArrayList<>()).add(fact);
                        }
                    }

                    // Object inferences
                    TileSnapshot tile = world.tile(plane, x, y).snapshot();
                    for (WorldObject obj : tile.objects()) {
                        String objDefNodeId = "loc:" + obj.id();
                        graph.addNode(GraphNode.of(objDefNodeId, GraphNode.NodeType.OBJECT_DEFINITION));
                        graph.addEdge(GraphEdge.of(tileNodeId, objDefNodeId, GraphEdge.EdgeType.CONTAINS));

                        if (objectClassifier != null) {
                            List<KnowledgeFact<SemanticTag>> objFacts = objectClassifier.classify(obj);
                            for (KnowledgeFact<SemanticTag> fact : objFacts) {
                                if (!userOverrides.isTagSuppressed(coord, fact.value())) {
                                    tileTags.computeIfAbsent(coord, k -> new HashSet<>()).add(fact.value());
                                    tagTiles.computeIfAbsent(fact.value(), k -> new HashSet<>()).add(coord);
                                    tileFacts.computeIfAbsent(coord, k -> new ArrayList<>()).add(fact);

                                    String tagNodeId = "tag:" + fact.value().qualifiedName();
                                    graph.addNode(GraphNode.of(tagNodeId, GraphNode.NodeType.SEMANTIC_TAG));
                                    graph.addEdge(GraphEdge.of(objDefNodeId, tagNodeId, GraphEdge.EdgeType.TAGGED_AS));
                                }
                            }
                        }

                        // Layer 5 Object Overrides
                        for (SemanticTag userTag : userOverrides.addedObjectTags(obj.id())) {
                            if (!userOverrides.isTagSuppressed(coord, userTag)) {
                                tileTags.computeIfAbsent(coord, k -> new HashSet<>()).add(userTag);
                                tagTiles.computeIfAbsent(userTag, k -> new HashSet<>()).add(coord);
                                tileFacts.computeIfAbsent(coord, k -> new ArrayList<>())
                                        .add(KnowledgeFact.user(userTag));
                            }
                        }
                    }

                    // Layer 5 Tile Overrides
                    for (SemanticTag userTag : userOverrides.addedTags(coord)) {
                        tileTags.computeIfAbsent(coord, k -> new HashSet<>()).add(userTag);
                        tagTiles.computeIfAbsent(userTag, k -> new HashSet<>()).add(coord);
                        tileFacts.computeIfAbsent(coord, k -> new ArrayList<>())
                                .add(KnowledgeFact.user(userTag));
                    }
                }
            }
        }

        // Freeze collections
        Map<TileCoordinate, Set<SemanticTag>> frozenTileTags = new HashMap<>();
        for (var e : tileTags.entrySet()) {
            frozenTileTags.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        Map<SemanticTag, Set<TileCoordinate>> frozenTagTiles = new HashMap<>();
        for (var e : tagTiles.entrySet()) {
            frozenTagTiles.put(e.getKey(), Collections.unmodifiableSet(e.getValue()));
        }
        Map<TileCoordinate, List<KnowledgeFact<SemanticTag>>> frozenFacts = new HashMap<>();
        for (var e : tileFacts.entrySet()) {
            frozenFacts.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }

        RegionProfile worldProfile = computeProfile(world, 0, 0, world.width(), world.length(), 0, topology);
        Map<Integer, RegionProfile> regionProfiles = new HashMap<>();
        regionProfiles.put(0, worldProfile);

        return new KnowledgeSnapshot(
                frozenTileTags,
                frozenTagTiles,
                frozenFacts,
                topology,
                regionProfiles,
                worldProfile,
                graph
        );
    }

    private RegionProfile computeProfile(
            WorldDocument world, int startX, int startY, int width, int length,
            int regionId, Map<TileCoordinate, TerrainTopology> topology
    ) {
        int totalTiles = width * length * world.planes();
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        long totalHeight = 0;
        int heightSamples = 0;
        double maxSlope = 0.0;
        int walkableTiles = 0;

        Map<Integer, Integer> underlays = new HashMap<>();
        Map<Integer, Integer> overlays = new HashMap<>();
        Map<ObjectCategory, Integer> categories = new HashMap<>();

        for (int plane = 0; plane < world.planes(); plane++) {
            for (int x = startX; x < startX + width; x++) {
                for (int y = startY; y < startY + length; y++) {
                    TileSnapshot tile = world.tile(plane, x, y).snapshot();
                    int h = (tile.southWestHeight() + tile.southEastHeight()
                            + tile.northEastHeight() + tile.northWestHeight()) >> 2;
                    minH = Math.min(minH, h);
                    maxH = Math.max(maxH, h);
                    totalHeight += h;
                    heightSamples++;

                    if (tile.underlayId() > 0) {
                        underlays.merge(tile.underlayId(), 1, Integer::sum);
                    }
                    if (tile.overlayId() > 0) {
                        overlays.merge(tile.overlayId(), 1, Integer::sum);
                    }
                    for (WorldObject obj : tile.objects()) {
                        categories.merge(obj.category(), 1, Integer::sum);
                    }

                    TileCoordinate coord = new TileCoordinate(plane, x, y);
                    TerrainTopology topo = topology.get(coord);
                    if (topo != null && topo.slopeMagnitude() > maxSlope) {
                        maxSlope = topo.slopeMagnitude();
                    }
                    if ((tile.flags() & 0x1) == 0) {
                        walkableTiles++;
                    }
                }
            }
        }

        double avgHeight = heightSamples > 0 ? (double) totalHeight / heightSamples : 0.0;
        if (minH == Integer.MAX_VALUE) minH = 0;
        if (maxH == Integer.MIN_VALUE) maxH = 0;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put(MetricKey.AVERAGE_HEIGHT.name(), avgHeight);
        metrics.put(MetricKey.HEIGHT_VARIANCE.name(), maxH - minH);
        metrics.put(MetricKey.MAX_SLOPE.name(), maxSlope);
        metrics.put(MetricKey.WALKABLE_RATIO.name(), totalTiles > 0 ? (double) walkableTiles / totalTiles : 0.0);

        return new RegionProfile(
                regionId, width, length, world.planes(), totalTiles,
                minH, maxH, avgHeight, underlays, overlays, categories, metrics
        );
    }
}
