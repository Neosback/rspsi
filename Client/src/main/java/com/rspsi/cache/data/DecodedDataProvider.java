package com.rspsi.cache.data;

import java.util.List;
import java.util.Optional;

/** Typed, lazy provider for one neutral family decoded from the cache. */
public interface DecodedDataProvider<T> {
    String familyId();
    Class<T> valueType();
    List<Integer> ids();
    Optional<T> get(int id);
}
