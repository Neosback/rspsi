package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.editor.model.FloorId;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.terrain.TerrainMeshBuilder;

import java.util.Objects;
import java.util.Optional;

/**
 * The terrain packet the renderer draws for one tile, for inspectors.
 *
 * <p>{@link Mode#BLENDED} is exactly the scene path (neighbour-blended
 * underlay, lighting, overlay shape). {@link Mode#UNBLENDED} keeps the same
 * geometry, lighting and overlay but paints the tile's own underlay colour on
 * every corner, isolating what underlay blending contributes.</p>
 */
public final class TilePreviewBuilder {
    public enum Mode {
        BLENDED,
        UNBLENDED
    }

    private final TerrainMeshBuilder meshes = new TerrainMeshBuilder();
    private final TerrainAppearanceBuilder appearances = new TerrainAppearanceBuilder();
    private final TerrainPacketBuilder packets = new TerrainPacketBuilder();

    /** Empty when the tile has no drawable floor (no underlay and no overlay). */
    public Optional<TerrainRenderPacket> build(WorldDocument document, DefinitionProvider definitions,
                                               int plane, int x, int y, Mode mode) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(mode, "mode");
        TileSnapshot tile = document.tile(plane, x, y).snapshot();
        TerrainAppearance appearance = appearances.buildTile(document, definitions, plane, x, y);
        if (mode == Mode.UNBLENDED) {
            appearance = unblended(appearance, tile, definitions);
        }
        TerrainLight light = TerrainLighting.buildTile(document, LightingProfile.osrs(), null, plane, x, y);
        TerrainRenderPacket packet = packets.build(new TileCoordinate(plane, x, y),
                meshes.build(tile), appearance, light);
        return packet.faces().isEmpty() ? Optional.empty() : Optional.of(packet);
    }

    private static TerrainAppearance unblended(TerrainAppearance blended, TileSnapshot tile,
                                               DefinitionProvider definitions) {
        int own = definitions.underlay(FloorId.definitionId(tile.underlayId()))
                .map(TilePreviewBuilder::underlayHsl)
                .orElse(blended.underlayHsl());
        return new TerrainAppearance(own, own, own, own, own,
                blended.overlayHsl(), blended.overlaySecondaryHsl(), blended.textureId(),
                blended.textureAverageHsl(), blended.shape(), blended.rotation(),
                blended.overlayHidden(), blended.overlayMinimapHsl());
    }

    private static int underlayHsl(FloorDefinitionView floor) {
        return OsrsTerrainColorMath.packHsl(floor.hue(), floor.saturation(), floor.luminance());
    }
}
