package com.rspsi.editor.plugin.event;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Type-safe synchronous event bus at the public plugin boundary. */
public final class EditorEventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners =
            new ConcurrentHashMap<>();

    public <E> AutoCloseable subscribe(Class<E> type, Consumer<? super E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                .add(listener);
        return () -> unsubscribe(type, listener);
    }

    public <E> void unsubscribe(Class<E> type, Consumer<? super E> listener) {
        List<Consumer<?>> values = listeners.get(type);
        if (values != null) values.remove(listener);
    }

    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<?> listener : listeners.getOrDefault(
                event.getClass(), new CopyOnWriteArrayList<>())) {
            ((Consumer<E>) listener).accept(event);
        }
    }

    public int subscriberCount(Class<?> type) {
        return listeners.getOrDefault(type, new CopyOnWriteArrayList<>()).size();
    }

    public void clear() {
        listeners.clear();
    }
}
