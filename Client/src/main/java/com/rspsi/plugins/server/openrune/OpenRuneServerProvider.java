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
import com.rspsi.server.OpenRuneServerAdapter;
import com.rspsi.server.ServerCapability;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerContentKind;
import com.rspsi.server.ServerPathKey;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * First-party OpenRune Server integration.
 *
 * <p>The neutral {@link OpenRuneServerAdapter} is the single authority for project detection,
 * cache roles, content/source inventory, path overrides, build tasks, fingerprints and runtime
 * plugin inventory. Format-specific providers layer GameVals, declarative references and spawn
 * semantics on top of that inspection instead of maintaining a second project-layout detector.</p>
 */
public final class OpenRuneServerProvider implements ServerIntegrationProvider {
    private final OpenRuneServerAdapter adapter;

    public OpenRuneServerProvider() {
        this(new OpenRuneServerAdapter());
    }

    OpenRuneServerProvider(OpenRuneServerAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    @Override public String id() { return "server.openrune"; }
    @Override public String name() { return "OpenRune Server"; }

    @Override
    public String description() {
        return "Connects an exact OpenRune checkout: LIVE/SERVER caches, source/content inventory, "
                + "GameVals, declarative content, build tasks and runtime plugin metadata.";
    }

    @Override
    public boolean canOpen(Path project) {
        return project != null && adapter.detect(project).matched();
    }

    @Override
    public IntegrationProbe probe(Path project) {
        Objects.requireNonNull(project, "project");
        return probe(ServerConnection.forRoot(project));
    }

    @Override
    public IntegrationProbe probe(ServerConnection connection) {
        Objects.requireNonNull(connection, "connection");
        ServerProjectInspection inspection = adapter.inspect(connection);
        return probe(connection, inspection);
    }

    private IntegrationProbe probe(ServerConnection connection, ServerProjectInspection inspection) {
        Path project = connection.root();
        if (!inspection.detection().matched()) {
            return IntegrationProbe.invalid(project, id());
        }

        Set<IntegrationCapability> capabilities = EnumSet.noneOf(IntegrationCapability.class);
        Map<String, String> details = new LinkedHashMap<>();
        Map<String, String> schemas = new LinkedHashMap<>();

        details.put("Project status", inspection.status().name());
        if (!inspection.revision().isBlank()) {
            details.put("Revision", inspection.revision());
        }
        details.put("Fingerprint", inspection.fingerprint().substring(
                0, Math.min(12, inspection.fingerprint().length())));
        inspection.path(ServerPathKey.LIVE_CACHE)
                .ifPresent(path -> details.put("LIVE cache", display(project, path)));
        inspection.path(ServerPathKey.SERVER_CACHE)
                .ifPresent(path -> details.put("SERVER cache", display(project, path)));

        if (inspection.supports(ServerCapability.GAMEVALS)) {
            capabilities.add(IntegrationCapability.SYMBOLS);
            capabilities.add(IntegrationCapability.GAMEVALS);
            details.put("GameVals", "Resolved through the inspected project GameVal roots");
            schemas.put("gamevals", "unversioned");
        }

        if (inspection.supports(ServerCapability.BUILD_CACHE)) {
            capabilities.add(IntegrationCapability.CACHE_BUILD);
            details.put("Build tooling", inspection.buildTasks().size()
                    + " inspected project task(s)");
        }

        if (inspection.content().stream()
                .anyMatch(entry -> entry.kind() == ServerContentKind.SERVER_SCRIPT)) {
            capabilities.add(IntegrationCapability.SOURCE_NAVIGATION);
            details.put("Server source", "Kotlin/Java source inventory available");
        }

        if (inspection.content().stream()
                .anyMatch(entry -> entry.kind() == ServerContentKind.CS2)) {
            capabilities.add(IntegrationCapability.CS2_SOURCES);
            details.put("ClientScripts", "CS2/assembly source inventory available");
        }

        long declarativeCount = inspection.content().stream()
                .map(entry -> entry.path().getFileName().toString().toLowerCase())
                .filter(name -> name.endsWith(".toml") || name.endsWith(".json"))
                .count();
        if (declarativeCount > 0) {
            capabilities.add(IntegrationCapability.CONTENT_INDEX);
            capabilities.add(IntegrationCapability.CONTENT_DIAGNOSTICS);
            capabilities.add(IntegrationCapability.LOC_REFERENCES);
            capabilities.add(IntegrationCapability.MAP_REFERENCES);
            details.put("Declarative content", declarativeCount
                    + " inspected TOML/JSON artifact(s)");
        }

        inspection.path(ServerPathKey.RAW_CACHE).ifPresent(raw -> {
            Path npcs = raw.resolve("map").resolve("npcs");
            Path areas = raw.resolve("map").resolve("area");
            if (Files.isDirectory(npcs)) {
                capabilities.add(IntegrationCapability.NPC_SPAWNS);
                details.put("NPC spawns", "OpenRune raw-cache NPC spawn TOML found");
                schemas.put("spawns", "openrune-raw/1");
            }
            if (Files.isDirectory(areas)) {
                capabilities.add(IntegrationCapability.AREAS);
                details.put("Areas", "OpenRune raw-cache area TOML found");
                schemas.put("areas", "openrune-raw/1");
            }
        });

        /*
         * Content manifests and declarative schema adapters are intentionally best-effort here.
         * They enrich the authoritative project inspection; they no longer decide whether the
         * checkout is an OpenRune project.
         */
        try {
            OpenRuneContentCatalog catalog = new OpenRuneContentCatalog(project);
            var discovery = catalog.discovery();
            var known = catalog.layout().knownRoots();

            if (known.containsKey(ContentCapability.GAMEVALS)) {
                capabilities.add(IntegrationCapability.SYMBOLS);
                capabilities.add(IntegrationCapability.GAMEVALS);
            }
            if (known.containsKey(ContentCapability.NPC_SPAWNS)) {
                capabilities.add(IntegrationCapability.NPC_SPAWNS);
            }
            if (known.containsKey(ContentCapability.AREAS)) {
                capabilities.add(IntegrationCapability.AREAS);
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
                details.put("Declarative content", discovery.artifacts().size()
                        + " TOML/JSON artifact(s)");
            }
            if (!discovery.unrecognized().isEmpty()) {
                details.put("Unrecognized content", discovery.unrecognized().size()
                        + " declarative file(s) available in the generic inspector");
            }
            if (!discovery.diagnostics().entries().isEmpty()) {
                details.put("Diagnostics", discovery.diagnostics().entries().size()
                        + " discovery/parse diagnostic(s)");
            }
        } catch (RuntimeException ignored) {
            // A custom checkout can still be a valid OpenRune project without the stock data layout.
        }

        return new IntegrationProbe(project, id(), name(), true, capabilities, schemas, details);
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
        return open(ServerConnection.forRoot(project), options);
    }

    @Override
    public IntegrationSession open(ServerConnection connection, IntegrationOptions options) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(options, "options");

        ServerProjectInspection inspection = adapter.inspect(connection);
        if (!inspection.detection().matched()) {
            throw new IllegalArgumentException("OpenRune Server was not detected at: " + connection.root());
        }

        IntegrationProbe probe = probe(connection, inspection);
        Path project = connection.root();

        OpenRuneSymbolProvider symbolProvider =
                options.isEnabled(IntegrationCapability.SYMBOLS)
                        && probe.supports(IntegrationCapability.SYMBOLS)
                        ? new OpenRuneSymbolProvider(inspection) : null;
        OpenRuneReferenceProvider referenceProvider =
                options.isEnabled(IntegrationCapability.CONTENT_INDEX)
                        && probe.supports(IntegrationCapability.CONTENT_INDEX)
                        ? new OpenRuneReferenceProvider(inspection) : null;
        OpenRuneNpcSpawnProvider npcSpawnProvider =
                options.isEnabled(IntegrationCapability.NPC_SPAWNS)
                        && probe.supports(IntegrationCapability.NPC_SPAWNS)
                        ? new OpenRuneNpcSpawnProvider(inspection) : null;

        Set<IntegrationCapability> activeCapabilities =
                EnumSet.noneOf(IntegrationCapability.class);
        activeCapabilities.addAll(options.enabledCapabilities());
        activeCapabilities.retainAll(probe.detectedCapabilities());

        return new OpenRuneSession(this, connection, inspection, activeCapabilities,
                symbolProvider, referenceProvider, npcSpawnProvider);
    }

