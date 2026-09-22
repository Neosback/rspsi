package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Incremental flattener that caches immutable per-tile GPU fragments.
 *
 * <p>The public {@link GpuUploadPlan} remains globally ordered for software and
 * native renderer compatibility, but expensive terrain/model face expansion is
 * only repeated for tiles in dirty world zones. Final assembly only offsets
 * already-flattened indices/commands and re-applies the same cross-tile command
 * merge rules as {@link GpuUploadPlanBuilder}.</p>
 */
public final class IncrementalGpuUploadPlanBuilder {
    private static final GpuUploadPlan EMPTY_GEOMETRY = new GpuUploadPlan(
            List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), "empty-tile-fragment");

    private final GpuUploadPlanBuilder fullBuilder = new GpuUploadPlanBuilder();
    private final Map<WorldTileAddress, TileFragment> cache = new LinkedHashMap<>();

    public BuildResult buildInitial(GpuScenePacket packet) {
        cache.clear();
        return build(packet, Set.of());
    }

    public BuildResult build(GpuScenePacket packet, Set<WorldZoneCoordinate> dirtyZones) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(dirtyZones, "dirtyZones");

        List<GpuSceneVertex> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();
        List<GpuTextureTriangle> textureTriangles = new ArrayList<>();
        LinkedHashSet<SceneOccluder> occluders = new LinkedHashSet<>();
        Map<WorldTileAddress, TileFragment> nextCache = new LinkedHashMap<>();

        int rebuilt = 0;
        int reused = 0;
        for (SceneTileSnapshot tile : packet.tiles()) {
            TileFragment fragment = cache.get(tile.worldAddress());
            boolean dirty = dirtyZones.contains(WorldZoneCoordinate.from(tile.worldAddress()));
            if (fragment == null || dirty || !sameTile(fragment.source(), tile)) {
                fragment = flattenTile(packet, tile);
                rebuilt++;
            } else {
                reused++;
            }
            nextCache.put(tile.worldAddress(), fragment);
            append(fragment, vertices, indices, commands, textureTriangles, occluders);
        }

        cache.clear();
        cache.putAll(nextCache);

        List<SceneOccluder> mergedOccluders = SceneOccluderMerger.merge(List.copyOf(occluders));
        GpuUploadPlan plan = new GpuUploadPlan(vertices, indices, commands, textureTriangles,
                packet.textures(), mergedOccluders,
                GpuUploadPlanBuilder.fingerprint(packet.fingerprint(), vertices, indices, commands,
                        textureTriangles, packet.textures(), mergedOccluders));
        return new BuildResult(plan, rebuilt, reused);
    }

    /** Creates an isolated cache snapshot for an asynchronous rebuild transaction. */
    public IncrementalGpuUploadPlanBuilder fork() {
        IncrementalGpuUploadPlanBuilder copy = new IncrementalGpuUploadPlanBuilder();
        copy.cache.putAll(cache);
        return copy;
    }

    public void invalidateAll() {
        cache.clear();
    }

    public int cachedTileCount() {
        return cache.size();
    }

    private TileFragment flattenTile(GpuScenePacket packet, SceneTileSnapshot tile) {
        if (tile.terrain().isEmpty() && tile.models().isEmpty()) {
            return new TileFragment(tile, EMPTY_GEOMETRY);
        }

        // Fragment geometry only depends on the immutable tile snapshot.
        // Texture pixels/resources are attached once to the final global plan,
        // so hashing the full texture repository for every dirty tile would
        // turn a local rebuild back into O(tiles * textures) work.
        GpuScenePacket singleTile = new GpuScenePacket(
                packet.window(), List.of(tile), packet.lightingProfile(),
                "tile:" + tile.worldAddress() + ":" + tile.hashCode(), Map.of());
        return new TileFragment(tile, fullBuilder.build(singleTile));
    }

    private static boolean sameTile(SceneTileSnapshot cached, SceneTileSnapshot current) {
        return cached == current || cached.equals(current);
    }

    private static void append(TileFragment fragment,
                               List<GpuSceneVertex> vertices,
                               List<Integer> indices,
                               List<GpuDrawCommand> commands,
                               List<GpuTextureTriangle> textureTriangles,
                               LinkedHashSet<SceneOccluder> occluders) {
        GpuUploadPlan plan = fragment.plan();
        int vertexBase = vertices.size();
        int indexBase = indices.size();

        vertices.addAll(plan.vertices());
        for (int index : plan.indices()) {
            indices.add(vertexBase + index);
        }
        for (GpuDrawCommand command : plan.commands()) {
            GpuDrawCommand shifted = new GpuDrawCommand(
                    command.tile(), command.scenePlane(), command.planeCullLevel(),
                    command.layer(), command.pass(),
                    indexBase + command.firstIndex(), command.indexCount(),
                    command.textureId(), command.priority(), command.depthBias(),
                    command.objectId(), command.renderMode(), command.wallDecorationPresentation());
            appendCommand(commands, shifted);
        }
        textureTriangles.addAll(plan.textureTriangles());
        occluders.addAll(fragment.source().occluders());
    }

    private static void appendCommand(List<GpuDrawCommand> commands, GpuDrawCommand command) {
        if (!commands.isEmpty() && command.pass() != GpuDrawCommand.SubmissionPass.ALPHA) {
            int last = commands.size() - 1;
            GpuDrawCommand previous = commands.get(last);
            if (previous.canMerge(command.tile(), command.scenePlane(), command.planeCullLevel(),
                    command.layer(), command.pass(), command.textureId(), command.priority(),
                    command.depthBias(), command.objectId(), command.firstIndex(),
                    command.renderMode(), command.wallDecorationPresentation())) {
                commands.set(last, previous.extend(command.indexCount()));
                return;
            }
        }
        commands.add(command);
    }

    private record TileFragment(SceneTileSnapshot source, GpuUploadPlan plan) {
        private TileFragment {
            source = Objects.requireNonNull(source, "source");
            plan = Objects.requireNonNull(plan, "plan");
        }
    }

    public record BuildResult(GpuUploadPlan plan, int rebuiltTiles, int reusedTiles) {
        public BuildResult {
            plan = Objects.requireNonNull(plan, "plan");
            if (rebuiltTiles < 0 || reusedTiles < 0) {
                throw new IllegalArgumentException("Tile counts cannot be negative");
            }
        }
    }
}
