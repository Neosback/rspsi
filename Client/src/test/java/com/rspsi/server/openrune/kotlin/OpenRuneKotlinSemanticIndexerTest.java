package com.rspsi.server.openrune.kotlin;

import com.rspsi.editor.integration.semantic.SemanticFactKind;
import com.rspsi.server.gradle.GradleProjectModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneKotlinSemanticIndexerTest {
    @Test
    void indexesBundledOpenRuneMiningAndQuestSources() {
        Path server = bundledServerRoot();
        Path miningProject = server.resolve("content/skills/mining");
        Path questProject = server.resolve("content/quest");
        Path miningSource = miningProject.resolve("src/main/kotlin");
        Path questSource = questProject.resolve("src/main/kotlin");

        assertTrue(Files.isDirectory(miningSource), "bundled mining source is missing");
        assertTrue(Files.isDirectory(questSource), "bundled quest source is missing");

        GradleProjectModel model = new GradleProjectModel(
                "OpenRune-Server",
                "8.14.3",
                List.of(
                        project(":content:skills:mining", "mining", miningProject, miningSource),
                        project(":content:quest", "quest", questProject, questSource)));

        var index = new OpenRuneKotlinSemanticIndexer().index(model);

        assertTrue(index.files().stream()
                .anyMatch(path -> path.getFileName().toString().equals("Mining.kt")));
        assertTrue(index.facts(SemanticFactKind.PLUGIN_SCRIPT).stream()
                .anyMatch(fact -> fact.name().equals("Mining")));
        assertTrue(index.facts(SemanticFactKind.SCRIPT_HANDLER).stream()
                .anyMatch(fact -> fact.name().equals("onOpContentLoc1")
                        && fact.arguments().contains("content.rock")));

        assertTrue(index.facts(SemanticFactKind.QUEST_SCRIPT).stream()
                .anyMatch(fact -> fact.name().equals("CooksAssistant")));
        assertTrue(index.facts(SemanticFactKind.QUEST_DEFINITION).stream()
                .anyMatch(fact -> "quest_cooksassistant".equals(
                        fact.attributes().get("questKey"))
                        && "varp.cookquest".equals(fact.attributes().get("questVar"))));

        assertFalse(index.references("obj.cake").isEmpty());
        assertFalse(index.references("varbit.runemysteries_talisman").isEmpty());
        assertTrue(index.facts(SemanticFactKind.VAR_BINDING).stream()
                .anyMatch(fact -> fact.arguments().contains("varbit.runemysteries_talisman")));
    }

    private static GradleProjectModel.ProjectInfo project(
            String path,
            String name,
            Path projectDir,
            Path sourceRoot) {
        return new GradleProjectModel.ProjectInfo(
                path,
                name,
                projectDir,
                Optional.empty(),
                List.of(new GradleProjectModel.SourceSetInfo(
                        "main", List.of(sourceRoot), List.of(), List.of())),
                List.of(),
                Set.of(),
                Set.of());
    }

    private static Path bundledServerRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path direct = cwd.resolve("OpenRune-Server-main");
        if (Files.isDirectory(direct)) return direct;

        Path parent = cwd.getParent();
        if (parent != null) {
            Path sibling = parent.resolve("OpenRune-Server-main");
            if (Files.isDirectory(sibling)) return sibling;
        }
        throw new IllegalStateException("OpenRune-Server-main is not available from " + cwd);
    }
}
