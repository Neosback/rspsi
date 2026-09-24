package com.rspsi.server.gradle;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradleProjectModelInspectorTest {
    @Test
    void parsesProjectSourceSetsDependenciesAndTasksFromWrapperPayload() throws Exception {
        Path root = Files.createTempDirectory("gradle-model");
        Path module = root.resolve("modules/gameplay/mining");
        Path sources = module.resolve("custom-kotlin");
        Path resources = module.resolve("assets");
        Files.createDirectories(sources);
        Files.createDirectories(resources);

        String payload = "{"
                + "\"rootName\":\"CustomOpenRune\","
                + "\"gradleVersion\":\"8.14.3\","
                + "\"projects\":[{"
                + "\"path\":\":gameplay:mining\","
                + "\"name\":\"mining\","
                + "\"projectDir\":\"" + json(module) + "\","
                + "\"buildFile\":\"" + json(module.resolve("build.gradle.kts")) + "\","
                + "\"sourceSets\":[{"
                + "\"name\":\"main\","
                + "\"sources\":[\"" + json(sources) + "\"],"
                + "\"resources\":[\"" + json(resources) + "\"],"
                + "\"outputs\":[]"
                + "}],"
                + "\"tasks\":[{"
                + "\"path\":\":gameplay:mining:compileKotlin\","
                + "\"name\":\"compileKotlin\","
                + "\"group\":\"build\","
                + "\"description\":\"Compile Kotlin\""
                + "}],"
                + "\"projectDependencies\":[\":api\"],"
                + "\"pluginClasses\":[\"org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper\"]"
                + "}]}";

        Files.writeString(root.resolve("gradlew"),
                "#!/bin/sh\n"
                        + "printf '%s\\n' 'RSPSI_GRADLE_MODEL=" + payload.replace("'", "'\\''") + "'\n");

        var inspection = new GradleProjectModelInspector(Duration.ofSeconds(5)).inspect(root);

        assertTrue(inspection.succeeded(), inspection.diagnostics().toString());
        GradleProjectModel model = inspection.model().orElseThrow();
        assertEquals("CustomOpenRune", model.rootName());
        assertEquals("8.14.3", model.gradleVersion());
        assertEquals(1, model.projects().size());

        var project = model.project(":gameplay:mining").orElseThrow();
        assertEquals(module.toAbsolutePath().normalize(), project.projectDir());
        assertEquals(1, project.sourceSets().size());
        assertTrue(project.sourceSets().get(0).sourceDirectories()
                .contains(sources.toAbsolutePath().normalize()));
        assertTrue(project.sourceSets().get(0).resourceDirectories()
                .contains(resources.toAbsolutePath().normalize()));
        assertTrue(project.projectDependencies().contains(":api"));
        assertEquals(":gameplay:mining:compileKotlin", project.tasks().get(0).path());
    }

    @Test
    void reportsWrapperFailureWithoutInventingAModel() throws Exception {
        Path root = Files.createTempDirectory("gradle-model-failure");
        Files.writeString(root.resolve("gradlew"),
                "#!/bin/sh\n"
                        + "echo 'custom build configuration failed'\n"
                        + "exit 7\n");

        var inspection = new GradleProjectModelInspector(Duration.ofSeconds(5)).inspect(root);

        assertTrue(inspection.model().isEmpty());
        assertTrue(inspection.diagnostics().stream()
                .anyMatch(message -> message.contains("exited with code 7")));
    }

    private static String json(Path path) {
        return path.toAbsolutePath().normalize().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
