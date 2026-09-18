package com.rspsi.compat.osrs;

import com.jagex.cache.graphics.Sprite;
import com.jagex.cache.loader.textures.TextureLoader;
import com.jagex.draw.textures.SpriteTexture;
import com.jagex.draw.textures.Texture;
import com.jagex.io.Buffer;
import com.rspsi.core.misc.FixedHashMap;
import com.rspsi.cache.store.CacheArchiveView;
import com.rspsi.cache.store.CacheIndexView;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.ByteBuffer;
import java.util.Arrays;

@Slf4j
public class OsrsTextureLoader extends TextureLoader {

	private Texture[] textures;
	private boolean[] transparent;
	private double brightness = 0.8;
	private FixedHashMap<Integer, int[]> textureCache = new FixedHashMap<Integer, int[]>(20);
	
	
	@Override
	public Texture forId(int arg0) {
		if(arg0 < 0 || arg0 >= textures.length)
			return null;
		return textures[arg0];
	}

	@Override
	public int[] getPixels(int textureId) {
		Texture texture = forId(textureId);
		if(texture == null) {
			log.info("Texture {} was not found!", textureId);
			return null;
		}

		if(textureCache.contains(textureId))
			return textureCache.get(textureId);
		
		int[] texels = new int[0x10000];
		
		texture.setBrightness(brightness);
		if (texture.getWidth() == 64)
			for (int y = 0; y < 128; y++)
				for (int x = 0; x < 128; x++)
					texels[x + (y << 7)] = texture.getPixel((x >> 1) + ((y >> 1) << 6));


		else
			for (int texelPtr = 0; texelPtr < 16384; texelPtr++)
				texels[texelPtr] = texture.getPixel(texelPtr);
		
		for (int l1 = 0; l1 < 16384; l1++) {
			texels[l1] &= 0xf8f8ff;
			int k2 = texels[l1];
			texels[16384 + l1] = k2 - (k2 >>> 3) & 0xf8f8ff;
			texels[32768 + l1] = k2 - (k2 >>> 2) & 0xf8f8ff;
			texels[49152 + l1] = k2 - (k2 >>> 2) - (k2 >>> 3) & 0xf8f8ff;
		}


		textureCache.put(textureId, texels);
	return texels;
	}

	
	public void init(CacheArchiveView archive, CacheIndexView spriteIndex) {
		val highestId = Arrays.stream(archive.fileIds()).max().orElse(-1);
		textures = new Texture[highestId + 1];
		transparent = new boolean[highestId + 1];
		for (int id : archive.fileIds()) {
			byte[] data = archive.file(id);
			if (data != null) {
				log.info("Loading texture {}", id);
				Buffer buffer = new Buffer(data);
				int spriteId = buffer.readUnsignedShort();
				byte[] spriteData = spriteIndex.archive(spriteId).file(0);
				if (spriteData == null) continue;
				Sprite sprite = Sprite.decode(ByteBuffer.wrap(spriteData));
				if(sprite.getWidth() != 128 || sprite.getHeight() != 128)
					sprite.resize(128, 128);
				Texture texture = new SpriteTexture(sprite);
				textures[id] = texture;
				transparent[id] = texture.supportsAlpha();
			}
		}
	}

	

	@Override
	public boolean isTransparent(int arg0) {
		if(arg0 < 0 || arg0 >= transparent.length)
			return false;
		return transparent[arg0];
	}

	@Override
	public void setBrightness(double arg0) {
		textureCache.clear();
		this.brightness = arg0;
	}

	@Override
	public int count() {
		return textures.length;
	}

	@Override
	public void init(CacheArchiveView archive) {
	}

}
