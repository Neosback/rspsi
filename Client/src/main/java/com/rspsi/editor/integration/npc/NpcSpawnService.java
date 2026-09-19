package com.rspsi.editor.integration.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Studio service coordinating NPC spawn queries from server integrations.
 */
public final class NpcSpawnService {
    private final List<NpcSpawnProvider> providers = new CopyOnWriteArrayList<>();

    public void registerProvider(NpcSpawnProvider provider) {
        Objects.requireNonNull(provider, "provider");
        providers.add(provider);
    }

    public void unregisterProvider(String providerId) {
        providers.removeIf(p -> p.id().equals(providerId));
    }

    public List<NpcSpawn> spawns(int plane, int minX, int minY, int maxX, int maxY) {
        List<NpcSpawn> result = new ArrayList<>();
        for (NpcSpawnProvider provider : providers) {
            result.addAll(provider.spawns(plane, minX, minY, maxX, maxY));
        }
        return Collections.unmodifiableList(result);
    }

    public int totalSpawnCount() {
        return providers.stream().mapToInt(NpcSpawnProvider::totalSpawnCount).sum();
    }
}
