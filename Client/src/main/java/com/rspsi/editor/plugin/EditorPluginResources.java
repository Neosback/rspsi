package com.rspsi.editor.plugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Host-owned lifecycle bucket for resources created by editor plugins.
 *
 * <p>Resources close in reverse registration order so dependent resources can
 * release before the handles they depend on. This is intentionally neutral:
 * a resource may be an event subscription, file watcher, executor, texture
 * owner, GPU handle wrapper, or external-plugin classloader adapter.</p>
 */
public final class EditorPluginResources implements AutoCloseable {
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Tracks a resource until the owning plugin host closes. */
    public <T extends AutoCloseable> T track(T resource) {
        Objects.requireNonNull(resource, "resource");
        if (closed.get()) {
            throw new IllegalStateException("Plugin resources are already closed");
        }
        resources.push(resource);
        return resource;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int size() {
        return resources.size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Throwable failure = null;
        while (!resources.isEmpty()) {
            try {
                resources.pop().close();
            } catch (RuntimeException | Error error) {
                failure = appendFailure(failure, error);
            } catch (Exception error) {
                failure = appendFailure(failure, error);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("One or more editor plugin resources failed to close", failure);
        }
    }

    private static Throwable appendFailure(Throwable current, Throwable next) {
        if (current == null) return next;
        current.addSuppressed(next);
        return current;
    }
}
