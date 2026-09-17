package com.jagex;

import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.displee.cache.index.archive.file.File;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Objects;
import java.util.function.BiFunction;

import com.jagex.cache.graphics.Sprite;
import com.jagex.net.ResourceProvider;
import com.rspsi.cache.CacheFileType;
import com.rspsi.cache.OsrsCacheIndexLayout;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.LegacyDefinitionProvider;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.cache.store.CacheIndexView;
import com.rspsi.cache.store.CacheStoreFactory;
import com.rspsi.cache.store.LegacyDispleeCacheStore;
import com.rspsi.core.misc.FixedIntegerKeyMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class Cache {

    private enum CacheFormat {
        LEGACY_317,
        OSRS,
        RS3
    }


    @Setter
    /**
     * Set this to override how all cache files except maps are loaded
     * args = [fileType, fileId]
     * return byte[] or Optional.empty() to continue with normal loading
     */
    private BiFunction<CacheFileType, Integer, Optional<byte[]>> fileRetrieverOverride;

    @Setter
    /**
     * Set this to override how the map files are loaded
     * args = [fileId, regionId]
     * return byte[] or Optional.empty() to continue with normal loading
     */
    private BiFunction<Integer, Integer, Optional<byte[]>> mapRetrieverOverride;


    private CacheLibrary indexedFileSystem;

    /**
     * Legacy-only escape hatch for the old renderer/cache loaders.
     *
     * <p>New editor code must use {@link #getStore()} and neutral cache
     * views. This method remains source-compatible while the compatibility
     * client is being retired.</p>
     */
    @Deprecated
    public final CacheLibrary getIndexedFileSystem() {
        return indexedFileSystem;
    }

    /**
     * Byte-oriented cache boundary for code that must not depend on Displee.
     * The legacy adapter remains the active backend until an OSRS backend has
     * passed the same compatibility tests.
     */
    @Getter
    private CacheStore store;

    /**
     * The detected format controls both archive mapping and backend choice.
     * Modern OSRS must never be treated as a legacy 317 cache merely because
     * this compatibility facade is still used by the old client renderer.
     */
    private CacheFormat format;

    @Getter
    private final DefinitionProvider definitions = new LegacyDefinitionProvider();

    private Index modelArchive, mapArchive, configArchive, skeletonArchive, skinArchive, spriteIndex, textureIndex, spotAnimIndex, varbitIndex, locIndex;

    private boolean isCacheNewOSRS(CacheLibrary library) {
        Index idx = library.index(OsrsCacheIndexLayout.CONFIGS);
        if (idx == null) return false;
        Index[] indices = library.indices();
        int firstMissing = ArrayUtils.indexOf(indices, null);
        // Preserve the historical trailing-slot convention used by the
        // Displee detector while making the no-null case deterministic.
        int indexCount = (firstMissing < 0 ? indices.length : firstMissing) - 1;
        return idx.getRevision() >= 300 && indexCount <= 24;
    }

    public Cache(Path path) {
        log.info("Loading cache at {}", path);
        indexedFileSystem = new CacheLibrary(path.toFile().toString(), false, null);
        if (indexedFileSystem.is317()) {
            format = CacheFormat.LEGACY_317;
            store = new LegacyDispleeCacheStore(indexedFileSystem);
            modelArchive = indexedFileSystem.index(1);
            mapArchive = indexedFileSystem.index(4);
            configArchive = indexedFileSystem.index(0);
            skinArchive = indexedFileSystem.index(2);
            skeletonArchive = null;//317 loads inside skins
            log.info("Loaded cache in 317 format!");
        } else if (isCacheNewOSRS(indexedFileSystem)) {
            format = CacheFormat.OSRS;
            // The compatibility facade remains in place for the old client,
            // but all modern OSRS reads go through OpenRune FileStore.
            store = CacheStoreFactory.openOsrs(path);
            modelArchive = indexedFileSystem.index(OsrsCacheIndexLayout.MODELS);
            mapArchive = indexedFileSystem.index(OsrsCacheIndexLayout.MAPS);
            configArchive = indexedFileSystem.index(OsrsCacheIndexLayout.CONFIGS);
            // DAT2 index 0 contains animation frame groups; index 1
            // contains skeleton/frame-base groups.
            skinArchive = indexedFileSystem.index(OsrsCacheIndexLayout.ANIMATIONS);
            skeletonArchive = indexedFileSystem.index(OsrsCacheIndexLayout.SKELETONS);
            spriteIndex = indexedFileSystem.index(OsrsCacheIndexLayout.SPRITES);
            textureIndex = indexedFileSystem.index(OsrsCacheIndexLayout.TEXTURES);
            log.info("Loaded cache in OSRS format through OpenRune FileStore!");
        } else if (indexedFileSystem.isRS3()) {
            format = CacheFormat.RS3;
            store = new LegacyDispleeCacheStore(indexedFileSystem);
            modelArchive = indexedFileSystem.index(OsrsCacheIndexLayout.MODELS);
            mapArchive = indexedFileSystem.index(OsrsCacheIndexLayout.MAPS);
            configArchive = indexedFileSystem.index(OsrsCacheIndexLayout.CONFIGS);
            skinArchive = indexedFileSystem.index(OsrsCacheIndexLayout.ANIMATIONS);
            skeletonArchive = indexedFileSystem.index(OsrsCacheIndexLayout.SKELETONS);
            spriteIndex = indexedFileSystem.index(OsrsCacheIndexLayout.SPRITES);
            textureIndex = indexedFileSystem.index(OsrsCacheIndexLayout.TEXTURES);
            spotAnimIndex = indexedFileSystem.index(21);
            varbitIndex = indexedFileSystem.index(22);
            locIndex = indexedFileSystem.index(16);
            log.info("Loaded cache in RS3 format!");
        } else {
            throw new UnsupportedOperationException("Cache format not supported!");
        }
        resourceProvider = new ResourceProvider(this);
        Thread t = new Thread(resourceProvider);
        t.start();
    }

    public ResourceProvider resourceProvider;

    public boolean is317() {
        return format == CacheFormat.LEGACY_317;
    }

    public boolean isOsrs() {
        return format == CacheFormat.OSRS;
    }

    public boolean isRs3() {
        return format == CacheFormat.RS3;
    }

    /** Returns the revision reported by the detected modern cache index. */
    public int revision() {
        if (is317()) return 317;
        Index revisionIndex = indexedFileSystem.index(OsrsCacheIndexLayout.CONFIGS);
        return revisionIndex == null ? -1 : revisionIndex.getRevision();
    }

    public byte[] read(int index, int archive, int file) {
        return store.read(index, archive, file);
    }

    public void write(int index, int archive, int file, byte[] data) {
        store.write(index, archive, file, data);
    }

    public void flush() {
        store.flush();
    }

    /**
     * Reads a regular cache resource through the byte-oriented boundary.
     * Legacy index objects remain available below for compatibility loaders,
     * but new consumers should not need to know their archive types.
     */
    public final byte[] readFile(CacheFileType type, int archive) {
        try {
            if (fileRetrieverOverride != null) {
                Optional<byte[]> data = fileRetrieverOverride.apply(type, archive);
                if (data.isPresent()) return data.get();
            }
            int index = cacheIndex(type);
            if (index < 0) return null;
            return store.read(index, archive, 0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Reads a named archive without exposing a Displee archive object. */
    public final byte[] readNamedFile(CacheFileType type, String archiveName, int file) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(archiveName, "archiveName");
        if (file < 0) throw new IllegalArgumentException("Cache file cannot be negative");
        try {
            int index = cacheIndex(type);
            if (index < 0) return null;
            int archive = store.archiveId(index, archiveName);
            return archive < 0 ? null : store.read(index, archive, file);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int cacheIndex(CacheFileType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case CONFIG -> is317() ? 0 : OsrsCacheIndexLayout.CONFIGS;
            case MODEL -> is317() ? 1 : OsrsCacheIndexLayout.MODELS;
            case ANIMATION -> is317() ? 2 : OsrsCacheIndexLayout.ANIMATIONS;
            case MAP -> is317() ? 4 : OsrsCacheIndexLayout.MAPS;
            case SKELETON -> is317() ? -1 : OsrsCacheIndexLayout.SKELETONS;
            case SPRITE -> is317() ? -1 : OsrsCacheIndexLayout.SPRITES;
            case TEXTURE -> is317() ? -1 : OsrsCacheIndexLayout.TEXTURES;
            case SOUND, VARBIT, LOC, SPOT -> -1;
        };
    }

    /** Returns a neutral index view for compatibility loaders. */
    public final Optional<CacheIndexView> indexView(CacheFileType type) {
        int index = cacheIndex(type);
        return index < 0 ? Optional.empty() : Optional.of(new CacheIndexView(store, index));
    }


    private FixedIntegerKeyMap<Sprite> spriteCache = new FixedIntegerKeyMap<Sprite>(100);


    public Sprite getSprite(int id) {
        if (spriteCache.contains(id))
            return spriteCache.get(id);
        if (!isOsrs())
            throw new RuntimeException("Cannot grab sprite by ID on 317!");
        byte[] data = readFile(CacheFileType.SPRITE, id);
        if (data == null) throw new IllegalArgumentException("Sprite not found: " + id);
        Sprite sprite = Sprite.decode(ByteBuffer.wrap(data));
        spriteCache.put(id, sprite);
        System.out.println("GETSPRITE " + id);
        return sprite;
    }

    /** Compatibility-only access for legacy loaders that still require indexes. */
    @Deprecated
    public final Index getFile(CacheFileType index) {
        try {
            switch (index) {
                case CONFIG:
                    return configArchive;
                case MODEL:
                    return modelArchive;
                case ANIMATION:
                    return skinArchive;
                case SKELETON:
                    return skeletonArchive;
                case SOUND:
                    break;
                case MAP:
                    return mapArchive;
                case SPRITE:
                    return spriteIndex;
                case TEXTURE:
                    return textureIndex;
                case SPOT:
                    return spotAnimIndex;
                case VARBIT:
                    return varbitIndex;
                case LOC:
                    return locIndex;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public final byte[] readMap(int groupId, int fileId, int regionId) {
        if (mapRetrieverOverride != null) {
            Optional<byte[]> data = mapRetrieverOverride.apply(groupId, regionId);
            if (data.isPresent())
                return data.get();
        }
        if (indexedFileSystem.is317())
            return store.read(4, groupId, 0);
        return store.read(5, groupId, fileId);
    }

    /**
     * Compatibility-only byte accessor for the old loader family. The read
     * itself now goes through {@link CacheStore}; callers that need a
     * cache-library index should use the explicitly deprecated index accessor
     * above and remain quarantined from the editor core.
     */
    @Deprecated
    public final byte[] getFile(CacheFileType type, int file) {
        try {
            if (fileRetrieverOverride != null) {
                Optional<byte[]> data = fileRetrieverOverride.apply(type, file);
                if (data.isPresent())
                    return data.get();
            }
            switch (type) {
                case CONFIG:
                case MODEL:
                case ANIMATION:
                case SKELETON:
                case MAP:
                    return readFile(type, file);
                case SOUND:
                    break;
                case TEXTURE:
                    break;
                case SPOT:
                    return store.read(21, file >>> 8, file & 0xff);
                case VARBIT:
                    return store.read(22, file >>> 1416501898, file & 0x3ffff);
                case LOC:
                    return store.read(16, file >>> 8, file & 0xff);
            }
        } catch (Exception ex) {
            //ex.printStackTrace();
        }
        return null;
    }


    /** Legacy write accessor retained for compatibility loaders only. */
    @Deprecated
    public final File writegetFile(CacheFileType index, String name, int file, byte[] data) {
        try {
            switch (index) {
                case CONFIG:
                   // configArchive.createIfNotExist(file);
                    return configArchive.archive(file).add(0, data);
                case MODEL:
                   // modelArchive.createIfNotExist(file);
                    return modelArchive.archive(file).add(0, data);
                case ANIMATION:
                    //skinArchive.createIfNotExist(file);
                    return skinArchive.archive(file).add(0, data);
                case SOUND:
                    break;
                case MAP:
                   // mapArchive.createIfNotExist(file);
                    return mapArchive.archive(file).add(0, data);
                case TEXTURE:
                    break;
                case SPOT:
                    return spotAnimIndex.add(file >>> 8).add(file & 0xff, data);
                case VARBIT:
                    return varbitIndex.add(file >>> 1416501898).add(file & 0x3ffff, data);
                case LOC:
                    return locIndex.add(file >> 8).add((file) & (1 << 8) - 1, data);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /** Legacy named-archive accessor retained for compatibility rendering only. */
    @Deprecated
    public final Archive createArchive(int file, String name) {
        return configArchive.archive(file);
    }

    /**
     * Loads the old named-sprite groups used by the compatibility renderer.
     *
     * <p>This is deliberately kept on the legacy cache facade. New OSRS
     * editor code should use the neutral sprite definition provider instead of
     * constructing Displee archives or legacy {@link Sprite} instances.</p>
     *
     * @param archiveName legacy group name, such as {@code mapscene}
     * @param maxCount maximum number of sequential sprite IDs to probe
     * @param emptyOnFirstFailure whether a first decode failure produces an
     *                             empty array (the non-317 behavior)
     */
    public final Sprite[] readLegacySprites(String archiveName, int maxCount,
                                            boolean emptyOnFirstFailure) {
        Objects.requireNonNull(archiveName, "archiveName");
        if (!is317()) {
            throw new UnsupportedOperationException(
                    "Legacy named sprites are only available for 317 caches; use the neutral OSRS sprite index");
        }
        if (maxCount < 0) {
            throw new IllegalArgumentException("Maximum sprite count cannot be negative");
        }
        if (maxCount == 0) {
            return new Sprite[0];
        }
        Sprite[] sprites = new Sprite[maxCount];
        int lastIndex = emptyOnFirstFailure ? -1 : 0;
        try {
            Archive graphics = createArchive(4, "2d graphics");
            for (int id = 0; id < maxCount; id++) {
                sprites[id] = new Sprite(graphics, archiveName, id);
                lastIndex = id;
            }
        } catch (Exception ignored) {
            // Legacy loading stops at the first missing named sprite.
            if (emptyOnFirstFailure) {
                lastIndex = -1;
            }
        }
        return lastIndex == -1 ? new Sprite[0] : Arrays.copyOf(sprites, lastIndex + 1);
    }

    public void close() throws IOException {
        try {
            store.close();
        } finally {
            // Modern OSRS reads use FileStore. The Displee library is kept
            // open only for deprecated compatibility accessors until those
            // call sites are retired.
            if (!is317()) {
                indexedFileSystem.close();
            }
        }
    }

    public ResourceProvider getProvider() {
        return resourceProvider;
    }


}
