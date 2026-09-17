package com.rspsi.plugin.loader;

import java.util.Map;

import com.google.common.collect.Maps;
import com.jagex.cache.anim.FrameBase;
import com.jagex.cache.loader.anim.FrameBaseLoader;
import com.jagex.io.Buffer;
import com.rspsi.cache.store.CacheArchiveView;
import com.rspsi.cache.store.CacheIndexView;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrameBaseLoaderOSRS extends FrameBaseLoader {
	
	private Map<Integer, FrameBase> skeletons = Maps.newConcurrentMap();
	
	@Override
	public FrameBase get(int id) {
		return skeletons.get(id);
	}

	@Override
	public FrameBase decode(Buffer buffer) {
		FrameBase base = new FrameBase();
		int count = buffer.readUByte();
		int[] transformationType = new int[count];
		int[][] vertexGroups = new int[count][];
		for (int index = 0; index < count; index++) {
			transformationType[index] = buffer.readUByte();
		}

		for (int label = 0; label < count; label++) {
			vertexGroups[label] = new int[buffer.readUByte()];
		}

		for (int label = 0; label < count; label++) {
			for (int index = 0; index < vertexGroups[label].length; index++) {
				vertexGroups[label][index] = buffer.readUByte();
			}
		}
		base.setCount(count);
		base.setTransformationType(transformationType);
		base.setVertexGroups(vertexGroups);
		return base;
	}

	public void init(CacheIndexView skeletonIndex) {
		for (CacheArchiveView archive : skeletonIndex.archives()) {
			try {
				byte[] data = archive.file(0);
				if (data != null) {
					FrameBase base = decode(new Buffer(data));
					skeletons.put(archive.id(), base);
				}
			} catch (RuntimeException exception) {
				// A modern cache can contain sparse or non-frame-base archives in
				// the animation index. One malformed group must not abort the
				// entire OSRS project startup; identify it so the revision/layout
				// can be audited instead of silently applying a 317 fallback.
				log.warn("Skipping malformed OSRS skeleton archive {} files {}",
						archive.id(), java.util.Arrays.toString(archive.fileIds()), exception);
			}
		}
		log.info("Loaded {} OSRS skeleton archives from index {}", skeletons.size(), skeletonIndex.id());
	}

}
