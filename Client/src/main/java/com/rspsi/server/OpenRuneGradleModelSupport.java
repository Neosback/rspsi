package com.rspsi.server;

import com.rspsi.server.gradle.GradleProjectModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** OpenRune-specific interpretation of the neutral evaluated Gradle project model. */
final class OpenRuneGradleModelSupport {
    private static final List<TaskSpec> TASK_SPECS = List.of(
            new TaskSpec("build-cache", "Build Cache", "buildCache",
                    ServerCapability.BUILD_CACHE,
                    List.of(ServerPathKey.LIVE_CACHE, ServerPathKey.SERVER_CACHE),
                    List.of(":or-cache:buildCache")),
            new TaskSpec("fresh-cache", "Fresh Cache", "freshCache",
                    ServerCapability.FRESH_CACHE,
                    List.of(ServerPathKey.LIVE_CACHE, ServerPathKey.SERVER_CACHE),
                    List.of(":or-cache:freshCache")),
            new TaskSpec("clean-cs2", "Clean CS2", "cleanCs2",
                    ServerCapability.CLEAN_CS2,
                    List.of(ServerPathKey.CS2),
                    List.of(":or-cache:cleanCs2")),
            new TaskSpec("merge-plugin-gamevals", "Merge Plugin GameVals", "mergePluginGamevals",
                    ServerCapability.MERGE_GAMEVALS,
                    List.of(ServerPathKey.GAMEVALS),
                    List.of(":or-cache:mergePluginGamevals")),
            new TaskSpec("run-server", "Run Server", "run",
                    ServerCapability.SERVER_LAUNCH,
                    List.of(),
                    List.of(":run", ":server:app:run")));

    private OpenRuneGradleModelSupport() {
    }

    static List<ServerContentEntry> inventoryContent(
            GradleProjectModel model,
            List<ServerContentEntry> existing) {
        LinkedHashMap<Path, ServerContentEntry> entries = new LinkedHashMap<>();
        if (existing != null) {
            existing.forEach(entry -> entries.put(entry.path(), entry));
        }

        for (GradleProjectModel.ProjectInfo project : model.projects()) {
            if (project.isPackModule()) {
                entries.putIfAbsent(project.projectDir(),
                        new ServerContentEntry(project.projectDir(), ServerContentKind.PACK_MODULE,
                                "gradle:" + project.path(), "Gradle pack module"));
            }

            for (GradleProjectModel.SourceSetInfo sourceSet : project.sourceSets()) {
                String source = "gradle:" + project.path() + ":" + sourceSet.name();
                for (Path root : sourceSet.sourceDirectories()) {
                    inventoryRoot(root, source, entries);
                }
                for (Path root : sourceSet.resourceDirectories()) {
                    inventoryRoot(root, source, entries);
                }
            }
        }
        return List.copyOf(entries.values());
    }

    static List<ServerBuildTask> buildTasks(
            ServerConnection connection,
            Path root,
            Map<ServerPathKey, Path> paths,
            GradleProjectModel model,
            List<String> diagnostics) {
        List<ServerBuildTask> result = new ArrayList<>();
        List<GradleProjectModel.TaskInfo> allTasks = model.tasks();

        for (TaskSpec spec : TASK_SPECS) {
            List<String> override = connection.commandOverride(spec.id()).orElse(null);
            if (override != null) {
                addTask(result, root, paths, spec, override);
                continue;
            }

            List<GradleProjectModel.TaskInfo> candidates = allTasks.stream()
                    .filter(task -> task.name().equals(spec.gradleName()))
                    .sorted(Comparator.comparing(GradleProjectModel.TaskInfo::path))
                    .toList();
            GradleProjectModel.TaskInfo selected = select(spec, candidates);
            if (selected == null) {
                if (candidates.size() > 1) {
                    diagnostics.add("Gradle task is ambiguous for " + spec.id() + ": "
                            + candidates.stream().map(GradleProjectModel.TaskInfo::path).toList()
                            + "; configure a command override");
                }
                continue;
            }
            addTask(result, root, paths, spec, List.of(wrapper(root), selected.path()));
        }
        return List.copyOf(result);
    }

