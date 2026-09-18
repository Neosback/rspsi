package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Owns plugin enable/disable decisions and rebuilds the live plugin host.
 *
 * <p>The manager keeps the full candidate list (built-ins plus any external
 * discoveries) and the persisted {@link EditorPluginStateStore}. Enabling or
 * disabling a plugin recomputes which candidates initialize: disabling a
 * plugin also disables every plugin that transitively depends on it, and
 * enabling only takes effect once all of its dependencies are enabled. The
 * active {@link EditorPluginHost} is then closed and rebuilt from scratch, so
 * contributions never linger from a disabled plugin.</p>
 *
 * <p>This class is frontend-neutral: it never touches UI objects, so a JavaFX
 * panel or a Dear ImGui window can drive the same decisions.</p>
 */
public final class EditorPluginLifecycleManager implements AutoCloseable {
    private final List<EditorPlugin> candidates;
    private final EditorPluginStateStore state;
    private final HostFactory hostFactory;
    private final List<AutoCloseable> ownedResources;
    private EditorPluginHost host;
    private RebuildResult lastRebuild;
    private boolean closed;

    /** Supplies a rebuilt host for one session/asset set. */
    public interface HostFactory {
        EditorPluginHost create(List<EditorPlugin> enabledCandidates);
    }

    /** Outcome of one enable/disable round-trip. */
    public record RebuildResult(List<String> initializedIds, List<String> skippedIds) {
        public RebuildResult {
            initializedIds = List.copyOf(initializedIds);
            skippedIds = List.copyOf(skippedIds);
        }
    }

    private EditorPluginLifecycleManager(List<EditorPlugin> candidates,
                                         EditorPluginStateStore state,
                                         HostFactory hostFactory,
                                         EditorPluginHost initialHost,
                                         RebuildResult initialResult,
                                         List<? extends AutoCloseable> ownedResources) {
        this.candidates = new ArrayList<>(candidates);
        this.state = Objects.requireNonNull(state, "state");
        this.hostFactory = Objects.requireNonNull(hostFactory, "hostFactory");
        this.ownedResources = List.copyOf(ownedResources);
        this.host = initialHost;
        this.lastRebuild = initialResult;
    }

    /**
     * Creates a manager, initializes the first host from persisted state and
     * hands it to {@code hostFactory}. Initialization failures propagate after
     * the partial host is released, matching {@link EditorPluginHost#initialize}.
     */
    public static EditorPluginLifecycleManager start(
            List<? extends EditorPlugin> candidates,
            EditorPluginStateStore state,
            EditorSession session,
            AssetRepository assets,
            EditorSceneAccess scene,
            HostFactory hostFactory) {
        return start(candidates, state, session, assets, scene, hostFactory,
                new AutoCloseable[0]);
    }

