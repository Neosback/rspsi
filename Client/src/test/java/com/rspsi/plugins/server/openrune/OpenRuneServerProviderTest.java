package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.IntegrationSession;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.semantic.SemanticFactKind;
import com.rspsi.editor.symbols.SymbolNamespace;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerIntegrationStatus;
import com.rspsi.server.ServerPathKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRuneServerProviderTest {
    @Test
    void recognizesOpenRuneCheckoutWithoutStockDeclarativeLayout() throws Exception {
        Path root = Files.createTempDirectory("openrune-custom-layout");
        Files.createDirectories(root.resolve("or-cache"));
        Files.writeString(root.resolve("or-cache/build.gradle.kts"),
                "tasks.register(\"buildCache\") {}\n");
        Files.writeString(root.resolve("game.yml"), "revision: 317\n");
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n");

        OpenRuneServerProvider provider = new OpenRuneServerProvider();

        assertTrue(provider.canOpen(root));
        var probe = provider.probe(root);
        assertTrue(probe.valid());
        assertTrue(probe.supports(IntegrationCapability.CACHE_BUILD));
        assertEquals("317", probe.details().get("Revision"));
    }

    @Test
    void savedConnectionOverridesDriveSessionProvidersAndInspection() throws Exception {
        Path root = fixtureRoot();
        Path customLive = root.resolve("custom/live");
        Path customRaw = root.resolve("custom/raw-cache");
        Path customGamevals = root.resolve("custom/gamevals");
        Path customContent = root.resolve("custom/content");

        Files.createDirectories(customLive);
        Files.createDirectories(customRaw.resolve("map/npcs"));
        Files.createDirectories(customGamevals);
        Files.createDirectories(customContent.resolve("skills/mining"));

        Files.writeString(customRaw.resolve("map/npcs/test.toml"),
                "[[spawn]]\n"
                        + "npc = \"npc.miner\"\n"
                        + "coords = \"0_50_50_1_1\"\n");
        Files.writeString(customGamevals.resolve("loc.rscm"), "coal_rock=1234\n");
        Files.writeString(customContent.resolve("skills/mining/rocks.toml"),
                "target = \"loc.coal_rock\"\nreward = \"obj.coal\"\n");
        Files.writeString(customContent.resolve("skills/mining/gamevals.toml"),
                "[gamevals.obj]\ncoal = 2000\n");

        ServerConnection connection = ServerConnection.forRoot(root)
                .withPath(ServerPathKey.LIVE_CACHE, "custom/live")
                .withPath(ServerPathKey.RAW_CACHE, "custom/raw-cache")
                .withPath(ServerPathKey.GAMEVALS, "custom/gamevals")
                .withPath(ServerPathKey.CONTENT, "custom/content");

        Set<IntegrationCapability> enabled = Set.of(
                IntegrationCapability.SYMBOLS,
                IntegrationCapability.CONTENT_INDEX,
                IntegrationCapability.NPC_SPAWNS);
        IntegrationOptions options = IntegrationOptions.defaults(root, enabled);

        OpenRuneServerProvider provider = new OpenRuneServerProvider();
        var probe = provider.probe(connection);
        assertTrue(probe.supports(IntegrationCapability.SYMBOLS));
        assertTrue(probe.supports(IntegrationCapability.CONTENT_INDEX));
        assertTrue(probe.supports(IntegrationCapability.NPC_SPAWNS));

        ServerIntegrationService service = new ServerIntegrationService();
        service.registerProvider(provider);
        IntegrationSession session = service.connect(connection, options);

        assertEquals(connection, session.connection().orElseThrow());
        var inspection = session.projectInspection().orElseThrow();
        assertEquals(ServerIntegrationStatus.SUPPORTED_WITH_OVERRIDES, inspection.status());
        assertEquals(customLive.toAbsolutePath().normalize(),
                inspection.path(ServerPathKey.LIVE_CACHE).orElseThrow());
        assertEquals(customRaw.toAbsolutePath().normalize(),
                inspection.path(ServerPathKey.RAW_CACHE).orElseThrow());
        assertEquals(inspection, service.activeProjectInspection().orElseThrow());

        var symbol = session.symbolProvider().orElseThrow()
                .resolve(SymbolNamespace.LOC, "coal_rock").orElseThrow();
        assertEquals(1234, symbol.id());
        var item = session.symbolProvider().orElseThrow()
                .resolve(SymbolNamespace.ITEM, "coal").orElseThrow();
        assertEquals(2000, item.id());
        assertEquals(1, session.npcSpawnProvider().orElseThrow().totalSpawnCount());
        var references = session.referenceProvider().orElseThrow();
        assertTrue(references.totalReferenceCount() > 0);
        assertTrue(!references.referencesFor(SymbolNamespace.ITEM, -1, "item.coal").isEmpty());

        service.disconnect();
    }

    @Test
    void indexesOpenRuneKotlinHandlersQuestsVarsAndSymbolsFromGradleSourceRoots() throws Exception {
        Path root = Files.createTempDirectory("openrune-semantic");
        Path sourceRoot = root.resolve("modules/gameplay/src/customKotlin");
        Files.createDirectories(sourceRoot.resolve("example"));
        Files.createDirectories(root.resolve(".data/cache/LIVE"));
        Files.writeString(root.resolve("game.yml"), "revision: 240.2\n");

        Path mining = sourceRoot.resolve("example/Mining.kt");
        Files.writeString(mining,
                "package example\n"
                        + "class Mining : PluginScript() {\n"
                        + "  override fun ScriptContext.startup() {\n"
                        + "    onOpContentLoc1(\"content.rock\") { mine(\"obj.coal\") }\n"
                        + "  }\n"
                        + "  private fun mine(item: String) { soundSynth(\"synth.mine\") }\n"
                        + "}\n");

        Path quest = sourceRoot.resolve("example/CooksAssistant.kt");
        Files.writeString(quest,
                "package example\n"
                        + "class CooksAssistant : QuestScript(\"quest_cooksassistant\", \"varp.cookquest\", rewards {}, ItemRewardDisplay(\"obj.cake\")) {\n"
                        + "  private val done by boolVarBit(\"varbit.cook_done\")\n"
                        + "  override fun ScriptContext.init() { onOpNpc1(\"npc.cook\") {} }\n"
                        + "}\n");

        String payload = "{"
                + "\"rootName\":\"CustomOpenRune\","
                + "\"gradleVersion\":\"8.14.3\","
                + "\"projects\":[{"
                + "\"path\":\":gameplay\","
                + "\"name\":\"gameplay\","
                + "\"projectDir\":\"" + json(root.resolve("modules/gameplay")) + "\","
                + "\"buildFile\":\"" + json(root.resolve("modules/gameplay/build.gradle.kts")) + "\","
                + "\"sourceSets\":[{"
                + "\"name\":\"main\","
                + "\"sources\":[\"" + json(sourceRoot) + "\"],"
                + "\"resources\":[],"
                + "\"outputs\":[]"
                + "}],"
                + "\"tasks\":[],"
                + "\"projectDependencies\":[],"
                + "\"pluginClasses\":[]"
                + "}]}";
        Files.writeString(root.resolve("gradlew"),
                "#!/bin/sh\n"
                        + "printf '%s\\n' 'RSPSI_GRADLE_MODEL="
                        + payload.replace("'", "'\\''") + "'\n");

        OpenRuneServerProvider provider = new OpenRuneServerProvider();
        var passiveProbe = provider.probe(root);
        assertTrue(passiveProbe.supports(IntegrationCapability.SOURCE_SEMANTICS));

        IntegrationOptions options = IntegrationOptions.defaults(
                root, Set.of(IntegrationCapability.SOURCE_SEMANTICS));
        IntegrationSession session = provider.open(root, options);
        var index = session.semanticSourceIndex().orElseThrow();

        assertEquals(2, index.files().size());
        assertTrue(index.facts(SemanticFactKind.PLUGIN_SCRIPT).stream()
                .anyMatch(fact -> fact.name().equals("Mining")));
        assertTrue(index.facts(SemanticFactKind.QUEST_SCRIPT).stream()
                .anyMatch(fact -> fact.name().equals("CooksAssistant")));
        assertTrue(index.facts(SemanticFactKind.SCRIPT_HANDLER).stream()
                .anyMatch(fact -> fact.name().equals("onOpContentLoc1")
                        && fact.arguments().contains("content.rock")));
        assertTrue(index.facts(SemanticFactKind.QUEST_DEFINITION).stream()
                .anyMatch(fact -> fact.attributes().get("questKey").equals("quest_cooksassistant")
                        && fact.attributes().get("questVar").equals("varp.cookquest")));
        assertTrue(index.facts(SemanticFactKind.VAR_BINDING).stream()
                .anyMatch(fact -> fact.arguments().contains("varbit.cook_done")));
        assertTrue(!index.references("obj.coal").isEmpty());
        assertTrue(!index.references("npc.cook").isEmpty());

        var handler = index.facts(SemanticFactKind.SCRIPT_HANDLER).stream()
                .filter(fact -> fact.name().equals("onOpContentLoc1"))
                .findFirst()
                .orElseThrow();
        assertEquals(mining.toAbsolutePath().normalize(), handler.source().file());
        assertEquals(4, handler.source().startLine());
        assertTrue(handler.source().endOffset() > handler.source().startOffset());
    }

    private static String json(Path path) {
        return path.toAbsolutePath().normalize().toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static Path fixtureRoot() throws Exception {
        Path root = Files.createTempDirectory("openrune-provider");
        Files.createDirectories(root.resolve("or-cache"));
        Files.createDirectories(root.resolve(".data/cache/SERVER"));
        Files.writeString(root.resolve("or-cache/build.gradle.kts"),
                "tasks.register(\"buildCache\") {}\n");
        Files.writeString(root.resolve("game.yml"), "revision: 240.2\n");
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n");
        return root;
    }
}
