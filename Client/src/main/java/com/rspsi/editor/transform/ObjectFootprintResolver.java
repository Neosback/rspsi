package com.rspsi.editor.transform;

import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.model.WorldObject;

import java.util.Objects;
import java.util.Optional;

/**
 * Supplies the unrotated definition footprint needed to transform location
 * anchors correctly.
 */
@FunctionalInterface
public interface ObjectFootprintResolver {
    Optional<ObjectFootprint> resolve(WorldObject object);

    static ObjectFootprintResolver fromAssets(AssetRepository assets) {
        Objects.requireNonNull(assets, "assets");
        return object -> assets.object(object.id())
                .map(definition -> new ObjectFootprint(
                        definition.width(), definition.length()));
    }

    record ObjectFootprint(int width, int length) {
        public ObjectFootprint {
            if (width <= 0 || length <= 0) {
                throw new IllegalArgumentException("Object footprint must be positive");
            }
        }
    }
}
