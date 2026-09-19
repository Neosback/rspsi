package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.IntegrationProbe;
import com.rspsi.editor.integration.IntegrationSession;
import com.rspsi.editor.integration.ServerIntegrationProvider;
import com.rspsi.editor.integration.npc.NpcSpawnProvider;
import com.rspsi.editor.integration.reference.ReferenceProvider;
import com.rspsi.editor.symbols.SymbolProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Concrete ServerIntegrationProvider connecting OpenRune Studio to an OpenRune Server repository.
 */
public final class OpenRuneServerProvider implements ServerIntegrationProvider {

    @Override
    public String id() {
        return "server.openrune";
    }

    @Override
    public String name() {
        return "OpenRune Server";
    }

    @Override
    public String description() {
        return "Connects to an OpenRune server project to load GameVals, RSCM mappings, content scripts, and NPC spawns.";
    }

    @Override
    public boolean canOpen(Path project) {
        if (project == null || !Files.isDirectory(project)) return false;
        return Files.exists(project.resolve("gamevals.toml"))
                || Files.isDirectory(project.resolve(".data").resolve("gamevals"))
                || (Files.exists(project.resolve("settings.gradle.kts")) && Files.isDirectory(project.resolve("content")));
    }

    @Override
    public IntegrationProbe probe(Path project) {
        Objects.requireNonNull(project, "project");
        if (!canOpen(project)) {
            return IntegrationProbe.invalid(project, id());
        }

        Set<IntegrationCapability> capabilities = EnumSet.noneOf(IntegrationCapability.class);
        Map<String, String> details = new HashMap<>();

        if (Files.exists(project.resolve("gamevals.toml")) || Files.isDirectory(project.resolve(".data").resolve("gamevals"))) {
            capabilities.add(IntegrationCapability.SYMBOLS);
            capabilities.add(IntegrationCapability.GAMEVALS);
            details.put("Gamevals", "Found RSCM / Gameval definition files");
        }

        if (Files.isDirectory(project.resolve("content"))) {
            capabilities.add(IntegrationCapability.CONTENT_INDEX);
            capabilities.add(IntegrationCapability.LOC_REFERENCES);
            capabilities.add(IntegrationCapability.NPC_SPAWNS);
            capabilities.add(IntegrationCapability.MAP_REFERENCES);
            capabilities.add(IntegrationCapability.SOURCE_NAVIGATION);
            details.put("Content", "Found Kotlin content modules in content/");
        }

        if (Files.exists(project.resolve("build.gradle.kts")) || Files.exists(project.resolve("settings.gradle.kts"))) {
            capabilities.add(IntegrationCapability.CACHE_BUILD);
            details.put("Build Tooling", "Gradle Kotlin DSL project structure detected");
        }

        return new IntegrationProbe(project, id(), name(), true, capabilities, details);
    }

    @Override
    public IntegrationSession open(Path project, IntegrationOptions options) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(options, "options");

        OpenRuneSymbolProvider symbolProvider = options.isEnabled(IntegrationCapability.SYMBOLS)
                ? new OpenRuneSymbolProvider(project) : null;
        OpenRuneReferenceProvider referenceProvider = options.isEnabled(IntegrationCapability.CONTENT_INDEX)
                ? new OpenRuneReferenceProvider(project) : null;
        OpenRuneNpcSpawnProvider npcSpawnProvider = options.isEnabled(IntegrationCapability.NPC_SPAWNS)
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
        @Override public void close() {}
    }
}
