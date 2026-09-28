package com.rspsi.cache.map;

import com.openrune.studio.core.osrs.map.ModernOsrsRegionCodec;
import com.openrune.studio.core.osrs.map.OsrsRegionData;
import com.rspsi.editor.model.WorldDocument;

public final class CoreRegionTestFixtures {
    private CoreRegionTestFixtures() {
    }

    public static byte[] encodeTerrain(WorldDocument document, int regionX, int regionY) {
        OsrsRegionData core = CoreOsrsRegionAdapter.toCore(document, regionX, regionY);
        return ModernOsrsRegionCodec.INSTANCE.encodeTerrain(core);
    }

    public static byte[] encodeLocations(WorldDocument document, int regionX, int regionY) {
        OsrsRegionData core = CoreOsrsRegionAdapter.toCore(document, regionX, regionY);
        return ModernOsrsRegionCodec.INSTANCE.encodeLocations(core);
    }

    public static WorldDocument decode(byte[] terrain, byte[] locations, int regionX, int regionY) {
        return CoreOsrsRegionAdapter.toClient(
                ModernOsrsRegionCodec.INSTANCE.decode(terrain, locations, regionX, regionY)
        ).document();
    }
}
