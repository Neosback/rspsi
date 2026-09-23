package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.content.ContentCapability;
import com.rspsi.editor.integration.content.ProjectLayoutResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Declarative-data layout resolver for stock OpenRune-Server.
 *
 * <p>The resolver follows the modern OpenRune cache/content build layout:
 * base client configs under {@code .data/raw-cache}, server-only overlays
 * under {@code .data/raw-cache/server}, plugin pack configs under
 * {@code content/**/src/main/resources/pack/configs}, and symbolic mappings
 * from both generated RSCM files and per-plugin {@code gamevals.toml}.
 * Kotlin source is never treated as a content input.</p>
 */
public final class OpenRuneProjectLayoutResolver implements ProjectLayoutResolver {
    public static final String ID = "openrune.stock";
    private static final int CONTENT_SCAN_DEPTH = 18;

    @Override public String id() { return ID; }
    @Override public int priority() { return 100; }

    @Override
    public Optional<ResolvedLayout> resolve(Path projectRoot) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) return Optional.empty();

        Path data = projectRoot.resolve(".data");
        Path rawCache = data.resolve("raw-cache");
        Path rawMap = rawCache.resolve("map");
        Path npcs = rawMap.resolve("npcs");
        Path areas = rawMap.resolve("area");
        Path serverDefinitions = rawCache.resolve("server");
        Path gamevals = data.resolve("gamevals");
        Path gamevalsBinary = data.resolve("gamevals-binary");
        Path rootGamevals = projectRoot.resolve("gamevals.toml");
        Path content = projectRoot.resolve("content");

        List<Path> packDefinitions = findPackConfigDirectories(content);
        List<Path> pluginGamevals = findPluginGamevals(content);

        boolean recognizable = Files.isDirectory(rawCache)
                || Files.isDirectory(gamevals)
                || Files.isDirectory(gamevalsBinary)
                || Files.isRegularFile(rootGamevals)
                || Files.isRegularFile(projectRoot.resolve("or-cache/build.gradle.kts"))
                || !packDefinitions.isEmpty()
                || !pluginGamevals.isEmpty();
        if (!recognizable) return Optional.empty();

        List<Path> contentRoots = new ArrayList<>();
        addDirectory(contentRoots, rawCache);
        addDirectory(contentRoots, gamevals);
        addDirectory(contentRoots, gamevalsBinary);
        addDirectory(contentRoots, content);
        packDefinitions.forEach(path -> addUnique(contentRoots, path));
        pluginGamevals.forEach(path -> addUnique(contentRoots, path));
        if (Files.isRegularFile(rootGamevals)) addUnique(contentRoots, rootGamevals);

        List<Path> manifestRoots = new ArrayList<>();
        manifestRoots.add(projectRoot);
        if (Files.isDirectory(content)) manifestRoots.add(content);

        Map<ContentCapability, List<Path>> known = new EnumMap<>(ContentCapability.class);
        putDirectory(known, ContentCapability.NPC_SPAWNS, npcs);
        putDirectory(known, ContentCapability.AREAS, areas);

        // Specific definition roots deliberately precede the broad raw-cache
        // root in ContentCapability enum order. ContentDiscoveryService
        // de-duplicates recognized files, so server/map rows keep their most
        // precise classification when the general cache-definition scan runs.
        putDirectory(known, ContentCapability.SERVER_DEFINITIONS, serverDefinitions);
        if (!packDefinitions.isEmpty()) {
            known.put(ContentCapability.PACK_DEFINITIONS, List.copyOf(packDefinitions));
        }
        putDirectory(known, ContentCapability.CACHE_DEFINITIONS, rawCache);

        List<Path> authoredGamevals = new ArrayList<>();
        if (Files.isRegularFile(rootGamevals)) authoredGamevals.add(rootGamevals);
        authoredGamevals.addAll(pluginGamevals);
        if (!authoredGamevals.isEmpty()) {
            known.put(ContentCapability.GAMEVALS, List.copyOf(authoredGamevals));
        }

        List<Path> symbolicRoots = new ArrayList<>();
        addDirectory(symbolicRoots, gamevals);
        addDirectory(symbolicRoots, gamevalsBinary);
        if (!symbolicRoots.isEmpty()) {
            known.put(ContentCapability.SYMBOLS, List.copyOf(symbolicRoots));
        }

        return Optional.of(new ResolvedLayout(ID, contentRoots, manifestRoots, known));
    }

    private static List<Path> findPackConfigDirectories(Path contentRoot) {
        if (!Files.isDirectory(contentRoot)) return List.of();
        try (Stream<Path> stream = Files.walk(contentRoot, CONTENT_SCAN_DEPTH)) {
            return stream.filter(Files::isDirectory)
                    .filter(path -> path.endsWith(
                            Path.of("src", "main", "resources", "pack", "configs")))
                    .filter(path -> !hasIgnoredSegment(contentRoot, path))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static List<Path> findPluginGamevals(Path contentRoot) {
        if (!Files.isDirectory(contentRoot)) return List.of();
        try (Stream<Path> stream = Files.walk(contentRoot, CONTENT_SCAN_DEPTH)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("gamevals.toml"))
                    .filter(path -> !hasIgnoredSegment(contentRoot, path))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static boolean hasIgnoredSegment(Path root, Path path) {
        Path relative;
        try {
            relative = root.relativize(path);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        for (Path part : relative) {
            String value = part.toString();
            if (value.equals(".git") || value.equals(".gradle")
                    || value.equals("build") || value.equals("out")
                    || value.equals("target")) {
                return true;
            }
        }
        return false;
    }

    private static void putDirectory(Map<ContentCapability, List<Path>> known,
                                     ContentCapability capability,
                                     Path path) {
        if (Files.isDirectory(path)) {
            known.put(capability, List.of(path.toAbsolutePath().normalize()));
        }
    }

    private static void addDirectory(List<Path> roots, Path path) {
        if (Files.isDirectory(path)) addUnique(roots, path);
    }

    private static void addUnique(List<Path> roots, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) roots.add(normalized);
    }
}
