package com.rspsi.editor.integration.npc;

import java.util.List;

/**
 * Provider interface supplying server NPC spawn data for regions and world areas.
 */
public interface NpcSpawnProvider {

    String id();

    List<NpcSpawn> spawns(int plane, int minX, int minY, int maxX, int maxY);

    int totalSpawnCount();
}
