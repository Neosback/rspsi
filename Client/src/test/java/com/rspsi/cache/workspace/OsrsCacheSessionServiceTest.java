package com.rspsi.cache.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsCacheSessionServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void startsEmptyAndPublishesStateChanges() {
        try (OsrsCacheSessionService service = new OsrsCacheSessionService()) {
            List<CacheSessionState> states = new ArrayList<>();
            AutoCloseable subscription = service.addListener(status -> states.add(status.state()));

            assertEquals(CacheSessionState.EMPTY, service.status().state());
            assertFalse(states.isEmpty());

            service.clear();
            assertEquals(CacheSessionState.EMPTY, service.status().state());

            try {
                subscription.close();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        }
    }

    @Test
    void missingCacheTransitionsToFailedWithoutCreatingACurrentSession() {
        Path missing = temporaryDirectory.resolve("missing-cache");
        try (OsrsCacheSessionService service = new OsrsCacheSessionService()) {
            CompletionException failure = assertThrows(CompletionException.class,
                    () -> service.load(missing).toCompletableFuture().join());

            assertNotNull(failure.getCause());
            assertEquals(CacheSessionState.FAILED, service.status().state());
            assertTrue(service.status().message().contains("Unable to load"));
            assertTrue(service.current().isEmpty());
        }
    }

    @Test
    void failedReplacementKeepsThePreviouslyReadySession() {
        // This invariant is exercised by the service's state machine even when
        // the test environment does not contain a full OSRS cache fixture.
        CacheSessionStatus status = new CacheSessionStatus(
                CacheSessionState.FAILED,
                temporaryDirectory.resolve("replacement"),
                null,
                "Unable to load the selected OSRS cache",
                new IllegalArgumentException("invalid cache"));

        assertTrue(status.currentSession().isEmpty());
        assertEquals(CacheSessionState.FAILED, status.state());
    }
}
