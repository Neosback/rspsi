package com.jagex.map.object;

import com.jagex.util.ObjectKey;

public final class Wall extends DefaultWorldObject {

	public int orientationA;

	public int orientationB;

	public Wall(ObjectKey id, int x, int y, int z) {
		super(id, x, y, z);
		// TODO Auto-generated constructor stub
	}

	@Override
	public WorldObjectType getType() {
		return WorldObjectType.WALL;
	}

}