    private static String display(Path root, Path path) {
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        } catch (IllegalArgumentException ignored) {
            return path.toString();
        }
    }

    private static final class OpenRuneSession implements IntegrationSession {
        private final ServerIntegrationProvider provider;
        private final ServerConnection connection;
        private final ServerProjectInspection inspection;
        private final Set<IntegrationCapability> capabilities;
        private final SymbolProvider symbolProvider;
        private final ReferenceProvider referenceProvider;
        private final NpcSpawnProvider npcSpawnProvider;

        private OpenRuneSession(ServerIntegrationProvider provider,
                                ServerConnection connection,
                                ServerProjectInspection inspection,
                                Set<IntegrationCapability> capabilities,
                                SymbolProvider symbolProvider,
                                ReferenceProvider referenceProvider,
                                NpcSpawnProvider npcSpawnProvider) {
            this.provider = provider;
            this.connection = connection;
            this.inspection = inspection;
            this.capabilities = Set.copyOf(capabilities);
            this.symbolProvider = symbolProvider;
            this.referenceProvider = referenceProvider;
            this.npcSpawnProvider = npcSpawnProvider;
        }

        @Override public ServerIntegrationProvider provider() { return provider; }
        @Override public Path projectRoot() { return connection.root(); }
        @Override public Set<IntegrationCapability> activeCapabilities() { return capabilities; }
        @Override public Optional<ServerConnection> connection() { return Optional.of(connection); }
        @Override public Optional<ServerProjectInspection> projectInspection() {
            return Optional.of(inspection);
        }
        @Override public Optional<SymbolProvider> symbolProvider() {
            return Optional.ofNullable(symbolProvider);
        }
        @Override public Optional<ReferenceProvider> referenceProvider() {
            return Optional.ofNullable(referenceProvider);
        }
        @Override public Optional<NpcSpawnProvider> npcSpawnProvider() {
            return Optional.ofNullable(npcSpawnProvider);
        }
        @Override public void close() { }
    }
}
