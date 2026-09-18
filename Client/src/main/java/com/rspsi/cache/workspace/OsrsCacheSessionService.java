package com.rspsi.cache.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Application-level owner of the selected read-only OpenRune cache session.
 * The service is neutral and can be consumed by JavaFX, ImGui, or headless
 * tooling without importing frontend types.
 */
public final class OsrsCacheSessionService implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "openrune-cache-loader");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong requestSequence = new AtomicLong();
    private final CopyOnWriteArrayList<Consumer<CacheSessionStatus>> listeners =
            new CopyOnWriteArrayList<>();
    private volatile CacheSessionStatus status = new CacheSessionStatus(
            CacheSessionState.EMPTY, null, null, "No cache selected", null);
    private volatile boolean closed;

    public CacheSessionStatus status() {
        return status;
    }

    public Optional<LoadedOsrsCacheSession> current() {
        return status.currentSession();
    }

    public CompletionStage<LoadedOsrsCacheSession> load(Path path) {
        Objects.requireNonNull(path, "path");
        if (closed) throw new IllegalStateException("Cache session service is closed");
        Path normalized = path.toAbsolutePath().normalize();
        long request = requestSequence.incrementAndGet();
        LoadedOsrsCacheSession previous = status.currentSession().orElse(null);
        publish(new CacheSessionStatus(CacheSessionState.LOADING, normalized, previous,
                "Validating selected cache…", null, CacheLoadPhase.VALIDATING, 0.1));
        return CompletableFuture.supplyAsync(() -> {
                    publishIfCurrent(request, new CacheSessionStatus(
                            CacheSessionState.LOADING, normalized, previous,
                            "Opening cache filesystem…", null,
                            CacheLoadPhase.OPENING_FILESYSTEM, 0.35));
                    LoadedOsrsCacheSession loaded = LoadedOsrsCacheSession.open(normalized);
                    publishIfCurrent(request, new CacheSessionStatus(
                            CacheSessionState.LOADING, normalized, previous,
                            "Preparing cache assets…", null,
                            CacheLoadPhase.PREPARING_ASSETS, 0.8));
                    return loaded;
                }, executor)
                .handle((loaded, failure) -> {
                    if (request != requestSequence.get()) {
                        if (loaded != null) loaded.close();
                        throw new java.util.concurrent.CancellationException("Cache load superseded");
                    }
                    if (failure != null) {
                        Throwable cause = failure instanceof java.util.concurrent.CompletionException
                                && failure.getCause() != null ? failure.getCause() : failure;
                        publish(new CacheSessionStatus(CacheSessionState.FAILED, normalized,
                                previous, "Unable to load the selected OSRS cache", cause));
                        throw new java.util.concurrent.CompletionException(cause);
                    }
                    if (previous != null && previous != loaded) previous.close();
                    publish(new CacheSessionStatus(CacheSessionState.READY, normalized, loaded,
                            "OpenRune cache ready", null));
                    return loaded;
                });
    }

    public AutoCloseable addListener(Consumer<CacheSessionStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.accept(status);
        return () -> listeners.remove(listener);
    }

    public void clear() {
        requestSequence.incrementAndGet();
        LoadedOsrsCacheSession previous = status.currentSession().orElse(null);
        publish(new CacheSessionStatus(CacheSessionState.EMPTY, null, null,
                "No cache selected", null));
        if (previous != null) previous.close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        requestSequence.incrementAndGet();
        LoadedOsrsCacheSession previous = status.currentSession().orElse(null);
        publish(new CacheSessionStatus(CacheSessionState.EMPTY, null, null,
                "Cache session closed", null));
        if (previous != null) previous.close();
        executor.shutdownNow();
    }

    private void publish(CacheSessionStatus next) {
        status = next;
        for (Consumer<CacheSessionStatus> listener : listeners) {
            listener.accept(next);
        }
    }

    private void publishIfCurrent(long request, CacheSessionStatus next) {
        if (request == requestSequence.get()) publish(next);
    }
}
