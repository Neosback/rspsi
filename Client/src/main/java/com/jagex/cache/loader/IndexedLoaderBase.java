package com.jagex.cache.loader;

import com.jagex.io.Buffer;
import com.rspsi.cache.store.CacheArchiveView;

public interface IndexedLoaderBase<T> {

	T forId(int id);
	int count();
	
	void init(CacheArchiveView archive);
	void init(Buffer data, Buffer indexBuffer);
}
