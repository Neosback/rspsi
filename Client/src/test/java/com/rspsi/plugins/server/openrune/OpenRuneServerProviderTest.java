package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.IntegrationSession;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.semantic.SemanticContentNodeKind;
import com.rspsi.editor.integration.semantic.SemanticEvidenceKind;
import com.rspsi.editor.integration.semantic.SemanticFactKind;
import com.rspsi.editor.integration.semantic.SemanticRelationKind;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.symbols.SymbolNamespace;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerIntegrationStatus;
import com.rspsi.server.ServerPathKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void lightweightStartupDefersGradleAndContentInventory() throws Exception {
        Path root = fixtureRoot();
        Files.createDirectories(root.resolve(".data/cache/LIVE"));
        Files.createDirectories(root.resolve(".data/gamevals"));
        Files.createDirectories(root.resolve("content/skills/mining"));
        Files.writeString(root.resolve(".data/gamevals/loc.rscm"), "coal_rock=1234\n");
        Files.writeString(root.resolve("content/skills/mining/Mining.kt"),
                "class Mining : PluginScript() {}\n");
        Files.writeString(root.resolve("content/skills/mining/rocks.toml"),
                "target = \"loc.coal_rock\"\n");

        IntegrationOptions options = IntegrationOptions.defaults(
                root, Set.of(IntegrationCapability.SYMBOLS, IntegrationCapability.GAMEVALS));

        OpenRuneServerProvider provider = new OpenRuneServerProvider();
        IntegrationSession session = provider.open(ServerConnection.forRoot(root), options);
        var inspection = session.projectInspection().orElseThrow();

        assertTrue(inspection.content().isEmpty(),
                "startup should not recursively inventory OpenRune content");
        assertTrue(inspection.gradleModel().isEmpty(),
                "startup should not evaluate the Gradle project model");
        assertFalse(session.activeCapabilities().contains(IntegrationCapability.CONTENT_INDEX));
        assertFalse(session.activeCapabilities().contains(IntegrationCapability.SOURCE_SEMANTICS));
        assertEquals(1234, session.symbolProvider().orElseThrow()
                .resolve(SymbolNamespace.LOC, "coal_rock").orElseThrow().id());
        session.close();
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
        ServerIntegrationService service = new ServerIntegrationService();
        service.registerProvider(provider);
        IntegrationSession session = service.connect(root, options);
        var index = service.activeSemanticSourceIndex().orElseThrow();
        assertEquals(index, session.semanticSourceIndex().orElseThrow());

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
        service.disconnect();
    }

    @Test
    void contentGraphJoinsHandlersGamevalsQuestStateAndDeclarativeReferences() throws Exception {
        Path root = Files.createTempDirectory("openrune-content-graph");
        Path project = root.resolve("modules/gameplay");
        Path sourceRoot = project.resolve("src/customKotlin");
        Path resourceRoot = project.resolve("src/customResources");
        Path gamevals = root.resolve(".data/gamevals");

        Files.createDirectories(sourceRoot.resolve("example"));
        Files.createDirectories(resourceRoot.resolve("data"));
        Files.createDirectories(gamevals);
        Files.createDirectories(root.resolve(".data/cache/LIVE"));
        Files.writeString(root.resolve("game.yml"), "revision: 240.2\n");

        Files.writeString(sourceRoot.resolve("example/Mining.kt"),
                "package example\n"
                        + "class Mining : PluginScript() {\n"
                        + "  override fun ScriptContext.startup() {\n"
                        + "    onOpContentLoc1(\"content.rock\") { mine(\"obj.coal\") }\n"
                        + "  }\n"
                        + "  private fun mine(item: String) {\n"
                        + "    useRow(\"dbrow.mining_coalrock\")\n"
                        + "    xp(\"stat.mining\", 50.0)\n"
                        + "    readParam(\"param.skill_xp\")\n"
                        + "  }\n"
                        + "}\n");
        Files.writeString(sourceRoot.resolve("example/CooksAssistant.kt"),
                "package example\n"
                        + "class CooksAssistant : QuestScript(\"quest_cooksassistant\", \"varp.cookquest\", rewards {}, ItemRewardDisplay(\"obj.cake\")) {\n"
                        + "  private val done by boolVarBit(\"varbit.cook_done\")\n"
                        + "  override fun ScriptContext.init() { onOpNpc1(\"npc.cook\") {} }\n"
                        + "}\n");
        Files.writeString(resourceRoot.resolve("data/mining.toml"),
                "rock = \"loc.coal_rock\"\n"
                        + "reward = \"obj.coal\"\n"
                        + "xp_param = \"param.skill_xp\"\n"
                        + "\n[[object]]\n"
                        + "id = \"loc.coal_rock\"\n"
                        + "inherit = \"loc.coal_rock\"\n"
                        + "contentGroup = \"content.rock\"\n"
                        + "\n[object.params]\n"
                        + "\"param.next_loc_stage\" = \"loc.depleted_rock\"\n");

        Files.writeString(gamevals.resolve("loc.rscm"),
                "coal_rock=1234\ndepleted_rock=1235\n");
        Files.writeString(gamevals.resolve("obj.rscm"), "coal=2000\ncake=2001\n");
        Files.writeString(gamevals.resolve("varp.rscm"), "cookquest=3000\n");
        Files.writeString(gamevals.resolve("varbit.rscm"), "cook_done=4000\n");
        Files.writeString(gamevals.resolve("npc.rscm"), "cook=5000\n");
        Files.writeString(gamevals.resolve("content.rscm"), "rock=6000\n");
        Files.writeString(gamevals.resolve("dbrow.rscm"), "mining_coalrock=55487\n");
        Files.writeString(gamevals.resolve("stat.rscm"), "mining=14\n");
        Files.writeString(gamevals.resolve("param.rscm"),
                "skill_xp=65493\nnext_loc_stage=65533\n");

        String payload = "{"
                + "\"rootName\":\"CustomOpenRune\","
                + "\"gradleVersion\":\"8.14.3\","
                + "\"projects\":[{"
                + "\"path\":\":gameplay\","
                + "\"name\":\"gameplay\","
                + "\"projectDir\":\"" + json(project) + "\","
                + "\"buildFile\":\"" + json(project.resolve("build.gradle.kts")) + "\","
                + "\"sourceSets\":[{"
                + "\"name\":\"main\","
                + "\"sources\":[\"" + json(sourceRoot) + "\"],"
                + "\"resources\":[\"" + json(resourceRoot) + "\"],"
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
        assertTrue(provider.probe(root).supports(IntegrationCapability.CONTENT_GRAPH));

        IntegrationOptions options = IntegrationOptions.defaults(
                root, Set.of(IntegrationCapability.CONTENT_GRAPH));
        ServerIntegrationService service = new ServerIntegrationService();
        service.registerProvider(provider);
        IntegrationSession session = service.connect(root, options);

        assertTrue(session.symbolProvider().isEmpty(),
                "graph prerequisites must not implicitly expose disabled symbol capability");
        assertTrue(session.referenceProvider().isEmpty(),
                "graph prerequisites must not implicitly expose disabled reference capability");
        assertTrue(session.semanticSourceIndex().isEmpty(),
                "graph prerequisites must not implicitly expose disabled source-semantic capability");

        var graph = service.activeSemanticContentGraph().orElseThrow();
        assertEquals(graph, session.semanticContentGraph().orElseThrow());

        var coalFromObj = graph.symbol("obj.coal").orElseThrow();
        var coalFromNeutral = graph.symbol("item.coal").orElseThrow();
        assertEquals(coalFromNeutral.id(), coalFromObj.id());
        assertEquals("2000", coalFromObj.attributes().get("numericId"));
        assertEquals("true", coalFromObj.attributes().get("resolved"));

        var rock = graph.symbol("content.rock").orElseThrow();
        assertEquals("6000", rock.attributes().get("numericId"));
        assertTrue(graph.incoming(rock.id(), SemanticRelationKind.TARGETS).stream()
                .map(edge -> graph.node(edge.from()).orElseThrow())
                .anyMatch(node -> node.kind() == SemanticContentNodeKind.HANDLER
                        && node.label().equals("onOpContentLoc1")));

        assertEquals("55487",
                graph.symbol("dbrow.mining_coalrock").orElseThrow()
                        .attributes().get("numericId"));
        assertEquals("14",
                graph.symbol("stat.mining").orElseThrow()
                        .attributes().get("numericId"));
        var skillXpParam = graph.symbol("param.skill_xp").orElseThrow();
        assertEquals("65493", skillXpParam.attributes().get("numericId"));
        assertTrue(graph.incoming(skillXpParam.id(), SemanticRelationKind.REFERENCES).stream()
                .flatMap(edge -> edge.evidence().stream())
                .anyMatch(evidence -> evidence.kind() == SemanticEvidenceKind.DECLARATIVE_REFERENCE));

        var selected = new WorldObject(1234, 10, 0, 0, 10, 10);
        var object = graph.objectDefinition(selected.id()).orElseThrow();
        assertEquals(SemanticContentNodeKind.OBJECT_DEFINITION, object.kind());
        assertEquals("loc.coal_rock", object.attributes().get("objectSymbol"));
        assertEquals("1234", object.attributes().get("numericId"));
        assertEquals("true", object.attributes().get("writableSource"));
        assertTrue(object.evidence().stream()
                .anyMatch(evidence -> evidence.kind() == SemanticEvidenceKind.DECLARATIVE_STRUCTURE));

        var identifiedLoc = graph.outgoing(object.id(), SemanticRelationKind.IDENTIFIED_BY).stream()
                .map(edge -> graph.node(edge.to()).orElseThrow())
                .findFirst()
                .orElseThrow();
        assertEquals("loc.coal_rock", identifiedLoc.key());

        var objectContentGroup = graph.outgoing(object.id(), SemanticRelationKind.CONTENT_GROUP).stream()
                .findFirst()
                .orElseThrow();
        assertEquals(rock.id(), objectContentGroup.to());
        assertTrue(objectContentGroup.evidence().stream()
                .anyMatch(evidence -> evidence.kind() == SemanticEvidenceKind.DECLARATIVE_STRUCTURE
                        && evidence.sourceSpan().isPresent()));

        assertTrue(graph.incoming(objectContentGroup.to(), SemanticRelationKind.TARGETS).stream()
                .map(edge -> graph.node(edge.from()).orElseThrow())
                .anyMatch(node -> node.kind() == SemanticContentNodeKind.HANDLER
                        && node.label().equals("onOpContentLoc1")));

        var nextStageParam = graph.symbol("param.next_loc_stage").orElseThrow();
        assertEquals("65533", nextStageParam.attributes().get("numericId"));
        assertTrue(graph.outgoing(object.id(), SemanticRelationKind.HAS_PARAM).stream()
                .anyMatch(edge -> edge.to().equals(nextStageParam.id())
                        && "loc.depleted_rock".equals(edge.attributes().get("value"))));

        var depleted = graph.symbol("loc.depleted_rock").orElseThrow();
        assertEquals("1235", depleted.attributes().get("numericId"));
        assertTrue(graph.outgoing(object.id(), SemanticRelationKind.PARAM_VALUE).stream()
                .anyMatch(edge -> edge.to().equals(depleted.id())
                        && "param.next_loc_stage".equals(edge.attributes().get("param"))));

        var quest = graph.nodes(SemanticContentNodeKind.QUEST).stream()
                .filter(node -> node.label().equals("CooksAssistant"))
                .findFirst()
                .orElseThrow();
        assertEquals("quest_cooksassistant", quest.attributes().get("questKey"));
        var questVar = graph.symbol("varp.cookquest").orElseThrow();
        assertTrue(graph.outgoing(quest.id(), SemanticRelationKind.USES_STATE).stream()
                .anyMatch(edge -> edge.to().equals(questVar.id())));

        var cookDone = graph.symbol("varbit.cook_done").orElseThrow();
        assertTrue(graph.incoming(cookDone.id(), SemanticRelationKind.BINDS_STATE).stream()
                .anyMatch(edge -> edge.from().equals(quest.id())));

        var loc = graph.symbol("loc.coal_rock").orElseThrow();
        assertEquals("1234", loc.attributes().get("numericId"));
        assertTrue(graph.incoming(loc.id(), SemanticRelationKind.REFERENCES).stream()
                .flatMap(edge -> edge.evidence().stream())
                .anyMatch(evidence -> evidence.kind() == SemanticEvidenceKind.DECLARATIVE_REFERENCE));

        assertTrue(graph.incoming(coalFromObj.id(), SemanticRelationKind.REFERENCES).stream()
                .flatMap(edge -> edge.evidence().stream())
                .anyMatch(evidence -> evidence.kind() == SemanticEvidenceKind.DECLARATIVE_REFERENCE));

        service.disconnect();
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
