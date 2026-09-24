package com.rspsi.server.gradle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Neutral snapshot of the connected server's evaluated Gradle project model.
 *
 * <p>This deliberately contains build structure only. It does not expose Gradle API types to
 * Studio and it does not resolve external dependency graphs.</p>
 */
public record GradleProjectModel(
        String rootName,
        String gradleVersion,
        List<ProjectInfo> projects) {

    public GradleProjectModel {
        rootName = requireText(rootName, "rootName");
        gradleVersion = gradleVersion == null ? "" : gradleVersion.trim();
        projects = List.copyOf(projects == null ? List.of() : projects);
    }

    public List<TaskInfo> tasks() {
        return projects.stream().flatMap(project -> project.tasks().stream()).toList();
    }

    public Set<Path> sourceDirectories() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        projects.forEach(project -> project.sourceSets().forEach(sourceSet -> {
            paths.addAll(sourceSet.sourceDirectories());
            paths.addAll(sourceSet.resourceDirectories());
        }));
        return Set.copyOf(paths);
    }

    public Optional<ProjectInfo> project(String path) {
        if (path == null) return Optional.empty();
        return projects.stream().filter(project -> project.path().equals(path)).findFirst();
    }

    public record ProjectInfo(
            String path,
            String name,
            Path projectDir,
            Optional<Path> buildFile,
            List<SourceSetInfo> sourceSets,
            List<TaskInfo> tasks,
            Set<String> projectDependencies,
            Set<String> pluginClasses) {

        public ProjectInfo {
            path = requireText(path, "path");
            name = requireText(name, "name");
            projectDir = normalize(projectDir, "projectDir");
            buildFile = buildFile == null ? Optional.empty()
                    : buildFile.map(value -> normalize(value, "buildFile"));
            sourceSets = List.copyOf(sourceSets == null ? List.of() : sourceSets);
            tasks = List.copyOf(tasks == null ? List.of() : tasks);
            projectDependencies = Set.copyOf(
                    projectDependencies == null ? Set.of() : projectDependencies);
            pluginClasses = Set.copyOf(pluginClasses == null ? Set.of() : pluginClasses);
        }

        public boolean isPackModule() {
            return name.equals("pack") || name.endsWith("-pack");
        }
    }

    public record SourceSetInfo(
            String name,
            List<Path> sourceDirectories,
            List<Path> resourceDirectories,
            List<Path> outputDirectories) {

        public SourceSetInfo {
            name = requireText(name, "name");
            sourceDirectories = normalizePaths(sourceDirectories);
            resourceDirectories = normalizePaths(resourceDirectories);
            outputDirectories = normalizePaths(outputDirectories);
        }

        private static List<Path> normalizePaths(List<Path> values) {
            if (values == null || values.isEmpty()) return List.of();
            List<Path> result = new ArrayList<>();
            for (Path value : values) {
                Path normalized = normalize(value, "source-set path");
                if (!result.contains(normalized)) result.add(normalized);
            }
            return List.copyOf(result);
        }
    }

    public record TaskInfo(
            String path,
            String name,
            String group,
            String description) {

        public TaskInfo {
            path = requireText(path, "path");
            name = requireText(name, "name");
            group = group == null ? "" : group.trim();
            description = description == null ? "" : description.trim();
        }
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
