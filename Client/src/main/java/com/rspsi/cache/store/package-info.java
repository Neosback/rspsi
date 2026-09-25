/**
 * Cache-backend boundary for OpenRune Studio.
 *
 * <p>Modern OSRS uses OpenRune FileStore as the single production backend. Backend-specific
 * OpenRune types are reduced to Studio-owned neutral interfaces inside this package. Displee is
 * retained only through {@link com.rspsi.cache.store.LegacyDispleeCacheStore} for explicit
 * legacy/custom-cache compatibility and must not become a second modern-OSRS path.</p>
 *
 * <p>Terrain/location semantic round-tripping is owned by
 * {@code com.rspsi.cache.map.OsrsRegionDecoder/OsrsRegionEncoder}; this package owns cache access,
 * definitions, metadata, and adapter-specific exceptions.</p>
 */
package com.rspsi.cache.store;
