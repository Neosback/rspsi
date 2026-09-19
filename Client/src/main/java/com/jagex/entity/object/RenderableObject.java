package com.jagex.entity.object;

import com.jagex.Client;
import com.jagex.cache.anim.Animation;
import com.jagex.cache.config.VariableBits;
import com.jagex.cache.def.ObjectDefinition;
import com.jagex.cache.loader.anim.AnimationDefinitionLoader;
import com.jagex.cache.loader.object.ObjectDefinitionLoader;
import com.jagex.entity.Renderable;
import com.jagex.entity.model.Mesh;
import com.rspsi.options.Options;

public class RenderableObject extends Renderable {

	public static Client client;
	private Animation animation;
	private int heightSW;
	private int heightSE;
	private int heightNE;
	private int heightNW;
	private int currentFrameId;
	private int id;
	private int animId;
	private int currentFrameDuration;
	private int orientation;
	private int type;
	private boolean randomFrame;

	public RenderableObject(int id, int orientation, int type, int heightSW, int heightSE, int heightNE, int heightNW, int animationId,
			boolean randomFrame) {
		this.id = id;
		this.type = type;
		this.orientation = orientation;
		this.heightSW = heightSW;
		this.heightSE = heightSE;
		this.heightNE = heightNE;
		this.heightNW = heightNW;
		this.animId = animationId;
		this.randomFrame = randomFrame;
		if (animationId != -1) {
			animation = AnimationDefinitionLoader.getAnimation(animationId);
			currentFrameId = 0;
			currentFrameDuration = Client.pulseTick;
			if (randomFrame && animation.getLoopOffset() != -1) {
				currentFrameId = (int) (Math.random() * animation.getFrameCount());
				currentFrameDuration -= (int) (Math.random() * animation.duration(currentFrameId));
			}
		}

	}

	@Override
	public final Mesh model() {
		int lastFrame = -1;
		if (animation != null && Options.loadAnimations.get()) {
			int tickDelta = Client.pulseTick - currentFrameDuration;
			if (tickDelta > 100 && animation.getLoopOffset() > 0) {
				tickDelta = 100;
			}

			while (tickDelta > animation.duration(currentFrameId)) {
				tickDelta -= animation.duration(currentFrameId);
				currentFrameId++;
				if (currentFrameId < animation.getFrameCount()) {
					continue;
				}
				currentFrameId -= animation.getLoopOffset();
				if (currentFrameId >= 0 && currentFrameId < animation.getFrameCount()) {
					continue;
				}
				animation = null;
				break;
			}

			currentFrameDuration = Client.pulseTick - tickDelta;
			if (animation != null) {
				lastFrame = animation.getPrimaryFrame(currentFrameId);
			}
		}

		ObjectDefinition definition = ObjectDefinitionLoader.lookup(id);
		if(definition.getMorphisms() != null)
			definition = morph();
		Mesh model = definition == null ? null
				: definition.modelAt(type, orientation, heightSW, heightSE, heightNE, heightNW, lastFrame);
		if(model != null && this.selected) {
			model = model.copy();
			model.selected = true;
		}
		return model;
	}

	public final ObjectDefinition morph() {
		return ObjectDefinitionLoader.getMorphism(id);
	}
	

}