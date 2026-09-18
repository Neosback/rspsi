package com.rspsi.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** First-party, capability-only integration for an OpenRune-Server checkout. */
public final class OpenRuneServerAdapter implements ServerAdapter {
    public static final String ID = "openrune-server";
    private static final Pattern REVISION = Pattern.compile(
            "(?m)^\\s*revision\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$");
    private static final List<String> IMPORTANT_FILES = List.of(
            "game.yml", "game.example.yml", "settings.gradle.kts", "build.gradle.kts",
            "or-cache/build.gradle.kts", "gradle.properties");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "OpenRune Server";
    }

    @Override
    public ServerDetection detect(Path root) {
        Path normalized = normalize(root);
        if (!Files.isDirectory(normalized)) {
            return ServerDetection.notMatched(normalized, "project root is not a directory");
        }
        List<String> evidence = new ArrayList<>();
        addFileEvidence(normalized, "or-cache/build.gradle.kts", evidence);
        addFileEvidence(normalized, "game.yml", evidence);
        addFileEvidence(normalized, "settings.gradle.kts", evidence);
        addFileEvidence(normalized, "build.gradle.kts", evidence);
        if (Files.isDirectory(normalized.resolve(".data"))) evidence.add(".data/");
        if (Files.isRegularFile(normalized.resolve("gradlew"))
                || Files.isRegularFile(normalized.resolve("gradlew.bat"))) {
            evidence.add("Gradle wrapper");
        }
        boolean matched = evidence.contains("or-cache/build.gradle.kts")
                || (evidence.contains("game.yml") && evidence.contains("Gradle wrapper"));
        int confidence = evidence.contains("or-cache/build.gradle.kts") ? 100
                : matched ? 70 : 0;
        String message = matched
                ? "OpenRune project markers detected"
                : "no OpenRune project markers found";
        return new ServerDetection(matched, confidence, evidence, message);
    }

    /** Inspects a connection without opening a cache or executing server code. */
    public ServerProjectInspection inspect(ServerConnection connection) {
        Objects.requireNonNull(connection, "connection");
        Path root = normalize(connection.root());
        ServerDetection detection = detect(root);
        Map<ServerPathKey, Path> paths = resolvePaths(connection);
        List<String> diagnostics = new ArrayList<>();
        ServerGitState git = readGit(root);
        String revision = readRevision(root);
        List<ServerContentEntry> content = inventoryContent(paths);
        List<ServerPluginInfo> plugins = inventoryPlugins(paths.get(ServerPathKey.RUNTIME_PLUGINS));
        List<ServerBuildTask> tasks = buildTasks(connection, root, paths, diagnostics);
        EnumSet<ServerCapability> capabilities = capabilities(paths, content, tasks);

        for (Map.Entry<ServerPathKey, Path> entry : paths.entrySet()) {
            if (!Files.exists(entry.getValue()) && isRequiredPath(entry.getKey())) {
                diagnostics.add("Missing " + entry.getKey().configName() + ": " + entry.getValue());
            }
        }
        if (!hasWrapper(root) && connection.commandOverrides().isEmpty()) {
            diagnostics.add("No Gradle wrapper found; configure command overrides to enable builds");
        }
        for (ServerPluginInfo plugin : plugins) {
            if (plugin.external() && !plugin.hasManifest()) {
                diagnostics.add("External plugin has no complete plugin.properties: " + plugin.id());
            }
        }

        String fingerprint = fingerprint(root, paths, content, plugins, git, revision);
        ServerIntegrationStatus status = status(connection, detection, revision, paths, tasks,
                fingerprint, diagnostics);
        return new ServerProjectInspection(connection, detection, status, paths, revision, git,
                content, plugins, tasks, capabilities, diagnostics, fingerprint);
    }

    public ServerProjectInspection inspect(Path root) {
        return inspect(ServerConnection.forRoot(root));
    }

    @Override
    public ServerProject project(Path root) {
        ServerProjectInspection inspection = inspect(root);
        requireDetected(root);
        Path live = inspection.path(ServerPathKey.LIVE_CACHE).orElse(normalize(root)
                .resolve(".data/cache/LIVE"));
        Path server = inspection.path(ServerPathKey.SERVER_CACHE).orElse(normalize(root)
                .resolve(".data/cache/SERVER"));
        List<Path> contentRoots = new ArrayList<>();
        inspection.path(ServerPathKey.CONTENT).ifPresent(contentRoots::add);
        inspection.path(ServerPathKey.PACKS).ifPresent(path -> {
            if (!contentRoots.contains(path)) contentRoots.add(path);
        });
        return new ServerProject(normalize(root), live, server, contentRoots,
                inspection.capabilities());
    }

    @Override
    public Optional<ServerBuildProvider> buildProvider(Path root) {
        ServerProjectInspection inspection = inspect(root);
        requireDetected(root);
        return Optional.of(() -> inspection.buildTasks());
    }

    private static Map<ServerPathKey, Path> resolvePaths(ServerConnection connection) {
        Path root = normalize(connection.root());
        Map<ServerPathKey, Path> paths = new LinkedHashMap<>();
        for (ServerPathKey key : ServerPathKey.values()) {
            String override = connection.pathOverride(key).orElse(null);
            paths.put(key, override == null ? resolveFirstExisting(root, key) : resolve(root, override));
        }
        return paths;
    }

    private static Path resolveFirstExisting(Path root, ServerPathKey key) {
        List<String> candidates = switch (key) {
            case LIVE_CACHE -> List.of(".data/cache/LIVE", ".data/cache/live", "data/cache/LIVE",
                    "cache/LIVE");
            case SERVER_CACHE -> List.of(".data/cache/SERVER", ".data/cache/server",
                    "data/cache/SERVER", "cache/SERVER");
            case RAW_CACHE -> List.of(".data/raw-cache", "data/raw-cache", "raw-cache");
            case GAMEVALS -> List.of(".data/gamevals", "data/gamevals", "gamevals");
            case GAMEVALS_BINARY -> List.of(".data/gamevals-binary", "data/gamevals-binary");
            case CONTENT -> List.of("content", "src/main/resources/content");
            case PACKS -> List.of("content", "packs");
            case RUNTIME_PLUGINS -> List.of("plugins", "server/plugins");
            case CS2 -> List.of("cs2", ".data/cs2", "data/cs2");
        };
        for (String candidate : candidates) {
            Path path = root.resolve(candidate);
            if (Files.exists(path)) return path.toAbsolutePath().normalize();
        }
        return root.resolve(candidates.get(0)).toAbsolutePath().normalize();
    }

    private static List<ServerBuildTask> buildTasks(ServerConnection connection, Path root,
                                                     Map<ServerPathKey, Path> paths,
                                                     List<String> diagnostics) {
        Path orCache = root.resolve("or-cache/build.gradle.kts");
        Path rootBuild = root.resolve("build.gradle.kts");
        if (!Files.isRegularFile(orCache) && !Files.isRegularFile(rootBuild)) return List.of();
        String rootText = readText(rootBuild);
        String orCacheText = readText(orCache);
        boolean wrapperAvailable = hasWrapper(root);
        List<ServerBuildTask> tasks = new ArrayList<>();
        addTaskIfDeclared(tasks, connection, root, paths, "build-cache", "Build Cache",
                "buildCache", ":or-cache:buildCache", ServerCapability.BUILD_CACHE,
                List.of(ServerPathKey.LIVE_CACHE, ServerPathKey.SERVER_CACHE),
                orCacheText, wrapperAvailable, diagnostics);
        addTaskIfDeclared(tasks, connection, root, paths, "fresh-cache", "Fresh Cache",
                "freshCache", ":or-cache:freshCache", ServerCapability.FRESH_CACHE,
                List.of(ServerPathKey.LIVE_CACHE, ServerPathKey.SERVER_CACHE),
                orCacheText, wrapperAvailable, diagnostics);
        addTaskIfDeclared(tasks, connection, root, paths, "clean-cs2", "Clean CS2",
                "cleanCs2", ":or-cache:cleanCs2", ServerCapability.CLEAN_CS2,
                List.of(ServerPathKey.CS2), orCacheText, wrapperAvailable, diagnostics);
        addTaskIfDeclared(tasks, connection, root, paths, "merge-plugin-gamevals", "Merge Plugin GameVals",
                "mergePluginGamevals", ":or-cache:mergePluginGamevals", ServerCapability.MERGE_GAMEVALS,
                List.of(ServerPathKey.GAMEVALS), orCacheText, wrapperAvailable, diagnostics);
        if (rootText.contains("tasks.register(\"run\")")
                || Files.isRegularFile(root.resolve("server/app/build.gradle.kts"))) {
            addTask(tasks, connection, root, paths, "run-server", "Run Server", "run",
                    ServerCapability.SERVER_LAUNCH, List.of(), wrapperAvailable, diagnostics);
        }
        return List.copyOf(tasks);
    }

    private static void addTaskIfDeclared(List<ServerBuildTask> tasks, ServerConnection connection,
                                          Path root, Map<ServerPathKey, Path> paths, String id,
                                          String label, String declaredTask, String gradleTask,
                                          ServerCapability capability, List<ServerPathKey> outputs,
                                          String buildText, boolean wrapperAvailable,
                                          List<String> diagnostics) {
        if (!buildText.contains("\"" + declaredTask + "\"")) {
            if (!connection.commandOverrides().containsKey(id)) {
                diagnostics.add("Gradle task not found in or-cache build: " + declaredTask);
                return;
            }
        }
        addTask(tasks, connection, root, paths, id, label, gradleTask, capability, outputs,
                wrapperAvailable, diagnostics);
    }

    private static void addTask(List<ServerBuildTask> tasks, ServerConnection connection, Path root,
                                Map<ServerPathKey, Path> paths, String id, String label,
                                String gradleTask, ServerCapability capability,
                                List<ServerPathKey> outputs, boolean wrapperAvailable,
                                List<String> diagnostics) {
        List<String> command = connection.commandOverride(id).orElseGet(() -> {
            if (!wrapperAvailable) return List.of();
            return List.of(wrapper(root), gradleTask);
        });
        if (command.isEmpty()) {
            diagnostics.add("Build task unavailable without a wrapper or override: " + id);
            return;
        }
        List<Path> outputPaths = outputs.stream().map(paths::get).filter(Objects::nonNull).toList();
        tasks.add(new ServerBuildTask(id, label, command, root, Set.of(capability), outputPaths));
    }

    private static EnumSet<ServerCapability> capabilities(Map<ServerPathKey, Path> paths,
                                                           List<ServerContentEntry> content,
                                                           List<ServerBuildTask> tasks) {
        EnumSet<ServerCapability> capabilities = EnumSet.noneOf(ServerCapability.class);
        capabilities.add(ServerCapability.PROJECT_LAYOUT);
        if (Files.exists(paths.get(ServerPathKey.LIVE_CACHE))
                || Files.exists(paths.get(ServerPathKey.SERVER_CACHE))) {
            capabilities.add(ServerCapability.CACHE_DISCOVERY);
        }
        if (Files.exists(paths.get(ServerPathKey.SERVER_CACHE))) {
            capabilities.add(ServerCapability.SERVER_CACHE);
        }
        if (Files.exists(paths.get(ServerPathKey.CONTENT))) {
            capabilities.add(ServerCapability.CONTENT_INVENTORY);
        }
        if (Files.exists(paths.get(ServerPathKey.GAMEVALS))) {
            capabilities.add(ServerCapability.GAMEVALS);
        }
        if (content.stream().anyMatch(entry -> entry.kind() == ServerContentKind.PACK_MODULE)) {
            capabilities.add(ServerCapability.PACK_MODULES);
        }
        capabilities.add(ServerCapability.SOURCE_STAGING);
        tasks.forEach(task -> capabilities.addAll(task.capabilities()));
        return capabilities;
    }

    private static ServerIntegrationStatus status(ServerConnection connection,
                                                  ServerDetection detection, String revision,
                                                  Map<ServerPathKey, Path> paths,
                                                  List<ServerBuildTask> tasks, String fingerprint,
                                                  List<String> diagnostics) {
        if (!detection.matched()) return ServerIntegrationStatus.NOT_DETECTED;
        if (!revision.isEmpty() && !(revision.equals("240") || revision.startsWith("240."))) {
            diagnostics.add("Detected server revision is outside the supported first-party profile: "
                    + revision);
            return ServerIntegrationStatus.INCOMPATIBLE;
        }
        if (!connection.expectedFingerprint().isEmpty()
                && !connection.expectedFingerprint().equals(fingerprint)) {
            diagnostics.add("The server project changed since this connection was saved");
            return ServerIntegrationStatus.STALE;
        }
        boolean hasLive = Files.isDirectory(paths.get(ServerPathKey.LIVE_CACHE));
        if (!hasLive || tasks.isEmpty()) return ServerIntegrationStatus.PARTIAL;
        if (!connection.pathOverrides().isEmpty() || !connection.commandOverrides().isEmpty()) {
            return ServerIntegrationStatus.SUPPORTED_WITH_OVERRIDES;
        }
        return ServerIntegrationStatus.SUPPORTED;
    }

    private static List<ServerContentEntry> inventoryContent(Map<ServerPathKey, Path> paths) {
        List<ServerContentEntry> entries = new ArrayList<>();
        Path contentRoot = paths.get(ServerPathKey.CONTENT);
        if (Files.isDirectory(contentRoot)) {
            try (Stream<Path> stream = Files.walk(contentRoot)) {
                stream.filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().endsWith("-pack")
                                || path.getFileName().toString().equals("pack"))
                        .sorted()
                        .forEach(path -> entries.add(new ServerContentEntry(path,
                                ServerContentKind.PACK_MODULE, "content", "cache pack module")));
            } catch (IOException ignored) {
                // The missing subtree is reported by path diagnostics; inventory remains partial.
            }
            inventoryFiles(contentRoot, contentRoot, entries);
        }
        Path rawCache = paths.get(ServerPathKey.RAW_CACHE);
        if (Files.isDirectory(rawCache)) inventoryFiles(rawCache, rawCache, entries);
        Path gamevals = paths.get(ServerPathKey.GAMEVALS);
        if (Files.isDirectory(gamevals)) inventoryFiles(gamevals, gamevals, entries);
        return List.copyOf(entries);
    }

    private static void inventoryFiles(Path root, Path base, List<ServerContentEntry> entries) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !containsGeneratedDirectory(root, path))
                    .sorted()
                    .forEach(path -> entries.add(new ServerContentEntry(path, classify(path, base),
                            base.toString(), path.getFileName().toString())));
        } catch (IOException ignored) {
            // Inspection is intentionally best effort.
        }
    }

    private static ServerContentKind classify(Path path, Path base) {
        String relative = base.relativize(path).toString().replace('\\', '/').toLowerCase();
        String name = path.getFileName().toString().toLowerCase();
        if (name.equals("gamevals.toml") || relative.contains("gamevals")) return ServerContentKind.GAMEVAL;
        if (relative.contains("/pack/") || relative.startsWith("pack/")) {
            if (relative.contains("/configs/") || relative.startsWith("configs/")) return ServerContentKind.CONFIG;
            if (relative.contains("/models/") || relative.startsWith("models/")) return ServerContentKind.MODEL;
            if (relative.contains("/sprites/") || relative.startsWith("sprites/")) return ServerContentKind.SPRITE;
            if (relative.contains("/textures/") || relative.startsWith("textures/")) return ServerContentKind.TEXTURE;
            if (relative.contains("/cs2/") || relative.startsWith("cs2/")) return ServerContentKind.CS2;
            if (relative.contains("/db") || relative.startsWith("db/")) return ServerContentKind.DB_TABLE;
            if (relative.contains("/interface") || relative.startsWith("interface/")) {
                return ServerContentKind.INTERFACE;
            }
        }
        if (relative.startsWith("map/") || relative.contains("/map/")) return ServerContentKind.MAP;
        if (relative.endsWith(".kt") || relative.endsWith(".java")) return ServerContentKind.SERVER_SCRIPT;
        if (name.endsWith(".cs2") || name.endsWith(".rs2asm")) return ServerContentKind.CS2;
        if (name.endsWith(".dat")) return ServerContentKind.MODEL;
        return ServerContentKind.OTHER;
    }

    private static List<ServerPluginInfo> inventoryPlugins(Path pluginRoot) {
        if (!Files.isDirectory(pluginRoot)) return List.of();
        List<ServerPluginInfo> plugins = new ArrayList<>();
        try (Stream<Path> stream = Files.list(pluginRoot)) {
            stream.filter(path -> !path.getFileName().toString().equals("plugins-state.properties"))
                    .filter(path -> Files.isDirectory(path) || path.toString().toLowerCase().endsWith(".jar"))
                    .sorted()
                    .forEach(path -> plugins.add(readPluginInfo(path)));
        } catch (IOException ignored) {
            // Metadata failures are represented by an incomplete inventory entry.
        }
        return List.copyOf(plugins);
    }

    private static ServerPluginInfo readPluginInfo(Path source) {
        String id = source.getFileName().toString();
        if (id.toLowerCase().endsWith(".jar")) id = id.substring(0, id.length() - 4);
        Properties properties = new Properties();
        try {
            if (Files.isDirectory(source)) {
                Path manifest = source.resolve("plugin.properties");
                if (Files.isRegularFile(manifest)) {
                    try (var input = Files.newInputStream(manifest)) { properties.load(input); }
                }
            } else {
                try (JarFile jar = new JarFile(source.toFile())) {
                    var entry = jar.getJarEntry("plugin.properties");
                    if (entry != null) {
                        try (var input = jar.getInputStream(entry)) { properties.load(input); }
                    }
                }
            }
        } catch (IOException ignored) {
            // Invalid plugin metadata is represented by an incomplete record.
        }
        return new ServerPluginInfo(id, source, true, properties.getProperty("name"),
                properties.getProperty("description"), properties.getProperty("revision"),
                properties.getProperty("author"));
    }

    private static ServerGitState readGit(Path root) {
        String commit = runGit(root, "rev-parse", "--short", "HEAD");
        if (commit.isEmpty()) return ServerGitState.unavailable();
        String branch = runGit(root, "symbolic-ref", "--short", "HEAD");
        boolean dirty = !runGit(root, "status", "--porcelain").isEmpty();
        return new ServerGitState(true, commit, branch, dirty);
    }

    private static String runGit(Path root, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) return "";
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            return "";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private static String readRevision(Path root) {
        for (String file : List.of("game.yml", "game.example.yml")) {
            Matcher matcher = REVISION.matcher(readText(root.resolve(file)));
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    private static String fingerprint(Path root, Map<ServerPathKey, Path> paths,
                                      List<ServerContentEntry> content,
                                      List<ServerPluginInfo> plugins, ServerGitState git,
                                      String revision) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "revision=" + revision);
            update(digest, "git=" + git.commit() + ":" + git.branch() + ":" + git.dirty());
            for (String relative : IMPORTANT_FILES) hashPath(digest, root.resolve(relative), root);
            for (Map.Entry<ServerPathKey, Path> entry : paths.entrySet()) {
                update(digest, entry.getKey().configName());
                hashTree(digest, entry.getValue(), root);
            }
            content.stream().map(ServerContentEntry::path).distinct().sorted()
                    .forEach(path -> hashPath(digest, path, root));
            plugins.stream().map(ServerPluginInfo::source).sorted()
                    .forEach(path -> hashPath(digest, path, root));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void hashTree(MessageDigest digest, Path path, Path root) {
        if (!Files.isDirectory(path)) {
            hashPath(digest, path, root);
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.filter(Files::isRegularFile).filter(file -> !containsGeneratedDirectory(path, file))
                    .sorted().forEach(file -> hashPath(digest, file, root));
        } catch (IOException ignored) {
            update(digest, "unreadable=" + displayPath(root, path));
        }
    }

    private static void hashPath(MessageDigest digest, Path path, Path root) {
        try {
            if (!Files.exists(path)) {
                update(digest, "missing=" + displayPath(root, path));
                return;
            }
            long size = Files.isRegularFile(path) ? Files.size(path) : -1L;
            long modified = Files.getLastModifiedTime(path).toMillis();
            update(digest, displayPath(root, path) + ":" + size + ":" + modified);
            if (Files.isRegularFile(path) && size >= 0 && size <= 1_048_576L) {
                digest.update(Files.readAllBytes(path));
            }
        } catch (IOException ignored) {
            update(digest, "unreadable=" + displayPath(root, path));
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static String readText(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path) : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private static boolean isRequiredPath(ServerPathKey key) {
        return key == ServerPathKey.LIVE_CACHE || key == ServerPathKey.SERVER_CACHE
                || key == ServerPathKey.RAW_CACHE || key == ServerPathKey.CONTENT;
    }

    private static boolean containsGeneratedDirectory(Path base, Path path) {
        Path relative;
        try {
            relative = base.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        for (Path part : relative) {
            if (part.toString().equals("build") || part.toString().equals(".gradle")
                    || part.toString().equals(".git")) return true;
        }
        return false;
    }

    private static String displayPath(Path root, Path path) {
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        } catch (IllegalArgumentException ignored) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static boolean hasWrapper(Path root) {
        return Files.isRegularFile(root.resolve("gradlew"))
                || Files.isRegularFile(root.resolve("gradlew.bat"));
    }

    private static String wrapper(Path root) {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat" : "./gradlew";
    }

    private static Path resolve(Path root, String value) {
        Path path = Path.of(value);
        return (path.isAbsolute() ? path : root.resolve(path)).toAbsolutePath().normalize();
    }

    private static void addFileEvidence(Path root, String relative, List<String> evidence) {
        if (Files.isRegularFile(root.resolve(relative))) evidence.add(relative);
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "root").toAbsolutePath().normalize();
    }
}
