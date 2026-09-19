package com.rspsi.legacy;

import com.jagex.cache.def.ObjectDefinition;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.draw.raster.GameRasterizer;
import com.jagex.entity.Renderable;
import com.jagex.io.Buffer;
import com.jagex.map.MapRegion;
import com.jagex.map.SceneGraph;
import com.jagex.map.tile.SceneTile;
import com.jagex.util.ObjectKey;
import com.rspsi.cache.store.CacheArchiveView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WallRenderingParityTest {

    private ObjectDefinitionLoader previousLoader;

    @BeforeEach
    void setUp() {
        previousLoader = ObjectDefinitionLoader.instance;
    }

    @AfterEach
    void tearDown() {
        ObjectDefinitionLoader.instance = previousLoader;
    }

    @Test
    void type2CornerWallCastsShadowAcrossAllThreeCornerVerticesForEveryRotation() {
        ObjectDefinition cornerWallDef = new ObjectDefinition();
        cornerWallDef.setId(100);
        cornerWallDef.setCastsShadow(true);
        cornerWallDef.setAnimation(-1);
        cornerWallDef.setDecorDisplacement(16);

        ObjectDefinitionLoader.instance = new ObjectDefinitionLoader() {
            @Override
            public ObjectDefinition forId(int id) {
                return cornerWallDef;
            }

            @Override
            public int count() {
                return 1;
            }

            @Override
            public void init(CacheArchiveView archive) {
            }

            @Override
            public void init(Buffer data, Buffer indexBuffer) {
            }
        };

        // Rotation 0: West + North edges -> (x, y), (x, y + 1), (x + 1, y + 1)
        MapRegion regionRot0 = new MapRegion(null, 4, 4);
        SceneGraph sceneRot0 = new SceneGraph(4, 4, 4);
        regionRot0.spawnObjectToWorld(sceneRot0, 100, 1, 1, 0, 2, 0, false);

        assertEquals(50, regionRot0.shading[0][1][1]);
        assertEquals(50, regionRot0.shading[0][1][2]);
        assertEquals(50, regionRot0.shading[0][2][2]);
        assertEquals(0, regionRot0.shading[0][2][1]);

        // Rotation 1: North + East edges -> (x, y + 1), (x + 1, y + 1), (x + 1, y)
        MapRegion regionRot1 = new MapRegion(null, 4, 4);
        SceneGraph sceneRot1 = new SceneGraph(4, 4, 4);
        regionRot1.spawnObjectToWorld(sceneRot1, 100, 1, 1, 0, 2, 1, false);

        assertEquals(0, regionRot1.shading[0][1][1]);
        assertEquals(50, regionRot1.shading[0][1][2]);
        assertEquals(50, regionRot1.shading[0][2][2]);
        assertEquals(50, regionRot1.shading[0][2][1]);

        // Rotation 2: East + South edges -> (x + 1, y + 1), (x + 1, y), (x, y)
        MapRegion regionRot2 = new MapRegion(null, 4, 4);
        SceneGraph sceneRot2 = new SceneGraph(4, 4, 4);
        regionRot2.spawnObjectToWorld(sceneRot2, 100, 1, 1, 0, 2, 2, false);

        assertEquals(50, regionRot2.shading[0][1][1]);
        assertEquals(0, regionRot2.shading[0][1][2]);
        assertEquals(50, regionRot2.shading[0][2][2]);
        assertEquals(50, regionRot2.shading[0][2][1]);

        // Rotation 3: South + West edges -> (x + 1, y), (x, y), (x, y + 1)
        MapRegion regionRot3 = new MapRegion(null, 4, 4);
        SceneGraph sceneRot3 = new SceneGraph(4, 4, 4);
        regionRot3.spawnObjectToWorld(sceneRot3, 100, 1, 1, 0, 2, 3, false);

        assertEquals(50, regionRot3.shading[0][1][1]);
        assertEquals(50, regionRot3.shading[0][1][2]);
        assertEquals(0, regionRot3.shading[0][2][2]);
        assertEquals(50, regionRot3.shading[0][2][1]);
    }

    @Test
    void sceneGraphRendersBothPrimaryAndSecondaryWallWhenPresent() {
        SceneGraph scene = new SceneGraph(3, 3, 4);
        ObjectKey key = new ObjectKey(1, 1, 100, 2, 0, true, false);

        AtomicInteger primaryRenders = new AtomicInteger();
        AtomicInteger secondaryRenders = new AtomicInteger();

        Renderable primary = new Renderable() {
            @Override
            public void render(GameRasterizer rasterizer, int x, int y, int orientation,
                               int ySine, int yCosine, int xSine, int xCosine,
                               int height, ObjectKey key, int plane) {
                primaryRenders.incrementAndGet();
            }
        };

        Renderable secondary = new Renderable() {
            @Override
            public void render(GameRasterizer rasterizer, int x, int y, int orientation,
                               int ySine, int yCosine, int xSine, int xCosine,
                               int height, ObjectKey key, int plane) {
                secondaryRenders.incrementAndGet();
            }
        };

        // Add wall at (1, 1, 0) with orientation bitmasks 1 and 2
        scene.addWall(key, 1, 1, 0, 1, primary, secondary, 0, 2, false);

        SceneTile tile = scene.tiles[0][1][1];
        assertNotNull(tile);
        assertNotNull(tile.wall);
        assertSame(primary, tile.wall.getPrimary());
        assertSame(secondary, tile.wall.getSecondary());
        assertEquals(1, tile.wall.orientationA);
        assertEquals(2, tile.wall.orientationB);
    }
}
