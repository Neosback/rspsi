package com.rspsi.editor.integration.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Data-only content discovery. It intentionally ignores source and bytecode
 * files. Unknown declarative files are catalogued instead of silently dropped.
 */
public final class ContentDiscoveryService {
    private static final Set<String> DECLARATIVE_EXTENSIONS =
            Set.of(".toml", ".json", ".rscm");
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".gradle", "build", "out", "target", "node_modules");

    private final ContentManifestReader manifests = new ContentManifestReader();

    public Discovery discover(Path projectRoot,
                              ProjectLayoutResolver.ResolvedLayout layout) {
        ParseDiagnostics diagnostics = new ParseDiagnostics();
        List<ContentManifest> foundManifests = new ArrayList<>();
        List<ContentArtifact> artifacts = new ArrayList<>();
        Set<Path> recognized = new HashSet<>();

        List<Path> manifestRoots = layout == null
                ? List.of(projectRoot) : layout.manifestRoots();
        for (Path root : manifestRoots) {
            if (!Files.exists(root)) continue;
            walk(root, diagnostics).filter(path ->
                            path.getFileName().toString().equalsIgnoreCase("content-manifest.toml"))
                    .forEach(path -> {
                        ContentManifest manifest = manifests.read(path, diagnostics);
                        if (manifest == null) return;
                        foundManifests.add(manifest);
                        recognized.add(path.toAbsolutePath().normalize());
                        manifest.resources().forEach((capabilityId, patterns) -> {
                            ContentCapability capability = ContentCapability.fromId(capabilityId);
                            for (String pattern : patterns) {
                                resolvePattern(manifest.source().getParent(), pattern, capability,
                                        manifest.pluginId(), artifacts, recognized, diagnostics);
                            }
                        });
                    });
        }

        if (layout != null) {
            layout.knownRoots().forEach((capability, roots) -> roots.forEach(root -> {
                if (!Files.exists(root)) return;
                walk(root, diagnostics)
                        .filter(ContentDiscoveryService::isDeclarative)
                        .forEach(path -> {
                            Path normalized = path.toAbsolutePath().normalize();
                            if (recognized.add(normalized)) {
                                artifacts.add(new ContentArtifact(path, extension(path),
                                        java.util.Optional.of(capability), true, layout.resolverId()));
                            }
                        });
            }));
        }

        List<Path> scanRoots = layout == null || layout.contentRoots().isEmpty()
                ? List.of(projectRoot) : layout.contentRoots();
        for (Path root : scanRoots) {
            if (!Files.exists(root)) continue;
            walk(root, diagnostics)
                    .filter(ContentDiscoveryService::isDeclarative)
                    .forEach(path -> {
                        Path normalized = path.toAbsolutePath().normalize();
                        if (recognized.add(normalized)) {
                            artifacts.add(ContentArtifact.unknown(path, extension(path)));
                        }
                    });
        }

        List<ContentArtifact> unknown = artifacts.stream()
                .filter(value -> !value.recognized()).toList();
        return new Discovery(foundManifests, artifacts, unknown, diagnostics.snapshot());
    }

    private static Stream<Path> walk(Path root, ParseDiagnostics diagnostics) {
        try {
            return Files.walk(root, 18)
                    .filter(path -> !hasIgnoredSegment(root, path))
                    .filter(Files::isRegularFile);
        } catch (IOException error) {
            diagnostics.warning("content.scan", root, 0, error.getMessage());
            return Stream.empty();
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
            if (IGNORED_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private static boolean isDeclarative(Path path) {
        return DECLARATIVE_EXTENSIONS.contains(extension(path));
    }

    private static String extension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static void resolvePattern(Path base, String pattern,
                                       ContentCapability capability, String providerId,
                                       List<ContentArtifact> artifacts, Set<Path> recognized,
                                       ParseDiagnostics diagnostics) {
        String normalizedPattern = pattern.replace('\\', '/');
        Path root = base;
        int wildcard = normalizedPattern.indexOf('*');
        String prefix = wildcard < 0 ? normalizedPattern : normalizedPattern.substring(0, wildcard);
        int slash = prefix.lastIndexOf('/');
        if (slash >= 0) root = base.resolve(prefix.substring(0, slash));
        if (!Files.exists(root)) {
            diagnostics.warning("content.resource.missing", base.resolve(pattern), 0,
                    "Manifest resource did not resolve");
            return;
        }
        final Path matcherBase = base;
        final java.nio.file.PathMatcher matcher =
                base.getFileSystem().getPathMatcher("glob:" + normalizedPattern);
        walk(root, diagnostics).forEach(path -> {
            Path relative;
            try {
                relative = matcherBase.relativize(path);
            } catch (IllegalArgumentException ignored) {
                return;
            }
            if (!matcher.matches(relative)) return;
            Path normalized = path.toAbsolutePath().normalize();
            if (recognized.add(normalized)) {
                artifacts.add(new ContentArtifact(path, extension(path),
                        java.util.Optional.of(capability), true, providerId));
            }
        });
    }

    public record Discovery(
            List<ContentManifest> manifests,
            List<ContentArtifact> artifacts,
            List<ContentArtifact> unrecognized,
            ParseDiagnostics.Snapshot diagnostics) {
        public Discovery {
            manifests = List.copyOf(manifests);
            artifacts = List.copyOf(artifacts);
            unrecognized = List.copyOf(unrecognized);
        }
    }
}
