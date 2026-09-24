package com.rspsi.editor.plugin;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EditorExecutionServiceTest {

    @Test
    void backgroundWorkUsesOwnedDaemonWorker() throws Exception {
        try (EditorExecutionService execution =
                     new EditorExecutionService("test-execution", 1)) {
            AtomicReference<Thread> thread = new AtomicReference<>();
            execution.runAsync(() -> thread.set(Thread.currentThread()))
                    .get(2, TimeUnit.SECONDS);

            assertNotNull(thread.get());
            assertNotEquals(Thread.currentThread(), thread.get());
            assertTrue(thread.get().isDaemon());
            assertTrue(thread.get().getName().startsWith("test-execution-worker-"));
        }
    }

    @Test
    void debounceRunsOnlyLatestPendingTask() throws Exception {
        try (EditorExecutionService execution =
                     new EditorExecutionService("test-debounce", 1)) {
            AtomicInteger value = new AtomicInteger();
            CountDownLatch ran = new CountDownLatch(1);

            execution.debounce("preview", Duration.ofMillis(80),
                    () -> value.set(1));
            execution.debounce("preview", Duration.ofMillis(10), () -> {
                value.set(2);
                ran.countDown();
            });

            assertTrue(ran.await(2, TimeUnit.SECONDS));
            Thread.sleep(120);
            assertEquals(2, value.get());
            assertEquals(0, execution.pendingDebounceCount());
        }
    }

    @Test
    void closeCancelsPendingDebounceAndRejectsNewWork() throws Exception {
        EditorExecutionService execution =
                new EditorExecutionService("test-close", 1);
        AtomicInteger runs = new AtomicInteger();
        execution.debounce("later", Duration.ofSeconds(1), runs::incrementAndGet);

        execution.close();

        Thread.sleep(50);
        assertEquals(0, runs.get());
        assertTrue(execution.isClosed());
        assertThrows(IllegalStateException.class,
                () -> execution.runAsync(() -> { }));
    }
}
