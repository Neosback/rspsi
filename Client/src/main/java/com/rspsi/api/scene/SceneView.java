package com.rspsi.api.scene;

import com.rspsi.api.GameObject;
import com.rspsi.api.Scene;
import com.rspsi.api.Tile;
import com.rspsi.api.TileObject;
import com.rspsi.api.WorldView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuScenePacket;
import com.rspsi.editor.render.SceneTileSnapshot;
import com.rspsi.editor.render.SceneWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable {@link WorldView}/{@link Scene} over one resolved
 * {@link GpuScenePacket} plus its authored tiles.
 *
 * <p>Tiles are placed at their scene plane. When a bridge column shifts a tile
 * down, the tile authored on that scene plane is demoted to
 * {@link Tile#getBridge()} of the shifted tile, as the client's
 * {@code Scene.setLinkBelow} does.</p>
 */
public final class SceneView implements WorldView, Scene {
    static final int PLANES = 4;

    private final SceneWindow window;
    private final int baseX;
    private final int baseY;
    private final int sizeX;
    private final int sizeY;
    private final ResolvedTile[][][] tiles;
    private final int[][][] heights;
    private final byte[][][] settings;
    private final short[][][] underlays;
    private final short[][][] overlays;
    private final byte[][][] shapes;
    private final AuthoredTileSource authored;
    private final DefinitionProvider definitions;

    private SceneView(GpuScenePacket packet, AuthoredTileSource authored,
                          DefinitionProvider definitions) {
        this.authored = authored;
        this.definitions = definitions;
        window = packet.window();
        baseX = window.sceneBaseX();
        baseY = window.sceneBaseY();
        int maxX = -1;
        int maxY = -1;
        for (SceneTileSnapshot tile : packet.tiles()) {
            maxX = Math.max(maxX, tile.worldAddress().worldX() - baseX);
            maxY = Math.max(maxY, tile.worldAddress().worldY() - baseY);
        }
        sizeX = maxX + 1;
        sizeY = maxY + 1;
        tiles = new ResolvedTile[PLANES][Math.max(0, sizeX)][Math.max(0, sizeY)];
        heights = new int[PLANES][sizeX + 1][sizeY + 1];
        settings = new byte[PLANES][Math.max(0, sizeX)][Math.max(0, sizeY)];
        underlays = new short[PLANES][Math.max(0, sizeX)][Math.max(0, sizeY)];
        overlays = new short[PLANES][Math.max(0, sizeX)][Math.max(0, sizeY)];
        shapes = new byte[PLANES][Math.max(0, sizeX)][Math.max(0, sizeY)];

        Map<Long, List<SceneTileSnapshot>> slots = new HashMap<>();
        for (SceneTileSnapshot snapshot : packet.tiles()) {
            WorldTileAddress address = snapshot.worldAddress();
            int sx = address.worldX() - baseX;
            int sy = address.worldY() - baseY;
            int authoredPlane = address.plane();
            if (sx < 0 || sy < 0 || authoredPlane >= PLANES) continue;
            settings[authoredPlane][sx][sy] = (byte) snapshot.tileFlags();
            Optional<TileSnapshot> source = authored.tile(address);
            source.ifPresent(tile -> recordAuthored(authoredPlane, sx, sy, tile));
            int scenePlane = snapshot.effectivePlane();
            if (scenePlane < 0 || scenePlane >= PLANES) continue;
            slots.computeIfAbsent(slotKey(scenePlane, sx, sy), key -> new ArrayList<>(2)).add(snapshot);
        }

        for (List<SceneTileSnapshot> occupants : slots.values()) {
            // A tile shifted into this slot by a bridge link owns it; the tile
            // authored on this plane becomes its bridge (Scene.setLinkBelow).
            SceneTileSnapshot shifted = null;
            SceneTileSnapshot resident = null;
            for (SceneTileSnapshot occupant : occupants) {
                if (occupant.effectivePlane() < occupant.authoredPlane()) {
                    shifted = occupant;
                } else {
                    resident = occupant;
                }
            }
            SceneTileSnapshot primary = shifted != null ? shifted : resident;
            ResolvedTile bridge = shifted != null && resident != null
                    ? new ResolvedTile(this, resident, null) : null;
            ResolvedTile tile = new ResolvedTile(this, primary, bridge);
            tiles[tile.getPlane()][tile.sceneX()][tile.sceneY()] = tile;
        }

        // RuneLite lists a multi-tile game object on every tile it covers.
        for (ResolvedTile[][] plane : tiles) {
            for (ResolvedTile[] column : plane) {
                for (ResolvedTile tile : column) {
                    if (tile == null) continue;
                    for (GameObject object : tile.anchoredGameObjects()) {
                        spread(tile.getPlane(), object);
                    }
                }
            }
        }
    }

    /**
     * Scene over a resolved packet. Authored heights, floors and object
     * placements come from {@code authored}; {@code definitions} supplies
     * placed-definition footprints for objects that submitted no geometry.
     */
    public static SceneView of(GpuScenePacket packet, AuthoredTileSource authored,
                                   DefinitionProvider definitions) {
        return new SceneView(Objects.requireNonNull(packet, "packet"),
                Objects.requireNonNull(authored, "authored"),
                Objects.requireNonNull(definitions, "definitions"));
    }

    Optional<TileSnapshot> authoredTile(WorldTileAddress address) {
        return authored.tile(address);
    }

    DefinitionProvider definitions() {
        return definitions;
    }

    private void recordAuthored(int plane, int sx, int sy, TileSnapshot tile) {
        heights[plane][sx][sy] = tile.southWestHeight();
        heights[plane][sx + 1][sy] = tile.southEastHeight();
        heights[plane][sx + 1][sy + 1] = tile.northEastHeight();
        heights[plane][sx][sy + 1] = tile.northWestHeight();
        // TileSnapshot keeps the map-file encoding (id + 1, 0 = none), which is
        // also RuneLite's Scene.getUnderlayIds/getOverlayIds encoding.
        underlays[plane][sx][sy] = (short) tile.underlayId();
        overlays[plane][sx][sy] = (short) tile.overlayId();
        shapes[plane][sx][sy] = (byte) tile.overlayShape();
    }

    private void spread(int plane, GameObject object) {
        int minX = object.getSceneMinLocation().x();
        int minY = object.getSceneMinLocation().y();
        int maxX = object.getSceneMaxLocation().x();
        int maxY = object.getSceneMaxLocation().y();
        for (int x = Math.max(0, minX); x <= Math.min(sizeX - 1, maxX); x++) {
            for (int y = Math.max(0, minY); y <= Math.min(sizeY - 1, maxY); y++) {
                ResolvedTile covered = tiles[plane][x][y];
                if (covered != null && (x != minX || y != minY)) {
                    covered.addCoveringGameObject(object);
                }
            }
        }
    }

    private static long slotKey(int plane, int x, int y) {
        return ((long) plane << 40) | ((long) x << 20) | y;
    }

    int[] heightsAt(int level, int sx, int sy) {
        return new int[]{heights[level][sx][sy], heights[level][sx + 1][sy],
                heights[level][sx + 1][sy + 1], heights[level][sx][sy + 1]};
    }

    /** Every object in the scene, each once, in tile order. */
    public List<TileObject> objects() {
        List<TileObject> all = new ArrayList<>();
        for (ResolvedTile[][] plane : tiles) {
            for (ResolvedTile[] column : plane) {
                for (ResolvedTile tile : column) {
                    if (tile == null) continue;
                    tile.collectAnchored(all);
                    if (tile.getBridge() instanceof ResolvedTile bridge) bridge.collectAnchored(all);
                }
            }
        }
        return all;
    }

    @Override public Scene getScene() { return this; }
    @Override public int getBaseX() { return baseX; }
    @Override public int getBaseY() { return baseY; }
    @Override public int getSizeX() { return sizeX; }
    @Override public int getSizeY() { return sizeY; }
    @Override public int getId() { return window.worldViewId(); }
    @Override public boolean isInstance() { return window.instance(); }
    @Override public int getMinLevel() { return window.minimumRenderLevel(); }

    @Override
    public int[] getMapRegions() {
        return window.sourceRegionIds().stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    @Override
    public Tile[][][] getTiles() {
        Tile[][][] copy = new Tile[PLANES][][];
        for (int plane = 0; plane < PLANES; plane++) {
            copy[plane] = new Tile[tiles[plane].length][];
            for (int x = 0; x < tiles[plane].length; x++) {
                copy[plane][x] = Arrays.copyOf(tiles[plane][x], tiles[plane][x].length, Tile[].class);
            }
        }
        return copy;
    }

    @Override
    public Tile getTile(int plane, int sceneX, int sceneY) {
        if (plane < 0 || plane >= PLANES || sceneX < 0 || sceneY < 0
                || sceneX >= sizeX || sceneY >= sizeY) {
            return null;
        }
        return tiles[plane][sceneX][sceneY];
    }

    @Override public int[][][] getTileHeights() { return deepCopy(heights); }
    @Override public byte[][][] getTileSettings() { return deepCopy(settings); }
    @Override public short[][][] getUnderlayIds() { return deepCopy(underlays); }
    @Override public short[][][] getOverlayIds() { return deepCopy(overlays); }
    @Override public byte[][][] getTileShapes() { return deepCopy(shapes); }

    @Override
    public int getTileHeight(int level, int vertexX, int vertexY) {
        return heights[level][vertexX][vertexY];
    }

    @Override
    public int getTileSetting(int level, int sceneX, int sceneY) {
        return settings[level][sceneX][sceneY];
    }

    private static int[][][] deepCopy(int[][][] source) {
        int[][][] copy = new int[source.length][][];
        for (int p = 0; p < source.length; p++) {
            copy[p] = new int[source[p].length][];
            for (int x = 0; x < source[p].length; x++) copy[p][x] = source[p][x].clone();
        }
        return copy;
    }

    private static byte[][][] deepCopy(byte[][][] source) {
        byte[][][] copy = new byte[source.length][][];
        for (int p = 0; p < source.length; p++) {
            copy[p] = new byte[source[p].length][];
            for (int x = 0; x < source[p].length; x++) copy[p][x] = source[p][x].clone();
        }
        return copy;
    }

    private static short[][][] deepCopy(short[][][] source) {
        short[][][] copy = new short[source.length][][];
        for (int p = 0; p < source.length; p++) {
            copy[p] = new short[source[p].length][];
            for (int x = 0; x < source[p].length; x++) copy[p][x] = source[p][x].clone();
        }
        return copy;
    }
}
