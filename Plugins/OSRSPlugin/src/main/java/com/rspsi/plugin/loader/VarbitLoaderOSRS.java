package com.rspsi.plugin.loader;

import com.jagex.cache.config.VariableBits;
import com.jagex.cache.loader.config.VariableBitLoader;
import com.jagex.io.Buffer;
import com.rspsi.cache.store.CacheArchiveView;
import lombok.val;

import java.util.Arrays;

public class VarbitLoaderOSRS extends VariableBitLoader{

	private int count;
	private VariableBits[] bits;
	
	@Override
	public int count() {
		return count;
	}

	@Override
	public VariableBits forId(int id) {
		if(id < 0 || id > bits.length)
			return null;
		
		return bits[id];
	}

	@Override
	public void init(CacheArchiveView archive) {
		val highestId = Arrays.stream(archive.fileIds()).max().orElse(-1);
		count = highestId + 1;
		bits = new VariableBits[count];
		

		for (int id : archive.fileIds()) {
			byte[] data = archive.file(id);
			if (data != null)
				bits[id] = decode(new Buffer(data));
		}

	}
	
	private VariableBits decode(Buffer buffer) {
		VariableBits bit = new VariableBits();
		while(true) {
		int opcode = buffer.readUByte();
			if(opcode == 0)
				break;
			else if(opcode == 1) {
				int setting = buffer.readUShort();
				int low = buffer.readUByte();
				int high = buffer.readUByte();
				bit.setSetting(setting);
				bit.setHigh(high);
				bit.setLow(low);
			}
		}
		return bit;
	}

	@Override
	public void init(Buffer arg0, Buffer arg1) {

	}

}
