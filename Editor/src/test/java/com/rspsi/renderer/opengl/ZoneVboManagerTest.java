package com.rspsi.renderer.opengl;

import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.render.GpuZoneStreamFingerprints;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneVboManagerTest {

    @Test
    void tilesInSameEightByEightZoneShareZoneKey() {
        // WorldTileAddress.of(worldX, worldY, plane)
        WorldTileAddress origin = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress inside = WorldTileAddress.of(3207, 3207, 0);
        assertEquals(ZoneVboManager.zoneKey(origin), ZoneVboManager.zoneKey(inside));
    }

    @Test
    void crossingEightTileBoundaryProducesDistinctZoneKeys() {
        WorldTileAddress zoneA = WorldTileAddress.of(3207, 3200, 0);
        WorldTileAddress zoneB = WorldTileAddress.of(3208, 3200, 0);
        assertNotEquals(ZoneVboManager.zoneKey(zoneA), ZoneVboManager.zoneKey(zoneB));

        WorldTileAddress zoneC = WorldTileAddress.of(3200, 3207, 0);
        WorldTileAddress zoneD = WorldTileAddress.of(3200, 3208, 0);
        assertNotEquals(ZoneVboManager.zoneKey(zoneC), ZoneVboManager.zoneKey(zoneD));
    }

    @Test
    void streamUploadDecisionKeepsStableGeometryResidentForShadingOnlyChange() {
        ZoneVboManager.ZoneAllocation existing = new ZoneVboManager.ZoneAllocation(
                1L, 10, 11, 12, 13,
                100L, 200L, 300L);
        GpuZoneStreamFingerprints changed =
                new GpuZoneStreamFingerprints(100L, 201L, 300L, 999L);

        ZoneVboManager.StreamUploadDecision decision =
                ZoneVboManager.streamUploadDecision(existing, changed);

        assertFalse(decision.geometry());
        assertTrue(decision.vertexShading());
        assertFalse(decision.faceMetadata());
        assertTrue(decision.shading());
        assertFalse(decision.indices());
        assertTrue(decision.any());
    }

    @Test
    void newZoneUploadsEveryCurrentVanillaStream() {
        ZoneVboManager.StreamUploadDecision decision =
                ZoneVboManager.streamUploadDecision(
                        null, new GpuZoneStreamFingerprints(100L, 200L, 300L, 400L));

        assertTrue(decision.geometry());
        assertTrue(decision.vertexShading());
        assertTrue(decision.faceMetadata());
        assertTrue(decision.shading());
        assertTrue(decision.indices());
    }


    @Test
    void faceOnlyChangeKeepsGeometryAndVertexShadingResident() {
        ZoneVboManager.ZoneAllocation existing = new ZoneVboManager.ZoneAllocation(
                1L, 10, 11, 12, 13, 0, 14,
                100L, 200L, 300L, 0L, 400L);
        GpuZoneStreamFingerprints changed =
                new GpuZoneStreamFingerprints(100L, 200L, 301L, 400L, 999L);

        ZoneVboManager.StreamUploadDecision decision =
                ZoneVboManager.streamUploadDecision(existing, changed);

        assertFalse(decision.geometry());
        assertFalse(decision.vertexShading());
        assertTrue(decision.faceMetadata());
        assertTrue(decision.shading());
        assertFalse(decision.normals());
        assertFalse(decision.indices());
        assertTrue(decision.any());
    }

    @Test
    void normalOnlyChangeIsIgnoredByVanillaButScheduledWhenNormalStreamIsEnabled() {
        ZoneVboManager.ZoneAllocation existing = new ZoneVboManager.ZoneAllocation(
                1L, 10, 11, 12, 14, 13,
                100L, 200L, 400L, 300L);
        GpuZoneStreamFingerprints changed =
                new GpuZoneStreamFingerprints(100L, 200L, 300L, 401L);

        ZoneVboManager.StreamUploadDecision vanilla =
                ZoneVboManager.streamUploadDecision(existing, changed, false);
        ZoneVboManager.StreamUploadDecision withNormals =
                ZoneVboManager.streamUploadDecision(existing, changed, true);

        assertFalse(vanilla.any());
        assertFalse(vanilla.normals());
        assertFalse(withNormals.geometry());
        assertFalse(withNormals.shading());
        assertTrue(withNormals.normals());
        assertFalse(withNormals.indices());
    }

    @Test
    void differentPlanesProduceDistinctZoneKeys() {
        WorldTileAddress plane0 = WorldTileAddress.of(3200, 3200, 0);
        WorldTileAddress plane1 = WorldTileAddress.of(3200, 3200, 1);
        assertNotEquals(ZoneVboManager.zoneKey(plane0), ZoneVboManager.zoneKey(plane1));
    }
}
