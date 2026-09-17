package com.rspsi.plugin.loader;

import com.jagex.cache.anim.Graphic;
import com.jagex.cache.loader.anim.AnimationDefinitionLoader;
import com.jagex.cache.loader.anim.GraphicLoader;
import com.jagex.io.Buffer;
import com.rspsi.cache.store.CacheArchiveView;
import lombok.val;

import java.util.Arrays;

public class GraphicLoaderOSRS extends GraphicLoader {


	private Graphic[] graphics;
	private int count;
	private int revision = -1;
	
	@Override
	public int count() {
		return count;
	}

	@Override
	public Graphic forId(int id) {
		if(id < 0 || id > count)
			return null;
		return graphics[id];
	}

	@Override
	public void init(CacheArchiveView archive) {
		init(archive, -1);
	}

	/** Initializes graphics using the selected cache revision's opcode rules. */
	public void init(CacheArchiveView archive, int revision) {
		this.revision = revision;

		val highestId = Arrays.stream(archive.fileIds()).max().orElse(-1);
		graphics = new Graphic[highestId + 1];
		

		for (int id : archive.fileIds()) {
			try {
				byte[] data = archive.file(id);
				if (data == null) continue;
				graphics[id] = decode(new Buffer(data));
				graphics[id].setId(id);
			} catch (Exception ex) {

			}
		}
	}

	public Graphic decode(Buffer buffer) {
		Graphic graphic = new Graphic();
		int lastOpcode = -1;
		do {
			int opcode = buffer.readUByte();
			if (opcode == 0)
				return graphic;

			if (opcode == 1) {
				graphic.setModel(buffer.readUShort());
			} else if (opcode == 2) {
				int animationId = buffer.readUShort();
				if (animationId >= 0) {
					graphic.setAnimation(AnimationDefinitionLoader.getAnimation(animationId));
				}
				graphic.setAnimationId(animationId);
			} else if (opcode == 4) {
				graphic.setBreadthScale(buffer.readUShort());
			} else if (opcode == 5) {
				graphic.setDepthScale(buffer.readUShort());
			} else if (opcode == 6) {
				graphic.setOrientation(buffer.readUShort());
			} else if (opcode == 7) {
				graphic.setAmbience(buffer.readUByte());
			} else if (opcode == 8) {
				graphic.setModelShadow(buffer.readUByte());
			} else if (opcode == 3) {
				// OSRS revision 237+ uses the widened model id opcode. The
				// older compatibility decoder left the four bytes unread,
				// causing every following byte to be interpreted as another
				// opcode and producing cascading spot-animation corruption.
				int modelId = buffer.readInt();
				if (revision >= 237) graphic.setModel(modelId);
			} else if (opcode == 9) {
				// Modern OSRS debug names are not rendered, but the payload
				// must still be consumed so later opcodes stay aligned.
				buffer.readOSRSString();
			} else if (opcode == 10) {
				// Modern caches can explicitly disable model rotation.
			} else if (opcode == 40) {
				int len = buffer.readUByte();
				int[] originalColours = new int[len];
				int[] replacementColours = new int[len];
				for (int i = 0; i < len; i++) {
					originalColours[i] = buffer.readUShort();
					replacementColours[i] = buffer.readUShort();
				}
				graphic.setOriginalColours(originalColours);
				graphic.setReplacementColours(replacementColours);
			} else if(opcode == 41) {
				int len = buffer.readUByte();
				
				for (int i = 0; i < len; i++) {
					buffer.readUShort();
					buffer.readUShort();
				}
			} else {
				System.out.println("Error unrecognised spotanim config code: " + opcode + " last: " + lastOpcode);
			}
			lastOpcode = opcode;
		} while (true);
	}

}
