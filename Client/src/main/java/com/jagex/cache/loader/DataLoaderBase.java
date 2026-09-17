package com.jagex.cache.loader;

import com.rspsi.cache.store.CacheArchiveView;

public interface DataLoaderBase<T> {

    T forId(final int id);

    int count();

    void init(final CacheArchiveView archive);

}
