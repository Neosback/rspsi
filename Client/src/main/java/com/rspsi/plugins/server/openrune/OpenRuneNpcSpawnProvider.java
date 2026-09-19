package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.npc.NpcSpawn;
import com.rspsi.editor.integration.npc.NpcSpawnProvider;
import com.rspsi.editor.model.TileCoordinate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * NpcSpawnProvider that parses OpenRune NPC spawn configurations.
 */
public final class OpenRuneNpcSpawnProvider implements NpcSpawnProvider {
    private final Path projectRoot;
    private final List<NpcSpawn> spawns = new ArrayList<>();

    public OpenRuneNpcSpawnProvider(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        indexSpawns();
    }

    private void indexSpawns() {
        Path spawnsFile = projectRoot.resolve("data").resolve("spawns.txt");
        if (!Files.isRegularFile(spawnsFile)) {
            spawnsFile = projectRoot.resolve(".data").resolve("spawns.txt");
        }
        if (!Files.isRegularFile(spawnsFile)) return;

        try (BufferedReader reader = Files.newBufferedReader(spawnsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseSpawnLine(line);
            }
        } catch (IOException ignored) {
        }
    }

    private void parseSpawnLine(String line) {
        String clean = line.trim();
        if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) return;

        // Simple format: [name/id] [plane] [x] [y] [wanderRadius]
        String[] parts = clean.split("\\s+");
        if (parts.length >= 4) {
            try {
                int id = Integer.parseInt(parts[0]);
                int plane = Integer.parseInt(parts[1]);
                int x = Integer.parseInt(parts[2]);
                int y = Integer.parseInt(parts[3]);
                int wander = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;

                spawns.add(new NpcSpawn(id, "npc." + id, new TileCoordinate(plane, x, y), wander, 0, "spawns", "spawns.txt"));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public void addSpawn(NpcSpawn spawn) {
        Objects.requireNonNull(spawn, "spawn");
        spawns.add(spawn);
    }

    @Override
    public String id() {
        return "openrune.spawns";
    }

    @Override
    public List<NpcSpawn> spawns(int plane, int minX, int minY, int maxX, int maxY) {
        List<NpcSpawn> inArea = new ArrayList<>();
        for (NpcSpawn s : spawns) {
            TileCoordinate c = s.coordinate();
            if (c.plane() == plane && c.x() >= minX && c.x() <= maxX && c.y() >= minY && c.y() <= maxY) {
                inArea.add(s);
            }
        }
        return Collections.unmodifiableList(inArea);
    }

    @Override
    public int totalSpawnCount() {
        return spawns.size();
    }
}
