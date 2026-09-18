package com.rspsi.compat.osrs;

import com.jagex.cache.def.Floor;
import com.jagex.cache.loader.floor.FloorDefinitionLoader;
import com.jagex.cache.loader.floor.FloorType;
import com.rspsi.cache.store.CacheArchiveView;
import lombok.val;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.rspsi.tools.ByteBufHelper.get24BitInt;
import static com.rspsi.tools.ByteBufHelper.getUnsignedByte;

public final class OsrsFloorDefinitionLoader extends FloorDefinitionLoader {

    private Floor[] underlays;
    private Floor[] overlays;

    private int highestUnderlayId = -1;
    private int highestOverlayId = -1;

    public void initUnderlays(final CacheArchiveView archive) {
        val highestId = Arrays.stream(archive.fileIds()).max().orElse(-1);
        this.highestUnderlayId = highestId;

        this.underlays = new Floor[highestId + 1];

        for (final int id : archive.fileIds()) {
            final byte[] data = archive.file(id);
            if (data == null) continue;

            final ByteBuffer buffer = ByteBuffer.wrap(data);

            final Floor floor = decodeUnderlay(id, buffer);
            floor.generateHsl();

            underlays[id] = floor;
        }
    }

    public void initOverlays(final CacheArchiveView archive) {
        val highestId = Arrays.stream(archive.fileIds()).max().orElse(-1);
        this.highestOverlayId = highestId;

        this.overlays = new Floor[highestId + 1];

        for (final int id : archive.fileIds()) {
            final byte[] data = archive.file(id);
            if (data == null) continue;

            final ByteBuffer buffer = ByteBuffer.wrap(data);

            final Floor floor = decodeOverlay(id, buffer);
            floor.generateHsl();

            overlays[id] = floor;
        }
    }

    public Floor decodeUnderlay(final int id, final ByteBuffer buffer) {
        final Floor floor = new Floor();

        while (true) {
            final int opcode = getUnsignedByte(buffer);
            if (opcode == 0) {
                break;
            } else if (opcode == 1) {
                final int color = get24BitInt(buffer);
                floor.setRgb(color);
            } else {
                System.err.println("Error unrecognized underlay code for ID " + id + ": " + opcode);
            }
        }

        return floor;
    }

    public Floor decodeOverlay(final int id, final ByteBuffer buffer) {
        final Floor floor = new Floor();

        while (true) {
            final int opcode = getUnsignedByte(buffer);
            if (opcode == 0) {
                break;
            } else if (opcode == 1) {
                final int color = get24BitInt(buffer);
                floor.setRgb(color);
            } else if (opcode == 2) {
                final int texture = getUnsignedByte(buffer);
                floor.setTexture(texture);
            } else if (opcode == 5) {
                floor.setShadowed(false);
            } else if (opcode == 7) {
                final int secondaryColor = get24BitInt(buffer);
                floor.setAnotherRgb(secondaryColor);
            } else {
                System.err.println("Error unrecognized overlay code for ID " + id + ": " + opcode);
            }
        }

        return floor;
    }

    @Override
    public Floor getFloor(final int id, final FloorType type) {
        return type == FloorType.OVERLAY
                ? id < 0 || id >= overlays.length ? null : overlays[id]
                : id < 0 || id >= underlays.length ? null : underlays[id];
    }

    @Override
    public int getSize(final FloorType type) {
        return type == FloorType.OVERLAY
                ? overlays.length
                : underlays.length;
    }

    @Override
    public int getHighestId(final FloorType type) {
        return type == FloorType.OVERLAY
                ? highestOverlayId
                : highestUnderlayId;
    }

}
