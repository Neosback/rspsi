package com.jagex.cache.def;

import com.displee.cache.index.archive.Archive;
import com.jagex.io.Buffer;

public final class TextureDef
{
	private TextureDef()
	{
	}

	public static void unpackConfig(Archive streamLoader)
	{
		Buffer buffer = new Buffer(streamLoader.file("textures.dat").getData());
		int count = buffer.readUShort();
		textures = new TextureDef[count];
		for (int i = 0; i != count; ++i)
			if (buffer.readUByte() == 1)
				textures[i] = new TextureDef();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].isTransparent = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].isLoaded = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].isBrightnessAdjusted = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].textureType = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].blendType = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].blendParam1 = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].blendParam2 = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].averageHsl = (short) buffer.readUShort();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].animationSpeed = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].animationDirection = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].clampS = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].clampT = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].mipmapping = buffer.readByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].useAlpha = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].isAlphaMask = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].isHd = buffer.readUByte() == 1;


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].spriteCount = buffer.readUByte();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].materialId = buffer.readInt();


		for (int i = 0; i != count; ++i)
			if (textures[i] != null)
				textures[i].combineMode = buffer.readUByte();


	}

	public static void clearCache()
	{
		textures = null;
	}

	@Deprecated
	public static void nullLoader()
	{
		clearCache();
	}

	public boolean isTransparent;
	public boolean isLoaded;
	public boolean isBrightnessAdjusted;
	public byte textureType;
	public byte blendType;
	public byte blendParam1;
	public byte blendParam2;
	public short averageHsl;
	public byte animationSpeed;
	public byte animationDirection;
	public boolean clampS;
	public boolean clampT;
	public byte mipmapping;
	public boolean useAlpha;
	public boolean isAlphaMask;
	public boolean isHd;
	public int spriteCount;
	public int materialId;
	public int combineMode;
	public static TextureDef[] textures;
}
