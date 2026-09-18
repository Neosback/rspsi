package com.rspsi.editor.render;

import java.util.List;
import java.util.Objects;

/** Backend-neutral scene batch prepared for a GPU uploader. */
public record GpuScenePacket(
        SceneWindow window,
        List<SceneTileSnapshot> tiles,
        LightingProfile lightingProfile,
        String fingerprint
) {
    public GpuScenePacket {
        window = Objects.requireNonNull(window, "window");
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
        lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint").trim();
        if (fingerprint.isEmpty()) {
            throw new IllegalArgumentException("GPU packet fingerprint cannot be empty");
        }
    }
}
