package com.rspsi.server.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates a connected Gradle build through its own wrapper and exports a neutral project model.
 *
 * <p>This is intentionally separate from passive project detection. Gradle configuration executes
 * build logic, so callers should use this only after the user has explicitly opened/trusted the
 * checkout. The generated init script registers one read-only reporting task; it never executes
 * server/game classes or resolves external dependency configurations.</p>
 */
public final class GradleProjectModelInspector {
    private static final String MARKER = "RSPSI_GRADLE_MODEL=";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);

    private final Duration timeout;

    public GradleProjectModelInspector() {
        this(DEFAULT_TIMEOUT);
    }

    public GradleProjectModelInspector(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public Inspection inspect(Path projectRoot) {
        Path root = Objects.requireNonNull(projectRoot, "projectRoot")
                .toAbsolutePath().normalize();
        List<String> diagnostics = new ArrayList<>();
        Path wrapper = wrapper(root);
        if (wrapper == null) {
            diagnostics.add("Gradle model unavailable: no Gradle wrapper found");
            return new Inspection(Optional.empty(), diagnostics);
        }

        Path initScript = null;
        Path output = null;
        Process process = null;
        try {
            initScript = Files.createTempFile("rspsi-gradle-model-", ".gradle");
            output = Files.createTempFile("rspsi-gradle-model-", ".log");
            Files.writeString(initScript, initScript(), StandardCharsets.UTF_8);

            List<String> command = command(wrapper, initScript);
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            process = builder.start();

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                diagnostics.add("Gradle model timed out after " + timeout.toSeconds() + " seconds");
                return new Inspection(Optional.empty(), diagnostics);
            }

            String log = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                diagnostics.add("Gradle model command exited with code " + process.exitValue());
                appendTailDiagnostic(log, diagnostics);
                return new Inspection(Optional.empty(), diagnostics);
            }

            int marker = log.lastIndexOf(MARKER);
            if (marker < 0) {
                diagnostics.add("Gradle model command completed without a Studio model payload");
                appendTailDiagnostic(log, diagnostics);
                return new Inspection(Optional.empty(), diagnostics);
            }

            int start = marker + MARKER.length();
            int end = log.indexOf('\n', start);
            String payload = (end < 0 ? log.substring(start) : log.substring(start, end)).trim();
            if (payload.isEmpty()) {
                diagnostics.add("Gradle model payload was empty");
                return new Inspection(Optional.empty(), diagnostics);
            }

            GradleProjectModel model = parse(payload);
            diagnostics.add("Gradle model loaded " + model.projects().size()
                    + " project(s) using Gradle " + model.gradleVersion());
            return new Inspection(Optional.of(model), diagnostics);
        } catch (IOException error) {
            diagnostics.add("Gradle model failed: " + message(error));
            return new Inspection(Optional.empty(), diagnostics);
        } catch (InterruptedException interrupted) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Thread.currentThread().interrupt();
            diagnostics.add("Gradle model interrupted");
            return new Inspection(Optional.empty(), diagnostics);
        } catch (RuntimeException error) {
            diagnostics.add("Gradle model payload could not be parsed: " + message(error));
            return new Inspection(Optional.empty(), diagnostics);
        } finally {
            deleteQuietly(initScript);
            deleteQuietly(output);
        }
    }

    private static GradleProjectModel parse(String payload) throws IOException {
        JsonNode root = JSON.readTree(payload);
        String rootName = text(root, "rootName", "Gradle root");
        String gradleVersion = text(root, "gradleVersion", "");

        List<GradleProjectModel.ProjectInfo> projects = new ArrayList<>();
        JsonNode values = root.path("projects");
        if (values.isArray()) {
            for (JsonNode project : values) {
                String path = text(project, "path", ":");
                String name = text(project, "name", path);
                Path projectDir = Path.of(text(project, "projectDir", "."));
                String buildFileText = text(project, "buildFile", "");
                Optional<Path> buildFile = buildFileText.isBlank()
                        ? Optional.empty() : Optional.of(Path.of(buildFileText));

                List<GradleProjectModel.SourceSetInfo> sourceSets = new ArrayList<>();
                JsonNode sourceSetValues = project.path("sourceSets");
                if (sourceSetValues.isArray()) {
                    for (JsonNode sourceSet : sourceSetValues) {
                        sourceSets.add(new GradleProjectModel.SourceSetInfo(
                                text(sourceSet, "name", "main"),
                                paths(sourceSet.path("sources")),
                                paths(sourceSet.path("resources")),
                                paths(sourceSet.path("outputs"))));
                    }
                }

                List<GradleProjectModel.TaskInfo> tasks = new ArrayList<>();
                JsonNode taskValues = project.path("tasks");
                if (taskValues.isArray()) {
                    for (JsonNode task : taskValues) {
                        tasks.add(new GradleProjectModel.TaskInfo(
                                text(task, "path", ""),
                                text(task, "name", ""),
                                text(task, "group", ""),
                                text(task, "description", "")));
                    }
                }

                projects.add(new GradleProjectModel.ProjectInfo(
                        path,
                        name,
                        projectDir,
                        buildFile,
                        sourceSets,
                        tasks,
                        strings(project.path("projectDependencies")),
                        strings(project.path("pluginClasses"))));
            }
        }
        return new GradleProjectModel(rootName, gradleVersion, projects);
    }

    private static List<Path> paths(JsonNode values) {
        List<Path> paths = new ArrayList<>();
        if (values.isArray()) {
            for (JsonNode value : values) {
                if (value.isTextual() && !value.asText().isBlank()) {
                    paths.add(Path.of(value.asText()));
                }
            }
        }
        return List.copyOf(paths);
    }

    private static Set<String> strings(JsonNode values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values.isArray()) {
            for (JsonNode value : values) {
                if (value.isTextual() && !value.asText().isBlank()) {
                    result.add(value.asText().trim());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        String result = value.asText();
        return result == null ? fallback : result;
    }

    private static List<String> command(Path wrapper, Path initScript) {
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add(wrapper.toString());
        } else if (Files.isExecutable(wrapper)) {
            command.add(wrapper.toString());
        } else {
            command.add("sh");
            command.add(wrapper.toString());
        }
        command.add("--no-daemon");
        command.add("--console=plain");
        command.add("--quiet");
        command.add("--init-script");
        command.add(initScript.toString());
        command.add("rspsiStudioModel");
        return command;
    }

    private static Path wrapper(Path root) {
        Path unix = root.resolve("gradlew");
        if (Files.isRegularFile(unix)) return unix.toAbsolutePath().normalize();
        Path windows = root.resolve("gradlew.bat");
        if (Files.isRegularFile(windows)) return windows.toAbsolutePath().normalize();
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void appendTailDiagnostic(String log, List<String> diagnostics) {
        if (log == null || log.isBlank()) return;
        String clean = log.strip();
        int start = Math.max(0, clean.length() - 600);
        diagnostics.add("Gradle output: " + clean.substring(start).replace('\n', ' '));
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    private static String initScript() {
        return """
                import groovy.json.JsonOutput
                import org.gradle.api.artifacts.ProjectDependency

                gradle.projectsLoaded {
                    def root = gradle.rootProject
                    root.tasks.register("rspsiStudioModel") {
                        group = "rspsi"
                        description = "Exports the project model consumed by OpenRune Studio."
                        doLast {
                            def projectModels = root.allprojects.sort { a, b -> a.path <=> b.path }.collect { p ->
                                def sourceSets = []
                                def sourceSetContainer = p.extensions.findByName("sourceSets")
                                if (sourceSetContainer != null) {
                                    sourceSetContainer.each { ss ->
                                        def outputs = []
                                        try {
                                            outputs.addAll(ss.output.classesDirs.files.collect { it.absolutePath })
                                            if (ss.output.resourcesDir != null) {
                                                outputs.add(ss.output.resourcesDir.absolutePath)
                                            }
                                        } catch (ignored) {
                                        }
                                        sourceSets.add([
                                            name: ss.name,
                                            sources: ss.allSource.srcDirs.collect { it.absolutePath }.unique().sort(),
                                            resources: ss.resources.srcDirs.collect { it.absolutePath }.unique().sort(),
                                            outputs: outputs.unique().sort()
                                        ])
                                    }
                                }

                                def projectDependencies = [] as Set
                                p.configurations.each { cfg ->
                                    cfg.dependencies.withType(ProjectDependency).each { dep ->
                                        try {
                                            projectDependencies.add(dep.path)
                                        } catch (ignored) {
                                            try {
                                                projectDependencies.add(dep.dependencyProject.path)
                                            } catch (ignoredLegacy) {
                                            }
                                        }
                                    }
                                }

                                def tasks = p.tasks.collect { task -> [
                                    path: task.path,
                                    name: task.name,
                                    group: task.group ?: "",
                                    description: task.description ?: ""
                                ] }.sort { a, b -> a.path <=> b.path }

                                [
                                    path: p.path,
                                    name: p.name,
                                    projectDir: p.projectDir.absolutePath,
                                    buildFile: p.buildFile != null ? p.buildFile.absolutePath : "",
                                    sourceSets: sourceSets,
                                    tasks: tasks,
                                    projectDependencies: projectDependencies.toList().sort(),
                                    pluginClasses: p.plugins.collect { it.class.name }.unique().sort()
                                ]
                            }

                            def payload = [
                                rootName: root.name,
                                gradleVersion: gradle.gradleVersion,
                                projects: projectModels
                            ]
                            println("RSPSI_GRADLE_MODEL=" + JsonOutput.toJson(payload))
                        }
                    }
                }
                """;
    }

    public record Inspection(
            Optional<GradleProjectModel> model,
            List<String> diagnostics) {

        public Inspection {
            model = model == null ? Optional.empty() : model;
            diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        }

        public boolean succeeded() {
            return model.isPresent();
        }
    }
}
