package com.rspsi.server.openrune;

import com.rspsi.editor.integration.content.ContentCapability;
import com.rspsi.editor.integration.content.ProjectLayoutResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Declarative-data layout resolver for stock OpenRune-Server.
 *
 * <p>No Kotlin source paths are treated as content inputs.</p>
 */
public final class OpenRuneProjectLayoutResolver implements ProjectLayoutResolver {
    public static final String ID = "openrune.stock";

    @Override public String id() { return ID; }
    @Override public int priority() { return 100; }

    @Override
    public Optional<ResolvedLayout> resolve(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) return Optional.empty();

        Path data = projectRoot.resolve(".data");
        Path rawMap = data.resolve("raw-cache").resolve("map");
        Path npcs = rawMap.resolve("npcs");
        Path areas = rawMap.resolve("area");
        Path gamevals = data.resolve("gamevals");
        Path gamevalsBinary = data.resolve("gamevals-binary");

        boolean recognizable = Files.isDirectory(rawMap)
                || Files.isDirectory(gamevals)
                || Files.isDirectory(gamevalsBinary)
                || Files.isRegularFile(projectRoot.resolve("gamevals.toml"));
        if (!recognizable) return Optional.empty();

        List<Path> contentRoots = new ArrayList<>();
        if (Files.isDirectory(rawMap)) contentRoots.add(rawMap);
        if (Files.isDirectory(gamevals)) contentRoots.add(gamevals);
        if (Files.isDirectory(gamevalsBinary)) contentRoots.add(gamevalsBinary);

        List<Path> manifestRoots = new ArrayList<>();
        manifestRoots.add(projectRoot);
        Path content = projectRoot.resolve("content");
        if (Files.isDirectory(content)) manifestRoots.add(content);

        Map<ContentCapability, List<Path>> known = new EnumMap<>(ContentCapability.class);
        if (Files.isDirectory(npcs)) known.put(ContentCapability.NPC_SPAWNS, List.of(npcs));
        if (Files.isDirectory(areas)) known.put(ContentCapability.AREAS, List.of(areas));
        if (Files.isDirectory(gamevals) || Files.isDirectory(gamevalsBinary)
                || Files.isRegularFile(projectRoot.resolve("gamevals.toml"))) {
            List<Path> roots = new ArrayList<>();
            if (Files.isDirectory(gamevals)) roots.add(gamevals);
            if (Files.isDirectory(gamevalsBinary)) roots.add(gamevalsBinary);
            if (Files.isRegularFile(projectRoot.resolve("gamevals.toml"))) {
                roots.add(projectRoot.resolve("gamevals.toml"));
            }
            known.put(ContentCapability.GAMEVALS, roots);
            known.put(ContentCapability.SYMBOLS, roots);
        }

        return Optional.of(new ResolvedLayout(ID, contentRoots, manifestRoots, known));
    }
}