    /**
     * Creates a manager and transfers ownership of resources needed by the
     * discovered candidates, such as an external plugin classloader.
     * Resources are closed after the active host so plugin instances can
     * release classes and files while their loader is still valid.
     */
    public static EditorPluginLifecycleManager start(
            List<? extends EditorPlugin> candidates,
            EditorPluginStateStore state,
            EditorSession session,
            AssetRepository assets,
            EditorSceneAccess scene,
            HostFactory hostFactory,
            AutoCloseable... ownedResources) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(hostFactory, "hostFactory");
        Objects.requireNonNull(ownedResources, "ownedResources");
        List<AutoCloseable> resources = new ArrayList<>(ownedResources.length);
        for (AutoCloseable resource : ownedResources) {
            resources.add(Objects.requireNonNull(resource, "owned resource"));
        }
        List<EditorPlugin> snapshot = List.copyOf(candidates);
        EditorPluginHost host = null;
        try {
            host = hostFactory.create(resolveEnabled(snapshot, state, null).enabled());
            RebuildResult result = new RebuildResult(
                    host.plugins().stream().map(EditorPlugin::id).toList(),
                    skippedIds(snapshot, host));
            return new EditorPluginLifecycleManager(
                    snapshot, state, hostFactory, host, result, resources);
        } catch (RuntimeException | Error failure) {
            if (host != null) {
                try {
                    host.close();
                } catch (Throwable closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            closeResources(resources, failure);
            throw failure;
        }
    }

    /** All known candidates, enabled or not. */
    public List<EditorPlugin> candidates() {
        return List.copyOf(candidates);
    }

    /** The active host, or null after close. */
    public EditorPluginHost host() {
        return host;
    }

    /** The persisted state store backing this manager. */
    public EditorPluginStateStore state() {
        return state;
    }

    /** Result of the most recent host build. */
    public RebuildResult lastRebuild() {
        return lastRebuild;
    }

    /** Effective decision for one candidate: enabled, disabled, or cascade-disabled. */
    public PluginStatus status(String pluginId) {
        PluginDecision decision = resolveEnabled(candidates, state, null)
                .decisions().get(requireCandidate(pluginId));
        return decision == null ? PluginStatus.ENABLED : decision.status();
    }

    /** True when every candidate is enabled (no user disable, no cascade). */
    public boolean allEnabled() {
        return resolveEnabled(candidates, state, null).skippedIds().isEmpty();
    }

    /**
     * Flips one plugin's user intent and rebuilds the host. Disabling a
     * dependency cascades to its transitive dependents; enabling re-evaluates
     * dependents whose only blocker was this plugin.
     */
    public RebuildResult setEnabled(String pluginId, boolean enabled) {
        String id = requireCandidate(pluginId);
        Set<String> disabled = new LinkedHashSet<>(state.disabledIds());
        if (enabled) {
            disabled.remove(id);
        } else {
            disabled.add(id);
        }
        Resolved resolved = resolveEnabled(candidates, state, disabled);
        state.replaceDisabled(disabled);
        return rebuild(resolved);
    }

    /** Re-enables every candidate and rebuilds the host. */
    public RebuildResult enableAll() {
        state.replaceDisabled(Set.of());
        return rebuild(resolveEnabled(candidates, state, Set.of()));
    }

    /** Returns the ids that would be cascade-disabled if {@code pluginId} were disabled. */
    public List<String> dependentsOf(String pluginId) {
        String id = requireCandidate(pluginId);
        return resolveEnabled(candidates, state,
                java.util.Set.of(id)).skippedIds().stream()
                .filter(candidate -> !candidate.equals(id))
                .toList();
    }

    /** Closes the active host and owned discovery resources; state survives. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        EditorPluginHost mounted = host;
        host = null;
        Throwable failure = null;
        if (mounted != null) {
            try {
                mounted.close();
            } catch (Throwable closeFailure) {
                failure = closeFailure;
            }
        }
        for (int index = ownedResources.size() - 1; index >= 0; index--) {
            try {
                ownedResources.get(index).close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        if (failure != null) throw new IllegalStateException("Unable to close plugin lifecycle", failure);
    }

    private RebuildResult rebuild(Resolved resolved) {
        EditorPluginHost previous = host;
        EditorPluginHost next = hostFactory.create(resolved.enabled());
        RebuildResult result = new RebuildResult(
                next.plugins().stream().map(EditorPlugin::id).toList(),
                resolved.skippedIds());
        host = next;
        lastRebuild = result;
        if (previous != null) previous.close();
        return result;
    }

    private String requireCandidate(String pluginId) {
        String id = Objects.requireNonNull(pluginId, "pluginId").trim();
        boolean known = candidates.stream().anyMatch(plugin -> plugin.id().equals(id));
        if (!known) {
            throw new IllegalArgumentException("Unknown plugin candidate: " + id);
        }
        return id;
    }

    private static List<String> skippedIds(List<EditorPlugin> candidates, EditorPluginHost host) {
        Set<String> initialized = host.plugins().stream().map(EditorPlugin::id)
                .collect(java.util.stream.Collectors.toSet());
        return candidates.stream()
                .map(EditorPlugin::id)
                .filter(id -> !initialized.contains(id))
                .toList();
    }

    private static void closeResources(List<? extends AutoCloseable> resources, Throwable failure) {
        for (int index = resources.size() - 1; index >= 0; index--) {
            try {
                resources.get(index).close();
            } catch (Throwable closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static Resolved resolveEnabled(List<EditorPlugin> candidates,
                                           EditorPluginStateStore state,
                                           Set<String> proposedDisabled) {
        Function<String, Boolean> intent = proposedDisabled == null
                ? state::isEnabled
                : id -> !proposedDisabled.contains(id);
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (EditorPlugin plugin : candidates) {
            String id = plugin.id();
            dependencies.put(id, plugin.descriptor() == null
                    ? List.of() : plugin.descriptor().dependencies());
            dependents.computeIfAbsent(id, ignored -> new ArrayList<>());
        }
        for (EditorPlugin plugin : candidates) {
            for (String dependency : dependencies.get(plugin.id())) {
                if (dependents.containsKey(dependency)) {
                    dependents.get(dependency).add(plugin.id());
                }
                // Unknown dependencies are rejected by host initialization.
            }
        }

        Map<String, PluginDecision> decisions = new LinkedHashMap<>();
        List<EditorPlugin> enabled = new ArrayList<>();
        LinkedHashSet<String> skipped = new LinkedHashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (EditorPlugin plugin : candidates) {
                String id = plugin.id();
                PluginDecision current = decisions.getOrDefault(id,
                        new PluginDecision(intent.apply(id) ? PluginStatus.ENABLED : PluginStatus.DISABLED));
                if (current.status() == PluginStatus.DISABLED) {
                    if (decisions.putIfAbsent(id, current) == null) skipped.add(id);
                    continue;
                }
                String blockedBy = dependencies.get(id).stream()
                        .filter(dependency -> {
                            PluginDecision decision = decisions.get(dependency);
                            return decision != null && decision.status() != PluginStatus.ENABLED;
                        })
                        .findFirst()
                        .orElse(null);
                if (blockedBy == null) continue;
                PluginDecision cascade = new PluginDecision(PluginStatus.CASCADE_DISABLED, blockedBy);
                if (decisions.putIfAbsent(id, cascade) == null) {
                    skipped.add(id);
                    changed = true;
                }
            }
        }
        for (EditorPlugin plugin : candidates) {
            if (decisions.get(plugin.id()) == null) enabled.add(plugin);
        }
        return new Resolved(enabled, decisions, List.copyOf(skipped));
    }

    /** Why a candidate is not part of the active host. */
    public enum PluginStatus {
        /** The user has not disabled the plugin and its dependencies are met. */
        ENABLED,
        /** The user disabled this plugin directly. */
        DISABLED,
        /** A dependency is disabled; the plugin cannot initialize. */
        CASCADE_DISABLED
    }

    private record PluginDecision(PluginStatus status, String blockedBy) {
        private PluginDecision(PluginStatus status) {
            this(status, null);
        }
    }

    private record Resolved(List<EditorPlugin> enabled,
                            Map<String, PluginDecision> decisions,
                            List<String> skippedIds) {
    }
}
