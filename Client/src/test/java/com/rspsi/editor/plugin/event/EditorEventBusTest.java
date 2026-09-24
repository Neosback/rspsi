package com.rspsi.editor.plugin.event;

import com.rspsi.editor.plugin.EditorExecutionService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EditorEventBusTest {

    @Test
    void callerDispatchPreservesSynchronousBehavior() {
        EditorEventBus bus = new EditorEventBus();
        AtomicReference<String> value = new AtomicReference<>();
        bus.subscribe(String.class, value::set);

        bus.publish("ready");

        assertEquals("ready", value.get());
        assertEquals(1, bus.subscriberCount(String.class));
    }

    @Test
    void backgroundDispatchUsesHostExecutionService() throws Exception {
        try (EditorExecutionService execution = new EditorExecutionService("event-test")) {
            EditorEventBus bus = new EditorEventBus(execution);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Thread> listenerThread = new AtomicReference<>();

            bus.subscribe(String.class, EditorEventBus.Dispatch.BACKGROUND, event -> {
                listenerThread.set(Thread.currentThread());
                latch.countDown();
            });
            bus.publish("async");

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(listenerThread.get());
            assertNotEquals(Thread.currentThread(), listenerThread.get());
            assertTrue(listenerThread.get().getName().startsWith("event-test-worker-"));
        }
    }

    @Test
    void unsubscribeRemovesRegistrationWithoutAllocatingEmptyListenerLists() throws Exception {
        EditorEventBus bus = new EditorEventBus();
        Consumer<String> listener = ignored -> { };
        AutoCloseable registration = bus.subscribe(String.class, listener);

        registration.close();

        assertEquals(0, bus.subscriberCount(String.class));
        bus.publish("no listeners");
    }

    @Test
    void backgroundSubscriptionRequiresExecutionService() {
        EditorEventBus bus = new EditorEventBus();
        assertThrows(IllegalStateException.class,
                () -> bus.subscribe(String.class,
                        EditorEventBus.Dispatch.BACKGROUND, ignored -> { }));
    }
}
