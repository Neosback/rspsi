package com.rspsi.studio.project;

import com.rspsi.cache.workspace.CacheLoadPhase;
import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.OsrsCacheSessionService;
import com.rspsi.editor.integration.IntegrationCapability;
import com.rspsi.editor.integration.IntegrationOptions;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.server.ServerConnection;
import com.rspsi.server.ServerIntegrationStatus;
import com.rspsi.server.ServerPathKey;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Project-level loading gate above cache and server-integration services.
 *
 * <p>OpenRune inspection/indexing runs only after the user explicitly opens a project. Passive
 * launcher rendering never executes Gradle or decodes a cache.</p>
 */
public final class StudioProjectLoadService implements AutoCloseable {
    private final OsrsCacheSessionService cacheSessions;
    private final ServerIntegrationService integrations;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "studio-project-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong sequence = new AtomicLong();
    private volatile ProjectLoadStatus status = ProjectLoadStatus.idle();
    private volatile CompletableFuture<ProjectOpenResult> active;
    private volatile boolean closed;

    public StudioProjectLoadService(
            OsrsCacheSessionService cacheSessions,
            ServerIntegrationService integrations) {
        this.cacheSessions = Objects.requireNonNull(cacheSessions, "cacheSessions");
        this.integrations = Objects.requireNonNull(integrations, "integrations");
    }

    public ProjectLoadStatus status() {
        return status;
    }

    public CompletionStage<ProjectOpenResult> open(
            StudioProjectDescriptor project,
            Consumer<LoadedOsrsCacheSession> cacheInitializer) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(cacheInitializer, "cacheInitializer");
        if (closed) throw new IllegalStateException("Project loader is closed");

        long request = sequence.incrementAndGet();
        CompletableFuture<ProjectOpenResult> previous = active;
        if (previous != null && !previous.isDone()) previous.cancel(true);

        integrations.disconnect();
        cacheSessions.clear();
        EnumMap<ProjectLoadStep, ProjectLoadCheck> checks = initialChecks(project);
        publish(request, ProjectLoadState.LOADING, project, checks,
                "Preparing " + project.name(), null);

