package com.rspsi.compat.osrs;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

import com.jagex.cache.def.RSArea;
import com.jagex.cache.loader.config.RSAreaLoader;
import com.jagex.io.Buffer;
import com.jagex.util.ByteBufferUtils;
import com.rspsi.cache.store.CacheArchiveView;
import lombok.val;

public class OsrsAreaLoader extends RSAreaLoader {

	private RSArea[] areas;
	@Override
	public RSArea forId(int id) {
		if(id < 0 || id >= areas.length)
			return null;
		return areas[id];
	}

	@Override
	public int count() {
		return areas.length;
	}

	@Override
	public void init(CacheArchiveView archive) {
		if(archive == null){
			areas = new RSArea[1000];
			IntStream.range(0, areas.length).forEach(index -> {
				RSArea dummyArea = new RSArea(index);
				dummyArea.setSpriteId(index);
				areas[index] = dummyArea;
			});
			return;
		}
		val highestId = Arrays.stream(archive.fileIds()).max().orElse(-1);
		areas = new RSArea[highestId + 1];
		for (int id : archive.fileIds()) {
			byte[] data = archive.file(id);
			if (data != null) {
				RSArea area = decode(id, ByteBuffer.wrap(data));
				areas[id] = area;
			}
		}
	}
	
	private RSArea decode(int id, ByteBuffer buffer) {
		RSArea area = new RSArea(id);
		while (true) {
			int opcode = buffer.get() & 0xFF;
			if (opcode == 0)
				break;

			if (opcode == 1) {
				area.setSpriteId(ByteBufferUtils.getSmartInt(buffer));
			} else if (opcode == 2) {
				area.setSprite2Id(ByteBufferUtils.getSmartInt(buffer));
			} else if (opcode == 3) {
				area.setName(ByteBufferUtils.getOSRSString(buffer));
			} else if (opcode == 4) {
				area.setFontColor(ByteBufferUtils.getMedium(buffer));
			} else if (opcode == 5) {
				ByteBufferUtils.getMedium(buffer);
			} else if (opcode == 6) {
				area.setTextSize(buffer.get() & 0xFF);
			} else if (opcode == 7) {
				int flags = buffer.get() & 0xFF;
				if ((flags & 0x1) == 0) {
				}
				if ((flags & 0x2) == 2) {
				}
			} else if (opcode == 8) {
				buffer.get();
			} else if (opcode >= 10 && opcode <= 14) {
				area.getMenuActions()[opcode - 10] = ByteBufferUtils.getOSRSString(buffer);
			} else if (opcode == 15) {
				int size = buffer.get() & 0xFF;
				int[] coordinateOffsets = new int[size * 2];

				for (int i = 0; i < size * 2; ++i) {
					coordinateOffsets[i] = buffer.getShort();
				}

				buffer.getInt();
				int size2 = buffer.get() & 0xFF;
				int[] compositeElementIds = new int[size2];

				for (int i = 0; i < compositeElementIds.length; ++i) {
					compositeElementIds[i] = buffer.getInt();
				}

				byte[] planeBytes = new byte[size];

				for (int i = 0; i < size; ++i) {
					planeBytes[i] = buffer.get();
				}
				area.setCoordinateOffsets(coordinateOffsets);
				area.setCompositeElementIds(compositeElementIds);
				area.setPlaneBytes(planeBytes);
			} else if (opcode == 17) {
				area.setMenuTargetName(ByteBufferUtils.getOSRSString(buffer));
			} else if (opcode == 18) {
				ByteBufferUtils.getSmartInt(buffer);
			} else if (opcode == 19) {
				area.setCategory(buffer.getShort() & 0xFFFF);
			} else if (opcode == 21) {
				buffer.getInt();
			} else if (opcode == 22) {
				buffer.getInt();
			} else if (opcode == 23) {
				buffer.get();
				buffer.get();
				buffer.get();
			} else if (opcode == 24) {
				buffer.getShort();
				buffer.getShort();
			} else if (opcode == 25) {
				ByteBufferUtils.getSmartInt(buffer);
			} else if (opcode == 28) {
				buffer.get();
			} else if (opcode == 29) {
				buffer.get();
			} else if (opcode == 30) {
				buffer.get();
			}
		}
		return area;
	}

	@Override
	public void init(Buffer data, Buffer indexBuffer) {
		// TODO Auto-generated method stub

	}

}
