package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldTileAddressInteropTest {

    @Test
    void remainsJvmRecordAndFactoryDerivesCanonicalBreakdown() {
        WorldTileAddress address = WorldTileAddress.of(3205, 3210, 2);

        assertTrue(WorldTileAddress.class.isRecord());
        assertEquals(3205, address.worldX());
        assertEquals(3210, address.worldY());
        assertEquals(2, address.plane());
        assertEquals(3205 >> 6, address.regionX());
        assertEquals(3210 >> 6, address.regionY());
        assertEquals(((3205 >> 6) << 8) | (3210 >> 6), address.regionId());
        assertEquals(3205 & 63, address.regionLocalX());
        assertEquals(3210 & 63, address.regionLocalY());
        assertEquals(3205 >> 3, address.chunkX());
        assertEquals(3210 >> 3, address.chunkY());
        assertEquals(3205 & 7, address.chunkLocalX());
        assertEquals(3210 & 7, address.chunkLocalY());
        assertTrue(address.toString().startsWith("WorldTileAddress["));
    }

    @Test
    void factoryPreservesNegativeCoordinateValidation() {
        assertThrows(IllegalArgumentException.class, () -> WorldTileAddress.of(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldTileAddress.of(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> WorldTileAddress.of(0, 0, -1));
    }

    @Test
    void constructorPreservesHistoricalConsistencyChecks() {
        WorldTileAddress canonical = WorldTileAddress.of(3205, 3210, 2);

        assertThrows(IllegalArgumentException.class, () -> new WorldTileAddress(
                canonical.worldX(),
                canonical.worldY(),
                canonical.plane(),
                canonical.regionId() + 1,
                canonical.regionX(),
                canonical.regionY(),
                canonical.regionLocalX(),
                canonical.regionLocalY(),
                canonical.chunkX(),
                canonical.chunkY(),
                canonical.chunkLocalX(),
                canonical.chunkLocalY()));

        assertThrows(IllegalArgumentException.class, () -> new WorldTileAddress(
                canonical.worldX(),
                canonical.worldY(),
                canonical.plane(),
                canonical.regionId(),
                canonical.regionX(),
                canonical.regionY(),
                canonical.regionLocalX() + 1,
                canonical.regionLocalY(),
                canonical.chunkX(),
                canonical.chunkY(),
                canonical.chunkLocalX(),
                canonical.chunkLocalY()));
    }

    @Test
    void directConstructorDoesNotGainStricterValidationDuringMigration() {
        WorldTileAddress canonical = WorldTileAddress.of(3205, 3210, 2);

        // Historical constructor validation does not re-derive chunkX/chunkY from world coords.
        WorldTileAddress compatible = new WorldTileAddress(
                canonical.worldX(),
                canonical.worldY(),
                canonical.plane(),
                canonical.regionId(),
                canonical.regionX(),
                canonical.regionY(),
                canonical.regionLocalX(),
                canonical.regionLocalY(),
                canonical.chunkX() + 1,
                canonical.chunkY() + 1,
                canonical.chunkLocalX(),
                canonical.chunkLocalY());

        assertEquals(canonical.chunkX() + 1, compatible.chunkX());
        assertEquals(canonical.chunkY() + 1, compatible.chunkY());
    }

    @Test
    void preservesStructuralValueSemantics() {
        WorldTileAddress left = WorldTileAddress.of(3205, 3210, 2);
        WorldTileAddress right = WorldTileAddress.of(3205, 3210, 2);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
