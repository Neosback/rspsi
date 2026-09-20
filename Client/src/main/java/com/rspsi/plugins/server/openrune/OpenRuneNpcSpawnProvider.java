package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.content.ParseDiagnostics;
import com.rspsi.editor.integration.npc.NpcSpawn;
import com.rspsi.editor.integration.npc.NpcSpawnProvider;
import com.rspsi.editor.model.WorldTile;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * NPC spawn provider for OpenRune declarative spawn data.
 *
 * <p>Stock OpenRune .data/raw-cache/map/npcs/*.toml is the primary input.
 * Legacy plain-text data/spawns.txt remains a data-only compatibility format.
 * Kotlin content source is never parsed.</p>
 */
public final class OpenRuneNpcSpawnProvider implements NpcSpawnProvider {
    private final Path projectRoot;
    private final List<NpcSpawn> spawns = new ArrayList<>();
    private final ParseDiagnostics diagnostics = new ParseDiagnostics();

    public OpenRuneNpcSpawnProvider(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        indexSpawns();
    }

    private void indexSpawns() {
        Path tomlRoot = projectRoot.resolve(".data").resolve("raw-cache")
                .resolve("map").resolve("npcs");
        if (Files.isDirectory(tomlRoot)) {
            try (Stream<Path> stream = Files.walk(tomlRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".toml"))
                        .sorted()
                        .forEach(this::parseToml);
            } catch (IOException error) {
                diagnostics.error("spawn.scan", tomlRoot, 0, message(error));
            }
        }

        // Compatibility for older integrations that already exported a
        // declarative flat spawn list.
        Path legacy = projectRoot.resolve("data").resolve("spawns.txt");
        if (!Files.isRegularFile(legacy)) legacy = projectRoot.resolve(".data").resolve("spawns.txt");
        if (Files.isRegularFile(legacy)) parseLegacy(legacy);
    }

    private void parseToml(Path file) {
        try {
            TomlParseResult result = Toml.parse(file);
            result.errors().forEach(error -> diagnostics.warning(
                    "spawn.toml", file,
                    error.position() == null ? 0 : error.position().line(),
                    error.getMessage()));
            if (result.hasErrors()) return;

            TomlArray values = result.getArray("spawn");
            if (values == null) {
                diagnostics.warning("spawn.missing-array", file, 0,
                        "No [[spawn]] records found");
                return;
            }

            for (int index = 0; index < values.size(); index++) {
                Object raw = values.get(index);
                if (!(raw instanceof TomlTable table)) {
                    diagnostics.skipped();
                    diagnostics.warning("spawn.type", file, 0,
                            "spawn[" + index + "] is not a table");
                    continue;
                }
                String npc = table.getString("npc");
                String coords = table.getString("coords");
                if (npc == null || npc.isBlank() || coords == null || coords.isBlank()) {
                    diagnostics.skipped();
                    diagnostics.warning("spawn.required", file, 0,
                            "spawn[" + index + "] requires npc and coords");
                    continue;
                }
                WorldTile coordinate = parseCoordinate(coords);
                if (coordinate == null) {
                    diagnostics.skipped();
                    diagnostics.warning("spawn.coords", file, 0,
                            "Unsupported OpenRune coords: " + coords);
                    continue;
                }

                Long wander = table.getLong("wander_radius");
                Long direction = table.getLong("direction");
                spawns.add(new NpcSpawn(
                        -1,
                        npc.trim(),
                        coordinate,
                        wander == null ? 0 : Math.toIntExact(wander),
                        direction == null ? 0 : Math.toIntExact(direction),
                        "raw-cache",
                        projectRoot.relativize(file).toString()));
                diagnostics.imported();

                for (String key : table.keySet()) {
                    if (!SetHolder.KNOWN_KEYS.contains(key)) {
                        diagnostics.info("spawn.unknown-key", file, 0,
                                "Preserved/ignored unknown spawn key: " + key);
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            diagnostics.error("spawn.read", file, 0, message(error));
        }
    }

    /**
     * OpenRune raw-cache coords are plane_regionX_regionY_localX_localY.
     * Convert once into an absolute world coordinate for server-content queries.
     */
    private static WorldTile parseCoordinate(String value) {
        String[] parts = value.trim().split("_");
        if (parts.length != 5) return null;
        try {
            int plane = Integer.parseInt(parts[0]);
            int regionX = Integer.parseInt(parts[1]);
            int regionY = Integer.parseInt(parts[2]);
            int localX = Integer.parseInt(parts[3]);
            int localY = Integer.parseInt(parts[4]);
            if (plane < 0 || localX < 0 || localX >= 64 || localY < 0 || localY >= 64) return null;
            return new WorldTile(plane, regionX * 64 + localX, regionY * 64 + localY);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void parseLegacy(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                parseLegacyLine(file, lineNumber, line);
            }
        } catch (IOException error) {
            diagnostics.error("spawn.legacy", file, 0, message(error));
        }
    }

    private void parseLegacyLine(Path file, int lineNumber, String line) {
        String clean = line.trim();
        if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) return;
        String[] parts = clean.split("\\s+");
        if (parts.length < 4) {
            diagnostics.skipped();
            diagnostics.warning("spawn.legacy.fields", file, lineNumber, "Expected at least four fields");
            return;
        }
        try {
            int id = Integer.parseInt(parts[0]);
            int plane = Integer.parseInt(parts[1]);
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int wander = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
            spawns.add(new NpcSpawn(id, "npc." + id,
                    new WorldTile(plane, x, y), wander, 0,
                    "spawns", projectRoot.relativize(file).toString()));
            diagnostics.imported();
        } catch (NumberFormatException error) {
            diagnostics.skipped();
            diagnostics.warning("spawn.legacy.number", file, lineNumber, error.getMessage());
        }
    }

    public ParseDiagnostics.Snapshot diagnostics() { return diagnostics.snapshot(); }

    public void addSpawn(NpcSpawn spawn) {
        spawns.add(Objects.requireNonNull(spawn, "spawn"));
    }

    @Override public String id() { return "openrune.spawns"; }

    @Override
    public List<NpcSpawn> spawns(int plane, int minX, int minY, int maxX, int maxY) {
        List<NpcSpawn> inArea = new ArrayList<>();
        for (NpcSpawn spawn : spawns) {
            WorldTile coordinate = spawn.coordinate();
            if (coordinate.plane() == plane
                    && coordinate.x() >= minX && coordinate.x() <= maxX
                    && coordinate.y() >= minY && coordinate.y() <= maxY) {
                inArea.add(spawn);
            }
        }
        return Collections.unmodifiableList(inArea);
    }

    @Override public int totalSpawnCount() { return spawns.size(); }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static final class SetHolder {
        private static final java.util.Set<String> KNOWN_KEYS =
                java.util.Set.of("npc", "coords", "wander_radius", "direction");
    }
}
