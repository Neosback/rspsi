package com.rspsi.editor.plugin;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Host-owned execution service for plugin/background work.
 *
 * <p>This deliberately uses the JDK concurrency primitives rather than a
 * second event/scheduling framework. Background work is isolated from the UI
 * and render threads, delayed/debounced work uses one scheduler thread, and
 * all owned threads are daemon threads released with the plugin host.</p>
 */
public final class EditorExecutionService implements AutoCloseable {
    private static final int DEFAULT_BACKGROUND_THREADS =
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private final ExecutorService background;
    private final ScheduledExecutorService scheduler;
    private final Map<String, DebouncedTask> debounced = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public EditorExecutionService() {
        this("rspsi-plugin");
    }

    public EditorExecutionService(String threadPrefix) {
        this(threadPrefix, DEFAULT_BACKGROUND_THREADS);
    }

    EditorExecutionService(String threadPrefix, int backgroundThreads) {
        String prefix = requireName(threadPrefix);
        if (backgroundThreads <= 0) {
            throw new IllegalArgumentException("Background thread count must be positive");
        }
        background = Executors.newFixedThreadPool(
                backgroundThreads, daemonFactory(prefix + "-worker"));
        scheduler = Executors.newSingleThreadScheduledExecutor(
                daemonFactory(prefix + "-scheduler"));
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        requireOpen();
        return CompletableFuture.runAsync(task, background);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        requireOpen();
        return CompletableFuture.supplyAsync(task, background);
    }

    public ScheduledFuture<?> schedule(Duration delay, Runnable task) {
        Objects.requireNonNull(task, "task");
        long delayNanos = requireDelay(delay);
        requireOpen();
        return scheduler.schedule(task, delayNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Replaces any pending task with the same key. Useful for cache warming,
     * preview rebuilds and other work that should run once after a burst.
     */
    public ScheduledFuture<?> debounce(String key, Duration delay, Runnable task) {
        String id = requireName(key);
        Objects.requireNonNull(task, "task");
        long delayNanos = requireDelay(delay);
        requireOpen();

        DebouncedTask next = new DebouncedTask();
        DebouncedTask previous = debounced.put(id, next);
        if (previous != null) previous.cancel();

        ScheduledFuture<?> future;
        try {
            future = scheduler.schedule(() -> {
                if (debounced.get(id) != next) return;
                try {
                    task.run();
                } finally {
                    debounced.remove(id, next);
                }
            }, delayNanos, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException failure) {
            debounced.remove(id, next);
            throw failure;
        }
        next.future = future;
        return future;
    }

    public boolean cancelDebounce(String key) {
        DebouncedTask task = debounced.remove(requireName(key));
        return task != null && task.cancel();
    }

    public int pendingDebounceCount() {
        return debounced.size();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (DebouncedTask task : debounced.values()) {
            task.cancel();
        }
        debounced.clear();
        scheduler.shutdownNow();
        background.shutdownNow();
        awaitTermination(scheduler);
        awaitTermination(background);
    }

    private static void awaitTermination(java.util.concurrent.ExecutorService executor) {
        boolean interrupted = false;
        try {
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interruption) {
                interrupted = true;
                executor.shutdownNow();
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Editor execution service is closed");
    }

    private static long requireDelay(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("Delay cannot be negative");
        }
        try {
            return delay.toNanos();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Delay is too large", failure);
        }
    }

    private static String requireName(String value) {
        String name = Objects.requireNonNull(value, "name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Name cannot be empty");
        return name;
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class DebouncedTask {
        private volatile ScheduledFuture<?> future;

        private boolean cancel() {
            ScheduledFuture<?> value = future;
            return value == null || value.cancel(false);
        }
    }
}