    private static GradleProjectModel.TaskInfo select(
            TaskSpec spec,
            List<GradleProjectModel.TaskInfo> candidates) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        for (String preferred : spec.preferredPaths()) {
            for (GradleProjectModel.TaskInfo candidate : candidates) {
                if (candidate.path().equals(preferred)) return candidate;
            }
        }
        return null;
    }

    private static void addTask(
            List<ServerBuildTask> target,
            Path root,
            Map<ServerPathKey, Path> paths,
            TaskSpec spec,
            List<String> command) {
        if (command == null || command.isEmpty()) return;
        List<Path> outputs = spec.outputs().stream()
                .map(paths::get)
                .filter(Objects::nonNull)
                .toList();
        target.add(new ServerBuildTask(spec.id(), spec.label(), command, root,
                Set.of(spec.capability()), outputs));
    }

    private static void inventoryRoot(
            Path root,
            String source,
            Map<Path, ServerContentEntry> entries) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.walk(root, 18)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !containsGeneratedSegment(root, path))
                    .sorted()
                    .forEach(path -> entries.putIfAbsent(
                            path.toAbsolutePath().normalize(),
                            new ServerContentEntry(
                                    path,
                                    classify(path, root),
                                    source,
                                    path.getFileName().toString())));
        } catch (IOException ignored) {
            // The Gradle model remains useful even if one source root cannot be traversed.
        }
    }

    private static ServerContentKind classify(Path path, Path base) {
        String relative;
        try {
            relative = base.relativize(path).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            relative = path.getFileName().toString().toLowerCase(Locale.ROOT);
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.equals("gamevals.toml") || name.endsWith(".rscm")
                || relative.contains("gamevals")) {
            return ServerContentKind.GAMEVAL;
        }
        if (relative.contains("/pack/") || relative.startsWith("pack/")) {
            if (relative.contains("/configs/") || relative.startsWith("pack/configs/")) {
                return ServerContentKind.CONFIG;
            }
            if (relative.contains("/models/") || relative.startsWith("pack/models/")) {
                return ServerContentKind.MODEL;
            }
            if (relative.contains("/sprites/") || relative.startsWith("pack/sprites/")) {
                return ServerContentKind.SPRITE;
            }
            if (relative.contains("/textures/") || relative.startsWith("pack/textures/")) {
                return ServerContentKind.TEXTURE;
            }
            if (relative.contains("/cs2/") || relative.startsWith("pack/cs2/")) {
                return ServerContentKind.CS2;
            }
            if (relative.contains("/db") || relative.startsWith("pack/db")) {
                return ServerContentKind.DB_TABLE;
            }
            if (relative.contains("/interface") || relative.startsWith("pack/interface")) {
                return ServerContentKind.INTERFACE;
            }
        }
        if (name.endsWith(".kt") || name.endsWith(".java")) {
            return ServerContentKind.SERVER_SCRIPT;
        }
        if (name.endsWith(".cs2") || name.endsWith(".rs2asm")) {
            return ServerContentKind.CS2;
        }
        if (name.endsWith(".dat")) return ServerContentKind.MODEL;
        if (name.endsWith(".toml") || name.endsWith(".json")) return ServerContentKind.CONFIG;
        return ServerContentKind.OTHER;
    }

    private static boolean containsGeneratedSegment(Path root, Path path) {
        Path relative;
        try {
            relative = root.toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        for (Path part : relative) {
            String value = part.toString();
            if (value.equals("build") || value.equals(".gradle") || value.equals(".git")
                    || value.equals("out") || value.equals("target")) {
                return true;
            }
        }
        return false;
    }

    private static String wrapper(Path root) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "gradlew.bat" : "./gradlew";
    }

    private record TaskSpec(
            String id,
            String label,
            String gradleName,
            ServerCapability capability,
            List<ServerPathKey> outputs,
            List<String> preferredPaths) {
        private TaskSpec {
            outputs = List.copyOf(outputs);
            preferredPaths = List.copyOf(preferredPaths);
        }
    }
}
