package com.rspsi.compat.osrs;

import com.jagex.Client;
import com.jagex.cache.loader.anim.AnimationDefinitionLoader;
import com.jagex.cache.loader.anim.FrameBaseLoader;
import com.jagex.cache.loader.anim.FrameLoader;
import com.jagex.cache.loader.anim.GraphicLoader;
import com.jagex.cache.loader.config.RSAreaLoader;
import com.jagex.cache.loader.config.VariableBitLoader;
import com.jagex.cache.loader.floor.FloorDefinitionLoader;
import com.jagex.cache.loader.map.MapIndexLoader;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.cache.loader.textures.TextureLoader;
import com.rspsi.cache.CacheFileType;
import com.rspsi.cache.store.CacheIndexView;

import lombok.extern.slf4j.Slf4j;

/**
 * Installs the OSRS compatibility loaders consumed by the legacy software
 * renderer.
 *
 * <p>This is the renderer bridge, not a feature plugin: it is compiled into
 * the {@code Client} compatibility boundary and installed directly during
 * startup. It exists so the old renderer keeps working while it is migrated
 * to neutral definitions; it must not gain new editor features, and new editor
 * code must use {@code CacheStore}, {@code DefinitionProvider}, and neutral
 * assets instead of these static loader singletons.</p>
 */
@Slf4j
public final class OsrsCompatibilityLoaders {

    private static OsrsAnimationFrameLoader frameLoader;
    private static OsrsFloorDefinitionLoader floorLoader;
    private static OsrsObjectDefinitionLoader objectLoader;
    private static OsrsAnimationDefinitionLoader animationLoader;
    private static OsrsGraphicLoader graphicLoader;
    private static OsrsVarbitLoader varbitLoader;
    private static OsrsMapIndexLoader mapIndexLoader;
    private static OsrsTextureLoader textureLoader;
    private static OsrsFrameBaseLoader skeletonLoader;
    private static OsrsAreaLoader areaLoader;

    private OsrsCompatibilityLoaders() {
    }

    /** Installs loader instances into the renderer's static singletons. */
    public static void install() {
        objectLoader = new OsrsObjectDefinitionLoader();
        floorLoader = new OsrsFloorDefinitionLoader();
        frameLoader = new OsrsAnimationFrameLoader();
        animationLoader = new OsrsAnimationDefinitionLoader();
        mapIndexLoader = new OsrsMapIndexLoader();
        textureLoader = new OsrsTextureLoader();
        skeletonLoader = new OsrsFrameBaseLoader();
        graphicLoader = new OsrsGraphicLoader();
        varbitLoader = new OsrsVarbitLoader();
        areaLoader = new OsrsAreaLoader();

        MapIndexLoader.instance = mapIndexLoader;
        GraphicLoader.instance = graphicLoader;
        VariableBitLoader.instance = varbitLoader;
        FrameLoader.instance = frameLoader;
        ObjectDefinitionLoader.instance = objectLoader;
        FloorDefinitionLoader.instance = floorLoader;
        FrameBaseLoader.instance = skeletonLoader;
        TextureLoader.instance = textureLoader;
        AnimationDefinitionLoader.instance = animationLoader;
        RSAreaLoader.instance = areaLoader;
        log.info("OSRS compatibility loaders installed for the legacy renderer");
    }

    /**
     * Initializes the loaders from the selected cache through the neutral
     * index views. Called by the client during startup after the cache has
     * been detected as modern OSRS.
     */
    public static void onGameLoaded(Client client) {
        frameLoader.init(2500);

        CacheIndexView configIndex = client.getCache().indexView(CacheFileType.CONFIG)
                .orElseThrow(() -> new IllegalStateException("Configuration index is unavailable"));

        floorLoader.initUnderlays(configIndex.archive(1));
        floorLoader.initOverlays(configIndex.archive(4));

        objectLoader.init(configIndex.archive(6));
        animationLoader.init(configIndex.archive(12));
        graphicLoader.init(configIndex.archive(13), client.getCache().revision());
        varbitLoader.init(configIndex.archive(14));
        areaLoader.init(configIndex.archive(35));

        objectLoader.renameMapFunctions(areaLoader);

        CacheIndexView skeletonIndex = client.getCache().indexView(CacheFileType.SKELETON)
                .orElseThrow(() -> new IllegalStateException("Skeleton index is unavailable"));
        skeletonLoader.init(skeletonIndex);

        CacheIndexView textureIndex = client.getCache().indexView(CacheFileType.TEXTURE)
                .orElseThrow(() -> new IllegalStateException("Texture index is unavailable"));
        CacheIndexView spriteIndex = client.getCache().indexView(CacheFileType.SPRITE)
                .orElseThrow(() -> new IllegalStateException("Sprite index is unavailable"));
        textureLoader.init(textureIndex.archive(0), spriteIndex);

        if (client.getCache().isOsrs()) {
            mapIndexLoader.load(client.getCache().getStore(), 5);
        }
        log.info("OSRS compatibility loaders initialized from cache revision {}", client.getCache().revision());
    }
}
