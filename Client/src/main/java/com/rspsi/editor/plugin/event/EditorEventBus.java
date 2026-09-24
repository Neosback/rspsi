package com.rspsi.editor.plugin.event;

import com.rspsi.editor.plugin.EditorExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Type-safe event bus at the public plugin boundary.
 *
 * <p>Caller-thread dispatch remains the default for compatibility. Hosts can
 * additionally provide {@link EditorExecutionService} so listeners can opt in
 * to controlled background dispatch without introducing a second event-bus
 * dependency or arbitrary unmanaged threads.</p>
 */
public final class EditorEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(EditorEventBus.class);

    private final Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> listeners =
            new ConcurrentHashMap<>();
    private final EditorExecutionService execution;

    public EditorEventBus() {
        this(null);
    }

    public EditorEventBus(EditorExecutionService execution) {
        this.execution = execution;
    }

    public <E> AutoCloseable subscribe(Class<E> type, Consumer<? super E> listener) {
        return subscribe(type, Dispatch.CALLER, listener);
    }

    public <E> AutoCloseable subscribe(
            Class<E> type,
            Dispatch dispatch,
            Consumer<? super E> listener
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(dispatch, "dispatch");
        Objects.requireNonNull(listener, "listener");
        if (dispatch == Dispatch.BACKGROUND && execution == null) {
            throw new IllegalStateException(
                    "Background event dispatch requires an EditorExecutionService");
        }
        Subscription<E> subscription = new Subscription<>(listener, dispatch);
        listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                .add(subscription);
        return () -> unsubscribe(type, listener);
    }

    public <E> void unsubscribe(Class<E> type, Consumer<? super E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        List<Subscription<?>> values = listeners.get(type);
        if (values == null) return;
        values.removeIf(subscription -> subscription.listener() == listener
                || subscription.listener().equals(listener));
        if (values.isEmpty()) listeners.remove(type, values);
    }

    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        Objects.requireNonNull(event, "event");
        List<Subscription<?>> values = listeners.get(event.getClass());
        if (values == null || values.isEmpty()) return;

        for (Subscription<?> raw : values) {
            Subscription<E> subscription = (Subscription<E>) raw;
            if (subscription.dispatch() == Dispatch.CALLER) {
                subscription.listener().accept(event);
            } else {
                execution.runAsync(() -> subscription.listener().accept(event))
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                LOGGER.error("Background editor-event listener failed for {}",
                                        event.getClass().getName(), failure);
                            }
                        });
            }
        }
    }

    public int subscriberCount(Class<?> type) {
        List<Subscription<?>> values = listeners.get(type);
        return values == null ? 0 : values.size();
    }

    public void clear() {
        listeners.clear();
    }

    public enum Dispatch {
        CALLER,
        BACKGROUND
    }

    private record Subscription<E>(
            Consumer<? super E> listener,
            Dispatch dispatch
    ) {
        private Subscription {
            listener = Objects.requireNonNull(listener, "listener");
            dispatch = Objects.requireNonNull(dispatch, "dispatch");
        }
    }
}