        active = CompletableFuture.supplyAsync(
                () -> load(request, project, cacheInitializer, checks), executor);
        active = active.handle((result, failure) -> {
            if (request != sequence.get()) {
                throw new java.util.concurrent.CancellationException("Project load superseded");
            }
            if (failure != null) {
                Throwable cause = unwrap(failure);
                failCurrent(request, project, checks, cause);
                throw new java.util.concurrent.CompletionException(cause);
            }
            return result;
        });
        return active;
    }

    public void cancel() {
        long request = sequence.incrementAndGet();
        CompletableFuture<ProjectOpenResult> future = active;
        if (future != null && !future.isDone()) future.cancel(true);
        active = null;
        integrations.disconnect();
        cacheSessions.clear();
        status = new ProjectLoadStatus(
                ProjectLoadState.CANCELLED,
                status.project(),
                status.checks(),
                "Project loading cancelled",
                null);
    }

    private ProjectOpenResult load(
            long request,
            StudioProjectDescriptor project,
            Consumer<LoadedOsrsCacheSession> cacheInitializer,
            EnumMap<ProjectLoadStep, ProjectLoadCheck> checks) {
        mark(request, project, checks, ProjectLoadStep.READ_PROJECT,
                ProjectLoadCheckState.RUNNING, "Reading saved project descriptor");
        checkCurrent(request);
        mark(request, project, checks, ProjectLoadStep.READ_PROJECT,
                ProjectLoadCheckState.SUCCESS, project.name());

        Path source = project.source();
        mark(request, project, checks, ProjectLoadStep.VALIDATE_SOURCE,
                ProjectLoadCheckState.RUNNING, source.toString());
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("Project source directory does not exist: " + source);
        }

        if (project.kind() == StudioProjectKind.STANDALONE_OSRS_CACHE
                && !Files.isRegularFile(source.resolve("main_file_cache.dat2"))) {
            throw new IllegalArgumentException(
                    "Selected directory is not an OSRS cache (main_file_cache.dat2 is missing): "
                            + source);
        }
        mark(request, project, checks, ProjectLoadStep.VALIDATE_SOURCE,
                ProjectLoadCheckState.SUCCESS, source.toString());

        Path cachePath;
        ServerProjectInspection inspection = null;
        if (project.kind() == StudioProjectKind.OPENRUNE_SERVER) {
            mark(request, project, checks, ProjectLoadStep.INSPECT_INTEGRATION,
                    ProjectLoadCheckState.RUNNING,
                    "Evaluating Gradle modules and indexing enabled OpenRune sources");

            Set<IntegrationCapability> capabilities = enabledCapabilities(project);
            var probe = integrations.probe(source)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No server integration provider recognized: " + source));
            if (!probe.valid()) {
                throw new IllegalArgumentException("OpenRune project markers were not detected: " + source);
            }

            checkCurrent(request);
            integrations.connect(
                    ServerConnection.forRoot(source),
                    IntegrationOptions.defaults(source, capabilities));
            inspection = integrations.activeProjectInspection()
                    .orElseThrow(() -> new IllegalStateException(
                            "OpenRune integration did not expose project inspection"));

            if (inspection.status() == ServerIntegrationStatus.NOT_DETECTED
                    || inspection.status() == ServerIntegrationStatus.INCOMPATIBLE) {
                throw new IllegalArgumentException(
                        "OpenRune project inspection failed: " + inspection.status());
            }

            String detail = inspection.revision().isBlank()
                    ? "OpenRune project scanned"
                    : "OpenRune revision " + inspection.revision()
                    + " · " + inspection.content().size() + " indexed source/content entries";
            mark(request, project, checks, ProjectLoadStep.INSPECT_INTEGRATION,
                    ProjectLoadCheckState.SUCCESS, detail);

            mark(request, project, checks, ProjectLoadStep.RESOLVE_CACHE,
                    ProjectLoadCheckState.RUNNING, "Resolving OpenRune LIVE cache");
            cachePath = inspection.path(ServerPathKey.LIVE_CACHE)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "OpenRune project did not expose a LIVE cache path"));
            if (!Files.isDirectory(cachePath)) {
                throw new IllegalArgumentException(
                        "OpenRune LIVE cache directory is missing: " + cachePath);
            }
            mark(request, project, checks, ProjectLoadStep.RESOLVE_CACHE,
                    ProjectLoadCheckState.SUCCESS, cachePath.toString());
        } else {
            mark(request, project, checks, ProjectLoadStep.INSPECT_INTEGRATION,
                    ProjectLoadCheckState.SKIPPED, "Standalone cache project");
            cachePath = source;
            mark(request, project, checks, ProjectLoadStep.RESOLVE_CACHE,
                    ProjectLoadCheckState.SUCCESS, cachePath.toString());
        }

        final ServerProjectInspection finalInspection = inspection;
        mark(request, project, checks, ProjectLoadStep.OPEN_CACHE,
                ProjectLoadCheckState.RUNNING, "Opening cache filesystem");

        AutoCloseable listener = cacheSessions.addListener(cacheStatus -> {
            if (request != sequence.get() || cacheStatus.state() != CacheSessionState.LOADING) return;
            if (cacheStatus.phase() == CacheLoadPhase.OPENING_FILESYSTEM) {
                mark(request, project, checks, ProjectLoadStep.OPEN_CACHE,
                        ProjectLoadCheckState.RUNNING, cacheStatus.message());
            } else if (cacheStatus.phase() == CacheLoadPhase.PREPARING_ASSETS) {
                mark(request, project, checks, ProjectLoadStep.OPEN_CACHE,
                        ProjectLoadCheckState.SUCCESS, "Cache filesystem opened");
                mark(request, project, checks, ProjectLoadStep.PREPARE_CACHE,
                        ProjectLoadCheckState.RUNNING, cacheStatus.message());
            }
        });

        final LoadedOsrsCacheSession cache;
        try {
            cache = cacheSessions.load(cachePath, cacheInitializer)
                    .toCompletableFuture().join();
        } finally {
            try {
                listener.close();
            } catch (Exception ignored) {
            }
        }
        checkCurrent(request);

        mark(request, project, checks, ProjectLoadStep.OPEN_CACHE,
                ProjectLoadCheckState.SUCCESS, cache.backendName());
        mark(request, project, checks, ProjectLoadStep.PREPARE_CACHE,
                ProjectLoadCheckState.SUCCESS,
                "Revision " + cache.identity().revision()
                        + " · " + cache.mapCount() + " map groups");

        mark(request, project, checks, ProjectLoadStep.BIND_SERVICES,
                ProjectLoadCheckState.RUNNING, "Binding project cache and integration services");
        // The cache session and OpenRune integration session are already the authoritative
        // application services at this point. This step exists to make that gate explicit.
        mark(request, project, checks, ProjectLoadStep.BIND_SERVICES,
                ProjectLoadCheckState.SUCCESS,
                finalInspection == null
                        ? "Standalone cache services ready"
                        : "OpenRune integration and cache services ready");

        mark(request, project, checks, ProjectLoadStep.READY,
                ProjectLoadCheckState.SUCCESS, "Project is ready");
        publish(request, ProjectLoadState.READY, project, checks,
                "Project loaded successfully", null);
        return new ProjectOpenResult(
                project, cachePath, cache,
                java.util.Optional.ofNullable(finalInspection));
    }

    private static EnumMap<ProjectLoadStep, ProjectLoadCheck> initialChecks(
            StudioProjectDescriptor project) {
        EnumMap<ProjectLoadStep, ProjectLoadCheck> checks =
                new EnumMap<>(ProjectLoadStep.class);
        for (ProjectLoadStep step : ProjectLoadStep.values()) {
            checks.put(step, new ProjectLoadCheck(
                    step, ProjectLoadCheckState.PENDING, ""));
        }
        if (project.kind() == StudioProjectKind.STANDALONE_OSRS_CACHE) {
            checks.put(ProjectLoadStep.INSPECT_INTEGRATION,
                    new ProjectLoadCheck(ProjectLoadStep.INSPECT_INTEGRATION,
                            ProjectLoadCheckState.SKIPPED, "Standalone cache project"));
        }
        return checks;
    }

    private static Set<IntegrationCapability> enabledCapabilities(
            StudioProjectDescriptor project) {
        EnumSet<IntegrationCapability> result =
                EnumSet.noneOf(IntegrationCapability.class);
        for (String value : project.enabledCapabilities()) {
            try {
                result.add(IntegrationCapability.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // Descriptor may contain a capability from a newer Studio version.
            }
        }
        return Set.copyOf(result);
    }

    private void mark(
            long request,
            StudioProjectDescriptor project,
            EnumMap<ProjectLoadStep, ProjectLoadCheck> checks,
            ProjectLoadStep step,
            ProjectLoadCheckState state,
            String detail) {
        if (request != sequence.get()) return;
        checks.put(step, new ProjectLoadCheck(step, state, detail));
        publish(request, ProjectLoadState.LOADING, project, checks,
                detail == null || detail.isBlank() ? step.label() : detail, null);
    }

    private void failCurrent(
            long request,
            StudioProjectDescriptor project,
            EnumMap<ProjectLoadStep, ProjectLoadCheck> checks,
            Throwable failure) {
        ProjectLoadStep current = checks.values().stream()
                .filter(check -> check.state() == ProjectLoadCheckState.RUNNING)
                .map(ProjectLoadCheck::step)
                .findFirst()
                .orElse(ProjectLoadStep.READY);
        checks.put(current, new ProjectLoadCheck(
                current, ProjectLoadCheckState.FAILED, rootMessage(failure)));
        publish(request, ProjectLoadState.FAILED, project, checks,
                rootMessage(failure), failure);
    }

    private void publish(
            long request,
            ProjectLoadState state,
            StudioProjectDescriptor project,
            EnumMap<ProjectLoadStep, ProjectLoadCheck> checks,
            String message,
            Throwable failure) {
        if (request != sequence.get()) return;
        status = new ProjectLoadStatus(
                state,
                project,
                List.copyOf(checks.values()),
                message,
                failure);
    }

    private void checkCurrent(long request) {
        if (request != sequence.get() || Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Project loading cancelled");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        cancel();
        executor.shutdownNow();
    }
}
