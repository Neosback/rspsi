package com.jagex.map.object;

public final class SpawnedObject extends LinkableWorldObject {

	private int delay;

	private int group;
	private int id;
	private int longevity = -1;
	private int orientation;
	private int previousId;
	private int previousOrientation;
	private int previousType;
	private int type;

	public SpawnedObject(int id, int x, int y, int z) {
		super(id, x, y, z);
		// TODO Auto-generated constructor stub
	}

	public int getDelay() {
		return delay;
	}

	public int getGroup() {
		return group;
	}

	@Override
	public int getId() {
		return id;
	}

	public int getLongevity() {
		return longevity;
	}

	@Deprecated
	public int getLongetivity() {
		return getLongevity();
	}

	public int getOrientation() {
		return orientation;
	}

	public int getPreviousId() {
		return previousId;
	}

	public int getPreviousOrientation() {
		return previousOrientation;
	}

	public int getPreviousType() {
		return previousType;
	}

	public int getType() {
		return type;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public void setGroup(int group) {
		this.group = group;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setLongevity(int longevity) {
		this.longevity = longevity;
	}

	@Deprecated
	public void setLongetivity(int longetivity) {
		setLongevity(longetivity);
	}

	public void setOrientation(int orientation) {
		this.orientation = orientation;
	}

	public void setPreviousId(int previousId) {
		this.previousId = previousId;
	}

	public void setPreviousOrientation(int previousOrientation) {
		this.previousOrientation = previousOrientation;
	}

	public void setPreviousType(int previousType) {
		this.previousType = previousType;
	}

	public void setType(int type) {
		this.type = type;
	}
}