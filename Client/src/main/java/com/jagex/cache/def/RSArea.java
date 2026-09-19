package com.jagex.cache.def;

import java.nio.ByteBuffer;

import com.jagex.util.ByteBufferUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
public class RSArea {
	
	private final int id;

	private int spriteId = -1;
	private int sprite2Id = -1;
	private String name;
	private int fontColor;
	private int textSize = 0;
	private int[] coordinateOffsets;
	private String menuTargetName;
	private int[] compositeElementIds;
	private int category;
	private byte[] planeBytes;
	private String[] menuActions = new String[5];

	@Deprecated
	public int getAnInt1967() {
		return sprite2Id;
	}

	@Deprecated
	public void setAnInt1967(int value) {
		this.sprite2Id = value;
	}

	@Deprecated
	public int getAnInt1959() {
		return fontColor;
	}

	@Deprecated
	public void setAnInt1959(int value) {
		this.fontColor = value;
	}

	@Deprecated
	public int getAnInt1968() {
		return textSize;
	}

	@Deprecated
	public void setAnInt1968(int value) {
		this.textSize = value;
	}

	@Deprecated
	public int[] getAnIntArray1982() {
		return coordinateOffsets;
	}

	@Deprecated
	public void setAnIntArray1982(int[] value) {
		this.coordinateOffsets = value;
	}

	@Deprecated
	public String getAString1970() {
		return menuTargetName;
	}

	@Deprecated
	public void setAString1970(String value) {
		this.menuTargetName = value;
	}

	@Deprecated
	public int[] getAnIntArray1981() {
		return compositeElementIds;
	}

	@Deprecated
	public void setAnIntArray1981(int[] value) {
		this.compositeElementIds = value;
	}

	@Deprecated
	public int getAnInt1980() {
		return category;
	}

	@Deprecated
	public void setAnInt1980(int value) {
		this.category = value;
	}

	@Deprecated
	public byte[] getAByteArray1979() {
		return planeBytes;
	}

	@Deprecated
	public void setAByteArray1979(byte[] value) {
		this.planeBytes = value;
	}

	@Deprecated
	public String[] getAStringArray1969() {
		return menuActions;
	}

	@Deprecated
	public void setAStringArray1969(String[] value) {
		this.menuActions = value;
	}

	

}
