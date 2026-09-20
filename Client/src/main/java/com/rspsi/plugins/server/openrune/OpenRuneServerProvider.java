package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.IntegrationProbe;
import com.rspsi.editor.integration.IntegrationSession;
import com.rspsi.editor.integration.ServerIntegrationProvider;
import com.rspsi.editor.integration.content.ContentCapability;
import com.rspsi.editor.integration.npc.NpcSpawnProvider;
import com.rspsi.editor.integration.reference.ReferenceProvider;
import com.rspsi.editor.symbols.SymbolProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * OpenRune-Server integration backed exclusively by declarative artifacts.
 *
 * <p>Studio never parses Kotlin source or executes server classes. Current
 * stock OpenRune data is discovered from .data/raw-cache, gamevals and
 * content-manifest.toml sidecars.</p>
 */
public final class OpenRuneServerProvider implements ServerIntegrationProvider {

    @Override public String id() { return "server.openrune"; }
    @Override public String name() { return "OpenRune Server"; }

    @Override
    public String description() {
        return "Connects to OpenRune declarative data: manifests, GameVals, NPC spawns, areas and compatible content adapters.";
    }

    @Override
    public boolean canOpen(Path project) {
        return project != null && Files.isDirectory(project)
                && new OpenRuneProjectLayoutResolver().resolve(project).isPresent();
    }

    @Override
    public IntegrationProbe probe(Path project) {
        Objects.requireNonNull(project, "project");
        if (!canOpen(project)) return IntegrationProbe.invalid(project, id());

        OpenRuneContentCatalog catalog = new OpenRuneContentCatalog(project);
        Set<IntegrationCapability> capabilities = EnumSet.noneOf(IntegrationCapability.class);
        Map<String, String> details = new LinkedHashMap<>();
        Map<String, String> schemas = new LinkedHashMap<>();

        var discovery = catalog.discovery();
        var known = catalog.layout().knownRoots();

        if (known.containsKey(ContentCapability.GAMEVALS)) {
            capabilities.add(IntegrationCapability.SYMBOLS);
            capabilities.add(IntegrationCapability.GAMEVALS);
            details.put("GameVals", "Declarative GameVal/RSCM data found");
            schemas.put("gamevals", "unversioned");
        }
        if (known.containsKey(ContentCapability.NPC_SPAWNS)) {
            capabilities.add(IntegrationCapability.NPC_SPAWNS);
            details.put("NPC spawns", "OpenRune .data/raw-cache/map/npcs TOML found");
            schemas.put("spawns", "openrune-raw/1");
        }
        if (known.containsKey(ContentCapability.AREAS)) {
            capabilities.add(IntegrationCapability.AREAS);
            details.put("Areas", "OpenRune .data/raw-cache/map/area TOML found");
            schemas.put("areas", "openrune-raw/1");
        }

        if (!discovery.manifests().isEmpty()) {
            capabilities.add(IntegrationCapability.CONTENT_MANIFESTS);
            for (var manifest : discovery.manifests()) {
                manifest.schemaVersions().forEach(schemas::putIfAbsent);
                for (ContentCapability capability : manifest.capabilities()) {
                    addCapability(capabilities, capability);
                }
            }
            details.put("Manifests", discovery.manifests().size()
                    + " content-manifest.toml sidecar(s)");
        }

        if (!discovery.artifacts().isEmpty()) {
            capabilities.add(IntegrationCapability.CONTENT_INDEX);
            capabilities.add(IntegrationCapability.CONTENT_DIAGNOSTICS);
            capabilities.add(IntegrationCapability.LOC_REFERENCES);
            capabilities.add(IntegrationCapability.MAP_REFERENCES);
            details.put("Declarative content", discovery.artifacts().size() + " TOML/JSON artifact(s)");
        }
        if (!discovery.unrecognized().isEmpty()) {
            details.put("Unrecognized content", discovery.unrecognized().size()
                    + " declarative file(s) available in the generic inspector");
        }
        if (!discovery.diagnostics().entries().isEmpty()) {
            details.put("Diagnostics", discovery.diagnostics().entries().size()
                    + " discovery/parse diagnostic(s)");
        }

        if (Files.exists(project.resolve("build.gradle.kts"))
                || Files.exists(project.resolve("settings.gradle.kts"))) {
            capabilities.add(IntegrationCapability.CACHE_BUILD);
            details.put("Build tooling", "Gradle Kotlin DSL project detected; source is not parsed as content");
        }

        return new IntegrationProbe(project, id(), name(), true,
                capabilities, schemas, details);
    }

    private static void addCapability(Set<IntegrationCapability> target,
                                      ContentCapability capability) {
        switch (capability) {
            case NPC_SPAWNS -> target.add(IntegrationCapability.NPC_SPAWNS);
            case AREAS -> target.add(IntegrationCapability.AREAS);
            case DROP_TABLES -> target.add(IntegrationCapability.DROP_TABLES);
            case SKILL_NODES -> target.add(IntegrationCapability.SKILL_NODES);
            case GAMEVALS -> target.add(IntegrationCapability.GAMEVALS);
            case SYMBOLS -> target.add(IntegrationCapability.SYMBOLS);
            case COLLISION -> target.add(IntegrationCapability.SERVER_COLLISION);
            default -> { }
        }
    }

    @Override
    public IntegrationSession open(Path project, IntegrationOptions options) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(options, "options");

        OpenRuneSymbolProvider symbolProvider = options.isEnabled(IntegrationCapability.SYMBOLS)
                ? new OpenRuneSymbolProvider(project) : null;
        OpenRuneReferenceProvider referenceProvider =
                options.isEnabled(IntegrationCapability.CONTENT_INDEX)
                        ? new OpenRuneReferenceProvider(project) : null;
        OpenRuneNpcSpawnProvider npcSpawnProvider =
                options.isEnabled(IntegrationCapability.NPC_SPAWNS)
                        ? new OpenRuneNpcSpawnProvider(project) : null;

        return new OpenRuneSession(this, project, options.enabledCapabilities(),
                symbolProvider, referenceProvider, npcSpawnProvider);
    }

    private static final class OpenRuneSession implements IntegrationSession {
        private final ServerIntegrationProvider provider;
        private final Path projectRoot;
        private final Set<IntegrationCapability> capabilities;
        private final SymbolProvider symbolProvider;
        private final ReferenceProvider referenceProvider;
        private final NpcSpawnProvider npcSpawnProvider;

        private OpenRuneSession(ServerIntegrationProvider provider, Path projectRoot,
                               Set<IntegrationCapability> capabilities,
                               SymbolProvider symbolProvider,
                               ReferenceProvider referenceProvider,
                               NpcSpawnProvider npcSpawnProvider) {
            this.provider = provider;
            this.projectRoot = projectRoot;
            this.capabilities = Set.copyOf(capabilities);
            this.symbolProvider = symbolProvider;
            this.referenceProvider = referenceProvider;
            this.npcSpawnProvider = npcSpawnProvider;
        }

        @Override public ServerIntegrationProvider provider() { return provider; }
        @Override public Path projectRoot() { return projectRoot; }
        @Override public Set<IntegrationCapability> activeCapabilities() { return capabilities; }
        @Override public Optional<SymbolProvider> symbolProvider() { return Optional.ofNullable(symbolProvider); }
        @Override public Optional<ReferenceProvider> referenceProvider() { return Optional.ofNullable(referenceProvider); }
        @Override public Optional<NpcSpawnProvider> npcSpawnProvider() { return Optional.ofNullable(npcSpawnProvider); }
        @Override public void close() { }
    }
}
