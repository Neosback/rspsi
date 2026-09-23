package com.rspsi.api.scene;

import com.rspsi.api.DecorativeObject;
import com.rspsi.api.GameObject;
import com.rspsi.api.GroundObject;
import com.rspsi.api.Point;
import com.rspsi.api.TileObject;
import com.rspsi.api.WallObject;
import com.rspsi.api.coords.LocalPoint;
import com.rspsi.api.coords.WorldPoint;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.SceneObjectIdentity;
import com.rspsi.editor.render.WallDecorationPresentation;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** {@link TileObject} over the model packets one placement submitted. */
abstract class ResolvedTileObject implements TileObject {
    private final ResolvedTile tile;
    protected final SceneObjectIdentity identity;
    private final boolean rendered;
    private final long hash;

    private ResolvedTileObject(ResolvedTile tile, SceneObjectIdentity identity, boolean rendered) {
        this.tile = tile;
        this.identity = identity;
        this.rendered = rendered;
        this.hash = fnv64(identity.stableId());
    }

    /** One placement; {@code packets} is empty when it submitted no geometry. */
    static ResolvedTileObject of(ResolvedTile tile, SceneObjectIdentity identity,
                                 List<ModelRenderPacket> packets) {
        boolean rendered = packets.stream().anyMatch(packet -> !packet.editorGhost());
        return switch (identity.category()) {
            case WALL -> new Wall(tile, identity, rendered);
            case WALL_DECOR -> new Decoration(tile, identity, rendered, primaryPresentation(packets));
            case GROUND_DECOR -> new Ground(tile, identity, rendered);
            case GROUND, UNKNOWN -> new Game(tile, identity, rendered);
        };
    }

    private static WallDecorationPresentation primaryPresentation(List<ModelRenderPacket> packets) {
        for (ModelRenderPacket packet : packets) {
            WallDecorationPresentation presentation = packet.wallDecorationPresentation();
            if (presentation.part() == WallDecorationPresentation.Part.PRIMARY) return presentation;
        }
        return packets.isEmpty() ? WallDecorationPresentation.none()
                : packets.get(0).wallDecorationPresentation();
    }

    private static long fnv64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    int sceneAnchorX() {
        return identity.anchorX() - tile.scene().getBaseX();
    }

    int sceneAnchorY() {
        return identity.anchorY() - tile.scene().getBaseY();
    }

    @Override public long getHash() { return hash; }
    @Override public String getStableId() { return identity.stableId(); }
    @Override public int getId() { return identity.objectId(); }
    @Override public int getType() { return identity.shape(); }
    @Override public int getRotation() { return identity.rotation(); }
    @Override public int getPlane() { return tile.getPlane(); }
    @Override public int getAuthoredPlane() { return identity.authoredPlane(); }
    @Override public boolean isRendered() { return rendered; }

    @Override
    public WorldPoint getWorldLocation() {
        return new WorldPoint(identity.anchorX(), identity.anchorY(), getPlane());
    }

    @Override
    public LocalPoint getLocalLocation() {
        return LocalPoint.fromScene(sceneAnchorX(), sceneAnchorY());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + identity.stableId() + "]";
    }

    private static final class Wall extends ResolvedTileObject implements WallObject {
        private final WorldObject placement;

        Wall(ResolvedTile tile, SceneObjectIdentity identity, boolean rendered) {
            super(tile, identity, rendered);
            placement = new WorldObject(identity.objectId(), identity.shape(), identity.rotation(),
                    identity.authoredPlane(), identity.anchorX(), identity.anchorY());
        }

        @Override public int getOrientationA() { return placement.wallOrientationA(); }
        @Override public int getOrientationB() { return placement.wallOrientationB(); }
    }

    private static final class Decoration extends ResolvedTileObject implements DecorativeObject {
        private final WallDecorationPresentation presentation;

        Decoration(ResolvedTile tile, SceneObjectIdentity identity, boolean rendered,
                   WallDecorationPresentation presentation) {
            super(tile, identity, rendered);
            this.presentation = presentation;
        }

        @Override public int getXOffset() { return presentation.offsetX(); }
        @Override public int getYOffset() { return presentation.offsetZ(); }
    }

    private static final class Ground extends ResolvedTileObject implements GroundObject {
        Ground(ResolvedTile tile, SceneObjectIdentity identity, boolean rendered) {
            super(tile, identity, rendered);
        }
    }

    private static final class Game extends ResolvedTileObject implements GameObject {
        Game(ResolvedTile tile, SceneObjectIdentity identity, boolean rendered) {
            super(tile, identity, rendered);
        }

        @Override public int sizeX() { return identity.footprintWidth(); }
        @Override public int sizeY() { return identity.footprintLength(); }

        @Override
        public Point getSceneMinLocation() {
            return new Point(sceneAnchorX(), sceneAnchorY());
        }

        @Override
        public Point getSceneMaxLocation() {
            return new Point(sceneAnchorX() + sizeX() - 1, sceneAnchorY() + sizeY() - 1);
        }
    }
}
