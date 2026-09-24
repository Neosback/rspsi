package com.rspsi.editor.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Small frontend-neutral task/progress surface available to plugins. */
public final class EditorTaskService {
    private final Map<String, TaskSnapshot> tasks = new LinkedHashMap<>();

    public synchronized void begin(String id, String label) {
        String key = requireId(id);
        tasks.put(key, new TaskSnapshot(key, label, TaskState.RUNNING, 0.0, ""));
    }

    public synchronized void update(String id, double progress, String message) {
        TaskSnapshot current = requireTask(id);
        tasks.put(current.id(), new TaskSnapshot(current.id(), current.label(),
                current.state(), clamp(progress), message));
    }

    public synchronized void complete(String id, String message) {
        TaskSnapshot current = requireTask(id);
        tasks.put(current.id(), new TaskSnapshot(current.id(), current.label(),
                TaskState.COMPLETE, 1.0, message));
    }

    public synchronized void fail(String id, String message) {
        TaskSnapshot current = requireTask(id);
        tasks.put(current.id(), new TaskSnapshot(current.id(), current.label(),
                TaskState.FAILED, current.progress(), message));
    }

    public synchronized List<TaskSnapshot> snapshots() {
        return List.copyOf(new ArrayList<>(tasks.values()).stream()
                .sorted(Comparator.comparing(TaskSnapshot::id)).toList());
    }

    /**
     * Runs plugin work on the host-owned background executor while keeping the
     * public task/progress surface in sync.
     */
    public CompletableFuture<Void> runAsync(
            EditorExecutionService execution,
            String id,
            String label,
            Runnable task
    ) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(task, "task");
        begin(id, label);
        return execution.runAsync(() -> {
            try {
                task.run();
                complete(id, "");
            } catch (RuntimeException | Error failure) {
                fail(id, failureMessage(failure));
                throw failure;
            }
        });
    }

    /**
     * Value-returning variant of {@link #runAsync(EditorExecutionService, String, String, Runnable)}.
     */
    public <T> CompletableFuture<T> supplyAsync(
            EditorExecutionService execution,
            String id,
            String label,
            Supplier<T> task
    ) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(task, "task");
        begin(id, label);
        return execution.supplyAsync(() -> {
            try {
                T result = task.get();
                complete(id, "");
                return result;
            } catch (RuntimeException | Error failure) {
                fail(id, failureMessage(failure));
                throw failure;
            }
        });
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private TaskSnapshot requireTask(String id) {
        TaskSnapshot task = tasks.get(requireId(id));
        if (task == null) throw new IllegalArgumentException("Unknown editor task: " + id);
        return task;
    }

    private static String requireId(String value) {
        String id = Objects.requireNonNull(value, "task id").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Task id cannot be empty");
        return id;
    }

    private static double clamp(double progress) {
        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Task progress must be between 0 and 1");
        }
        return progress;
    }

    public enum TaskState { RUNNING, COMPLETE, FAILED }

    public record TaskSnapshot(String id, String label, TaskState state,
                               double progress, String message) {
        public TaskSnapshot {
            id = requireId(id);
            label = Objects.requireNonNull(label, "task label").trim();
            if (label.isEmpty()) throw new IllegalArgumentException("Task label cannot be empty");
            state = Objects.requireNonNull(state, "task state");
            progress = clamp(progress);
            message = message == null ? "" : message.trim();
        }
    }
}
