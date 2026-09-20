package com.rspsi.editor.corpus;

import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in OSRS map-square fingerprint: floor grammar, shaped tiles, texture
 * usage, height/step distributions, tile flags, object mix and collision.
 */
public final class OsrsRegionFeatureExtractor implements RegionFeatureExtractor {
    public static final String ID = "osrs.map-square";

    @Override public String id() { return ID; }

    @Override
    public List<FeatureBlock> extract(RegionFeatureContext context) {
        WorldDocument world = context.region().document();
        Map<String, Double> underlays = new LinkedHashMap<>();
        Map<String, Double> overlays = new LinkedHashMap<>();
        Map<String, Double> shapeRotation = new LinkedHashMap<>();
        Map<String, Double> transitions = new LinkedHashMap<>();
        Map<String, Double> textures = new LinkedHashMap<>();
        Map<String, Double> heights = new LinkedHashMap<>();
        Map<String, Double> flags = new LinkedHashMap<>();
        Map<String, Double> objectIds = new LinkedHashMap<>();
        Map<String, Double> objectKinds = new LinkedHashMap<>();
        Map<String, Double> collision = new LinkedHashMap<>();

        for (int plane = 0; plane < world.planes(); plane++) {
            for (int x = 0; x < world.width(); x++) {
                for (int y = 0; y < world.length(); y++) {
                    TileSnapshot tile = world.tile(plane, x, y).snapshot();
                    String planeKey = "p" + plane + ":";
                    if (tile.underlayId() > 0) add(underlays, planeKey + tile.underlayId(), 1.0);
                    if (tile.overlayId() > 0) {
                        add(overlays, planeKey + tile.overlayId(), 1.0);
                        add(shapeRotation, planeKey + tile.overlayShape() + "x"
                                + tile.overlayRotation(), 1.0);
                        context.assets().overlay(tile.overlayId() - 1).ifPresent(floor -> {
                            if (floor.texture() >= 0) {
                                add(textures, planeKey + floor.texture(), 1.0);
                            }
                        });
                    }

                    add(heights, planeKey + "height." + bin(tile.southWestHeight(), 64), 1.0);
                    addGradientFeatures(heights, planeKey, tile);
                    if (tile.flags() != 0) add(flags, planeKey + "raw." + tile.flags(), 1.0);
                    if ((tile.flags() & 0x2) != 0) add(flags, planeKey + "bridge", 1.0);
                    if ((tile.flags() & 0x4) != 0) add(flags, planeKey + "roof", 1.0);

                    if (x + 1 < world.width()) {
                        addTransition(transitions, planeKey, tile,
                                world.tile(plane, x + 1, y).snapshot());
                    }
                    if (y + 1 < world.length()) {
                        addTransition(transitions, planeKey, tile,
                                world.tile(plane, x, y + 1).snapshot());
                    }

                    for (WorldObject object : tile.objects()) {
                        add(objectIds, planeKey + object.id(), 1.0);
                        add(objectKinds, planeKey + kind(object.type()), 1.0);
                        ObjectCollisionView objectCollision =
                                context.assets().objectCollision(object.id()).orElse(null);
                        if (objectCollision != null) {
                            add(collision, planeKey + "objects", 1.0);
                            if (objectCollision.blockWalk()) add(collision, planeKey + "walk", 1.0);
                            if (objectCollision.blockProjectile()) {
                                add(collision, planeKey + "projectile", 1.0);
                            }
                            if (objectCollision.breakRouteFinding()) {
                                add(collision, planeKey + "route-break", 1.0);
                            }
                        }
                    }
                }
            }
        }

        List<FeatureBlock> result = new ArrayList<>();
        result.add(new FeatureBlock("terrain.underlays", underlays));
        result.add(new FeatureBlock("terrain.overlays", overlays));
        result.add(new FeatureBlock("terrain.shape-rotation", shapeRotation));
        result.add(new FeatureBlock("terrain.floor-transitions", transitions));
        result.add(new FeatureBlock("terrain.textures", textures));
        result.add(new FeatureBlock("terrain.height", heights));
        result.add(new FeatureBlock("terrain.flags", flags));
        result.add(new FeatureBlock("objects.ids", objectIds));
        result.add(new FeatureBlock("objects.kinds", objectKinds));
        result.add(new FeatureBlock("objects.collision", collision));
        return List.copyOf(result);
    }

    private static void addGradientFeatures(Map<String, Double> features, String prefix,
                                            TileSnapshot tile) {
        int[] deltas = {
                Math.abs(tile.southEastHeight() - tile.southWestHeight()),
                Math.abs(tile.northWestHeight() - tile.southWestHeight()),
                Math.abs(tile.northEastHeight() - tile.southEastHeight()),
                Math.abs(tile.northEastHeight() - tile.northWestHeight())
        };
        for (int delta : deltas) {
            add(features, prefix + "gradient." + gradientBin(delta), 1.0);
            if (delta >= 8) add(features, prefix + "step>=8", 1.0);
            if (delta >= 16) add(features, prefix + "step>=16", 1.0);
            if (delta >= 24) add(features, prefix + "step>=24", 1.0);
        }
    }

    private static String gradientBin(int value) {
        if (value < 8) return "0-7";
        if (value < 16) return "8-15";
        if (value < 24) return "16-23";
        if (value < 48) return "24-47";
        return "48+";
    }

    private static int bin(int value, int size) {
        return Math.floorDiv(value, size);
    }

    private static void addTransition(Map<String, Double> transitions, String prefix,
                                      TileSnapshot first, TileSnapshot second) {
        String a = floor(first);
        String b = floor(second);
        if (a.equals(b)) return;
        String key = a.compareTo(b) <= 0 ? a + "<>" + b : b + "<>" + a;
        add(transitions, prefix + key, 1.0);
    }

    private static String floor(TileSnapshot tile) {
        if (tile.overlayId() > 0) {
            return "o" + tile.overlayId() + ":u" + tile.underlayId();
        }
        return "u" + tile.underlayId();
    }

    private static String kind(int type) {
        if (type >= 0 && type <= 3) return "wall";
        if (type >= 4 && type <= 8) return "wall-decoration";
        if (type == 22) return "ground-decoration";
        return "game-object";
    }

    private static void add(Map<String, Double> values, String key, double amount) {
        values.merge(key, amount, Double::sum);
    }
}
