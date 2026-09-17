package com.jagex;

import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.displee.cache.index.archive.Archive;
import com.displee.cache.index.archive.file.File;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Objects;
import java.util.function.BiFunction;

import com.jagex.cache.graphics.Sprite;
import com.jagex.net.ResourceProvider;
import com.rspsi.cache.CacheFileType;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.LegacyDefinitionProvider;
import com.rspsi.cache.store.CacheStore;
import com.rspsi.cache.store.CacheIndexView;
import com.rspsi.cache.store.LegacyDispleeCacheStore;
import com.rspsi.core.misc.FixedIntegerKeyMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Slf4j
public class Cache {


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


    @Getter
    private CacheLibrary indexedFileSystem;

    /**
     * Byte-oriented cache boundary for code that must not depend on Displee.
     * The legacy adapter remains the active backend until an OSRS backend has
     * passed the same compatibility tests.
     */
    @Getter
    private CacheStore store;

    @Getter
    private final DefinitionProvider definitions = new LegacyDefinitionProvider();

    private Index modelArchive, mapArchive, configArchive, skeletonArchive, skinArchive, spriteIndex, textureIndex, spotAnimIndex, varbitIndex, locIndex;

    private boolean isCacheNewOSRS(CacheLibrary library) {
        Index idx = library.index(2);
        int indexCount = ArrayUtils.indexOf(library.indices(), null) - 1;
        return idx.getRevision() >= 300 && indexCount <= 24;
    }

    public Cache(Path path) {
        log.info("Loading cache at {}", path);
        indexedFileSystem = new CacheLibrary(path.toFile().toString(), false, null);
        store = new LegacyDispleeCacheStore(indexedFileSystem);
        if (indexedFileSystem.is317()) {
            modelArchive = indexedFileSystem.index(1);
            mapArchive = indexedFileSystem.index(4);
            configArchive = indexedFileSystem.index(0);
            skinArchive = indexedFileSystem.index(2);
            skeletonArchive = null;//317 loads inside skins
            log.info("Loaded cache in 317 format!");
        } else if (isCacheNewOSRS(indexedFileSystem)) {
            modelArchive = indexedFileSystem.index(7);
            mapArchive = indexedFileSystem.index(5);
            configArchive = indexedFileSystem.index(2);
            skeletonArchive = indexedFileSystem.index(0);
            skinArchive = indexedFileSystem.index(1);
            spriteIndex = indexedFileSystem.index(8);
            textureIndex = indexedFileSystem.index(9);
            log.info("Loaded cache in OSRS format!");
        } else if (indexedFileSystem.isRS3()) {
            modelArchive = indexedFileSystem.index(7);
            mapArchive = indexedFileSystem.index(5);
            configArchive = indexedFileSystem.index(2);
            skeletonArchive = indexedFileSystem.index(0);
            skinArchive = indexedFileSystem.index(1);
            spriteIndex = indexedFileSystem.index(8);
            textureIndex = indexedFileSystem.index(9);
            spotAnimIndex = indexedFileSystem.index(21);
            varbitIndex = indexedFileSystem.index(22);
            locIndex = indexedFileSystem.index(16);
            log.info("Loaded cache in RS3 format!");
        } else if (indexedFileSystem.isRS3()) {
            throw new UnsupportedOperationException("Cache format not supported!");
        }
        resourceProvider = new ResourceProvider(this);
        Thread t = new Thread(resourceProvider);
        t.start();
    }

    public ResourceProvider resourceProvider;

    public boolean is317() {
        return indexedFileSystem.is317();
    }

    public boolean isOsrs() {
        return isCacheNewOSRS(indexedFileSystem);
    }

    public boolean isRs3() {
        return indexedFileSystem.isRS3();
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
            case CONFIG -> is317() ? 0 : 2;
            case MODEL -> is317() ? 1 : 7;
            case ANIMATION -> is317() ? 2 : 1;
            case MAP -> is317() ? 4 : 5;
            case SKELETON -> is317() ? -1 : 0;
            case SPRITE -> is317() ? -1 : 8;
            case TEXTURE -> is317() ? -1 : 9;
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
        if (!isCacheNewOSRS(indexedFileSystem))
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

    public final Archive createArchive(int file, String name) {
        return configArchive.archive(file);
    }

    public void close() throws IOException {
        store.close();
    }

    public ResourceProvider getProvider() {
        return resourceProvider;
    }


}
