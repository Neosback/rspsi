package com.rspsi.compat.osrs;

import com.jagex.cache.loader.map.MapIndexLoader;
import com.jagex.cache.loader.map.MapType;
import com.jagex.io.Buffer;
import com.rspsi.cache.map.MapArchiveType;
import com.rspsi.cache.map.MapIndexEntry;
import com.rspsi.cache.map.MapIndexTable;
import com.rspsi.cache.store.CacheArchiveView;
import com.rspsi.cache.store.CacheStore;

import java.util.Objects;

public class OsrsMapIndexLoader extends MapIndexLoader {
	private MapIndexTable index = MapIndexTable.of(java.util.List.of());

	/** Discovers named mX_Y/lX_Y archives through the neutral cache boundary. */
	public void load(CacheStore store, int mapIndex) {
		this.index = MapIndexTable.discover(Objects.requireNonNull(store, "store"), mapIndex);
	}

	@Override
	public void init(CacheArchiveView archive) {
		if (archive == null || archive.file(0) == null) {
			index = MapIndexTable.of(java.util.List.of());
			return;
		}
		init(new Buffer(archive.file(0)));
	}

	@Override
	public void init(Buffer buffer) {
		Objects.requireNonNull(buffer, "buffer");
		byte[] payload = buffer.getPayload();
		if (payload.length < 2) {
			index = MapIndexTable.of(java.util.List.of());
			return;
		}
		int count = buffer.readUShort();
		if (payload.length < 2L + count * 6L) {
			throw new IllegalArgumentException("Truncated map index payload");
		}
		MapIndexTable decoded = MapIndexTable.of(java.util.List.of());
		for (int i = 0; i < count; i++) {
			int region = buffer.readUShort();
			int regionX = region >>> 8;
			int regionY = region & 0xFF;
			decoded.put(new MapIndexEntry(regionX, regionY, buffer.readUShort(), buffer.readUShort(),
					"m" + regionX + "_" + regionY, "l" + regionX + "_" + regionY));
		}
		index = decoded;
	}

	@Override
	public int getFileId(int hash, MapType type) {
		int regionX = hash >>> 8;
		int regionY = hash & 0xFF;
		return index.archiveId(regionX, regionY,
				type == MapType.LANDSCAPE ? MapArchiveType.LANDSCAPE : MapArchiveType.OBJECT);
	}

	@Override
	public String getGroupName(int hash, MapType type) {
		int x = hash >> 8;
		int z = hash & 0xFF;

		String prefix = type == MapType.LANDSCAPE ? "m" : "l";
		return prefix + x + "_" + z;
	}

	@Override
	public boolean landscapePresent(int id) {
		return index.present(id, MapArchiveType.LANDSCAPE);
	}

	@Override
	public boolean objectPresent(int id) {
		return index.present(id, MapArchiveType.OBJECT);
	}

	@Override
	public byte[] encode() {
		var entries = index.entries();
		Buffer buffer = new Buffer(new byte[2 + entries.size() * 6]);
		buffer.writeShort(entries.size());
		for (MapIndexEntry entry : entries) {
			if (!entry.hasLandscape() || !entry.hasObjects()
					|| entry.landscapeArchiveId() > 0xFFFF || entry.objectArchiveId() > 0xFFFF) {
				throw new IllegalStateException("Cannot export an OSRS named map index with missing or oversized archive IDs");
			}
			buffer.writeShort(entry.key());
			buffer.writeShort(entry.landscapeArchiveId());
			buffer.writeShort(entry.objectArchiveId());
		}
		return java.util.Arrays.copyOf(buffer.getPayload(), buffer.getPosition());
	}

	@Override
	public void set(int regionX, int regionY, int landscapeId, int objectsId) {
		index.put(new MapIndexEntry(regionX, regionY, landscapeId, objectsId,
				"m" + regionX + "_" + regionY, "l" + regionX + "_" + regionY));
	}

	@Override
	public String getFileName(int hash, MapType type) {
		int regionX = hash >>> 8;
		int regionY = hash & 0xFF;
		MapIndexEntry entry = index.region(regionX, regionY);
		if (entry == null) {
			return getGroupName(hash, type);
		}
		return type == MapType.LANDSCAPE ? entry.landscapeName() : entry.objectName();
	}

}
